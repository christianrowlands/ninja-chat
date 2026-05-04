package im.conversations.android.xmpp.model.commands;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.data.Data;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@XmlElement
public class Command extends Extension {

    public Command() {
        super(Command.class);
    }

    public Command(final String node, final Action action) {
        this(node, action, null, null);
    }

    public Command(
            final String node,
            final Action action,
            @Nullable final String sessionId,
            @Nullable final Map<String, Object> data) {
        this();
        this.setNode(node);
        this.setAction(action);
        this.setSessionId(sessionId);
        if (data != null) {
            this.addExtension(Data.of(data, null));
        }
    }

    public void setAction(final Action action) {
        this.setAttribute("action", action);
    }

    public void setNode(final String node) {
        this.setAttribute("node", node);
    }

    public Data getData() {
        return this.getOnlyExtension(Data.class);
    }

    public void setSessionId(final String sessionId) {
        this.setAttribute("sessionid", sessionId);
    }

    public String getSessionId() {
        return this.getAttribute("sessionid");
    }

    public Status getStatus() {
        final var status = this.getAttribute("status");
        if (Strings.isNullOrEmpty(status)) {
            return null;
        }
        try {
            return Status.valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, status));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public Actions getActions() {
        return this.getOnlyExtension(Actions.class);
    }

    public enum Status {
        EXECUTING,
        COMPLETED,
        CANCELED
    }

    public enum Action {
        EXECUTE,
        CANCEL,
        PREV,
        NEXT,
        COMPLETE
    }
}
