package im.conversations.android.xmpp.model;

import com.google.common.base.Strings;
import im.conversations.android.xmpp.model.sasl.SaslError;

public abstract class AuthenticationFailure extends StreamElement {

    protected AuthenticationFailure(Class<? extends AuthenticationFailure> clazz) {
        super(clazz);
    }

    public SaslError getErrorCondition() {
        return this.getExtension(SaslError.class);
    }

    public abstract String getText();

    public static String message(final String text, final SaslError condition) {
        if (Strings.isNullOrEmpty(text)) {
            return condition != null ? condition.getClass().getSimpleName() : null;
        } else {
            return text;
        }
    }
}
