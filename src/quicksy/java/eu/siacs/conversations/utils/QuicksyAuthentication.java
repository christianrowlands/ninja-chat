package eu.siacs.conversations.utils;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import eu.siacs.conversations.BuildConfig;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class QuicksyAuthentication {

    private static final byte[] SHARED_SECRET = loadSecret();

    private static byte[] loadSecret() {
        if (Strings.isNullOrEmpty(BuildConfig.QUICKSY_AUTHENTICATION_SECRET)) {
            return null;
        }
        return BaseEncoding.base64().decode(BuildConfig.QUICKSY_AUTHENTICATION_SECRET);
    }

    private QuicksyAuthentication() {
        throw new AssertionError("Do not instantiate me");
    }

    public static boolean hasSharedSecret() {
        return SHARED_SECRET != null && SHARED_SECRET.length != 0;
    }

    public static SecretKey getSharedSecret() {
        return new SecretKeySpec(SHARED_SECRET, "HmacSHA256");
    }
}
