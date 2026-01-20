package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class InitialResponse extends Extension implements ByteContent {

    public InitialResponse() {
        super(InitialResponse.class);
    }
}
