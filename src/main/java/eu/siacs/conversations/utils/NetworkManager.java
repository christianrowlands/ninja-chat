package eu.siacs.conversations.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.util.Log;
import eu.siacs.conversations.Config;

public class NetworkManager {

    private final Context context;

    public NetworkManager(final Context context) {
        this.context = context;
    }

    public Hint getHint() {
        final var connectivityManager = context.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return Hint.ACTIVE;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                final Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) {
                    return getNoInternetOrAirplaneMode();
                }
                final NetworkCapabilities capabilities =
                        connectivityManager.getNetworkCapabilities(activeNetwork);
                if (capabilities == null) {
                    return getNoInternetOrAirplaneMode();
                }
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return Hint.NO_INTERNET;
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    if (isAirplaneMode()) {
                        return Hint.AIRPLANE_MODE;
                    }
                }
                return Hint.ACTIVE;
            } else {
                final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                if (networkInfo != null
                        && (networkInfo.isConnected()
                                || networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET)) {
                    return Hint.ACTIVE;
                } else {
                    return getNoInternetOrAirplaneMode();
                }
            }
        } catch (final RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to check for internet connection", e);
            return Hint.ACTIVE;
        }
    }

    private boolean isAirplaneMode() {
        return Settings.Global.getInt(
                        context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0)
                != 0;
    }

    private Hint getNoInternetOrAirplaneMode() {
        if (isAirplaneMode()) {
            return Hint.AIRPLANE_MODE;
        } else {
            return Hint.NO_INTERNET;
        }
    }

    public enum Hint {
        ACTIVE,
        NO_INTERNET,
        AIRPLANE_MODE
    }
}
