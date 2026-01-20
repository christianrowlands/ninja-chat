package im.conversations.android.xmpp.model.up;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Register extends Extension {

    public Register() {
        super(Register.class);
    }

    public void setApplication(final String application) {
        this.setAttribute("application", application);
    }

    public void setInstance(final String instance) {
        this.setAttribute("instance", instance);
    }
}
