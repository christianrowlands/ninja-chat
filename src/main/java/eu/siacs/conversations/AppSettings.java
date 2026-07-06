package eu.siacs.conversations;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.Random;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.model.AttachmentChoice;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class AppSettings {

    public static final String KEEP_FOREGROUND_SERVICE = "enable_foreground_service";
    public static final String AWAY_WHEN_SCREEN_IS_OFF = "away_when_screen_off";
    public static final String DND_SYNC_SYSTEM = "dnd_on_silent_mode";
    public static final String DND_INCLUDE_SILENT_MODES = "treat_vibrate_as_silent";
    public static final String GRACE_PERIOD_LENGTH = "grace_period_length";
    public static final String MANUALLY_CHANGE_PRESENCE = "manually_change_presence";
    public static final String BLIND_TRUST_BEFORE_VERIFICATION = "btbv";
    public static final String AUTOMATIC_MESSAGE_DELETION = "automatic_message_deletion";
    public static final String BROADCAST_LAST_ACTIVITY = "last_activity";
    public static final String SEND_CHAT_STATES = "chat_states";
    public static final String ENTITY_TIME = "entity_time";
    public static final String THEME = "theme";
    public static final String DYNAMIC_COLORS = "dynamic_colors";
    public static final String SHOW_DYNAMIC_TAGS = "show_dynamic_tags";
    public static final String OMEMO = "omemo";
    public static final String ALLOW_SCREENSHOTS = "allow_screenshots";
    public static final String RINGTONE = "call_ringtone";
    public static final String DISPLAY_ENTER_KEY = "display_enter_key";
    public static final String ENTER_IS_SEND = "enter_is_send";
    public static final String SCROLL_TO_BOTTOM = "scroll_to_bottom";

    public static final String READ_RECEIPTS = "confirm_messages";
    public static final String ALLOW_MESSAGE_CORRECTION = "allow_message_correction";

    public static final String TRUST_SYSTEM_CA_STORE = "trust_system_ca_store";
    public static final String REQUIRE_CHANNEL_BINDING = "channel_binding_required";
    public static final String REQUIRE_TLS_V1_3 = "require_tls_v1_3";
    public static final String NOTIFICATION_RINGTONE = "notification_ringtone";
    public static final String NOTIFICATION_HEADS_UP = "notification_headsup";
    public static final String NOTIFICATION_VIBRATE = "vibrate_on_notification";
    public static final String NOTIFICATION_LED = "led";
    public static final String SHOW_CONNECTION_OPTIONS = "show_connection_options";
    public static final String USE_TOR = "use_tor";
    public static final String USE_RELAYS = "use_relays";
    public static final String CHANNEL_DISCOVERY_METHOD = "channel_discovery_method";
    public static final String SEND_CRASH_REPORTS = "send_crash_reports";
    public static final String COLORFUL_CHAT_BUBBLES = "use_green_background";
    public static final String LARGE_FONT = "large_font";
    public static final String SHOW_AVATARS_11 = "show_avatars";
    public static final String SHOW_AVATARS_ACCOUNTS = "show_avatars_accounts";
    public static final String CALL_INTEGRATION = "call_integration";
    public static final String ALIGN_START = "align_start";
    public static final String BACKUP_LOCATION = "backup_location";
    public static final String AUTO_ACCEPT_FILE_SIZE = "auto_accept_file_size";
    public static final String VIDEO_COMPRESSION = "video_compression";
    public static final String AUTO_SEND_RECORDING = "auto_send_recording";
    public static final String USE_SHARED_STORAGE = "use_shared_storage";
    public static final String QUICK_ACTION = "quick_action_button";

    private static final String ACCEPT_INVITES_FROM_STRANGERS = "accept_invites_from_strangers";
    private static final String NOTIFICATIONS_FROM_STRANGERS = "notifications_from_strangers";
    private static final String INSTALLATION_ID = "im.conversations.android.install_id";

    private static final String EXTERNAL_STORAGE_AUTHORITY =
            "com.android.externalstorage.documents";

    public static final Set<Jid> SECURE_DOMAINS;

    static {
        final var builder = new ImmutableSet.Builder<Jid>();
        if (Objects.nonNull(Config.MAGIC_CREATE_DOMAIN)) {
            builder.add(Jid.ofDomain(Config.MAGIC_CREATE_DOMAIN));
        }
        if (Objects.nonNull(Config.QUICKSY_DOMAIN)) {
            builder.add(Config.QUICKSY_DOMAIN);
        }
        SECURE_DOMAINS = builder.build();
    }

    private final Context context;

    public AppSettings(final Context context) {
        this.context = context;
    }

    public Uri getRingtone() {
        final var incomingCallRingtone =
                getStringPreference(RINGTONE, R.string.incoming_call_ringtone);
        return Strings.isNullOrEmpty(incomingCallRingtone) ? null : Uri.parse(incomingCallRingtone);
    }

    public void setRingtone(final Uri uri) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putString(RINGTONE, uri == null ? null : uri.toString()).apply();
    }

    public Uri getNotificationTone() {
        final var tone = getStringPreference(NOTIFICATION_RINGTONE, R.string.notification_ringtone);
        return Strings.isNullOrEmpty(tone) ? null : Uri.parse(tone);
    }

    public void setNotificationTone(final Uri uri) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences
                .edit()
                .putString(NOTIFICATION_RINGTONE, uri == null ? null : uri.toString())
                .apply();
    }

    public boolean isBTBVEnabled() {
        return getBooleanPreference(BLIND_TRUST_BEFORE_VERIFICATION, R.bool.btbv);
    }

    public boolean isTrustSystemCAStore() {
        return getBooleanPreference(TRUST_SYSTEM_CA_STORE, R.bool.trust_system_ca_store);
    }

    public boolean isAllowScreenshots() {
        return getBooleanPreference(ALLOW_SCREENSHOTS, R.bool.allow_screenshots);
    }

    public boolean isColorfulChatBubbles() {
        return getBooleanPreference(COLORFUL_CHAT_BUBBLES, R.bool.use_green_background);
    }

    public boolean isLargeFont() {
        return getBooleanPreference(LARGE_FONT, R.bool.large_font);
    }

    public boolean isShowAvatars11() {
        return getBooleanPreference(SHOW_AVATARS_11, R.bool.show_avatars);
    }

    public boolean isShowAvatarsAccounts() {
        return getBooleanPreference(SHOW_AVATARS_ACCOUNTS, R.bool.show_avatars_accounts);
    }

    public boolean isAutoSendRecording() {
        return getBooleanPreference(AUTO_SEND_RECORDING, R.bool.auto_send_recording);
    }

    public boolean isQuickActionRecordingAuto() {
        return isAutoSendRecording() && getQuickAction() == AttachmentChoice.Type.RECORDING;
    }

    public boolean isCallIntegration() {
        return getBooleanPreference(CALL_INTEGRATION, R.bool.call_integration);
    }

    public boolean isAlignStart() {
        return getBooleanPreference(ALIGN_START, R.bool.align_start);
    }

    public boolean isReadReceipts() {
        return getBooleanPreference(READ_RECEIPTS, R.bool.confirm_messages);
    }

    public boolean isAllowMessageCorrection() {
        return getBooleanPreference(ALLOW_MESSAGE_CORRECTION, R.bool.allow_message_correction);
    }

    public boolean isBroadcastLastActivity() {
        return getBooleanPreference(BROADCAST_LAST_ACTIVITY, R.bool.last_activity);
    }

    public boolean isUserManagedAvailability() {
        return getBooleanPreference(MANUALLY_CHANGE_PRESENCE, R.bool.manually_change_presence);
    }

    public boolean isAutomaticAvailability() {
        return !isUserManagedAvailability();
    }

    public boolean isDndSyncSystem() {
        return getBooleanPreference(AppSettings.DND_SYNC_SYSTEM, R.bool.dnd_sync_system);
    }

    public boolean isDndIncludeSilentMode() {
        return getBooleanPreference(
                AppSettings.DND_INCLUDE_SILENT_MODES, R.bool.dnd_include_silent_modes);
    }

    public boolean isAwayWhenScreenLocked() {
        return getBooleanPreference(
                AppSettings.AWAY_WHEN_SCREEN_IS_OFF, R.bool.away_when_screen_off);
    }

    public boolean isUseSharedStorage() {
        return getBooleanPreference(USE_SHARED_STORAGE, R.bool.use_shared_storage);
    }

    public boolean isUseTor() {
        return QuickConversationsService.isConversations()
                && getBooleanPreference(USE_TOR, R.bool.use_tor);
    }

    public boolean isUseRelays() {
        return getBooleanPreference(USE_RELAYS, R.bool.use_relays);
    }

    public boolean isSendChatStates() {
        return getBooleanPreference(SEND_CHAT_STATES, R.bool.chat_states);
    }

    public boolean isEntityTime() {
        return getBooleanPreference(ENTITY_TIME, R.bool.entity_time);
    }

    public boolean isExtendedConnectionOptions() {
        return QuickConversationsService.isConversations()
                && getBooleanPreference(
                        AppSettings.SHOW_CONNECTION_OPTIONS, R.bool.show_connection_options);
    }

    public boolean isAcceptInvitesFromStrangers() {
        return getBooleanPreference(
                ACCEPT_INVITES_FROM_STRANGERS, R.bool.accept_invites_from_strangers);
    }

    public boolean isNotificationsFromStrangers() {
        return getBooleanPreference(
                NOTIFICATIONS_FROM_STRANGERS, R.bool.notifications_from_strangers);
    }

    public boolean isKeepForegroundService() {
        return Compatibility.twentySix()
                || getBooleanPreference(KEEP_FOREGROUND_SERVICE, R.bool.enable_foreground_service);
    }

    public boolean isDynamicColorsDesired() {
        return getBooleanPreference(AppSettings.DYNAMIC_COLORS, R.bool.dynamic_colors);
    }

    public int getDesiredNightMode() {
        final var theme = getStringPreference(THEME, R.string.theme);
        return getDesiredNightMode(theme);
    }

    public static int getDesiredNightMode(final String theme) {
        if ("automatic".equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if ("light".equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    private String getStringPreference(@NonNull final String name, @StringRes final int res) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getString(name, context.getResources().getString(res));
    }

    private boolean getBooleanPreference(@NonNull final String name, @BoolRes final int res) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(name, context.getResources().getBoolean(res));
    }

    private long getLongPreference(final String name, @IntegerRes final int res) {
        final long defaultValue = context.getResources().getInteger(res);
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        try {
            return Long.parseLong(sharedPreferences.getString(name, String.valueOf(defaultValue)));
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    public Duration getGracePeriodLength() {
        final var length = getLongPreference(GRACE_PERIOD_LENGTH, R.integer.grace_period);
        return Duration.ofSeconds(Math.max(length, 0));
    }

    public String getOmemo() {
        return getStringPreference(OMEMO, R.string.omemo_setting_default);
    }

    public Uri getBackupLocation() {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final String location = sharedPreferences.getString(BACKUP_LOCATION, null);
        if (Strings.isNullOrEmpty(location)) {
            final var directory = FileBackend.getBackupDirectory(context);
            return Uri.fromFile(directory);
        }
        return Uri.parse(location);
    }

    public String getBackupLocationAsPath() {
        return asPath(getBackupLocation());
    }

    public static String asPath(final Uri uri) {
        final var scheme = uri.getScheme();
        final var path = uri.getPath();
        if (path == null) {
            return uri.toString();
        }
        if ("file".equalsIgnoreCase(scheme)) {
            return path;
        } else if ("content".equalsIgnoreCase(scheme)) {
            if (EXTERNAL_STORAGE_AUTHORITY.equalsIgnoreCase(uri.getAuthority())) {
                final var parts = Splitter.on(':').limit(2).splitToList(path);
                if (parts.size() == 2 && "/tree/primary".equals(parts.get(0))) {
                    return Joiner.on('/')
                            .join(Environment.getExternalStorageDirectory(), parts.get(1));
                }
            }
        }
        return uri.toString();
    }

    public void setBackupLocation(final Uri uri) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences
                .edit()
                .putString(BACKUP_LOCATION, uri == null ? "" : uri.toString())
                .apply();
    }

    public boolean isSendCrashReports() {
        return getBooleanPreference(SEND_CRASH_REPORTS, R.bool.send_crash_reports);
    }

    public boolean isDisplayEnterKey() {
        return getBooleanPreference(DISPLAY_ENTER_KEY, R.bool.display_enter_key);
    }

    public boolean isEnterSend() {
        return getBooleanPreference(ENTER_IS_SEND, R.bool.enter_is_send);
    }

    public boolean isScrollToBottom() {
        return getBooleanPreference(SCROLL_TO_BOTTOM, R.bool.scroll_to_bottom);
    }

    public void setSendCrashReports(boolean value) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putBoolean(SEND_CRASH_REPORTS, value).apply();
    }

    public boolean isRequireChannelBinding() {
        return getBooleanPreference(REQUIRE_CHANNEL_BINDING, R.bool.require_channel_binding);
    }

    public boolean isRequireTlsV13() {
        return getBooleanPreference(REQUIRE_TLS_V1_3, R.bool.require_tls_v1_3);
    }

    public synchronized long getInstallationId() {
        final var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final long existing = sharedPreferences.getLong(INSTALLATION_ID, 0);
        if (existing != 0) {
            return existing;
        }
        final var installationId = Random.SECURE_RANDOM.nextLong();
        sharedPreferences.edit().putLong(INSTALLATION_ID, installationId).apply();
        return installationId;
    }

    public Optional<Long> getAutoAcceptFileSize() {
        final long autoAcceptFileSize =
                getLongPreference(AUTO_ACCEPT_FILE_SIZE, R.integer.auto_accept_filesize);
        return autoAcceptFileSize <= 0 ? Optional.empty() : Optional.of(autoAcceptFileSize);
    }

    public String getVideoCompression() {
        return getStringPreference(VIDEO_COMPRESSION, R.string.video_compression);
    }

    public AttachmentChoice.Type getQuickAction() {
        final var setting = getStringPreference(QUICK_ACTION, R.string.quick_action);
        if (Strings.isNullOrEmpty(setting)) {
            return getDefaultQuickAction();
        }
        try {
            return AttachmentChoice.Type.valueOf(setting);
        } catch (final IllegalArgumentException e) {
            return getDefaultQuickAction();
        }
    }

    private AttachmentChoice.Type getDefaultQuickAction() {
        return AttachmentChoice.Type.valueOf(context.getString(R.string.quick_action));
    }

    public Optional<Duration> getAutomaticMessageDeletion() {
        final var value =
                getLongPreference(AUTOMATIC_MESSAGE_DELETION, R.integer.automatic_message_deletion);
        if (value <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(value));
    }

    public Optional<Instant> getAutomaticMessageDeletionInstant() {
        return getAutomaticMessageDeletion().map(duration -> Instant.now().minus(duration));
    }

    public boolean isCompressVideo() {
        return Arrays.asList("1080", "720", "480", "360").contains(getVideoCompression());
    }

    public synchronized void resetInstallationId() {
        final var installationId = Random.SECURE_RANDOM.nextLong();
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(INSTALLATION_ID, installationId)
                .apply();
    }
}
