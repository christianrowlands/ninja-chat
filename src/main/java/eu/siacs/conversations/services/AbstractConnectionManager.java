package eu.siacs.conversations.services;

import static eu.siacs.conversations.entities.Transferable.VALID_CRYPTO_EXTENSIONS;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.http.HttpUploadConnection;
import im.conversations.android.model.TransportSecurity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

public class AbstractConnectionManager {

    protected XmppConnectionService mXmppConnectionService;

    public AbstractConnectionManager(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // For progress tracking see:
    // https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/Progress.java

    public static RequestBody requestBody(
            final HttpUploadConnection.Upload upload, final ProgressListener progressListener) {
        return new RequestBody() {

            @Override
            public long contentLength() {
                return upload.totalSize();
            }

            @Nullable
            @Override
            public MediaType contentType() {
                return MediaType.parse(upload.mime());
            }

            @Override
            public void writeTo(@NonNull final BufferedSink sink) throws IOException {
                long transmitted = 0;
                try (final Source source =
                        Okio.source(
                                openFileInputStream(upload.file(), upload.transportSecurity()))) {
                    long read;
                    while ((read = source.read(sink.buffer(), 8196)) != -1) {
                        transmitted += read;
                        sink.flush();
                        progressListener.onProgress(transmitted);
                    }
                }
            }
        };
    }

    private static InputStream openFileInputStream(
            final File file, final TransportSecurity transportSecurity)
            throws FileNotFoundException {
        final var fileInputStream = new FileInputStream(file);
        if (transportSecurity == null) {
            return fileInputStream;
        } else {
            final AEADBlockCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
            cipher.init(
                    true,
                    new AEADParameters(
                            new KeyParameter(transportSecurity.key()),
                            128,
                            transportSecurity.iv()));
            Log.d(Config.LOGTAG, "setting up CipherInputStream");
            return new CipherInputStream(fileInputStream, cipher);
        }
    }

    public interface ProgressListener {
        void onProgress(long progress);
    }

    public XmppConnectionService getXmppConnectionService() {
        return this.mXmppConnectionService;
    }

    public static class Extension {
        public final String main;
        public final String secondary;

        private Extension(String main, String secondary) {
            this.main = main;
            this.secondary = secondary;
        }

        public String getExtension() {
            if (VALID_CRYPTO_EXTENSIONS.contains(main)) {
                return secondary;
            } else {
                return main;
            }
        }

        public static Extension of(String path) {
            // TODO accept List<String> pathSegments
            final int pos = path.lastIndexOf('/');
            final String filename = path.substring(pos + 1).toLowerCase();
            final String[] parts = filename.split("\\.");
            final String main = parts.length >= 2 ? parts[parts.length - 1] : null;
            final String secondary = parts.length >= 3 ? parts[parts.length - 2] : null;
            return new Extension(main, secondary);
        }
    }
}
