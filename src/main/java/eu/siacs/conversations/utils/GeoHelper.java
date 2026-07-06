package eu.siacs.conversations.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import de.gultsch.common.Patterns;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ShowLocationActivity;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import org.osmdroid.util.GeoPoint;

public class GeoHelper {

    public static GeoPoint parseGeoPoint(final Uri uri) {
        return parseGeoPoint(uri.toString());
    }

    public static GeoPoint parseGeoPoint(String body) throws IllegalArgumentException {
        final Matcher matcher = Patterns.URI_GEO.matcher(body);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid geo uri");
        }
        final double latitude;
        final double longitude;
        try {
            latitude = Double.parseDouble(matcher.group(1));
            if (latitude > 90.0 || latitude < -90.0) {
                throw new IllegalArgumentException("Invalid geo uri");
            }
            longitude = Double.parseDouble(matcher.group(2));
            if (longitude > 180.0 || longitude < -180.0) {
                throw new IllegalArgumentException("Invalid geo uri");
            }
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("Invalid geo uri", e);
        }
        return new GeoPoint(latitude, longitude);
    }

    public static void view(Context context, Message message) {
        final GeoPoint geoPoint = parseGeoPoint(message.getBody());
        final String label = getLabel(context, message);
        context.startActivity(geoIntent(geoPoint, label));
    }

    private static Intent geoIntent(GeoPoint geoPoint, String label) {
        // TODO use mini uri geo
        Intent geoIntent = new Intent(Intent.ACTION_VIEW);
        geoIntent.setData(
                Uri.parse(
                        "geo:"
                                + geoPoint.getLatitude()
                                + ","
                                + geoPoint.getLongitude()
                                + "?q="
                                + geoPoint.getLatitude()
                                + ","
                                + geoPoint.getLongitude()
                                + "("
                                + label
                                + ")"));
        return geoIntent;
    }

    public static boolean openInOsmAnd(Context context, Message message) {
        try {
            final GeoPoint geoPoint = parseGeoPoint(message.getBody());
            final String label = getLabel(context, message);
            return geoIntent(geoPoint, label).resolveActivity(context.getPackageManager()) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String getLabel(Context context, Message message) {
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            try {
                return URLEncoder.encode(UIHelper.getMessageDisplayName(message), "UTF-8");
            } catch (final UnsupportedEncodingException e) {
                throw new AssertionError(e);
            }
        } else {
            return context.getString(R.string.me);
        }
    }

    public static Intent showLocationIntent(final Context context, final Message message) {
        final GeoPoint geoPoint = GeoHelper.parseGeoPoint(message.getBody());
        final Intent intent = new Intent(context, ShowLocationActivity.class);
        intent.setAction(ShowLocationActivity.ACTION_SHOW_LOCATION);
        intent.putExtra("latitude", geoPoint.getLatitude());
        intent.putExtra("longitude", geoPoint.getLongitude());
        return intent;
    }
}
