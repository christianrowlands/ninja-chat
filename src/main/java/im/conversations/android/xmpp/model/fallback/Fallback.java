package im.conversations.android.xmpp.model.fallback;

import com.google.common.base.Optional;
import eu.siacs.conversations.xml.LocalizedContent;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.stanza.Message;

@XmlElement
public class Fallback extends Extension {
    public Fallback() {
        super(Fallback.class);
    }

    public String getFor() {
        return this.getAttribute("for");
    }

    public static Optional<Range> get(
            final Message message,
            final Class<? extends Extension> extension,
            final Class<? extends Element> element) {
        final var id = ExtensionFactory.id(extension);
        if (id == null) {
            throw new IllegalArgumentException(
                    String.format("%s is not a registered extension", extension.getName()));
        }
        for (final var fallback : message.getExtensions(Fallback.class)) {
            if (id.namespace().equals(fallback.getFor())) {
                if (fallback.isNoChildren()) {
                    return Optional.of(new FullRange());
                }
                final var e = fallback.getExtension(element);
                if (e != null) {
                    return Optional.of(e.getRange());
                }
            }
        }
        return Optional.absent();
    }

    private boolean isNoChildren() {
        return this.getExtensions(Body.class).isEmpty()
                && this.getExtensions(Subject.class).isEmpty();
    }

    public sealed interface Range permits StartEndRange, FullRange {
        boolean isEntire(final LocalizedContent content);
    }

    private record StartEndRange(int start, int end) implements Range {
        @Override
        public boolean isEntire(final LocalizedContent content) {
            return start == 0 && end >= content.content.length() - 1;
        }
    }

    private record FullRange() implements Range {

        @Override
        public boolean isEntire(LocalizedContent content) {
            return true;
        }
    }
    ;

    public abstract static sealed class Element extends Extension permits Body, Subject {

        public Element(Class<? extends Extension> clazz) {
            super(clazz);
        }

        public Range getRange() {
            final var start = this.getOptionalIntAttribute("start");
            final var end = this.getOptionalIntAttribute("end");
            if (start.isPresent() && end.isPresent()) {
                return new StartEndRange(start.get(), end.get());
            } else {
                return new FullRange();
            }
        }
    }
}
