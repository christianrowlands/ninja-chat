package eu.siacs.conversations.services;

import android.content.Context;
import android.content.Intent;
import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Conversations;

public abstract class AbstractQuickConversationsService {

    public static final String SMS_RETRIEVED_ACTION =
            "com.google.android.gms.auth.api.phone.SMS_RETRIEVED";

    protected final XmppConnectionService service;

    public AbstractQuickConversationsService(XmppConnectionService service) {
        this.service = service;
    }

    public abstract void considerSync();

    public static boolean isQuicksy() {
        return "quicksy".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isConversations() {
        return "conversations".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isPlayStoreFlavor() {
        return "playstore".equals(BuildConfig.FLAVOR_distribution);
    }

    public static boolean isContactListIntegration(final Context context) {
        if ("quicksy".equals(BuildConfig.FLAVOR_mode)) {
            return true;
        }
        return Conversations.getInstance(context).isDeclaredContactsPermissions();
    }

    public static boolean isQuicksyPlayStore() {
        return isQuicksy() && isPlayStoreFlavor();
    }

    public abstract void signalAccountStateChange();

    public abstract boolean isSynchronizing();

    public abstract void considerSyncBackground(boolean force);

    public abstract void handleSmsReceived(Intent intent);
}
