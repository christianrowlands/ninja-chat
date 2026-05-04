package im.conversations.android.xmpp.model.tls;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Failure extends StreamElement {

    public Failure() {
        super(Failure.class);
    }
}
