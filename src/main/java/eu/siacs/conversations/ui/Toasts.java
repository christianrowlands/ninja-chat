package eu.siacs.conversations.ui;

import android.widget.Toast;
import org.jspecify.annotations.Nullable;

public final class Toasts {

    private Toasts() {
        throw new AssertionError("Do not instantiate me");
    }

    public static void hide(@Nullable final Toast toast) {
        if (toast == null) {
            return;
        }
        toast.cancel();
    }
}
