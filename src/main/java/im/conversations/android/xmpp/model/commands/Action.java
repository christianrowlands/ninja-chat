package im.conversations.android.xmpp.model.commands;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

public abstract sealed class Action extends Extension {
    protected Action(final Class<? extends Action> clazz) {
        super(clazz);
    }

    @XmlElement
    public static final class Prev extends Action {

        public Prev() {
            super(Prev.class);
        }
    }

    @XmlElement
    public static final class Next extends Action {

        public Next() {
            super(Next.class);
        }
    }

    @XmlElement
    public static final class Complete extends Action {

        public Complete() {
            super(Complete.class);
        }
    }
}
