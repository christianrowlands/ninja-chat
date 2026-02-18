package eu.siacs.conversations.services;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.PushNotificationManager;
import org.jspecify.annotations.NonNull;

public class PushManagementService {

    public static final String ACTION_REGISTER = "com.google.android.c2dm.intent.REGISTER";
    public static final String ACTION_REGISTRATION = "com.google.android.c2dm.intent.REGISTRATION";
    public static final String ACTION_RECEIVE = "com.google.android.c2dm.intent.RECEIVE";
    public static final String PACKAGE_NAME_GMS = "com.google.android.gms";

    protected final Context context;

    public PushManagementService(final Context service) {
        this.context = service;
    }

    private Jid getAppServer() {
        return Jid.of(context.getString(R.string.app_server));
    }

    public void registerPushTokenOnServer(final Account account) {
        final var pushManager =
                account.getXmppConnection().getManager(PushNotificationManager.class);
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": has push support");
        final var fcmTokenFuture = retrieveFcmInstanceTokenTiny();
        final var registrationFuture =
                Futures.transformAsync(
                        fcmTokenFuture,
                        fcmToken -> {
                            Log.d(Config.LOGTAG, "FCM Token: " + fcmToken);
                            final var androidId = PhoneHelper.getAndroidId(context);
                            final var appServer = getAppServer();
                            return pushManager.register(appServer, fcmToken, androidId);
                        },
                        MoreExecutors.directExecutor());
        final var enableFuture =
                Futures.transformAsync(
                        registrationFuture, pushManager::enable, MoreExecutors.directExecutor());
        Futures.addCallback(
                enableFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": successfully enabled push");
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not register for push", t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private @NonNull ListenableFuture<String> retrieveFcmInstanceTokenTiny() {
        final var broadcastIntent = new Intent();
        broadcastIntent.setPackage(context.getPackageName());
        final var intent = new Intent(ACTION_REGISTER);
        intent.setPackage(PACKAGE_NAME_GMS);
        intent.putExtra("scope", "GCM");
        intent.putExtra("sender", context.getString(R.string.gcm_defaultSenderId));
        intent.putExtra(
                "app",
                PendingIntent.getBroadcast(
                        context, 0, broadcastIntent, PendingIntent.FLAG_IMMUTABLE));
        final var handler = new RegistrationMessageHandler(Looper.getMainLooper());
        intent.putExtra("google.messenger", new Messenger(handler));
        context.startService(intent);
        return handler.endpointFuture;
    }

    public boolean available(final Account account) {
        final XmppConnection connection = account.getXmppConnection();
        return connection != null
                && connection.getFeatures().sm()
                && connection.getManager(PushNotificationManager.class).hasFeature()
                && playServicesAvailable();
    }

    private boolean playServicesAvailable() {
        final var intent = new Intent(ACTION_REGISTER);
        intent.setPackage(PACKAGE_NAME_GMS);
        final var packageManager = context.getPackageManager();
        final var resolveInfo =
                packageManager.resolveService(intent, PackageManager.GET_RESOLVED_FILTER);
        return resolveInfo != null;
    }

    public static boolean isStub() {
        return false;
    }

    private static class RegistrationMessageHandler extends Handler {

        private final SettableFuture<String> endpointFuture = SettableFuture.create();

        public RegistrationMessageHandler(final Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@androidx.annotation.NonNull final Message message) {
            if (message.obj instanceof Intent intent) {
                final var registrationId = intent.getStringExtra("registration_id");
                final var error = intent.getStringExtra("error");
                if (error != null) {
                    endpointFuture.setException(
                            new IllegalStateException(String.format("Firebase error %s", error)));
                }
                if (Strings.isNullOrEmpty(registrationId)) {
                    endpointFuture.setException(
                            new IllegalStateException("Response did not contain registration id"));
                    return;
                }
                endpointFuture.set(registrationId);
            } else {
                endpointFuture.setException(
                        new IllegalStateException("Response did not contain intent"));
            }
        }
    }
}
