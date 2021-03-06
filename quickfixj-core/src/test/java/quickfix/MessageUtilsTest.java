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

package quickfix;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import junit.framework.TestCase;
import quickfix.field.ApplVerID;
import quickfix.field.BeginString;
import quickfix.field.DefaultApplVerID;
import quickfix.field.EmailThreadID;
import quickfix.field.EmailType;
import quickfix.field.EncryptMethod;
import quickfix.field.HeartBtInt;
import quickfix.field.MsgType;
import quickfix.field.SenderCompID;
import quickfix.field.Subject;
import quickfix.field.TargetCompID;
import quickfix.fix40.Logon;
import quickfix.fix50.Email;

public class MessageUtilsTest extends TestCase {
    public void testGetStringField() throws Exception {
        String messageString = "8=FIX.4.2\0019=12\00135=X\001108=30\00110=049\001";
        assertEquals("wrong value", "FIX.4.2", MessageUtils.getStringField(messageString,
                BeginString.FIELD));
        assertEquals("wrong value", "X", MessageUtils.getStringField(messageString, MsgType.FIELD));
        assertNull(messageString, MessageUtils.getStringField(messageString, SenderCompID.FIELD));
    }

    public void testSessionIdFromMessage() throws Exception {
        Message message = new Logon();
        message.getHeader().setString(SenderCompID.FIELD, "TW");
        message.getHeader().setString(TargetCompID.FIELD, "ISLD");
        SessionID sessionID = MessageUtils.getSessionID(message);
        assertEquals(sessionID.getBeginString(), "FIX.4.0");
        assertEquals("TW", sessionID.getSenderCompID());
        assertEquals("ISLD", sessionID.getTargetCompID());
    }

    public void testReverseSessionIdFromMessage() throws Exception {
        Message message = new Logon();
        message.getHeader().setString(SenderCompID.FIELD, "TW");
        message.getHeader().setString(TargetCompID.FIELD, "ISLD");
        SessionID sessionID = MessageUtils.getReverseSessionID(message);
        assertEquals(sessionID.getBeginString(), "FIX.4.0");
        assertEquals("ISLD", sessionID.getSenderCompID());
        assertEquals("TW", sessionID.getTargetCompID());
    }

    public void testReverseSessionIdFromMessageWithMissingFields() throws Exception {
        Message message = new Logon();
        SessionID sessionID = MessageUtils.getReverseSessionID(message);
        assertEquals(sessionID.getBeginString(), "FIX.4.0");
        assertEquals(sessionID.getSenderCompID(), SessionID.NOT_SET);
        assertEquals(sessionID.getTargetCompID(), SessionID.NOT_SET);
    }

    public void testSessionIdFromRawMessage() throws Exception {
        String messageString = "8=FIX.4.0\0019=56\00135=A\00134=1\00149=TW\001" +
            "52=20060118-16:34:19\00156=ISLD\00198=0\001108=2\00110=223\001";
        SessionID sessionID = MessageUtils.getSessionID(messageString);
        assertEquals(sessionID.getBeginString(), "FIX.4.0");
        assertEquals("TW", sessionID.getSenderCompID());
        assertEquals("ISLD", sessionID.getTargetCompID());
    }

    public void testReverseSessionIdFromRawMessage() throws Exception {
        String messageString = "8=FIX.4.0\0019=56\00135=A\00134=1\00149=TW\00150=TWS\001" +
            "142=TWL\00152=20060118-16:34:19\00156=ISLD\00198=0\001108=2\00110=223\001";
        SessionID sessionID = MessageUtils.getReverseSessionID(messageString);
        assertEquals(sessionID.getBeginString(), "FIX.4.0");
        assertEquals("ISLD", sessionID.getSenderCompID());
        assertEquals("TW", sessionID.getTargetCompID());
        assertEquals("TWS", sessionID.getTargetSubID());
        assertEquals("TWL", sessionID.getTargetLocationID());
    }

    public void testMessageType() throws Exception {
        String messageString = "8=FIX.4.0\0019=56\00135=A\00134=1\00149=TW\001" +
            "52=20060118-16:34:19\00156=ISLD\00198=0\001108=2\00110=223\001";
        assertEquals("A", MessageUtils.getMessageType(messageString));
    }

    public void testMessageTypeError() throws Exception {
        String messageString = "8=FIX.4.0\0019=56\00134=1\00149=TW\001" +
            "52=20060118-16:34:19\00156=ISLD\00198=0\001108=2\00110=223\001";
        try {
            MessageUtils.getMessageType(messageString);
            fail("expected exception");
        } catch (InvalidMessage e) {
            // expected
        }
    }

    public void testMessageTypeError2() throws Exception {
        String messageString = "8=FIX.4.0\0019=56\00135=1";
        try {
            MessageUtils.getMessageType(messageString);
            fail("expected exception");
        } catch (InvalidMessage e) {
            // expected
        }
    }

    public void testGetNonexistentStringField() throws Exception {
        String messageString = "8=FIX.4.0\0019=56\00134=1\00149=TW\001" +
            "52=20060118-16:34:19\00156=ISLD\00198=0\001108=2\00110=223\001";
        assertNull(MessageUtils.getStringField(messageString, 35));
    }

    public void testGetStringFieldWithBadValue() throws Exception {
        String messageString = "8=FIX.4.0\0019=56\00134=1\00149=TW\001" +
            "52=20060118-16:34:19\00156=ISLD\00198=0\001108=2\00110=223";
        assertNull(MessageUtils.getStringField(messageString, 10));
    }

    public void testParse() throws Exception {
        Session mockSession = mock(Session.class);
        DataDictionaryProvider mockDataDictionaryProvider = mock(DataDictionaryProvider.class);
        stub(mockSession.getDataDictionaryProvider()).toReturn(mockDataDictionaryProvider);
        stub(mockSession.getMessageFactory()).toReturn(new quickfix.fix40.MessageFactory());
        String messageString = "8=FIX.4.0\0019=56\00135=A\00134=1\00149=TW\001" +
            "52=20060118-16:34:19\00156=ISLD\00198=0\001108=2\00110=223\001";

        Message message = MessageUtils.parse(mockSession, messageString);

        assertThat(message, is(notNullValue()));
    }

    public void testLegacyParse() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=ASX\00156=CL1_FIX44\00134=4\001" +
            "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001" +
            "39=0\00111=184271\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001" +
            "37=B-WOW-1494E9A0:58BD3F9D\00155=WOW\00154=1\001151=200\00114=0\00140=2\001" +
            "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001452=3\001448=8\001" +
            "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=169\001";

        Message message = MessageUtils.parse(new quickfix.fix40.MessageFactory(), DataDictionaryTest.getDictionary(), data);
        assertThat(message, is(notNullValue()));
    }

    public void testDuplicateTagsHeaderFirstTag() throws Exception {
        try {
            String data = "8=FIX.4.4\0018=FIX.4.4\0019=309\0019=309\00135=8\00149=SENDER\00156=CL1_FIX44\00134=4\001"
                    + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                    + "39=0\00111=184271\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001"
                    + "37=B-WOW-1494E9A0:58BD3F9D\00155=WOW\00154=1\001151=200\00114=0\00140=2\001"
                    + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001452=3\001448=8\001"
                    + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=169\001";
            Message message = MessageUtils.parse(new quickfix.fix44.MessageFactory(), DataDictionaryTest.getDictionary(), data);
            fail("Message with duplicate header tags was parsed");
        } catch (InvalidMessage e) {
            //expected
        }
    }

    public void testDuplicateTagsHeader() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=SENDER\00149=SENDER\00156=CL1_FIX44\00134=4\001"
                + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                + "39=0\00111=184271\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001"
                + "37=B-WOW-1494E9A0:58BD3F9D\00155=WOW\00154=1\001151=200\00114=0\00140=2\001"
                + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001452=3\001448=8\001"
                + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=169\001";
        Message message = new Message();
        message.parse(data, DataDictionaryTest.getDictionary(),DataDictionaryTest.getDictionary(), true, false);
        assertEquals(message.getException().getMessage(), "Tag appears more than once, field=49");
    }

    public void testDuplicateTagsHeaderWithTrueDuplicateTags() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=SENDER\00149=SENDER2\00156=CL1_FIX44\00134=4\001"
                + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                + "39=0\00111=184271\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001"
                + "37=B-WOW-1494E9A0:58BD3F9D\00155=WOW\00154=1\001151=200\00114=0\00140=2\001"
                + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001452=3\001448=8\001"
                + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=28\001";
        Message message = new Message();
        message.parse(data, DataDictionaryTest.getDictionary(),DataDictionaryTest.getDictionary(), true, true);
        assertEquals(message.getHeader().getField(49).getValue(), "SENDER2");
    }

    public void testDuplicateTagsBody() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=SENDER\00156=CL1_FIX44\00134=4\001"
                + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                + "39=0\00111=184271\00138=200\00138=200\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001" + "37=B-WOW-1494E9A0:58BD3F9D\001"
                + "55=WOW\00155=WOW\00154=1\001151=200\001151=200\001151=200\00155=WOW\00155=WOW\001"
                + "151=200\001151=200\001151=200\001151=200\001151=200\00114=0\00140=2\001"
                + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001452=3\001448=8\001"
                + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=169\001";
        Message message = new Message();
        message.parse(data, DataDictionaryTest.getDictionary(),DataDictionaryTest.getDictionary(), true, false);
        assertEquals(message.getException().getMessage(), "Tag appears more than once, field=38");
    }

    public void testDuplicateTagsBodyWithTrueDuplicateTags() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=SENDER\00156=CL1_FIX44\00134=4\001"
                + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                + "39=0\00111=184271\00138=200\00138=200\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001" + "37=B-WOW-1494E9A0:58BD3F9D\001"
                + "55=WOW\00155=WOW\00154=1\001151=200\001151=200\001151=200\00155=WOW\00155=WOW\001"
                + "151=200\001151=200\001151=200\001151=200\001151=208\00114=0\00140=2\001"
                + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001452=3\001448=8\001"
                + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=188\001";
        Message message = new Message();
        message.parse(data, DataDictionaryTest.getDictionary(),DataDictionaryTest.getDictionary(), true, true);
        assertEquals(message.getField(55).getValue(), "WOW");
        assertEquals(message.getField(151).getValue(), "208");
    }

    public void testDuplicateTagsGroup() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=SENDER\00156=CL1_FIX44\00134=4\001"
                + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                + "39=0\00111=184271\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001"
                + "37=B-WOW-1494E9A0:58BD3F9D\00155=WOW\00154=1\001151=200\00114=0\00140=2\001"
                + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001447=A\001452=3\001448=8\001"
                + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=199\001";
        Message message = new Message();
        message.parse(data, DataDictionaryTest.getDictionary(),DataDictionaryTest.getDictionary(), true, false);
        assertEquals(message.getException().getMessage(), "Tag appears more than once, field=447");
    }

    public void testDuplicateTagsGroupWithTrueDuplicateTags() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=SENDER\00156=CL1_FIX44\00134=4\001"
                + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                + "39=0\00111=184271\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001"
                + "37=B-WOW-1494E9A0:58BD3F9D\00155=WOW\00154=1\001151=200\00114=0\00140=2\001"
                + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001447=A\001452=3\001448=8\001"
                + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=156\001";
        Message message = new Message();
        message.parse(data, DataDictionaryTest.getDictionary(),DataDictionaryTest.getDictionary(), true, true);
        assertEquals(message.getGroups(453).get(0).getField(447).getValue(), "A");
    }

    public void testDuplicateTagsTrailer() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=SENDER\00156=CL1_FIX44\00134=4\001"
                + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                + "39=0\00111=184271\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001"
                + "37=B-WOW-1494E9A0:58BD3F9D\00155=WOW\00154=1\001151=200\00114=0\00140=2\001"
                + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001452=3\001448=8\001"
                + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=169\00193=4\00189=aaaa\00189=aaaa\00110=169\001";
        Message message = new Message();
        message.parse(data, DataDictionaryTest.getDictionary(),DataDictionaryTest.getDictionary(), true, false);
        assertEquals(message.getException().getMessage(), "Tag appears more than once, field=89");
    }

    public void testDuplicateTagsTrailerWithTrueDuplicateTags() throws Exception {
        String data = "8=FIX.4.4\0019=309\00135=8\00149=SENDER\00156=CL1_FIX44\00134=4\001"
                + "52=20060324-01:05:58\00117=X-B-WOW-1494E9A0:58BD3F9D-1109\001150=D\001"
                + "39=0\00111=184271\00138=200\001198=1494E9A0:58BD3F9D\001526=4324\001"
                + "37=B-WOW-1494E9A0:58BD3F9D\00155=WOW\00154=1\001151=200\00114=0\00140=2\001"
                + "44=15\00159=1\0016=0\001453=3\001448=AAA35791\001447=D\001452=3\001448=8\001"
                + "447=D\001452=4\001448=FIX11\001447=D\001452=36\00160=20060320-03:34:29\00110=169\00193=4\00189=aaaa\00189=aaab\00110=2\001";
        Message message = new Message();
        message.parse(data, DataDictionaryTest.getDictionary(),DataDictionaryTest.getDictionary(), true, true);
        assertEquals(message.getTrailer().getField(89).getValue(), "aaab");

    }

    public void testParseFixt() throws Exception {
        Session mockSession = mock(Session.class);
        DataDictionaryProvider mockDataDictionaryProvider = mock(DataDictionaryProvider.class);
        stub(mockSession.getDataDictionaryProvider()).toReturn(mockDataDictionaryProvider);
        stub(mockSession.getMessageFactory()).toReturn(new quickfix.fix40.MessageFactory());

        Email email = new Email(new EmailThreadID("THREAD_ID"), new EmailType(EmailType.NEW), new Subject("SUBJECT"));
        email.getHeader().setField(new ApplVerID(ApplVerID.FIX42));
        email.getHeader().setField(new SenderCompID("SENDER"));
        email.getHeader().setField(new TargetCompID("TARGET"));

        Message message = MessageUtils.parse(mockSession, email.toString());

        assertThat(message, is(notNullValue()));
        assertThat((quickfix.fix40.Email)message, is(quickfix.fix40.Email.class));
    }

    public void testParseFixtLogon() throws Exception {
        Session mockSession = mock(Session.class);
        DataDictionaryProvider mockDataDictionaryProvider = mock(DataDictionaryProvider.class);
        stub(mockSession.getDataDictionaryProvider()).toReturn(mockDataDictionaryProvider);
        stub(mockSession.getMessageFactory()).toReturn(new DefaultMessageFactory());

        quickfix.fixt11.Logon logon = new quickfix.fixt11.Logon(new EncryptMethod(EncryptMethod.NONE_OTHER), new HeartBtInt(30),
                new DefaultApplVerID(ApplVerID.FIX42));

        Message message = MessageUtils.parse(mockSession, logon.toString());

        assertThat(message, is(notNullValue()));
        assertThat((quickfix.fixt11.Logon)message, is(quickfix.fixt11.Logon.class));
    }

    public void testParseFix50() throws Exception {
        Session mockSession = mock(Session.class);
        DataDictionaryProvider mockDataDictionaryProvider = mock(DataDictionaryProvider.class);
        stub(mockSession.getDataDictionaryProvider()).toReturn(mockDataDictionaryProvider);
        stub(mockSession.getMessageFactory()).toReturn(new DefaultMessageFactory());

        Email email = new Email(new EmailThreadID("THREAD_ID"), new EmailType(EmailType.NEW), new Subject("SUBJECT"));
        email.getHeader().setField(new ApplVerID(ApplVerID.FIX50));
        email.getHeader().setField(new SenderCompID("SENDER"));
        email.getHeader().setField(new TargetCompID("TARGET"));

        Message message = MessageUtils.parse(mockSession, email.toString());

        assertThat(message, is(notNullValue()));
        assertThat((quickfix.fix50.Email)message, is(quickfix.fix50.Email.class));
    }
}
