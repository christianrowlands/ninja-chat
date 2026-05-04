package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.AuthenticationFailure;

@XmlElement
public class Failure extends AuthenticationFailure {

    public Failure() {
        super(Failure.class);
    }

    @Override
    public String getText() {
        final var text = this.getExtension(Text.class);
        return text != null ? text.getContent() : null;
    }
}
