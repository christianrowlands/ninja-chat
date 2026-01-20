package im.conversations.android.xmpp.model.idle;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.time.Instant;

@XmlElement
public class Idle extends Extension {

    public Idle() {
        super(Idle.class);
    }

    public Idle(final Instant instant) {
        this();
        this.setAttribute("since", instant);
    }

    public Instant getSince() {
        return this.getAttributeAsInstant("since");
    }
}
