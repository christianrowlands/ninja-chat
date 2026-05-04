package im.conversations.android.xmpp.model.carbons;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Enabled extends Extension {

    public Enabled() {
        super(Enabled.class);
    }
}
