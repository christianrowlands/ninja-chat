package im.conversations.android.xmpp.model.fallback;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Body extends Fallback.Element {
    public Body() {
        super(Body.class);
    }
}
