package im.conversations.android.xmpp.model.csi;

import im.conversations.android.xmpp.model.StreamElement;

public abstract class Indication extends StreamElement {

    protected Indication(Class<? extends Indication> clazz) {
        super(clazz);
    }
}
