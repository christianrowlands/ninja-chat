package im.conversations.android.xmpp.model.roster;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;

@XmlElement(name = "sub", namespace = Namespace.ROSTER_PRE_APPROVAL)
public class PreApproval extends StreamFeature {
    public PreApproval() {
        super(PreApproval.class);
    }
}
