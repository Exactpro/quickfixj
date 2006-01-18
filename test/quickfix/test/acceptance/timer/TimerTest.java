package quickfix.test.acceptance.timer;

import junit.framework.TestCase;
import quickfix.ConfigError;
import quickfix.SessionNotFound;

public class TimerTest extends TestCase {
    private Thread serverThread;

    public void testAcceptorTimer() {
        try {
            new TimerTestClient().run();
        } catch (ConfigError configError) {
            fail(configError.getMessage());
        } catch (SessionNotFound sessionNotFound) {
            fail(sessionNotFound.getMessage());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    protected void setUp() throws Exception {
        super.setUp();
        TimerTestServer server = new TimerTestServer();
        serverThread = new Thread(server, "TimerTestServer");
        serverThread.start();
        server.waitForInitialization();
    }

    protected void tearDown() throws Exception {
        serverThread.interrupt();
        super.tearDown();
    }
}