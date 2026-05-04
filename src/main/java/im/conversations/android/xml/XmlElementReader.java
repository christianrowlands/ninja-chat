package im.conversations.android.xml;

import com.google.common.io.CharSource;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.XmlReader;
import im.conversations.android.xmpp.model.Extension;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class XmlElementReader {

    public static <T extends Extension> T read(final String input, final Class<T> clazz)
            throws IOException {
        final var element =
                read(CharSource.wrap(input).asByteSource(StandardCharsets.UTF_8).openStream());
        if (clazz.isInstance(element)) {
            return clazz.cast(element);
        }
        throw new IOException(
                String.format("Read unexpected {%s}%s", element.getNamespace(), element.getName()));
    }

    private static Element read(final InputStream inputStream) throws IOException {
        try (final XmlReader xmlReader = new XmlReader()) {
            xmlReader.setInputStream(inputStream);
            if (xmlReader.readTag() instanceof Tag.Start start) {
                return xmlReader.readElement(start);
            } else {
                throw new IOException("Document did not start with start tag");
            }
        }
    }
}
