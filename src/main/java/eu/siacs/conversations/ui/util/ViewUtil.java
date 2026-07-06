package eu.siacs.conversations.ui.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import com.google.common.base.Strings;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.MimeUtils;
import java.io.File;
import java.util.Objects;

public class ViewUtil {

    public static final String WILDCARD = "*/*";

    public static void view(final Context context, final Attachment attachment) {
        File file = new File(Objects.requireNonNull(attachment.getUri().getPath()));
        view(context, file, attachment.getUuid().toString(), nullToWildcard(attachment.getMime()));
    }

    public static void view(final Context context, final File file, final String uuid) {
        if (!file.exists()) {
            Toast.makeText(context, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        final var mime = nullToWildcard(MimeUtils.getMimeType(file));
        view(context, file, uuid, mime);
    }

    public static String nullToWildcard(final String mime) {
        return Strings.isNullOrEmpty(mime) ? WILDCARD : mime;
    }

    private static void view(
            final Context context, final File file, final String uuid, final String mime) {
        final Intent openIntent = new Intent(Intent.ACTION_VIEW);
        final Uri uri;
        try {
            uri =
                    FileBackend.getUriForFile(context, file)
                            .buildUpon()
                            .appendQueryParameter("uuid", uuid)
                            .build();
        } catch (final SecurityException e) {
            Toast.makeText(
                            context,
                            context.getString(
                                    R.string.no_permission_to_access_x, file.getAbsolutePath()),
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        openIntent.setDataAndType(uri, mime);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(openIntent);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
