package im.conversations.android.provider;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;

public class ApplicationProvider {

    public static final Set<String> KNOWN_CONTACTS_APP =
            ImmutableSet.of(
                    "com.google.android.contacts",
                    "com.android.contacts",
                    "com.samsung.android.app.contacts",
                    "com.samsung.android.contacts",
                    "org.fossify.contacts",
                    "com.simplemobiletools.contacts",
                    "com.simplemobiletools.contacts.pro");

    private final Context context;

    public ApplicationProvider(final Context context) {
        this.context = context;
    }

    public ImmutableSet<String> resolve(final Intent intent) {
        final var packageManager = context.getPackageManager();

        final List<ResolveInfo> resolveInfos;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveInfos =
                    packageManager.queryIntentActivities(
                            intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL));
        } else {
            resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        }

        final var builder = new ImmutableSet.Builder<String>();

        for (final var resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo == null) {
                continue;
            }
            final var packageName = resolveInfo.activityInfo.packageName;
            if (Strings.isNullOrEmpty(packageName)) {
                continue;
            }
            builder.add(packageName);
        }

        return builder.build();
    }
}
