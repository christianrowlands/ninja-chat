package im.conversations.android.xmpp.model.version;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Query extends Extension {

    public Query() {
        super(Query.class);
    }

    public void setSoftwareName(final String name) {
        this.addExtension(new Name(name));
    }

    public void setVersion(final String version) {
        this.addExtension(new Version(version));
    }

    public void setOs(final String os) {
        this.addExtension(new Os(os));
    }
}
