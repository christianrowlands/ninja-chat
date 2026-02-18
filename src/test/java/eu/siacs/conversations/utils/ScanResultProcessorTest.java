package eu.siacs.conversations.utils;

import de.gultsch.common.MiniUri;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ScanResultProcessorTest {

    @Test(expected = ExecutionException.class)
    public void justSomeWords() throws ExecutionException, InterruptedException {
        new ScanResultProcessor(RuntimeEnvironment.getApplication())
                .process("Just some words")
                .get();
    }

    @Test(expected = ExecutionException.class)
    public void aUriAndMore() throws ExecutionException, InterruptedException {
        new ScanResultProcessor(RuntimeEnvironment.getApplication())
                .process("xmpp:test@example.com what’s up?")
                .get();
    }

    @Test(expected = ExecutionException.class)
    public void helloUri() throws ExecutionException, InterruptedException {
        new ScanResultProcessor(RuntimeEnvironment.getApplication())
                .process("Hello xmpp:test@example.com")
                .get();
    }

    @Test
    public void xmppUri() throws ExecutionException, InterruptedException {
        final var miniUri =
                new ScanResultProcessor(RuntimeEnvironment.getApplication())
                        .process("xmpp:test@example.com")
                        .get();
        Assert.assertTrue(miniUri instanceof MiniUri.Xmpp);
    }

    @Test(expected = ExecutionException.class)
    public void xmppAuthority() throws ExecutionException, InterruptedException {
        new ScanResultProcessor(RuntimeEnvironment.getApplication())
                .process("xmpp://test@example.com")
                .get();
    }

    @Test(expected = ExecutionException.class)
    public void invalidXmppAddress() throws ExecutionException, InterruptedException {
        new ScanResultProcessor(RuntimeEnvironment.getApplication())
                .process("xmpp:te'st@example.com")
                .get();
    }

    @Test
    public void xmppUriInVCard() throws ExecutionException, InterruptedException {
        final String vCard =
"""
BEGIN:VCARD
VERSION:4.0
FN:Jane Doe
N:Doe;Jane;;;
IMPP;PREF=1:xmpp:jane.doe@example.com
END:VCARD
""";
        final var miniUri =
                new ScanResultProcessor(RuntimeEnvironment.getApplication()).process(vCard).get();
    }

    @Test(expected = ExecutionException.class)
    public void noXmppUriInVCard() throws ExecutionException, InterruptedException {
        final String vCard =
"""
BEGIN:VCARD
VERSION:4.0
FN:Jane Doe
N:Doe;Jane;;;
END:VCARD
""";
        new ScanResultProcessor(RuntimeEnvironment.getApplication()).process(vCard).get();
    }
}
