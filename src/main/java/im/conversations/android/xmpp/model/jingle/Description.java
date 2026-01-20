package im.conversations.android.xmpp.model.jingle;

import im.conversations.android.xmpp.model.Extension;

public abstract class Description extends Extension {

    public Description(Class<? extends Description> clazz) {
        super(clazz);
    }
}
