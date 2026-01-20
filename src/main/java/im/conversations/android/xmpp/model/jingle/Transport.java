package im.conversations.android.xmpp.model.jingle;

import im.conversations.android.xmpp.model.Extension;

public abstract class Transport extends Extension {
    public Transport(Class<? extends Transport> clazz) {
        super(clazz);
    }
}
