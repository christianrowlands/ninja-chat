package eu.siacs.conversations.utils;

import dev.paseto.jpaseto.lang.Keys;
import javax.crypto.SecretKey;

public final class QuicksyAuthentication {

    private static final byte[] SHARED_SECRET = null;

    private QuicksyAuthentication() {
        throw new AssertionError("Do not instantiate me");
    }

    public static boolean hasSharedSecret() {
        return SHARED_SECRET != null && SHARED_SECRET.length != 0;
    }

    public static SecretKey getSharedSecret() {
        return Keys.secretKey(SHARED_SECRET);
    }
}
