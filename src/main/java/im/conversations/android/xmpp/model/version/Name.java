package im.conversations.android.xmpp.model.version;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Name extends Extension {

    public Name() {
        super(Name.class);
    }

    public Name(final String name) {
        this();
        this.setContent(name);
    }
}
