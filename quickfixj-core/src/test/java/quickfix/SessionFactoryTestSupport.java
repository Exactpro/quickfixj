package quickfix;

import quickfix.field.ApplVerID;
import quickfix.field.DefaultApplVerID;

public class SessionFactoryTestSupport implements SessionFactory {
    private static final SessionFactoryTestSupport instance = new SessionFactoryTestSupport();

    public Session create(SessionID sessionID, SessionSettings settings) throws ConfigError {
        if (sessionID == null) {
            sessionID = new SessionID("FIX.4.2", "SENDER", "TARGET");
        }
        return createSession(sessionID, new UnitTestApplication(), false);
    }

    public static Session createSession(SessionID sessionID, Application application,
            boolean isInitiator) {
        return new Session(application, new MemoryStoreFactory(), sessionID, null, null,
                new ScreenLogFactory(true, true, true), new DefaultMessageFactory(), isInitiator
                        ? 30
                        : 0);
    }

    public static Session createFileStoreSession(SessionID sessionID, Application application,
            boolean isInitiator, SessionSettings settings, SessionSchedule sessionSchedule) {
        return new Session(application, new FileStoreFactory(settings), sessionID, null,
                sessionSchedule,
                new ScreenLogFactory(true, true, true), new DefaultMessageFactory(), isInitiator
                        ? 30
                        : 0);
    }

    public static Session createSession(SessionID sessionID, Application application,
            boolean isInitiator, boolean resetOnLogon, boolean validateSequenceNumbers) {
        return new Session(application, new MemoryStoreFactory(), sessionID, null, null,
                new ScreenLogFactory(true, true, true), new DefaultMessageFactory(), isInitiator
                        ? 30
                        : 0, false, 30, true, false, resetOnLogon, false, false, false, false, false,
                true, false, 1.5, null, null, validateSequenceNumbers, new int[] { 5 }, false, false, false,
                true, false, true, false, null, true, 0, false, false, true, true);
    }

    public static Session createSession(SessionID sessionID, Application application,
            boolean isInitiator, boolean resetOnLogon, boolean validateSequenceNumbers,
            boolean useDataDictionary, DefaultApplVerID senderDefaultApplVerID) {
        return createSession(sessionID, application, isInitiator, resetOnLogon, validateSequenceNumbers, 
                useDataDictionary, senderDefaultApplVerID, null);
    }
    
    public static Session createSession(SessionID sessionID, Application application,
            boolean isInitiator, boolean resetOnLogon, boolean validateSequenceNumbers,
            boolean useDataDictionary, DefaultApplVerID senderDefaultApplVerID, ApplVerID targetDefaultApplVerID) {
        return new Session(application, new MemoryStoreFactory(), sessionID,
                new DefaultDataDictionaryProvider(), null, new ScreenLogFactory(true, true, true),
                new DefaultMessageFactory(), isInitiator ? 30 : 0, false, 30, true, false, resetOnLogon,
                false, false, false, false, false, true, false, 1.5, senderDefaultApplVerID,
                targetDefaultApplVerID, validateSequenceNumbers, new int[] { 5 }, false, false, false, true, false,
                true, false, null, true, 0, false, false, true, true);
    }

    public static Session createSession(SessionID sessionID, Application application,
            boolean isInitiator, boolean resetOnLogon) {
        return createSession(sessionID, application, isInitiator, resetOnLogon, false);
    }

    public static Session createNonpersistedSession(SessionID sessionID, Application application,
            boolean isInitiator) {
        return new Session(application, new MemoryStoreFactory(), sessionID, null, null,
                new ScreenLogFactory(true, true, true), new DefaultMessageFactory(), isInitiator
                        ? 30
                        : 0, false, 30, true, false, true, false, false, false, false, false,
                false/*persistMessages*/, false, 1.5, null, null, true, new int[] { 5 }, false,
                false, false, true, false, true, false, null, true, 0, false, false, true, true);
    }

    public static Session createSession() throws ConfigError {
        return instance.create(null, null);
    }
}
