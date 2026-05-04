package eu.siacs.conversations.xml;

import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.model.Extension;
import java.util.Map;

public abstract sealed class Tag {

    public abstract static sealed class IdentifiableTag extends Tag {

        private final ExtensionFactory.Id id;

        protected IdentifiableTag(ExtensionFactory.Id id) {
            this.id = id;
        }

        public ExtensionFactory.Id getId() {
            return this.id;
        }

        public boolean is(final Class<? extends Extension> clazz) {
            return ExtensionFactory.id(clazz).equals(this.id);
        }
    }

    public abstract static sealed class StartOrEmpty extends IdentifiableTag {

        private final Map<String, String> attributes;

        public StartOrEmpty(final ExtensionFactory.Id id, Map<String, String> attributes) {
            super(id);
            this.attributes = attributes;
        }

        public Map<String, String> getAttributes() {
            return this.attributes;
        }

        public String getAttribute(final String name) {
            return this.attributes.get(name);
        }
    }

    public static final class Start extends StartOrEmpty {

        public Start(final ExtensionFactory.Id id, final Map<String, String> attributes) {
            super(id, attributes);
        }
    }

    public static final class End extends IdentifiableTag {

        public End(final ExtensionFactory.Id id) {
            super(id);
        }
    }

    public static final class Empty extends StartOrEmpty {

        public Empty(final ExtensionFactory.Id id, final Map<String, String> attributes) {
            super(id, attributes);
        }
    }

    public static final class No extends Tag {

        private final String text;

        public No(final String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }
    }
}
