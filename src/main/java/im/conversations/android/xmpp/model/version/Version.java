package im.conversations.android.xmpp.model.version;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Version extends Extension {

    public Version() {
        super(Version.class);
    }

    public Version(final String version) {
        this();
        this.setContent(version);
    }
}
