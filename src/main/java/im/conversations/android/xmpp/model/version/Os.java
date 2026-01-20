package im.conversations.android.xmpp.model.version;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Os extends Extension {

    public Os() {
        super(Os.class);
    }

    public Os(final String os) {
        this();
        this.setContent(os);
    }
}
