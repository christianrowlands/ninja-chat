package eu.siacs.conversations.ui.activity.result;

import android.content.Context;
import android.content.Intent;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import eu.siacs.conversations.ui.ScanQrCodeActivity;

public class ScanQrCode extends ActivityResultContract<Void, String> {
    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context, final Void unused) {
        return new Intent(context, ScanQrCodeActivity.class);
    }

    @Override
    public String parseResult(final int resultCode, final Intent intent) {
        if (resultCode == ScanQrCodeActivity.RESULT_OK && intent != null) {
            return Strings.nullToEmpty(
                    intent.getStringExtra(ScanQrCodeActivity.INTENT_EXTRA_RESULT));
        }
        return null;
    }
}
