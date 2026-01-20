package im.conversations.android.xmpp.model.streams;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;
import im.conversations.android.xmpp.model.StreamFeature;
import im.conversations.android.xmpp.model.capabilties.EntityCapabilities;
import im.conversations.android.xmpp.model.csi.ClientStateIndication;
import im.conversations.android.xmpp.model.register.RegisterStreamFeature;
import im.conversations.android.xmpp.model.roster.Versioning;
import im.conversations.android.xmpp.model.session.Session;
import im.conversations.android.xmpp.model.sm.StreamManagement;
import im.conversations.android.xmpp.model.token.Register;

@XmlElement
public class Features extends StreamElement implements EntityCapabilities {
    public Features() {
        super(Features.class);
    }

    public boolean streamManagement() {
        return hasStreamFeature(StreamManagement.class);
    }

    public boolean clientStateIndication() {
        return this.hasStreamFeature(ClientStateIndication.class);
    }

    public boolean rosterVersioning() {
        return this.hasStreamFeature(Versioning.class);
    }

    public boolean register() {
        return hasStreamFeature(RegisterStreamFeature.class);
    }

    public boolean preAuthenticatedInBandRegistration() {
        return hasStreamFeature(Register.class);
    }

    public boolean hasStreamFeature(final Class<? extends StreamFeature> clazz) {
        return hasExtension(clazz);
    }

    public boolean session() {
        final var session = getExtension(Session.class);
        return session != null && !session.isOptional();
    }
}
