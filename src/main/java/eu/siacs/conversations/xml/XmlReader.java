package eu.siacs.conversations.xml;

import android.util.Xml;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.model.StreamElement;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.jspecify.annotations.NonNull;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class XmlReader implements Closeable {

    private static final int XML_ELEMENT_MAX_DEPTH = 128;

    private ParserInputStream parserInputStream;

    private static ParserInputStream of(final InputStream inputStream) throws IOException {
        final var parser = Xml.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(new InputStreamReader(inputStream));
            return new ParserInputStream(parser, inputStream);
        } catch (final XmlPullParserException e) {
            throw new IOException(e);
        }
    }

    public void setInputStream(final InputStream inputStream) throws IOException {
        this.parserInputStream = of(inputStream);
    }

    public void reset() throws IOException {
        final var current = this.parserInputStream;
        if (current == null) {
            throw new IOException("Unable to reset. No current parser");
        }
        this.parserInputStream = of(current.inputStream());
    }

    @Override
    public void close() {
        final var current = this.parserInputStream;
        if (current != null) {
            this.parserInputStream = null;
            Closeables.closeQuietly(current.inputStream());
        }
    }

    public @NonNull Tag readTag() throws IOException {
        try {
            while (parserInputStream != null
                    && parserInputStream.parser.next() != XmlPullParser.END_DOCUMENT) {
                final var parser = parserInputStream.parser;
                if (parserInputStream.parser.getEventType() == XmlPullParser.START_TAG) {
                    final var id =
                            new ExtensionFactory.Id(
                                    parser.getName(), parserInputStream.parser.getNamespace());
                    final var attrBuilder = new ImmutableMap.Builder<String, String>();
                    for (int i = 0; i < parser.getAttributeCount(); ++i) {
                        // TODO we would also look at parser.getAttributeNamespace()
                        final var value = parser.getAttributeValue(i);
                        final var prefix = parser.getAttributePrefix(i);
                        final String name;
                        if (Strings.isNullOrEmpty(prefix)) {
                            name = parser.getAttributeName(i);
                        } else {
                            name = prefix + ":" + parser.getAttributeName(i);
                        }
                        if (name != null && value != null) {
                            attrBuilder.put(name, value);
                        }
                    }
                    return new Tag.Start(id, attrBuilder.buildKeepingLast());
                } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                    final var id = new ExtensionFactory.Id(parser.getName(), parser.getNamespace());
                    return new Tag.End(id);
                } else if (parser.getEventType() == XmlPullParser.TEXT) {
                    return new Tag.No(parser.getText());
                }
            }

        } catch (final IOException e) {
            throw e;
        } catch (final Throwable throwable) {
            throw new IOException(
                    "xml parser mishandled "
                            + throwable.getClass().getSimpleName()
                            + "("
                            + throwable.getMessage()
                            + ")",
                    throwable);
        }
        throw new EOFException();
    }

    public <T extends StreamElement> T readElement(final Tag.Start current, final Class<T> clazz)
            throws IOException {
        final var element = readElement(current);
        if (clazz.isInstance(element)) {
            return clazz.cast(element);
        }
        throw new IOException(
                String.format("Read unexpected {%s}%s", element.getNamespace(), element.getName()));
    }

    public Element readElement(final Tag.Start currentTag) throws IOException {
        return readElement(currentTag, 0);
    }

    private Element readElement(final Tag.Start parent, final int depth) throws IOException {
        if (depth >= XML_ELEMENT_MAX_DEPTH) {
            throw new XmlMaxDepthReachedException();
        }
        final var id = parent.getId();
        final var element = ExtensionFactory.create(id);
        ;
        element.setAttributes(parent.getAttributes());
        while (true) {
            final var tag = this.readTag();
            switch (tag) {
                case Tag.Start innerStart -> {
                    final var child = this.readElement(innerStart, depth + 1);
                    element.addChild(child);
                }
                case Tag.No no -> {
                    if (element.getChildren().isEmpty()) {
                        element.setContent(no.getText());
                    }
                }
                case Tag.End end -> {
                    if (end.getId().equals(id)) {
                        return element;
                    } else {
                        throw new IOException("End tag did not match start tag");
                    }
                }
                default -> throw new IOException("Read invalid tag " + tag.getClass());
            }
        }
    }

    public static class XmlMaxDepthReachedException extends IOException {
        public XmlMaxDepthReachedException() {
            super("Reached maximum depth of XML stream");
        }
    }

    private record ParserInputStream(XmlPullParser parser, InputStream inputStream) {}
}
