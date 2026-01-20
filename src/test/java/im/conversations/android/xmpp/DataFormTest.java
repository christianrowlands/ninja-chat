package im.conversations.android.xmpp;

import com.google.common.collect.ImmutableMap;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.mam.Query;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

public class DataFormTest {

    @Test
    public void serialize() throws IOException {
        final var iq = new Iq(Iq.Type.SET);
        final var query = iq.addExtension(new Query());
        query.addExtension(
                Data.of(
                        ImmutableMap.of(
                                "with", Jid.of("juliet@capulet.lit"),
                                "before", Instant.ofEpochSecond(1876543210)),
                        Namespace.MESSAGE_ARCHIVE_MANAGEMENT));
        final var byteArrayOutputStream = new ByteArrayOutputStream();
        final var streamElementWriter = new StreamElementWriter(byteArrayOutputStream);
        streamElementWriter.write(iq);
        streamElementWriter.flush();
        Assert.assertEquals(
                """
                <iq type="set"><query xmlns="urn:xmpp:mam:2"><x xmlns="jabber:x:data" type="submit"><field var="FORM_TYPE" type="hidden"><value>urn:xmpp:mam:2</value></field><field var="with"><value>juliet@capulet.lit</value></field><field var="before"><value>2029-06-19T06:00:10Z</value></field></x></query></iq>\
                """,
                byteArrayOutputStream.toString());
    }
}
