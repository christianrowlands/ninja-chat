package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import eu.siacs.conversations.xmpp.XmppConnection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class AbstractManager extends XmppConnection.Delegate {

    protected static final ScheduledExecutorService FUTURE_TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor();

    protected AbstractManager(final Context context, final XmppConnection connection) {
        super(context.getApplicationContext(), connection);
    }
}
