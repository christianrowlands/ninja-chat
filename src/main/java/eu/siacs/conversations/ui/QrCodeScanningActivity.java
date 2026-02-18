package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.activity.result.ScanQrCode;

public abstract class QrCodeScanningActivity extends XmppActivity {

    private final ActivityResultLauncher<Void> scanQrCode =
            registerForActivityResult(
                    new ScanQrCode(),
                    result -> {
                        if (result == null) {
                            return;
                        }
                        onQrCodeScanned(result);
                    });
    private final ActivityResultLauncher<String> requestPermissionScanQr =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    result -> {
                        if (Boolean.TRUE.equals(result)) {
                            scanQrCode.launch(null);
                        } else {
                            Toast.makeText(
                                            this,
                                            R.string.qr_code_scanner_needs_access_to_camera,
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

    protected void requestPermissionAndScanQrCode() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            this.scanQrCode.launch(null);
        } else {
            this.requestPermissionScanQr.launch(Manifest.permission.CAMERA);
        }
    }

    abstract void onQrCodeScanned(final String code);
}
