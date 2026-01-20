package im.conversations.android.xmpp.model.jingle.apps.rtp;

import eu.siacs.conversations.xmpp.jingle.Media;
import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Description extends im.conversations.android.xmpp.model.jingle.Description {
    public Description() {
        super(Description.class);
    }

    public void setMedia(final Media media) {
        this.setAttribute("media", media);
    }
}
