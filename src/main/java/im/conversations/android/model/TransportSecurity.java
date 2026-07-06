package im.conversations.android.model;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Bytes;

public record TransportSecurity(byte[] key, byte[] iv) {

    public static TransportSecurity ofAnchor(final byte[] anchor) {
        if (anchor.length == 48) {
            final var key = new byte[32];
            final var iv = new byte[16];
            System.arraycopy(anchor, 0, iv, 0, 16);
            System.arraycopy(anchor, 16, key, 0, 32);
            return new TransportSecurity(key, iv);
        } else if (anchor.length == 44) {
            return ofKeyAndIv(anchor);
        } else if (anchor.length >= 32) {
            final var key = new byte[32];
            final var iv =
                    new byte[] {
                        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b,
                        0x0c, 0x0d, 0x0e, 0xf
                    };
            System.arraycopy(anchor, 0, key, 0, 32);
            return new TransportSecurity(key, iv);
        } else {
            throw new IllegalArgumentException("Unrecognized key+iv format");
        }
    }

    public static TransportSecurity ofKeyAndIv(final byte[] keyIv) {
        Preconditions.checkArgument(keyIv.length == 44);
        final var key = new byte[32];
        final var iv = new byte[12];
        System.arraycopy(keyIv, 0, iv, 0, 12);
        System.arraycopy(keyIv, 12, key, 0, 32);
        return new TransportSecurity(key, iv);
    }

    public byte[] asBytes() {
        return Bytes.concat(this.iv, this.key);
    }
}
