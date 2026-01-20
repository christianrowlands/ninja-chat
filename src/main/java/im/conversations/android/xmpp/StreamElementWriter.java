package im.conversations.android.xmpp;

import android.util.Log;
import com.google.common.collect.ImmutableMap;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Tag;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.StreamElement;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class StreamElementWriter extends BufferedWriter {

    private static final Map<Character, String> ENTITIES =
            new ImmutableMap.Builder<Character, String>()
                    .put('"', "quot")
                    .put('\'', "apos")
                    .put('<', "lt")
                    .put('>', "gt")
                    .put('&', "amp")
                    .build();
    private static final Collection<Character> ALLOWED_CONTROL_CHARS =
            Arrays.asList('\n', '\t', '\r');

    public StreamElementWriter(final OutputStream outputStream) {
        super(new OutputStreamWriter(outputStream));
    }

    public void write(final Tag tag, final String namespace) throws IOException {
        this.write('<');
        if (tag.getType() == Tag.Type.END) {
            this.write('/');
        }

        this.write(tag.getName());

        if (tag.getType() == Tag.Type.START || tag.getType() == Tag.Type.EMPTY) {
            if (tag.getNamespace() != null && !Objects.equals(tag.getNamespace(), namespace)) {
                this.write(' ');
                this.write("xmlns=");
                this.write('"');
                this.write(tag.getNamespace());
                this.write('"');
            }
            for (final var entry : tag.getAttributes().entrySet()) {
                this.write(' ');
                this.write(entry.getKey());
                this.write('=');
                this.write('"');
                this.writeEncoded(entry.getValue());
                this.write('"');
            }
        }
        if (tag.getType() == Tag.Type.EMPTY) {
            this.write('/');
        }
        this.write('>');
    }

    public void write(final StreamElement streamElement) throws IOException {
        this.write(streamElement, Namespace.JABBER_CLIENT);
    }

    public void write(final Tag tag) throws IOException {
        this.write(tag, null);
    }

    private void write(final Element element, final String namespace) throws IOException {
        final var content = element.getContent();
        final var children = element.getChildren();
        if (content == null && children.isEmpty()) {
            final Tag emptyTag = Tag.empty(element.getName(), element.getNamespace());
            emptyTag.setAttributes(element.getAttributes());
            this.write(emptyTag, namespace);
        } else {
            final Tag startTag = Tag.start(element.getName(), element.getNamespace());
            startTag.setAttributes(element.getAttributes());
            this.write(startTag, namespace);
            if (content != null) {
                this.writeEncoded(content);
            } else {
                for (final Element child : children) {
                    this.write(child, element.getNamespace());
                }
            }
            final Tag endTag = Tag.end(element.getName());
            this.write(endTag, namespace);
        }
    }

    private void writeEncoded(final String text) throws IOException {
        for (final var c : text.toCharArray()) {
            if (ALLOWED_CONTROL_CHARS.contains(c)) {
                this.write(c);
            } else if (Character.isISOControl(c)) {
                Log.e(Config.LOGTAG, "invalid control chars in: " + text);
            } else {
                final var entity = ENTITIES.get(c);
                if (entity != null) {
                    this.write('&');
                    this.write(entity);
                    this.write(';');
                } else {
                    this.write(c);
                }
            }
        }
    }

    public static String asString(final Extension extension) throws IOException {
        final var outputStream = new ByteArrayOutputStream();
        final var writer = new StreamElementWriter(outputStream);
        writer.write(extension, Namespace.JABBER_CLIENT);
        writer.flush();
        return outputStream.toString();
    }

    public static String asStringUnchecked(final Extension extension) {
        try {
            return asString(extension);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
