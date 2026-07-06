package im.conversations.android.provider;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import org.jspecify.annotations.Nullable;

public class VCardProvider {

    private final Context context;

    public VCardProvider(Context context) {
        this.context = context;
    }

    public Uri vCardFromPickIntent(@Nullable final Uri data) {
        if (data == null || !ContactsContract.AUTHORITY.equals(data.getAuthority())) {
            throw new IllegalArgumentException("Invalid authority");
        }
        return Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, asLookup(data));
    }

    private String asLookup(final Uri data) {
        try (final var cursor =
                context.getContentResolver()
                        .query(
                                data,
                                new String[] {ContactsContract.Contacts.LOOKUP_KEY},
                                null,
                                null,
                                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY));
            }
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
        throw new IllegalArgumentException("Could not find look up uri");
    }
}
