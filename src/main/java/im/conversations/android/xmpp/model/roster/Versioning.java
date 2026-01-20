package im.conversations.android.xmpp.model.roster;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;

@XmlElement(name = "ver", namespace = Namespace.ROSTER_VERSIONING)
public class Versioning extends StreamFeature {

    public Versioning() {
        super(Versioning.class);
    }
}
