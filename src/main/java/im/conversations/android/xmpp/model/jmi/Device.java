package im.conversations.android.xmpp.model.jmi;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.OMEMO_DTLS_SRTP_VERIFICATION)
public class Device extends Extension {

    public Device() {
        super(Device.class);
    }

    public void setId(final int deviceId) {
        this.setAttribute("id", deviceId);
    }

    public Integer getId() {
        return this.getOptionalIntAttribute("id").orNull();
    }
}
