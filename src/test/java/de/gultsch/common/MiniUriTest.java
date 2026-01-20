package de.gultsch.common;

import com.google.common.collect.Iterables;
import java.util.Objects;
import org.junit.Assert;
import org.junit.Test;

public class MiniUriTest {

    @Test
    public void httpsUrl() {
        final var miniUri = new MiniUri("https://example.com");
        Assert.assertEquals("https", miniUri.getScheme());
        Assert.assertEquals("example.com", miniUri.getAuthority());
        Assert.assertNull(miniUri.getPath());
    }

    @Test
    public void httpsUrlHtml() {
        final var miniUri = new MiniUri("https://example.com/test.html");
        Assert.assertEquals("https", miniUri.getScheme());
        Assert.assertEquals("example.com", miniUri.getAuthority());
        Assert.assertEquals("/test.html", miniUri.getPath());
    }

    @Test
    public void httpsUrlCgiFooBar() {
        final var miniUri = new MiniUri("https://example.com/test.cgi?foo=bar");
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
        final var miniUri = new MiniUri("xmpp:user@example.com");
        Assert.assertEquals("xmpp", miniUri.getScheme());
        Assert.assertNull(miniUri.getAuthority());
        Assert.assertEquals("user@example.com", miniUri.getPath());
    }

    @Test
    public void xmppUriJoin() {
        final var miniUri = new MiniUri("xmpp:room@chat.example.com?join");
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
                new MiniUri("xmpp:romeo@montague.net?message;body=Here%27s%20a%20test%20message");
        Assert.assertEquals("xmpp", miniUri.getScheme());
        Assert.assertNull(miniUri.getAuthority());
        Assert.assertEquals("romeo@montague.net", miniUri.getPath());
        final var parameter = miniUri.getParameter();
        Assert.assertTrue(parameter.containsKey("message"));
        Assert.assertEquals(
                "Here's a test message",
                Iterables.getOnlyElement(Objects.requireNonNull(parameter.get("body"))));
    }
}
