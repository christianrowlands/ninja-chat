package de.gultsch.common;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.xmpp.Jid;
import java.util.Collections;
import java.util.Objects;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class MiniUriTest {

    @Test
    public void httpsUrl() {
        final var miniUri = MiniUri.tryParse("https://example.com");
        Assert.assertEquals("https", miniUri.getScheme());
        Assert.assertEquals("example.com", miniUri.getAuthority());
        Assert.assertNull(miniUri.getPath());
    }

    @Test
    public void httpsUrlHtml() {
        final var miniUri = MiniUri.tryParse("https://example.com/test.html");
        Assert.assertEquals("https", miniUri.getScheme());
        Assert.assertEquals("example.com", miniUri.getAuthority());
        Assert.assertEquals("/test.html", miniUri.getPath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidHttpUrl() {
        MiniUri.tryParse("https://example.com:70000");
    }

    @Test
    public void httpsUrlCgiFooBar() {
        final var miniUri = MiniUri.tryParse("https://example.com/test.cgi?foo=bar");
        Assert.assertEquals("https", miniUri.getScheme());
        Assert.assertEquals("example.com", miniUri.getAuthority());
        Assert.assertEquals("/test.cgi", miniUri.getPath());
        final var parameter = miniUri.getParameter();
        Assert.assertEquals(1, parameter.size());
        Assert.assertEquals(
                "bar", Iterables.getOnlyElement(Objects.requireNonNull(parameter.get("foo"))));
    }

    @Test
    public void xmppUri() {
        final var miniUri = MiniUri.tryParse("xmpp:user@example.com");
        Assert.assertEquals("xmpp", miniUri.getScheme());
        Assert.assertNull(miniUri.getAuthority());
        Assert.assertEquals("user@example.com", miniUri.getPath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidXmppUri() {
        MiniUri.tryParse("xmpp:foo'bar@example.com");
    }

    @Test
    public void xmppUriJoin() {
        final var miniUri = MiniUri.tryParse("xmpp:room@chat.example.com?join");
        Assert.assertEquals("xmpp", miniUri.getScheme());
        Assert.assertNull(miniUri.getAuthority());
        Assert.assertEquals("room@chat.example.com", miniUri.getPath());
        final var parameter = miniUri.getParameter();
        Assert.assertEquals(1, parameter.size());
        Assert.assertTrue(parameter.containsKey("join"));
    }

    @Test
    public void xmppUriMessage() {
        final var miniUri =
                MiniUri.tryParse(
                        "xmpp:romeo@montague.net?message;body=Here%27s%20a%20test%20message");
        Assert.assertEquals("xmpp", miniUri.getScheme());
        Assert.assertNull(miniUri.getAuthority());
        Assert.assertEquals("romeo@montague.net", miniUri.getPath());
        final var parameter = miniUri.getParameter();
        Assert.assertTrue(parameter.containsKey("message"));
        Assert.assertEquals(
                "Here's a test message",
                Iterables.getOnlyElement(Objects.requireNonNull(parameter.get("body"))));
    }

    @Test
    public void imtoJabber() {
        final var miniUri = MiniUri.tryInternalParse("imto://xmpp/test%40example.com");
        Assert.assertTrue(miniUri instanceof MiniUri.Imto);
        final var xmppUri = ((MiniUri.Imto) miniUri).transform();
        Assert.assertEquals(Jid.of("test@example.com"), xmppUri.asJid());
    }

    @Test
    public void legacyInviteDomain() {
        final var miniUri = MiniUri.tryParse("https://conversations.im/i/test@example.com");
        Assert.assertTrue(miniUri instanceof MiniUri.Transformable);
        final var transformed = ((MiniUri.Transformable) miniUri).transform();
        Assert.assertTrue(transformed instanceof MiniUri.Xmpp);
        Assert.assertEquals(Jid.of("test@example.com"), ((MiniUri.Xmpp) transformed).asJid());
    }

    @Test
    public void legacyInviteDomainWithParameter() {
        final var miniUri =
                MiniUri.tryParse(
                        "https://conversations.im/i/test@example.com?omemo-sid-123=deadbeef&omemo-sid-456=deadbeef2&omemo=deadbeef3");
        Assert.assertTrue(miniUri instanceof MiniUri.Transformable);
        final var transformed = ((MiniUri.Transformable) miniUri).transform();
        Assert.assertTrue(transformed instanceof MiniUri.Xmpp);
        Assert.assertEquals(Jid.of("test@example.com"), ((MiniUri.Xmpp) transformed).asJid());
        Assert.assertTrue(transformed.getParameter().containsKey("omemo-sid-123"));
        Assert.assertTrue(transformed.getParameter().containsKey("omemo-sid-456"));
        Assert.assertEquals(
                ImmutableSet.of("deadbeef", "deadbeef2", "deadbeef3"),
                ((MiniUri.Xmpp) transformed).getOmemoFingerprints());
    }

    @Test
    public void legacyInviteConference() {
        final var miniUri =
                MiniUri.tryParse("https://conversations.im/j/test@conference.example.com");
        Assert.assertTrue(miniUri instanceof MiniUri.Transformable);
        final var transformed = ((MiniUri.Transformable) miniUri).transform();
        Assert.assertTrue(transformed instanceof MiniUri.Xmpp);
        Assert.assertEquals(
                Jid.of("test@conference.example.com"), ((MiniUri.Xmpp) transformed).asJid());
        Assert.assertTrue(transformed.getParameter().containsKey("join"));
    }

    @Test
    public void modernInvite() {
        final var miniUri = MiniUri.tryParse("https://invite.joinjabber.org/#test@example.com");
        Assert.assertTrue(miniUri instanceof MiniUri.Transformable);
        final var transformed = ((MiniUri.Transformable) miniUri).transform();
        Assert.assertTrue(transformed instanceof MiniUri.Xmpp);
        Assert.assertEquals(Jid.of("test@example.com"), ((MiniUri.Xmpp) transformed).asJid());
    }

    @Test
    public void modernInviteConference() {
        final var miniUri =
                MiniUri.tryParse(
                        "https://invite.joinjabber.org/#test@conference.example.com%3Fjoin");
        Assert.assertTrue(miniUri instanceof MiniUri.Transformable);
        final var transformed = ((MiniUri.Transformable) miniUri).transform();
        Assert.assertTrue(transformed instanceof MiniUri.Xmpp);
        Assert.assertEquals(
                Jid.of("test@conference.example.com"), ((MiniUri.Xmpp) transformed).asJid());
        Assert.assertTrue(transformed.getParameter().containsKey("join"));
    }

    @Test
    public void xmppMessageUri() {
        final var miniUri = MiniUri.tryParse("xmpp:?message;body=Hi");
        Assert.assertTrue(miniUri instanceof MiniUri.Xmpp);
        Assert.assertEquals("Hi", miniUri.getParameter("body"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void xmppUriEmpty() {
        MiniUri.tryParse("xmpp:?");
    }

    @Test
    public void invitationUri() {
        final var uri =
                new MiniUri.Xmpp(
                        Jid.of("test@conference.example.com"),
                        ImmutableMap.of(
                                MiniUri.Xmpp.ACTION_JOIN,
                                Collections.singleton(MiniUri.EMPTY_STRING)));
        Assert.assertEquals(
                "https://xmpp.link/#test%40conference.example.com%3Fjoin",
                uri.asInvitationUri().asUri().toString());
    }
}
