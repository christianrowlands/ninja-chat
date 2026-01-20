package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.AuthenticationChallenge;

@XmlElement
public class Challenge extends AuthenticationChallenge {

    public Challenge() {
        super(Challenge.class);
    }
}
