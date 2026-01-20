package im.conversations.android.xmpp;

import im.conversations.android.xmpp.model.error.Condition;

public class IqProcessingException extends Exception {

    private final Condition condition;
    private final String text;

    public IqProcessingException(final Condition condition, final String text) {
        super(text);
        this.condition = condition;
        this.text = text;
    }

    public IqProcessingException(
            final Condition condition, final String text, final Throwable throwable) {
        super(text, throwable);
        this.condition = condition;
        this.text = text;
    }

    public Condition getCondition() {
        return condition;
    }

    public String getText() {
        return text;
    }
}
