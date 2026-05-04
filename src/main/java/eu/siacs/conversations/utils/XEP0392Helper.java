package eu.siacs.conversations.utils;

import android.graphics.Color;
import android.os.Build;
import androidx.annotation.ColorInt;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.hsluv.HsluvColorConverter;

public class XEP0392Helper {

    private static double angle(final String nickname) {
        try {
            final var digest =
                    Hashing.sha1().hashString(nickname, StandardCharsets.UTF_8).asBytes();
            final var angle = Byte.toUnsignedInt(digest[0]) + Byte.toUnsignedInt(digest[1]) * 256;
            return angle / 65536. * 360;
        } catch (final Exception e) {
            return 0.0;
        }
    }

    @ColorInt
    public static int rgbFromNick(final String name) {
        return rgbFromAngle(angle(name));
    }

    @ColorInt
    public static int rgbFromAngle(final double angle) {
        final var converter = new HsluvColorConverter();
        converter.hsluv_h = angle;
        converter.hsluv_s = 100;
        converter.hsluv_l = 50;
        converter.hsluvToRgb();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Color.rgb(
                    (float) converter.rgb_r, (float) converter.rgb_g, (float) converter.rgb_b);
        }
        return Color.rgb(
                (int) (converter.rgb_r * 255 + 0.5f),
                (int) (converter.rgb_g * 255 + 0.5f),
                (int) (converter.rgb_b * 255 + 0.5f));
    }
}
