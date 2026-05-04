package im.conversations.android.xmpp.model.hats;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;

@XmlElement
public class Hats extends Extension {
    public Hats() {
        super(Hats.class);
    }

    public Collection<Hat> getHats() {
        return this.getExtensions(Hat.class);
    }
}
