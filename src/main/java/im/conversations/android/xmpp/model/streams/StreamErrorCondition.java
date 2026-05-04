package im.conversations.android.xmpp.model.streams;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

public abstract sealed class StreamErrorCondition extends Extension {

    private StreamErrorCondition(Class<? extends StreamErrorCondition> clazz) {
        super(clazz);
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class BadFormat extends StreamErrorCondition {

        public BadFormat() {
            super(BadFormat.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class BadNamespacePrefix extends StreamErrorCondition {

        public BadNamespacePrefix() {
            super(BadNamespacePrefix.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class Conflict extends StreamErrorCondition {

        public Conflict() {
            super(Conflict.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class ConnectionTimeout extends StreamErrorCondition {

        public ConnectionTimeout() {
            super(ConnectionTimeout.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class HostGone extends StreamErrorCondition {

        public HostGone() {
            super(HostGone.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class HostUnknown extends StreamErrorCondition {

        public HostUnknown() {
            super(HostUnknown.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class ImproperAddressing extends StreamErrorCondition {

        public ImproperAddressing() {
            super(ImproperAddressing.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class InternalServerError extends StreamErrorCondition {

        public InternalServerError() {
            super(InternalServerError.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class InvalidFrom extends StreamErrorCondition {

        public InvalidFrom() {
            super(InvalidFrom.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class InvalidNamespace extends StreamErrorCondition {

        public InvalidNamespace() {
            super(InvalidNamespace.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class InvalidXml extends StreamErrorCondition {

        public InvalidXml() {
            super(InvalidXml.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class NotAuthorized extends StreamErrorCondition {

        public NotAuthorized() {
            super(NotAuthorized.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class NotWellFormed extends StreamErrorCondition {

        public NotWellFormed() {
            super(NotWellFormed.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class PolicyViolation extends StreamErrorCondition {

        public PolicyViolation() {
            super(PolicyViolation.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class RemoteConnectionFailed extends StreamErrorCondition {

        public RemoteConnectionFailed() {
            super(RemoteConnectionFailed.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class Reset extends StreamErrorCondition {

        public Reset() {
            super(Reset.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class ResourceConstraint extends StreamErrorCondition {

        public ResourceConstraint() {
            super(ResourceConstraint.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class RestrictedXml extends StreamErrorCondition {

        public RestrictedXml() {
            super(RestrictedXml.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class SeeOtherHost extends StreamErrorCondition {

        public SeeOtherHost() {
            super(SeeOtherHost.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class SystemShutdown extends StreamErrorCondition {

        public SystemShutdown() {
            super(SystemShutdown.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class UndefinedCondition extends StreamErrorCondition {

        public UndefinedCondition() {
            super(UndefinedCondition.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class UnsupportedEncoding extends StreamErrorCondition {

        public UnsupportedEncoding() {
            super(UnsupportedEncoding.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class UnsupportedFeature extends StreamErrorCondition {

        public UnsupportedFeature() {
            super(UnsupportedFeature.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class UnsupportedStanzaType extends StreamErrorCondition {

        public UnsupportedStanzaType() {
            super(UnsupportedStanzaType.class);
        }
    }

    @XmlElement(namespace = Namespace.XMPP_STREAMS)
    public static final class UnsupportedVersion extends StreamErrorCondition {

        public UnsupportedVersion() {
            super(UnsupportedVersion.class);
        }
    }
}
