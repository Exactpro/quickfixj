/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved.
 *
 * This file is part of the QuickFIX FIX Engine
 *
 * This file may be distributed under the terms of the quickfixengine.org
 * license as defined by quickfixengine.org and appearing in the file
 * LICENSE included in the packaging of this file.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information.
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing
 * are not clear to you.
 ******************************************************************************/

package quickfix.mina.initiator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.filterchain.IoFilterChainBuilder;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.SocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.ConfigError;
import quickfix.LogUtil;
import quickfix.Session;
import quickfix.SystemTime;
import quickfix.mina.CompositeIoFilterChainBuilder;
import quickfix.mina.EventHandlingStrategy;
import quickfix.mina.NetworkingOptions;
import quickfix.mina.ProtocolFactory;
import quickfix.mina.message.FIXProtocolCodecFactory;
import quickfix.mina.ssl.SSLContextFactory;
import quickfix.mina.ssl.SSLSupport;

public class IoSessionInitiator {
    private final static long CONNECT_POLL_TIMEOUT = 2000L;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ConnectTask reconnectTask;

    private Future<?> reconnectFuture;
    protected final static Logger log = LoggerFactory.getLogger("display." + IoSessionInitiator.class.getName());

    public IoSessionInitiator(Session fixSession, SocketAddress[] socketAddresses, SocketAddress localAddress,
            int[] reconnectIntervalInSeconds, ScheduledExecutorService scheduledExecutorService,
            NetworkingOptions networkingOptions, EventHandlingStrategy eventHandlingStrategy,
            IoFilterChainBuilder userIoFilterChainBuilder, boolean sslEnabled, String keyStoreName,
            String keyStorePassword, String[] enableProtocole, String[] cipherSuites, Executor executor) throws ConfigError {
        this.scheduledExecutorService = scheduledExecutorService;
        final long[] reconnectIntervalInMillis = new long[reconnectIntervalInSeconds.length];
        for (int ii = 0; ii != reconnectIntervalInSeconds.length; ++ii) {
            reconnectIntervalInMillis[ii] = reconnectIntervalInSeconds[ii] * 1000L;
        }
        try {
            reconnectTask = new ConnectTask(sslEnabled, socketAddresses, localAddress, userIoFilterChainBuilder,
                    fixSession, reconnectIntervalInMillis, networkingOptions,
                    eventHandlingStrategy, keyStoreName, keyStorePassword, enableProtocole, cipherSuites, executor);
        } catch (GeneralSecurityException e) {
            throw new ConfigError(e);
        }
        log.info("[" + fixSession.getSessionID() + "] " + Arrays.asList(socketAddresses));
    }

    private static class ConnectTask implements Runnable {
        private final SocketAddress[] socketAddresses;
        private final SocketAddress localAddress;
        private final IoConnector ioConnector;
        private final IoProcessor ioProcessor;
        private final Session fixSession;
        private final long[] reconnectIntervalInMillis;
        private final String keyStoreName;
        private final String keyStorePassword;
        private final String[] enableProtocole;
        private final String[] cipherSuites;
        private final InitiatorIoHandler ioHandler;

        private IoSession ioSession;
        private long lastReconnectAttemptTime;
        private long lastConnectTime;
        private int nextSocketAddressIndex;
        private int connectionFailureCount;
        private ConnectFuture connectFuture;

        public ConnectTask(boolean sslEnabled, SocketAddress[] socketAddresses,
                SocketAddress localAddress, IoFilterChainBuilder userIoFilterChainBuilder, Session fixSession,
                long[] reconnectIntervalInMillis, NetworkingOptions networkingOptions,
                EventHandlingStrategy eventHandlingStrategy, String keyStoreName,
                String keyStorePassword, String[] enableProtocole, String[] cipherSuites, Executor executor) throws ConfigError, GeneralSecurityException {
            this.socketAddresses = socketAddresses;
            this.localAddress = localAddress;
            this.fixSession = fixSession;
            this.reconnectIntervalInMillis = reconnectIntervalInMillis;
            this.keyStoreName = keyStoreName;
            this.keyStorePassword = keyStorePassword;
            this.enableProtocole = enableProtocole;
            this.cipherSuites = cipherSuites;
            Map.Entry<IoConnector, IoProcessor> entry = ProtocolFactory.createIoConnector(socketAddresses[0], executor);
            this.ioConnector = entry.getKey();
            this.ioProcessor = entry.getValue();
            CompositeIoFilterChainBuilder ioFilterChainBuilder = new CompositeIoFilterChainBuilder(
                    userIoFilterChainBuilder);

            if (sslEnabled) {
                installSslFilter(ioFilterChainBuilder);
            }

            ioFilterChainBuilder.addLast(FIXProtocolCodecFactory.FILTER_NAME,
                    new ProtocolCodecFilter(new FIXProtocolCodecFactory()));

            this.ioConnector.setFilterChainBuilder(ioFilterChainBuilder);
            ioHandler = new InitiatorIoHandler(fixSession, networkingOptions,
                    eventHandlingStrategy);
        }

        private void installSslFilter(CompositeIoFilterChainBuilder ioFilterChainBuilder)
                throws GeneralSecurityException {
            SslFilter sslFilter = new SslFilter(SSLContextFactory.getInstance(keyStoreName,
                    keyStorePassword.toCharArray()));
            if (enableProtocole != null)
                sslFilter.setEnabledProtocols(enableProtocole);
            if (cipherSuites != null)
                sslFilter.setEnabledCipherSuites(cipherSuites);
            sslFilter.setUseClientMode(true);
            ioFilterChainBuilder.addLast(SSLSupport.FILTER_NAME, sslFilter);
        }

        public synchronized void run() {
            if (connectFuture == null) {
                if (shouldReconnect()) {
                    connect();
                }
            } else {
                pollConnectFuture();
            }
        }

        private void connect() {
            lastReconnectAttemptTime = SystemTime.currentTimeMillis();
            SocketAddress nextSocketAddress = getNextSocketAddress();
            ioConnector.setHandler(ioHandler);
            try {
                if (localAddress == null) {
                    connectFuture = ioConnector.connect(nextSocketAddress);
                } else {
                    // QFJ-482
                    connectFuture = ioConnector.connect(nextSocketAddress, localAddress);
                }
                pollConnectFuture();
            } catch (Throwable e) {
                handleConnectException(e);
            }
        }

        private void pollConnectFuture() {
            try {
                connectFuture.awaitUninterruptibly(CONNECT_POLL_TIMEOUT);
                if (connectFuture.getSession() != null) {
                    ioSession = connectFuture.getSession();
                    connectionFailureCount = 0;
                    lastConnectTime = System.currentTimeMillis();
                    connectFuture = null;
                } else {
                    fixSession.getLog().onEvent(
                            "Pending connection not established after "
                                    + (System.currentTimeMillis() - lastReconnectAttemptTime)
                                    + " ms.");
                }
            } catch (Throwable e) {
                handleConnectException(e);
            }
        }

        private void handleConnectException(Throwable e) {
            ++connectionFailureCount;
            while (e.getCause() != null) {
                e = e.getCause();
            }
            final String nextRetryMsg = " (Next retry in " + computeNextRetryConnectDelay() + " milliseconds)";
            if (e instanceof IOException) {
                fixSession.getLog().onErrorEvent(e.getClass().getName() + ": " + e + nextRetryMsg);
            } else {
                LogUtil.logThrowable(fixSession.getLog(), "Exception during connection" + nextRetryMsg, e);
            }
            connectFuture = null;
            fixSession.onConnectionProblem("Exception during connection" + nextRetryMsg);
        }

        private SocketAddress getNextSocketAddress() {
            SocketAddress socketAddress = socketAddresses[nextSocketAddressIndex];

            // QFJ-266 Recreate socket address for unresolved addresses
            if (socketAddress instanceof InetSocketAddress) {
                InetSocketAddress inetAddr = (InetSocketAddress) socketAddress;
                if (inetAddr.isUnresolved()) {
                    socketAddress = new InetSocketAddress(inetAddr.getHostName(), inetAddr
                            .getPort());
                    socketAddresses[nextSocketAddressIndex] = socketAddress;
                }
            }
            nextSocketAddressIndex = (nextSocketAddressIndex + 1) % socketAddresses.length;
            return socketAddress;
        }

        private boolean shouldReconnect() {
            return (ioSession == null || !ioSession.isConnected()) && isTimeForReconnect()
                    && (fixSession.isEnabled() && fixSession.isSessionTime());
        }

        private long computeNextRetryConnectDelay() {
            int index = connectionFailureCount - 1;
            if (index < 0)
                index = 0;
            long millis;
            if (index >= reconnectIntervalInMillis.length) {
                millis = reconnectIntervalInMillis[reconnectIntervalInMillis.length - 1];
            } else {
                millis = reconnectIntervalInMillis[index];
            }
            return millis;
        }

        private boolean isTimeForReconnect() {
            return SystemTime.currentTimeMillis() - lastReconnectAttemptTime >= computeNextRetryConnectDelay();
        }

        public void close(){
            if(ioConnector instanceof SocketConnector){
                SocketConnector socketConnector = (SocketConnector)ioConnector;
                socketConnector.dispose();
                if(ioProcessor != null){
                    ioProcessor.dispose();
                }
            }
        }

        // TODO JMX Expose reconnect property

        @SuppressWarnings("unused") // exposed via JMX
        public synchronized int getConnectionFailureCount() {
            return connectionFailureCount;
        }

        @SuppressWarnings("unused") // exposed via JMX
        public synchronized long getLastReconnectAttemptTime() {
            return lastReconnectAttemptTime;
        }

        @SuppressWarnings("unused") // exposed via JMX
        public synchronized long getLastConnectTime() {
            return lastConnectTime;
        }

        public Session getFixSession() {
            return fixSession;
        }
    }

    synchronized void start() {
        if (reconnectFuture == null) {
            // The following logon reenabled the session. The actual logon will take
            // place as a side-effect of the session timer task (not the reconnect task).
            reconnectTask.getFixSession().logon(); // only enables the session
            reconnectFuture = scheduledExecutorService
                    .scheduleWithFixedDelay(reconnectTask, 0, 1, TimeUnit.SECONDS);
        }
    }

    synchronized void stop() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
            reconnectFuture = null;
        }
        reconnectTask.close();
    }
}
