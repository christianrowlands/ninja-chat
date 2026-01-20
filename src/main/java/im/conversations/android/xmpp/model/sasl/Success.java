package im.conversations.android.xmpp.model.sasl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Success extends StreamElement implements ByteContent {

    public Success() {
        super(Success.class);
    }
}
