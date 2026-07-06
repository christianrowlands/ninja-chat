package eu.siacs.conversations;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.EmojiInitializationService;
import eu.siacs.conversations.ui.util.SettingsUtils;
import eu.siacs.conversations.utils.ExceptionHelper;
import java.security.Security;
import java.util.Arrays;
import java.util.Collection;
import org.conscrypt.Conscrypt;

public class Conversations extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context CONTEXT;

    public static Context getContext() {
        return Conversations.CONTEXT;
    }

    private final Supplier<Collection<DatabaseBackend.AccountWithOptions>>
            accountWithOptionsSupplier =
                    () -> {
                        final var stopwatch = Stopwatch.createStarted();
                        final var accounts =
                                DatabaseBackend.getInstance(Conversations.this)
                                        .getAccountWithOptions();
                        Log.d(
                                Config.LOGTAG,
                                "fetching accounts from database in " + stopwatch.stop());
                        return accounts;
                    };
    private Supplier<Collection<DatabaseBackend.AccountWithOptions>> accountWithOptions =
            Suppliers.memoize(accountWithOptionsSupplier);

    private final Supplier<Boolean> microphoneAvailability =
            () -> getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    private final Supplier<Boolean> declaredReadContacts =
            () -> {
                final String[] permissions;
                try {
                    permissions =
                            getPackageManager()
                                    .getPackageInfo(
                                            getPackageName(), PackageManager.GET_PERMISSIONS)
                                    .requestedPermissions;
                } catch (final PackageManager.NameNotFoundException e) {
                    return false;
                }
                if (permissions == null || permissions.length == 0) {
                    return false;
                }
                return Iterables.any(
                        Arrays.asList(permissions),
                        p -> p != null && p.equals(Manifest.permission.READ_CONTACTS));
            };

    @Override
    public void onCreate() {
        super.onCreate();
        installSecurityProvider();
        CONTEXT = this.getApplicationContext();
        EmojiInitializationService.execute(getApplicationContext());
        ExceptionHelper.init(getApplicationContext());
        SettingsUtils.applyThemeSettings(this);
    }

    private static void installSecurityProvider() {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (final Throwable throwable) {
            Log.e(Config.LOGTAG, "could not install security provider", throwable);
        }
    }

    public static Conversations getInstance(final Context context) {
        if (context.getApplicationContext() instanceof Conversations c) {
            return c;
        }
        throw new IllegalStateException("Application is not Conversations");
    }

    public void resetAccounts() {
        this.accountWithOptions = Suppliers.memoize(accountWithOptionsSupplier);
    }

    public Collection<DatabaseBackend.AccountWithOptions> getAccounts() {
        return this.accountWithOptions.get();
    }

    public boolean hasEnabledAccount() {
        return DatabaseBackend.AccountWithOptions.hasEnabledAccount(getAccounts());
    }

    public boolean isMicrophoneAvailable() {
        return this.microphoneAvailability.get();
    }

    public boolean isDeclaredContactsPermissions() {
        return this.declaredReadContacts.get();
    }
}
