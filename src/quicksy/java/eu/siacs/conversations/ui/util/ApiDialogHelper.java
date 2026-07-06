package eu.siacs.conversations.ui.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.StringRes;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.utils.TimeFrameUtils;

public class ApiDialogHelper {

    public static Dialog createError(final Context context, final int code) {
        @StringRes
        final int res =
                switch (code) {
                    case QuickConversationsService.API_ERROR_AIRPLANE_MODE ->
                            R.string.no_network_connection;
                    case QuickConversationsService.API_ERROR_OTHER ->
                            R.string.unknown_api_error_network;
                    case QuickConversationsService.API_ERROR_CONNECT ->
                            R.string.unable_to_connect_to_server;
                    case QuickConversationsService.API_ERROR_SSL_HANDSHAKE ->
                            R.string.unable_to_establish_secure_connection;
                    case QuickConversationsService.API_ERROR_UNKNOWN_HOST ->
                            R.string.unable_to_find_server;
                    case QuickConversationsService.API_ERROR_SSL_CERTIFICATE ->
                            R.string.unable_to_verify_server_identity;
                    case QuickConversationsService.API_ERROR_SSL_GENERAL ->
                            R.string.unknown_security_error;
                    case QuickConversationsService.API_ERROR_TIMEOUT ->
                            R.string.timeout_while_connecting_to_server;
                    case 400 -> R.string.invalid_user_input;
                    case 403 -> R.string.the_app_is_out_of_date;
                    case 409 -> R.string.logged_in_with_another_device;
                    case 422 -> R.string.use_physical_device;
                    case 451 -> R.string.not_available_in_your_country;
                    case 500 -> R.string.something_went_wrong_processing_your_request;
                    case 502, 503, 504 -> R.string.temporarily_unavailable;
                    default -> R.string.unknown_api_error_response;
                };
        final var builder = new AlertDialog.Builder(context);
        builder.setMessage(res);
        if (code == 403 && resolvable(context, getMarketViewIntent(context))) {
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(
                    R.string.update,
                    (dialog, which) -> context.startActivity(getMarketViewIntent(context)));
        } else {
            builder.setPositiveButton(R.string.ok, null);
        }
        return builder.create();
    }

    public static Dialog createRateLimited(final Context context, final long timestamp) {
        final var builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.rate_limited);
        builder.setMessage(
                context.getString(
                        R.string.try_again_in_x,
                        TimeFrameUtils.resolve(
                                context, timestamp - SystemClock.elapsedRealtime())));
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }

    public static Dialog createTooManyAttempts(final Context context) {
        final var builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.too_many_attempts);
        builder.setPositiveButton(R.string.ok, null);
        return builder.create();
    }

    private static Intent getMarketViewIntent(final Context context) {
        return new Intent(
                Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
    }

    private static boolean resolvable(final Context context, final Intent intent) {
        return !context.getPackageManager().queryIntentActivities(intent, 0).isEmpty();
    }
}
