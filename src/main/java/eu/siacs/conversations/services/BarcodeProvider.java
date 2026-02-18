package eu.siacs.conversations.services;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xmpp.Jid;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Hashtable;

public class BarcodeProvider extends ContentProvider implements ServiceConnection {

    private static final String AUTHORITY = ".barcodes";

    private final Object lock = new Object();

    private XmppConnectionService mXmppConnectionService;
    private boolean mBindingInProcess = false;

    public static Uri getUriForAccount(Context context, Account account) {
        final String packageId = context.getPackageName();
        return Uri.parse(
                "content://" + packageId + AUTHORITY + "/" + account.getJid().asBareJid() + ".png");
    }

    public static Bitmap create2dBarcodeBitmap(final String input, final int size)
            throws WriterException {
        return create2dBarcodeBitmap(input, size, Color.BLACK, Color.WHITE);
    }

    public static Bitmap create2dBarcodeBitmap(
            final String input, final int size, final int black, final int white)
            throws WriterException {
        final QRCodeWriter barcodeWriter = new QRCodeWriter();
        final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        final BitMatrix result =
                barcodeWriter.encode(input, BarcodeFormat.QR_CODE, size, size, hints);
        final int width = result.getWidth();
        final int height = result.getHeight();
        final int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            final int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? black : white;
            }
        }
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    @Override
    public boolean onCreate() {
        final var context = getContext();
        final var cacheDir = context == null ? null : context.getCacheDir();
        final var barcodeDirectory =
                cacheDir == null ? null : new File(cacheDir.getAbsolutePath() + "/barcodes/");
        if (barcodeDirectory != null
                && barcodeDirectory.exists()
                && barcodeDirectory.isDirectory()) {
            final var files = barcodeDirectory.listFiles();
            if (files == null) {
                return true;
            }
            for (final var file : files) {
                if (file.isFile() && !file.isHidden()) {
                    if (file.delete()) {
                        Log.d(Config.LOGTAG, "deleted old barcode file " + file.getAbsolutePath());
                    }
                }
            }
        }
        return true;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull final Uri uri,
            final String[] projection,
            final String selection,
            final String[] selectionArgs,
            final String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull final Uri uri) {
        return "image/png";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull final Uri uri, final ContentValues values) {
        return null;
    }

    @Override
    public int delete(
            @NonNull final Uri uri, final String selection, final String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
            @NonNull final Uri uri,
            final ContentValues values,
            final String selection,
            final String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull final Uri uri, @NonNull final String mode)
            throws FileNotFoundException {
        return openFile(uri, mode, null);
    }

    @Override
    public ParcelFileDescriptor openFile(
            @NonNull final Uri uri, @NonNull final String mode, final CancellationSignal signal)
            throws FileNotFoundException {
        Log.d(Config.LOGTAG, "opening file with uri (normal): " + uri);
        String path = uri.getPath();
        if (path != null && path.endsWith(".png") && path.length() >= 5) {
            String jid = path.substring(1).substring(0, path.length() - 4);
            Log.d(Config.LOGTAG, "account:" + jid);
            if (connectAndWait()) {
                Log.d(Config.LOGTAG, "connected to background service");
                try {
                    Account account = mXmppConnectionService.findAccountByJid(Jid.of(jid));
                    if (account != null) {
                        final var shareableUri = account.getShareableUri().asUri().toString();
                        String hash = CryptoHelper.getFingerprint(shareableUri);
                        File file =
                                new File(
                                        getContext().getCacheDir().getAbsolutePath()
                                                + "/barcodes/"
                                                + hash);
                        if (file.exists()) {
                            return ParcelFileDescriptor.open(
                                    file, ParcelFileDescriptor.MODE_READ_ONLY);
                        }
                        file.getParentFile().mkdirs();
                        file.createNewFile();
                        Bitmap bitmap = create2dBarcodeBitmap(shareableUri, 1024);
                        OutputStream outputStream = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        outputStream.close();
                        outputStream.flush();
                    }
                } catch (final Exception e) {
                    Log.d(Config.LOGTAG, "could not create QR code", e);
                    throw new FileNotFoundException();
                }
            }
        }
        throw new FileNotFoundException();
    }

    private boolean connectAndWait() {
        Intent intent = new Intent(getContext(), XmppConnectionService.class);
        intent.setAction(this.getClass().getSimpleName());
        Context context = getContext();
        if (context != null) {
            synchronized (this) {
                if (mXmppConnectionService == null && !mBindingInProcess) {
                    Log.d(Config.LOGTAG, "calling to bind service");
                    context.bindService(intent, this, Context.BIND_AUTO_CREATE);
                    this.mBindingInProcess = true;
                }
            }
            try {
                waitForService();
                return true;
            } catch (InterruptedException e) {
                return false;
            }
        } else {
            Log.d(Config.LOGTAG, "context was null");
            return false;
        }
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        synchronized (this) {
            XmppConnectionService.XmppConnectionBinder binder =
                    (XmppConnectionService.XmppConnectionBinder) service;
            mXmppConnectionService = binder.getService();
            mBindingInProcess = false;
            synchronized (this.lock) {
                lock.notifyAll();
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        synchronized (this) {
            mXmppConnectionService = null;
        }
    }

    private void waitForService() throws InterruptedException {
        if (mXmppConnectionService == null) {
            synchronized (this.lock) {
                lock.wait();
            }
        } else {
            Log.d(Config.LOGTAG, "not waiting for service because already initialized");
        }
    }
}
