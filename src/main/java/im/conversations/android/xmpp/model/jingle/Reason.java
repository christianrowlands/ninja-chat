package im.conversations.android.xmpp.model.jingle;

import com.google.common.base.Throwables;
import eu.siacs.conversations.crypto.axolotl.CryptoFailedException;
import eu.siacs.conversations.xmpp.jingle.RtpContentMap;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

public abstract class Reason extends Extension {

    private Reason(final Class<? extends Reason> clazz) {
        super(clazz);
    }

    @XmlElement
    public static class AlternativeSession extends Reason {
        public AlternativeSession() {
            super(AlternativeSession.class);
        }
    }

    @XmlElement
    public static class Busy extends Reason {
        public Busy() {
            super(Busy.class);
        }
    }

    @XmlElement
    public static class Cancel extends Reason {
        public Cancel() {
            super(Cancel.class);
        }
    }

    @XmlElement
    public static class ConnectivityError extends Reason {
        public ConnectivityError() {
            super(ConnectivityError.class);
        }
    }

    @XmlElement
    public static class Decline extends Reason {
        public Decline() {
            super(Decline.class);
        }
    }

    @XmlElement
    public static class Expired extends Reason {
        public Expired() {
            super(Expired.class);
        }
    }

    @XmlElement
    public static class FailedApplication extends Reason {
        public FailedApplication() {
            super(FailedApplication.class);
        }
    }

    @XmlElement
    public static class FailedTransport extends Reason {
        public FailedTransport() {
            super(FailedTransport.class);
        }
    }

    @XmlElement
    public static class GeneralError extends Reason {
        public GeneralError() {
            super(GeneralError.class);
        }
    }

    @XmlElement
    public static class Gone extends Reason {
        public Gone() {
            super(Gone.class);
        }
    }

    @XmlElement
    public static class IncompatibleParameters extends Reason {
        public IncompatibleParameters() {
            super(IncompatibleParameters.class);
        }
    }

    @XmlElement
    public static class MediaError extends Reason {
        public MediaError() {
            super(MediaError.class);
        }
    }

    @XmlElement
    public static class SecurityError extends Reason {
        public SecurityError() {
            super(SecurityError.class);
        }
    }

    @XmlElement
    public static class Success extends Reason {
        public Success() {
            super(Success.class);
        }
    }

    @XmlElement
    public static class Timeout extends Reason {
        public Timeout() {
            super(Timeout.class);
        }
    }

    @XmlElement
    public static class UnsupportedApplications extends Reason {
        public UnsupportedApplications() {
            super(UnsupportedApplications.class);
        }
    }

    @XmlElement
    public static class UnsupportedTransports extends Reason {
        public UnsupportedTransports() {
            super(UnsupportedTransports.class);
        }
    }

    public static Reason of(final RuntimeException e) {
        return switch (e) {
            case SecurityException ignored -> new SecurityError();
            case RtpContentMap.UnsupportedTransportException ignored -> new UnsupportedTransports();
            case RtpContentMap.UnsupportedApplicationException ignored ->
                    new UnsupportedApplications();
            case null, default -> new FailedApplication();
        };
    }

    public static Reason ofThrowable(final Throwable throwable) {
        final Throwable root = Throwables.getRootCause(throwable);
        if (root instanceof RuntimeException e) {
            return of(e);
        }
        if (root instanceof CryptoFailedException) {
            return new SecurityError();
        }
        return new FailedApplication();
    }
}
