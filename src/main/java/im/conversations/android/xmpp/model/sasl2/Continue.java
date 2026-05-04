package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Continue extends StreamElement {

    public Continue() {
        super(Continue.class);
    }
}
