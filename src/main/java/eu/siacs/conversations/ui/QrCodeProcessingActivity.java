package eu.siacs.conversations.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import de.gultsch.common.MiniUri;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.DialogOpenBrowserBinding;
import eu.siacs.conversations.utils.ScanResultProcessor;
import eu.siacs.conversations.utils.XmppUriLauncher;

public abstract class QrCodeProcessingActivity extends QrCodeScanningActivity {

    @Override
    public void onQrCodeScanned(final String code) {
        final var scanResultProcessor = new ScanResultProcessor(this);
        final var future = scanResultProcessor.process(code);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final MiniUri result) {
                        onMiniUriScanned(result);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "did not recognize qr code content", t);
                        Toast.makeText(
                                        QrCodeProcessingActivity.this,
                                        R.string.invalid_barcode,
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void onMiniUriScanned(final MiniUri miniUri) {
        if (miniUri instanceof MiniUri.Xmpp xmpp) {
            final var uriLauncher = new XmppUriLauncher(this, true);
            uriLauncher.launch(xmpp);
        } else if (miniUri instanceof MiniUri.Http http) {
            final var builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(R.string.qr_code_contains_link);
            final DialogOpenBrowserBinding binding =
                    DataBindingUtil.inflate(
                            getLayoutInflater(), R.layout.dialog_open_browser, null, false);
            binding.uri.setText(http.asUri().toString());
            builder.setView(binding.getRoot());
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(
                    R.string.open_browser,
                    (dialog, which) -> {
                        final Intent intent = new Intent(Intent.ACTION_VIEW, http.asUri());
                        try {
                            startActivity(intent);
                        } catch (final ActivityNotFoundException e) {
                            Toast.makeText(
                                            this,
                                            R.string.no_application_found_to_open_link,
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
            builder.create().show();
        } else {
            Log.d(Config.LOGTAG, "mini uri result: " + miniUri);
            Toast.makeText(
                            QrCodeProcessingActivity.this,
                            R.string.invalid_barcode,
                            Toast.LENGTH_LONG)
                    .show();
        }
    }
}
