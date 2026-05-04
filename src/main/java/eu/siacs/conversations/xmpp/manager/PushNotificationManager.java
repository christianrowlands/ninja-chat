package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.push.Disable;
import im.conversations.android.xmpp.model.push.Enable;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PushNotificationManager extends AbstractManager {

    private static final String COMMAND_NODE = "register-push-fcm";

    private final AtomicInteger pushNotificationCounter = new AtomicInteger();

    public PushNotificationManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Registration> register(
            final Jid appServer, final String fcmToken, final String androidId) {
        final Map<String, Object> parameter =
                ImmutableMap.of("token", fcmToken, "android-id", androidId);
        final var future =
                getManager(AdHocCommandsManager.class)
                        .commandComplete(appServer, COMMAND_NODE, parameter);
        return Futures.transform(
                future,
                result -> {
                    Preconditions.checkNotNull(result);
                    final var node = result.getValue("node");
                    final var secret = result.getValue("secret");
                    if (Strings.isNullOrEmpty(node) || Strings.isNullOrEmpty(secret)) {
                        throw new IllegalStateException("Missing node or secret in response");
                    }
                    return new Registration(appServer, node, secret);
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> registerAndEnable(
            final Jid appServer, final String fmcToken, final String androidId) {
        final var future = register(appServer, fmcToken, androidId);
        return Futures.transformAsync(
                future,
                registration -> {
                    Preconditions.checkNotNull(registration);
                    return enable(registration);
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> enable(final Registration registration) {
        final var iq = new Iq(Iq.Type.SET);
        final var enable = iq.addExtension(new Enable());
        enable.setJid(registration.address);
        enable.setNode(registration.node);
        enable.addExtension(
                Data.of(
                        ImmutableMap.of("secret", registration.secret),
                        Namespace.PUB_SUB_PUBLISH_OPTIONS));
        return Futures.transform(
                connection.sendIqPacket(iq), response -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> disable(final Jid appServer, final String node) {
        final var iq = new Iq(Iq.Type.SET);
        final var disable = iq.addExtension(new Disable());
        disable.setJid(appServer);
        disable.setNode(node);
        return Futures.transform(
                connection.sendIqPacket(iq), response -> null, MoreExecutors.directExecutor());
    }

    public boolean hasFeature() {
        return getManager(DiscoManager.class).hasAccountFeature(Namespace.PUSH);
    }

    public int incrementAndGetPushNotificationCounter() {
        return this.pushNotificationCounter.incrementAndGet();
    }

    public int getPushNotificationCounter() {
        return this.pushNotificationCounter.get();
    }

    public record Registration(Jid address, String node, String secret) {}
}
