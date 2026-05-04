package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Text extends Extension {

    public Text() {
        super(Text.class);
    }
}
