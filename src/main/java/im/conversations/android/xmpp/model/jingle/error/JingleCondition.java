package im.conversations.android.xmpp.model.jingle.error;

import com.google.common.collect.ImmutableMap;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import java.util.Map;

public abstract class JingleCondition extends Error.Extension {

    public static final Map<Class<? extends JingleCondition>, Class<? extends Condition>>
            JINGLE_CONDITION_ERROR_MAP =
                    new ImmutableMap.Builder<
                                    Class<? extends JingleCondition>, Class<? extends Condition>>()
                            .put(OutOfOrder.class, Condition.UnexpectedRequest.class)
                            .put(TieBreak.class, Condition.Conflict.class)
                            .put(UnknownSession.class, Condition.ItemNotFound.class)
                            .put(UnsupportedInfo.class, Condition.FeatureNotImplemented.class)
                            .build();

    private JingleCondition(Class<? extends JingleCondition> clazz) {
        super(clazz);
    }

    @XmlElement(namespace = Namespace.JINGLE_ERRORS)
    public static class OutOfOrder extends JingleCondition {

        public OutOfOrder() {
            super(OutOfOrder.class);
        }
    }

    @XmlElement(namespace = Namespace.JINGLE_ERRORS)
    public static class TieBreak extends JingleCondition {

        public TieBreak() {
            super(TieBreak.class);
        }
    }

    @XmlElement(namespace = Namespace.JINGLE_ERRORS)
    public static class UnknownSession extends JingleCondition {

        public UnknownSession() {
            super(UnknownSession.class);
        }
    }

    @XmlElement(namespace = Namespace.JINGLE_ERRORS)
    public static class UnsupportedInfo extends JingleCondition {

        public UnsupportedInfo() {
            super(UnsupportedInfo.class);
        }
    }

    public static Class<? extends Condition> getErrorCondition(
            final JingleCondition jingleCondition) {
        return getErrorCondition(jingleCondition.getClass());
    }

    public static Class<? extends Condition> getErrorCondition(
            final Class<? extends JingleCondition> jingleCondition) {
        final var errorCondition = JINGLE_CONDITION_ERROR_MAP.get(jingleCondition);
        if (errorCondition == null) {
            throw new IllegalArgumentException(
                    String.format(
                            "JingleCondition %s is not mapped", jingleCondition.getSimpleName()));
        }
        return errorCondition;
    }
}
