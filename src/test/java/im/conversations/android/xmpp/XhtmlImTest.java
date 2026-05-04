package im.conversations.android.xmpp;

import im.conversations.android.xml.XmlElementReader;
import im.conversations.android.xmpp.model.stanza.Message;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class XhtmlImTest {

    @Test
    public void twoLists() throws IOException {
        final var xml =
"""
<message xmlns='jabber:client'>
  <body>Here&apos;s my .plan for today:
  1. Add the following examples to XEP-0071:
     - ordered and unordered lists
     - more styles (e.g., indentation)
  2. Kick back and relax
  </body>
  <html xmlns='http://jabber.org/protocol/xhtml-im'>
    <body xmlns='http://www.w3.org/1999/xhtml'>
      <p>Here&apos;s my .plan for today:</p>
      <ol>
        <li>Add the following examples to XEP-0071:
          <ul>
            <li>ordered and unordered lists</li>
            <li>more styles (e.g., indentation)</li>
          </ul>
        </li>
        <li>Kick back and relax</li>
      </ol>
    </body>
  </html>
</message>
""";
        final var message = XmlElementReader.read(xml, Message.class);
    }
}
