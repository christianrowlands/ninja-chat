package im.conversations.android.xmpp;

import com.google.common.collect.ImmutableSet;
import im.conversations.android.xml.XmlElementReader;
import im.conversations.android.xmpp.model.reactions.Reactions;
import im.conversations.android.xmpp.model.stanza.Message;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class EmojiReactionsTest {

    @Test
    public void xep0444Example5() throws IOException {
        final var xml =
"""
<message xmlns='jabber:client' to='romeo@capulet.net/orchard' id='7fdd29fa-a57a-11e9-b04a-4889e7820c76' type='chat'>
  <reactions id='744f6e18-a57a-11e9-a656-4889e7820c76' xmlns='urn:xmpp:reactions:0'>
    <reaction>👋</reaction>
  </reactions>
  <store xmlns="urn:xmpp:hints"/>
</message>
""";
        final var message = XmlElementReader.read(xml, Message.class);
        final var reactions = message.getOnlyExtension(Reactions.class);
        Assert.assertEquals(ImmutableSet.of("\uD83D\uDC4B"), reactions.getReactions());
    }

    @Test
    public void xep0444Example5PlusInvalid() throws IOException {
        final var xml =
"""
<message xmlns='jabber:client' to='romeo@capulet.net/orchard' id='7fdd29fa-a57a-11e9-b04a-4889e7820c76' type='chat'>
  <reactions id='744f6e18-a57a-11e9-a656-4889e7820c76' xmlns='urn:xmpp:reactions:0'>
    <reaction>👋</reaction>
    <reaction>test</reaction>
  </reactions>
  <store xmlns="urn:xmpp:hints"/>
</message>
""";
        final var message = XmlElementReader.read(xml, Message.class);
        final var reactions = message.getOnlyExtension(Reactions.class);
        Assert.assertEquals(ImmutableSet.of("\uD83D\uDC4B"), reactions.getReactions());
    }

    @Test
    public void xep0444Example6() throws IOException {
        final var xml =
"""
<message to='romeo@capulet.net/orchard' id='96d73204-a57a-11e9-88b8-4889e7820c76' type='chat' xmlns='jabber:client'>
  <reactions id='744f6e18-a57a-11e9-a656-4889e7820c76' xmlns='urn:xmpp:reactions:0'>
    <reaction>👋</reaction>
    <reaction>🐢</reaction>
  </reactions>
  <store xmlns="urn:xmpp:hints"/>
</message>\
""";
        final var message = XmlElementReader.read(xml, Message.class);
        final var reactions = message.getOnlyExtension(Reactions.class);
        Assert.assertEquals(
                ImmutableSet.of("\uD83D\uDC4B", "\uD83D\uDC22"), reactions.getReactions());
    }

    @Test
    public void oneReactionTwoEmoji() throws IOException {
        final var xml =
"""
<message xmlns='jabber:client' to='romeo@capulet.net/orchard' id='7fdd29fa-a57a-11e9-b04a-4889e7820c76' type='chat'>
  <reactions id='744f6e18-a57a-11e9-a656-4889e7820c76' xmlns='urn:xmpp:reactions:0'>
    <reaction>👋🐢</reaction>
  </reactions>
  <store xmlns="urn:xmpp:hints"/>
</message>
""";
        final var message = XmlElementReader.read(xml, Message.class);
        final var reactions = message.getOnlyExtension(Reactions.class);
        Assert.assertTrue(reactions.getReactions().isEmpty());
    }
}
