package eu.siacs.conversations.xmpp.manager;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.persistance.UnifiedPushDatabase;
import eu.siacs.conversations.receiver.UnifiedPushDistributor;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.up.Push;
import im.conversations.android.xmpp.model.up.Register;
import im.conversations.android.xmpp.model.up.Registered;
import java.time.Instant;
import okhttp3.HttpUrl;

public class UnifiedPushManager extends AbstractManager {

    private final XmppConnectionService service;

    public UnifiedPushManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
        this.service = service;
    }

    public void push(final Iq packet) {
//        final Jid transport = packet.getFrom();
//        final var push = packet.getOnlyExtension(Push.class);
//        if (push == null || transport == null) {
//            connection.sendErrorFor(packet, new Condition.BadRequest());
//            return;
//        }
//        if (service.processUnifiedPushMessage(getAccount(), transport, push)) {
//            connection.sendResultFor(packet);
//        } else {
//            connection.sendErrorFor(packet, new Condition.ItemNotFound());
//        }
    }

    public ListenableFuture<Registration> register(
            final Jid transport, final UnifiedPushDatabase.PushTarget renewal) {
        final var uuid = getAccount().getUuid();
        final String hashedApplication = UnifiedPushDistributor.hash(uuid, renewal.application());
        final String hashedInstance = UnifiedPushDistributor.hash(uuid, renewal.instance());
        final Iq iq = new Iq(Iq.Type.SET);
        iq.setTo(transport);
        final var register = iq.addExtension(new Register());
        register.setApplication(hashedApplication);
        register.setInstance(hashedInstance);
        final var future = this.connection.sendIqPacket(iq);
        return Futures.transform(
                future,
                response -> {
                    final var registered = response.getExtension(Registered.class);
                    if (registered == null) {
                        throw new IllegalStateException("Registered missing from response");
                    }
                    final var endpoint = registered.getEndpoint();
                    final var expiration = registered.getExpiration();
                    return new Registration(endpoint, expiration);
                },
                MoreExecutors.directExecutor());
    }

    public record Registration(HttpUrl endpoint, Instant expiration) {}
}
