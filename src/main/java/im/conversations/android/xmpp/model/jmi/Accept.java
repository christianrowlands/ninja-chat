package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Accept extends JingleMessage {

    public Accept() {
        super(Accept.class);
    }

    public Accept(final String sessionId) {
        this();
        this.setSessionId(sessionId);
    }
}
