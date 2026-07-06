package eu.siacs.conversations.persistance;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.system.Os;
import android.system.StructStat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import androidx.annotation.StringRes;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.utils.FileWriterException;
import eu.siacs.conversations.utils.MimeUtils;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class FileBackend {

    private static final Object THUMBNAIL_LOCK = new Object();

    private static final String FILE_PROVIDER = ".files";
    private static final float IGNORE_PADDING = 0.15f;
    private final XmppConnectionService mXmppConnectionService;

    public FileBackend(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static long getFileSize(Context context, Uri uri) {
        try (final Cursor cursor =
                context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index == -1) {
                    return -1;
                }
                return cursor.getLong(index);
            }
            return -1;
        } catch (final Exception ignored) {
            return -1;
        }
    }

    public static boolean allFilesUnderSize(
            Context context, List<Attachment> attachments, final Long max) {
        final var appSettings = new AppSettings(context);
        final boolean compressVideo = appSettings.isCompressVideo();
        if (max == null || max <= 0) {
            Log.d(Config.LOGTAG, "server did not report max file size for http upload");
            return true; // exception to be compatible with HTTP Upload < v0.2
        }
        for (Attachment attachment : attachments) {
            if (attachment.getType() != Attachment.Type.FILE) {
                continue;
            }
            String mime = attachment.getMime();
            if (mime != null && mime.startsWith("video/") && compressVideo) {
                try {
                    Dimensions dimensions =
                            FileBackend.getVideoDimensions(context, attachment.getUri());
                    if (dimensions.getMin() > 720) {
                        Log.d(
                                Config.LOGTAG,
                                "do not consider video file with min width larger than 720 for size"
                                        + " check");
                        continue;
                    }
                } catch (final IOException | NotAVideoFile e) {
                    // ignore and fall through
                }
            }
            if (FileBackend.getFileSize(context, attachment.getUri()) > max) {
                Log.d(
                        Config.LOGTAG,
                        "not all files are under "
                                + max
                                + " bytes. suggesting falling back to jingle");
                return false;
            }
        }
        return true;
    }

    public static File getBackupDirectory(final Context context) {
        final File conversationsDownloadDirectory =
                new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS),
                        context.getString(R.string.app_name));
        return new File(conversationsDownloadDirectory, "Backup");
    }

    public static File getLegacyBackupDirectory(final String app) {
        final File appDirectory = new File(Environment.getExternalStorageDirectory(), app);
        return new File(appDirectory, "Backup");
    }

    private static Bitmap rotate(final Bitmap bitmap, final int degree) {
        if (degree == 0) {
            return bitmap;
        }
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        final Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return result;
    }

    private static Paint createAntiAliasingPaint() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        return paint;
    }

    public static Optional<File> getFile(final Uri uri) {
        final var path = uri.getPath();
        if ("file".equals(uri.getScheme()) && path != null) {
            return Optional.of(new File(path));
        } else {
            return Optional.absent();
        }
    }

    public static Uri getUriForUri(final Context context, final Uri uri) {
        final var file = getFile(uri);
        if (file.isPresent()) {
            return getUriForFile(context, file.get());
        } else {
            return uri;
        }
    }

    public static Uri getUriForFile(final Context context, final File file) {
        try {
            return FileProvider.getUriForFile(context, getAuthority(context), file);
        } catch (final IllegalArgumentException e) {
            throw new SecurityException(e);
        }
    }

    public static Optional<String> getMessageUuid(final Context context, final Uri uri) {
        final var scheme = uri.getScheme();
        final var authority = uri.getAuthority();
        if (Strings.isNullOrEmpty(authority) || Strings.isNullOrEmpty(scheme)) {
            return Optional.absent();
        }
        if (scheme.equals("content") && getAuthority(context).equals(authority)) {
            final var uuid = uri.getQueryParameter("uuid");
            if (Strings.isNullOrEmpty(uuid)) {
                return Optional.absent();
            } else {
                return Optional.of(uuid);
            }
        } else {
            return Optional.absent();
        }
    }

    private static String getAuthority(final Context context) {
        return context.getPackageName() + FILE_PROVIDER;
    }

    public static boolean hasAlpha(final Bitmap bitmap) {
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final int yStep = Math.max(1, w / 100);
        final int xStep = Math.max(1, h / 100);
        for (int x = 0; x < w; x += xStep) {
            for (int y = 0; y < h; y += yStep) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int calcSampleSize(File image, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(image.getAbsolutePath(), options);
        return calcSampleSize(options, size);
    }

    private static int calcSampleSize(BitmapFactory.Options options, int size) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > size || width > size) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > size && (halfWidth / inSampleSize) > size) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static Dimensions getVideoDimensions(Context context, Uri uri)
            throws NotAVideoFile, IOException {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
            mediaMetadataRetriever.setDataSource(context, uri);
        } catch (RuntimeException e) {
            throw new NotAVideoFile(e);
        }
        return getVideoDimensions(mediaMetadataRetriever);
    }

    private static Dimensions getVideoDimensionsOfFrame(
            MediaMetadataRetriever mediaMetadataRetriever) {
        Bitmap bitmap = null;
        try {
            bitmap = mediaMetadataRetriever.getFrameAtTime();
            return new Dimensions(bitmap.getHeight(), bitmap.getWidth());
        } catch (Exception e) {
            return null;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private static Dimensions getVideoDimensions(MediaMetadataRetriever metadataRetriever)
            throws NotAVideoFile, IOException {
        String hasVideo =
                metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
        if (hasVideo == null) {
            throw new NotAVideoFile();
        }
        Dimensions dimensions = getVideoDimensionsOfFrame(metadataRetriever);
        if (dimensions != null) {
            return dimensions;
        }
        final int rotation = extractRotationFromMediaRetriever(metadataRetriever);
        boolean rotated = rotation == 90 || rotation == 270;
        int height;
        try {
            String h =
                    metadataRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            height = Integer.parseInt(h);
        } catch (Exception e) {
            height = -1;
        }
        int width;
        try {
            String w =
                    metadataRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            width = Integer.parseInt(w);
        } catch (Exception e) {
            width = -1;
        }
        metadataRetriever.release();
        Log.d(Config.LOGTAG, "extracted video dims " + width + "x" + height);
        return rotated ? new Dimensions(width, height) : new Dimensions(height, width);
    }

    private static int extractRotationFromMediaRetriever(MediaMetadataRetriever metadataRetriever) {
        String r =
                metadataRetriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        try {
            return Integer.parseInt(r);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void close(final Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to close stream", e);
            }
        }
    }

    public static void close(final Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.d(Config.LOGTAG, "unable to close socket", e);
            }
        }
    }

    public static void close(final ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.d(Config.LOGTAG, "unable to close server socket", e);
            }
        }
    }

    public static boolean dangerousFile(final Uri uri) {
        if (uri == null || Strings.isNullOrEmpty(uri.getScheme())) {
            return true;
        }
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // On Android 7 (and apps that target 7) it is now longer possible to share files
                // with a file scheme. By now you should probably not be running apps that target
                // anything less than 7 any more
                return true;
            } else {
                return isFileOwnedByProcess(uri);
            }
        }
        return false;
    }

    private static boolean isFileOwnedByProcess(final Uri uri) {
        final String path = uri.getPath();
        if (path == null) {
            return true;
        }
        try (final var pfd =
                ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY)) {
            final FileDescriptor fd = pfd.getFileDescriptor();
            final StructStat st = Os.fstat(fd);
            return st.st_uid == android.os.Process.myUid();
        } catch (final Exception e) {
            // when in doubt. better safe than sorry
            return true;
        }
    }

    public static Uri getMediaUri(Context context, File file) {
        final String filePath = file.getAbsolutePath();
        try (final Cursor cursor =
                context.getContentResolver()
                        .query(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                new String[] {MediaStore.Images.Media._ID},
                                MediaStore.Images.Media.DATA + "=? ",
                                new String[] {filePath},
                                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int id =
                        cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                return Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            } else {
                return null;
            }
        } catch (final Exception e) {
            return null;
        }
    }

    public static void updateFileParams(Message message, String url, long size) {
        message.setBody(url + '|' + size);
    }

    public Bitmap getPreviewForUri(Attachment attachment, int size, boolean cacheOnly) {
        final String key = "attachment_" + attachment.getUuid().toString() + "_" + size;
        final LruCache<String, Bitmap> cache = mXmppConnectionService.getBitmapCache();
        final Bitmap cached = cache.get(key);
        if (cached != null || cacheOnly) {
            return cached;
        }
        final String mime = attachment.getMime();
        final Bitmap bitmap;
        if ("application/pdf".equals(mime)) {
            bitmap = cropCenterSquarePdf(attachment.getUri(), size);
            drawOverlay(
                    bitmap,
                    paintOverlayBlackPdf(bitmap)
                            ? R.drawable.open_pdf_black
                            : R.drawable.open_pdf_white,
                    0.75f);
        } else if (mime != null && mime.startsWith("video/")) {
            bitmap = cropCenterSquareVideo(attachment.getUri(), size);
            drawOverlay(
                    bitmap,
                    paintOverlayBlack(bitmap)
                            ? R.drawable.play_video_black
                            : R.drawable.play_video_white,
                    0.75f);
        } else {
            final var preview = cropCenterSquare(attachment.getUri(), size);
            if (preview != null && "image/gif".equals(mime)) {
                Bitmap withGifOverlay = preview.copy(Bitmap.Config.ARGB_8888, true);
                drawOverlay(
                        withGifOverlay,
                        paintOverlayBlack(withGifOverlay)
                                ? R.drawable.play_gif_black
                                : R.drawable.play_gif_white,
                        1.0f);
                preview.recycle();
                bitmap = withGifOverlay;
            } else {
                bitmap = preview;
            }
        }
        if (bitmap != null) {
            cache.put(key, bitmap);
        }
        return bitmap;
    }

    public void updateMediaScanner(final File file) {
        updateMediaScanner(file, null);
    }

    public void updateMediaScanner(final File file, final Runnable callback) {
        MediaScannerConnection.scanFile(
                mXmppConnectionService,
                new String[] {file.getAbsolutePath()},
                null,
                new MediaScannerConnection.MediaScannerConnectionClient() {
                    @Override
                    public void onMediaScannerConnected() {}

                    @Override
                    public void onScanCompleted(final String path, final Uri uri) {
                        if (!file.getAbsolutePath().equals(path)) {
                            Log.w(Config.LOGTAG, "media scanner scanned wrong file");
                        }
                        if (callback != null) {
                            callback.run();
                        }
                    }
                });
    }

    public boolean deleteFile(Message message) {
        File file = getFile(message);
        if (file.delete()) {
            updateMediaScanner(file);
            return true;
        } else {
            return false;
        }
    }

    public static File getLegacyFileForFilename(final Context context, final String filename) {
        final var mime =
                MimeUtils.guessMimeTypeFromExtension(MimeUtils.extractRelevantExtension(filename));
        return getLegacyFileForFilename(context, filename, mime);
    }

    public static File getLegacyFileForFilename(
            final Context context, final String filename, final String mime) {
        if (Strings.isNullOrEmpty(mime)) {
            return new File(getLegacyStorageLocation(context, "Files"), filename);
        } else if (mime.startsWith("image/")) {
            return new File(getLegacyStorageLocation(context, "Images"), filename);
        } else if (mime.startsWith("video/")) {
            return new File(getLegacyStorageLocation(context, "Videos"), filename);
        } else {
            return new File(getLegacyStorageLocation(context, "Files"), filename);
        }
    }

    public File getFile(final Message message) {
        return getFile(message, true);
    }

    public File getFile(final Message message, final boolean cleartext) {
        final boolean encrypted =
                !cleartext
                        && (message.getEncryption() == Message.ENCRYPTION_PGP
                                || message.getEncryption() == Message.ENCRYPTION_DECRYPTED);
        final var storageLocation = message.getRelativeFilePath();
        final File file;
        if (storageLocation != null) {
            file = storageLocation.file();
        } else {
            Log.w(Config.LOGTAG, "accessing old file w/o storage location. inferring from UUID");
            file =
                    getLegacyFileForFilename(
                            mXmppConnectionService, message.getUuid(), message.getMimeType());
        }
        if (encrypted) {
            return new File(
                    mXmppConnectionService.getCacheDir(),
                    String.format("%s.%s", file.getName(), "pgp"));
        } else {
            return file;
        }
    }

    public List<Attachment> convertToAttachments(List<DatabaseBackend.FilePath> relativeFilePaths) {
        final List<Attachment> attachments = new ArrayList<>();
        for (final DatabaseBackend.FilePath relativeFilePath : relativeFilePaths) {
            final String mime =
                    MimeUtils.guessMimeTypeFromExtension(
                            MimeUtils.extractRelevantExtension(relativeFilePath.path));
            final File file;
            if (relativeFilePath.path != null
                    && !relativeFilePath.path.isEmpty()
                    && relativeFilePath.path.charAt(0) == '/') {
                file = new File(relativeFilePath.path);
            } else {
                file =
                        getLegacyFileForFilename(
                                mXmppConnectionService, relativeFilePath.path, mime);
            }
            attachments.add(Attachment.of(relativeFilePath.uuid, file, mime));
        }
        return attachments;
    }

    private static File getLegacyStorageLocation(final Context context, final String type) {
        final var appDirectory =
                new File(
                        Environment.getExternalStorageDirectory(),
                        context.getString(R.string.app_name));
        final var appMediaDirectory = new File(appDirectory, "Media");
        final var locationName = String.format("%s %s", context.getString(R.string.app_name), type);
        return new File(appMediaDirectory, locationName);
    }

    private Bitmap resize(final Bitmap originalBitmap, int size) throws IOException {
        int w = originalBitmap.getWidth();
        int h = originalBitmap.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IOException("Decoded bitmap reported bounds smaller 0");
        } else if (Math.max(w, h) > size) {
            int scalledW;
            int scalledH;
            if (w <= h) {
                scalledW = Math.max((int) (w / ((double) h / size)), 1);
                scalledH = size;
            } else {
                scalledW = size;
                scalledH = Math.max((int) (h / ((double) w / size)), 1);
            }
            final Bitmap result =
                    Bitmap.createScaledBitmap(originalBitmap, scalledW, scalledH, true);
            if (!originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            return result;
        } else {
            return originalBitmap;
        }
    }

    private void copyFileToPrivateStorage(final File file, final Uri uri) throws FileCopyException {
        final var parent = file.getParentFile();
        if (parent != null && parent.mkdirs()) {
            Log.d(Config.LOGTAG, "created parent directory: " + parent.getAbsolutePath());
            mXmppConnectionService.restartFileObserver();
        }
        try {
            if (file.createNewFile()) {
                Log.d(Config.LOGTAG, "created empty file " + file.getAbsolutePath());
            }
        } catch (final IOException e) {
            throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
        }
        try (final OutputStream os = new FileOutputStream(file);
                final InputStream is =
                        mXmppConnectionService.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new FileCopyException(R.string.error_file_not_found);
            }
            try {
                ByteStreams.copy(is, os);
            } catch (final IOException e) {
                throw new FileWriterException(file);
            }
            try {
                os.flush();
            } catch (final IOException e) {
                throw new FileWriterException(file);
            }
        } catch (final FileNotFoundException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (final FileWriterException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
        } catch (final SecurityException | IllegalStateException | IllegalArgumentException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_security_exception);
        } catch (final IOException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_io_exception);
        }
        final var originalFile = getFile(uri);
        if (originalFile.isPresent()
                && new Cache(mXmppConnectionService).isCachedFile(originalFile.get())) {
            if (originalFile.get().delete()) {
                Log.d(
                        Config.LOGTAG,
                        "deleted temporary file: " + originalFile.get().getAbsolutePath());
            }
        }
    }

    public void copyFileToPrivateStorage(final Message message, final Uri uri, final String type)
            throws FileCopyException {
        final String mime =
                MimeUtils.guessMimeTypeFromUriAndMime(mXmppConnectionService, uri, type);
        Log.d(Config.LOGTAG, "copy " + uri.toString() + " to private storage (mime=" + mime + ")");
        final var file = getFile(uri);
        if (file.isPresent()
                && new FileBackend.Cache(mXmppConnectionService).isCachedFile(file.get())) {
            // These are files we created ourselves like recordings and photos
            setupRelativeFilePath(message, file.get().getName());
        } else {
            final String extension;
            final var extensionForType = MimeUtils.guessExtensionFromMimeType(mime);
            if (extensionForType != null) {
                extension = extensionForType;
            } else {
                final var extensionFromUri = getExtensionFromUri(uri);
                if ("ogg".equals(extensionFromUri) && type != null && type.startsWith("audio")) {
                    extension = "oga";
                } else {
                    extension = extensionFromUri;
                }
            }
            setupRelativeFilePath(message, String.format("%s.%s", message.getUuid(), extension));
        }
        copyFileToPrivateStorage(mXmppConnectionService.getFileBackend().getFile(message), uri);
    }

    private String getExtensionFromUri(final Uri uri) {
        final String filename = getFilenameFromUri(uri);
        return Iterables.getLast(Splitter.on('.').split(filename), null);
    }

    private String getFilenameFromUri(final Uri uri) {
        final String[] projection = {MediaStore.MediaColumns.DATA};
        try (final Cursor cursor =
                mXmppConnectionService
                        .getContentResolver()
                        .query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return Strings.emptyToNull(cursor.getString(0));
            } else {
                return Iterables.getLast(uri.getPathSegments(), null);
            }
        } catch (final Exception e) {
            return Iterables.getLast(uri.getPathSegments(), null);
        }
    }

    private void copyImageToPrivateStorage(final File file, final Uri image, int sampleSize)
            throws FileCopyException, ImageCompressionException {
        final File parent = file.getParentFile();
        if (parent != null && parent.mkdirs()) {
            Log.d(Config.LOGTAG, "created parent directory: " + parent.getAbsolutePath());
            mXmppConnectionService.restartFileObserver();
        }
        InputStream is = null;
        OutputStream os = null;
        try {
            if (!file.exists() && !file.createNewFile()) {
                throw new FileCopyException(R.string.error_unable_to_create_temporary_file);
            }
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                throw new FileCopyException(R.string.error_not_an_image_file);
            }
            final Bitmap originalBitmap;
            final BitmapFactory.Options options = new BitmapFactory.Options();
            final int inSampleSize = (int) Math.pow(2, sampleSize);
            Log.d(Config.LOGTAG, "reading bitmap with sample size " + inSampleSize);
            options.inSampleSize = inSampleSize;
            originalBitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            if (originalBitmap == null) {
                throw new ImageCompressionException("Source file was not an image");
            }
            if (!"image/jpeg".equals(options.outMimeType) && hasAlpha(originalBitmap)) {
                originalBitmap.recycle();
                throw new ImageCompressionException("Source file had alpha channel");
            }
            Bitmap scaledBitmap = resize(originalBitmap, Config.IMAGE_SIZE);
            final int rotation = getRotation(mXmppConnectionService, image);
            scaledBitmap = rotate(scaledBitmap, rotation);
            boolean targetSizeReached = false;
            int quality = Config.IMAGE_QUALITY;
            final int imageMaxSize =
                    mXmppConnectionService
                            .getResources()
                            .getInteger(R.integer.auto_accept_filesize);
            while (!targetSizeReached) {
                os = new FileOutputStream(file);
                Log.d(Config.LOGTAG, "compressing image with quality " + quality);
                boolean success = scaledBitmap.compress(Config.IMAGE_FORMAT, quality, os);
                if (!success) {
                    throw new FileCopyException(R.string.error_compressing_image);
                }
                os.flush();
                final long fileSize = file.length();
                Log.d(Config.LOGTAG, "achieved file size of " + fileSize);
                targetSizeReached = fileSize <= imageMaxSize || quality <= 50;
                quality -= 5;
            }
            scaledBitmap.recycle();
        } catch (final FileNotFoundException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_file_not_found);
        } catch (final IOException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_io_exception);
        } catch (SecurityException e) {
            cleanup(file);
            throw new FileCopyException(R.string.error_security_exception_during_image_copy);
        } catch (final OutOfMemoryError e) {
            ++sampleSize;
            if (sampleSize <= 3) {
                copyImageToPrivateStorage(file, image, sampleSize);
            } else {
                throw new FileCopyException(R.string.error_out_of_memory);
            }
        } finally {
            close(os);
            close(is);
        }
        final var originalFile = getFile(image);
        if (originalFile.isPresent()
                && new Cache(mXmppConnectionService).isCachedFile(originalFile.get())) {
            if (originalFile.get().delete()) {
                Log.d(
                        Config.LOGTAG,
                        "deleted temporary file: " + originalFile.get().getAbsolutePath());
            }
        }
    }

    private static void cleanup(final File file) {
        try {
            file.delete();
        } catch (Exception e) {

        }
    }

    public void copyImageToPrivateStorage(File file, Uri image)
            throws FileCopyException, ImageCompressionException {
        Log.d(
                Config.LOGTAG,
                "copy image ("
                        + image.toString()
                        + ") to private storage "
                        + file.getAbsolutePath());
        copyImageToPrivateStorage(file, image, 0);
    }

    public void copyImageToPrivateStorage(final Message message, final Uri image)
            throws FileCopyException, ImageCompressionException {
        final var file = getFile(image);
        if (Config.IMAGE_FORMAT == Bitmap.CompressFormat.JPEG
                && file.isPresent()
                && new Cache(mXmppConnectionService).isCachedFile(file.get())) {
            setupRelativeFilePath(message, file.get().getName());
        } else {
            final String filename =
                    switch (Config.IMAGE_FORMAT) {
                        case JPEG -> String.format("%s.%s", message.getUuid(), "jpg");
                        case PNG -> String.format("%s.%s", message.getUuid(), "png");
                        case WEBP -> String.format("%s.%s", message.getUuid(), "webp");
                        default -> throw new IllegalStateException("Unknown image format");
                    };
            setupRelativeFilePath(message, filename);
        }
        copyImageToPrivateStorage(getFile(message), image);
        updateFileParams(message);
    }

    public void setupRelativeFilePath(final Message message, final String filename) {
        final String extension = MimeUtils.extractRelevantExtension(filename);
        final String mime = MimeUtils.guessMimeTypeFromExtension(extension);
        setupRelativeFilePath(message, filename, mime);
    }

    public Message.StorageLocation getStorageLocation(final String filename, final String mime) {
        final var appSettings = new AppSettings(mXmppConnectionService.getApplicationContext());
        if (appSettings.isUseSharedStorage()) {
            final var file = getSharedStorageLocation(filename, mime);
            return new Message.StorageLocation(file, true);
        } else {
            final var file = getInternalStorageLocation(filename);
            return new Message.StorageLocation(file, false);
        }
    }

    private File getSharedStorageLocation(final String filename, final String mime) {
        final File parentDirectory;
        if (Strings.isNullOrEmpty(mime)) {
            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        } else if (mime.startsWith("image/")) {
            if (Cache.isImageFilenamePattern(filename)) {
                parentDirectory =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            } else {
                parentDirectory =
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES);
            }
        } else if (mime.startsWith("video/")) {
            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        } else if (MediaAdapter.DOCUMENT_MIMES.contains(mime)) {
            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        } else if (mime.equals("audio/x-m4b") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS);
        } else if (mime.startsWith("audio/")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && Cache.isRecordingFilenamePattern(filename)) {

            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS);
        } else {
            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }
        final File appDirectory =
                new File(parentDirectory, mXmppConnectionService.getString(R.string.app_name));
        return new File(appDirectory, filename);
    }

    private File getInternalStorageLocation(final String filename) {
        final var filesDir = mXmppConnectionService.getFilesDir();
        final var attachments = new File(filesDir, "attachments");
        return new File(attachments, filename);
    }

    public List<Message.StorageLocation> inferStorageLocation(
            final Collection<Attachment> attachments) {
        final var filesDir = mXmppConnectionService.getFilesDir();
        final var attachmentsDir = new File(filesDir, "attachments");
        final var builder = new ImmutableList.Builder<Message.StorageLocation>();
        for (final var attachment : attachments) {
            final var file = getFile(attachment.getUri());
            if (file.isPresent()) {
                final var parent = file.get().getParentFile();
                final var sharedStorage = parent == null || !parent.equals(attachmentsDir);
                builder.add(new Message.StorageLocation(file.get(), sharedStorage));
            }
        }
        return builder.build();
    }

    public void setupRelativeFilePath(
            final Message message, final String filename, final String mime) {
        final var storageLocation = getStorageLocation(filename, mime);
        Log.d(Config.LOGTAG, storageLocation.toString());
        message.setRelativeFilePath(storageLocation);
    }

    public boolean unusualBounds(final Uri image) {
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            final InputStream inputStream =
                    mXmppConnectionService.getContentResolver().openInputStream(image);
            BitmapFactory.decodeStream(inputStream, null, options);
            close(inputStream);
            float ratio = (float) options.outHeight / options.outWidth;
            return ratio > (21.0f / 9.0f) || ratio < (9.0f / 21.0f);
        } catch (final Exception e) {
            Log.w(Config.LOGTAG, "unable to detect image bounds", e);
            return false;
        }
    }

    private int getRotation(final File file) {
        try (final InputStream inputStream = new FileInputStream(file)) {
            return getRotation(inputStream);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getRotation(final Context context, final Uri image) {
        try (final InputStream is = context.getContentResolver().openInputStream(image)) {
            return is == null ? 0 : getRotation(is);
        } catch (final Exception e) {
            return 0;
        }
    }

    private static int getRotation(final InputStream inputStream) throws IOException {
        final ExifInterface exif = new ExifInterface(inputStream);
        final int orientation =
                exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    public Bitmap getThumbnail(Message message, int size, boolean cacheOnly) throws IOException {
        final String uuid = message.getUuid();
        final LruCache<String, Bitmap> cache = mXmppConnectionService.getBitmapCache();
        Bitmap thumbnail = cache.get(uuid);
        if ((thumbnail == null) && (!cacheOnly)) {
            synchronized (THUMBNAIL_LOCK) {
                thumbnail = cache.get(uuid);
                if (thumbnail != null) {
                    return thumbnail;
                }
                final var file = getFile(message);
                final String mime = MimeUtils.getMimeType(file);
                if ("application/pdf".equals(mime)) {
                    thumbnail = getPdfDocumentPreview(file, size);
                } else if (mime.startsWith("video/")) {
                    thumbnail = getVideoPreview(file, size);
                } else {
                    final Bitmap fullSize = getFullSizeImagePreview(file, size);
                    if (fullSize == null) {
                        throw new FileNotFoundException();
                    }
                    thumbnail = resize(fullSize, size);
                    thumbnail = rotate(thumbnail, getRotation(file));
                    if (mime.equals("image/gif")) {
                        Bitmap withGifOverlay = thumbnail.copy(Bitmap.Config.ARGB_8888, true);
                        drawOverlay(
                                withGifOverlay,
                                paintOverlayBlack(withGifOverlay)
                                        ? R.drawable.play_gif_black
                                        : R.drawable.play_gif_white,
                                1.0f);
                        thumbnail.recycle();
                        thumbnail = withGifOverlay;
                    }
                }
                cache.put(uuid, thumbnail);
            }
        }
        return thumbnail;
    }

    private Bitmap getFullSizeImagePreview(File file, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calcSampleSize(file, size);
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            options.inSampleSize *= 2;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }
    }

    private void drawOverlay(Bitmap bitmap, int resource, float factor) {
        Bitmap overlay =
                BitmapFactory.decodeResource(mXmppConnectionService.getResources(), resource);
        Canvas canvas = new Canvas(bitmap);
        float targetSize = Math.min(canvas.getWidth(), canvas.getHeight()) * factor;
        Log.d(
                Config.LOGTAG,
                "target size overlay: "
                        + targetSize
                        + " overlay bitmap size was "
                        + overlay.getHeight());
        float left = (canvas.getWidth() - targetSize) / 2.0f;
        float top = (canvas.getHeight() - targetSize) / 2.0f;
        RectF dst = new RectF(left, top, left + targetSize - 1, top + targetSize - 1);
        canvas.drawBitmap(overlay, null, dst, createAntiAliasingPaint());
    }

    /** https://stackoverflow.com/a/3943023/210897 */
    private boolean paintOverlayBlack(final Bitmap bitmap) {
        final int h = bitmap.getHeight();
        final int w = bitmap.getWidth();
        int record = 0;
        for (int y = Math.round(h * IGNORE_PADDING); y < h - Math.round(h * IGNORE_PADDING); ++y) {
            for (int x = Math.round(w * IGNORE_PADDING);
                    x < w - Math.round(w * IGNORE_PADDING);
                    ++x) {
                int pixel = bitmap.getPixel(x, y);
                if ((Color.red(pixel) * 0.299
                                + Color.green(pixel) * 0.587
                                + Color.blue(pixel) * 0.114)
                        > 186) {
                    --record;
                } else {
                    ++record;
                }
            }
        }
        return record < 0;
    }

    private boolean paintOverlayBlackPdf(final Bitmap bitmap) {
        final int h = bitmap.getHeight();
        final int w = bitmap.getWidth();
        int white = 0;
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                int pixel = bitmap.getPixel(x, y);
                if ((Color.red(pixel) * 0.299
                                + Color.green(pixel) * 0.587
                                + Color.blue(pixel) * 0.114)
                        > 186) {
                    white++;
                }
            }
        }
        return white > (h * w * 0.4f);
    }

    private Bitmap cropCenterSquareVideo(Uri uri, int size) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        Bitmap frame;
        try {
            metadataRetriever.setDataSource(mXmppConnectionService, uri);
            frame = metadataRetriever.getFrameAtTime(0);
            metadataRetriever.release();
            return cropCenterSquare(frame, size);
        } catch (Exception e) {
            frame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            frame.eraseColor(0xff000000);
            return frame;
        }
    }

    private Bitmap getVideoPreview(final File file, final int size) {
        final MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        Bitmap frame;
        try {
            metadataRetriever.setDataSource(file.getAbsolutePath());
            frame = metadataRetriever.getFrameAtTime(0);
            metadataRetriever.release();
            frame = resize(frame, size);
        } catch (IOException | RuntimeException e) {
            frame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            frame.eraseColor(0xff000000);
        }
        drawOverlay(
                frame,
                paintOverlayBlack(frame)
                        ? R.drawable.play_video_black
                        : R.drawable.play_video_white,
                0.75f);
        return frame;
    }

    private Bitmap getPdfDocumentPreview(final File file, final int size) {
        try {
            final ParcelFileDescriptor fileDescriptor =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            final Bitmap rendered = renderPdfDocument(fileDescriptor, size, true);
            drawOverlay(
                    rendered,
                    paintOverlayBlackPdf(rendered)
                            ? R.drawable.open_pdf_black
                            : R.drawable.open_pdf_white,
                    0.75f);
            return rendered;
        } catch (final IOException | SecurityException e) {
            Log.d(Config.LOGTAG, "unable to render PDF document preview", e);
            final Bitmap placeholder = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            placeholder.eraseColor(0xff000000);
            return placeholder;
        }
    }

    private Bitmap cropCenterSquarePdf(final Uri uri, final int size) {
        try {
            ParcelFileDescriptor fileDescriptor =
                    mXmppConnectionService.getContentResolver().openFileDescriptor(uri, "r");
            final Bitmap bitmap = renderPdfDocument(fileDescriptor, size, false);
            return cropCenterSquare(bitmap, size);
        } catch (Exception e) {
            final Bitmap placeholder = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            placeholder.eraseColor(0xff000000);
            return placeholder;
        }
    }

    private Bitmap renderPdfDocument(
            ParcelFileDescriptor fileDescriptor, int targetSize, boolean fit) throws IOException {
        final PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
        final PdfRenderer.Page page = pdfRenderer.openPage(0);
        final Dimensions dimensions =
                scalePdfDimensions(
                        new Dimensions(page.getHeight(), page.getWidth()), targetSize, fit);
        final Bitmap rendered =
                Bitmap.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888);
        rendered.eraseColor(0xffffffff);
        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        pdfRenderer.close();
        fileDescriptor.close();
        return rendered;
    }

    public void deleteHistoricAvatarPath() {
        delete(getHistoricAvatarPath());
    }

    private void delete(final File file) {
        if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (files != null) {
                for (final File f : files) {
                    delete(f);
                }
            }
        }
        if (file.delete()) {
            Log.d(Config.LOGTAG, "deleted " + file.getAbsolutePath());
        }
    }

    private File getHistoricAvatarPath() {
        return new File(mXmppConnectionService.getFilesDir(), "/avatars/");
    }

    public File getAvatarFile(final String avatar) {
        return getAvatarFile(mXmppConnectionService, avatar);
    }

    public static File getAvatarFile(Context context, final String avatar) {
        return new File(context.getCacheDir(), "/avatars/" + avatar);
    }

    public Uri getAvatarUri(String avatar) {
        return Uri.fromFile(getAvatarFile(avatar));
    }

    public Bitmap cropCenterSquare(final Uri image, final int size) {
        return cropCenterSquare(mXmppConnectionService, image, size);
    }

    public static Bitmap cropCenterSquare(final Context context, final Uri image, final int size) {
        if (image == null) {
            return null;
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        try {
            options.inSampleSize = calcSampleSize(context, image, size);
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "unable to calculate sample size for " + image, e);
            return null;
        }
        try (final InputStream is = context.getContentResolver().openInputStream(image)) {
            if (is == null) {
                return null;
            }
            final var originalBitmap = BitmapFactory.decodeStream(is, null, options);
            if (originalBitmap == null) {
                return null;
            } else {
                final var bitmap = rotate(originalBitmap, getRotation(context, image));
                return cropCenterSquare(bitmap, size);
            }
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "unable to open file " + image, e);
            return null;
        }
    }

    public Bitmap cropCenter(final Uri image, final int newHeight, final int newWidth) {
        if (image == null) {
            return null;
        }
        InputStream is = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calcSampleSize(image, Math.max(newHeight, newWidth));
            is = mXmppConnectionService.getContentResolver().openInputStream(image);
            if (is == null) {
                return null;
            }
            Bitmap source = BitmapFactory.decodeStream(is, null, options);
            if (source == null) {
                return null;
            }
            int sourceWidth = source.getWidth();
            int sourceHeight = source.getHeight();
            float xScale = (float) newWidth / sourceWidth;
            float yScale = (float) newHeight / sourceHeight;
            float scale = Math.max(xScale, yScale);
            float scaledWidth = scale * sourceWidth;
            float scaledHeight = scale * sourceHeight;
            float left = (newWidth - scaledWidth) / 2;
            float top = (newHeight - scaledHeight) / 2;

            RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
            Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(dest);
            canvas.drawBitmap(source, null, targetRect, createAntiAliasingPaint());
            if (source.isRecycled()) {
                source.recycle();
            }
            return dest;
        } catch (SecurityException e) {
            return null; // android 6.0 with revoked permissions for example
        } catch (IOException e) {
            return null;
        } finally {
            close(is);
        }
    }

    public static Bitmap cropCenterSquare(final Bitmap input, final int sizeIn) {
        final int w = input.getWidth();
        final int h = input.getHeight();
        final int size;
        final float outWidth;
        final float outHeight;
        if (w < sizeIn || h < sizeIn) {
            size = Math.min(w, h);
            outWidth = w;
            outHeight = h;
        } else {
            size = sizeIn;
            final float scale = Math.max((float) sizeIn / h, (float) sizeIn / w);
            outWidth = scale * w;
            outHeight = scale * h;
        }
        float left = (size - outWidth) / 2;
        float top = (size - outHeight) / 2;
        final var target = new RectF(left, top, left + outWidth, top + outHeight);

        final var output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        final var canvas = new Canvas(output);
        canvas.drawBitmap(input, null, target, createAntiAliasingPaint());
        if (!input.isRecycled()) {
            input.recycle();
        }
        return output;
    }

    private int calcSampleSize(final Uri image, int size) throws IOException, SecurityException {
        return calcSampleSize(mXmppConnectionService, image, size);
    }

    private static int calcSampleSize(final Context context, final Uri image, int size)
            throws IOException, SecurityException {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (final InputStream inputStream = context.getContentResolver().openInputStream(image)) {
            BitmapFactory.decodeStream(inputStream, null, options);
            return calcSampleSize(options, size);
        }
    }

    public void updateFileParams(final Message message) {
        updateFileParams(message, null);
    }

    public void updateFileParams(final Message message, final String url) {
        final boolean encrypted =
                message.getEncryption() == Message.ENCRYPTION_PGP
                        || message.getEncryption() == Message.ENCRYPTION_DECRYPTED;
        final var file = getFile(message);
        final String mime = MimeUtils.getMimeType(file);
        final boolean image =
                message.getType() == Message.TYPE_IMAGE
                        || (mime != null && mime.startsWith("image/"));
        final boolean privateMessage = message.isPrivateMessage();
        final StringBuilder body = new StringBuilder();
        if (url != null) {
            body.append(url);
        }
        if (encrypted && !file.exists()) {
            Log.d(Config.LOGTAG, "skipping updateFileParams because file is encrypted");
            final var encryptedFile = getFile(message, false);
            body.append('|').append(encryptedFile.length());
        } else {
            Log.d(Config.LOGTAG, "running updateFileParams");
            final boolean ambiguous = MimeUtils.AMBIGUOUS_CONTAINER_FORMATS.contains(mime);
            final boolean video = mime != null && mime.startsWith("video/");
            final boolean audio = mime != null && mime.startsWith("audio/");
            final boolean pdf = "application/pdf".equals(mime);
            body.append('|').append(file.length());
            if (ambiguous) {
                try {
                    final Dimensions dimensions = getVideoDimensions(file);
                    if (dimensions.valid()) {
                        Log.d(Config.LOGTAG, "ambiguous file " + mime + " is video");
                        body.append('|')
                                .append(dimensions.width)
                                .append('|')
                                .append(dimensions.height);
                    } else {
                        Log.d(Config.LOGTAG, "ambiguous file " + mime + " is audio");
                        body.append("|0|0|").append(getMediaRuntime(file));
                    }
                } catch (final IOException | NotAVideoFile e) {
                    Log.d(Config.LOGTAG, "ambiguous file " + mime + " is audio");
                    body.append("|0|0|").append(getMediaRuntime(file));
                }
            } else if (image || video || pdf) {
                try {
                    final Dimensions dimensions;
                    if (video) {
                        dimensions = getVideoDimensions(file);
                    } else if (pdf) {
                        dimensions = getPdfDocumentDimensions(file);
                    } else {
                        dimensions = getImageDimensions(file);
                    }
                    if (dimensions.valid()) {
                        body.append('|')
                                .append(dimensions.width)
                                .append('|')
                                .append(dimensions.height);
                    }
                } catch (final IOException | NotAVideoFile notAVideoFile) {
                    Log.d(
                            Config.LOGTAG,
                            "file with mime type "
                                    + MimeUtils.getMimeType(file)
                                    + " was not a video file");
                    // fall threw
                }
            } else if (audio) {
                body.append("|0|0|").append(getMediaRuntime(file));
            }
        }
        message.setBody(body.toString());
        message.setDeleted(false);
        message.setType(
                privateMessage
                        ? Message.TYPE_PRIVATE_FILE
                        : (image ? Message.TYPE_IMAGE : Message.TYPE_FILE));
    }

    private int getMediaRuntime(final File file) {
        try {
            final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(file.toString());
            final String value =
                    mediaMetadataRetriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (Strings.isNullOrEmpty(value)) {
                return 0;
            }
            return Integer.parseInt(value);
        } catch (final Exception e) {
            return 0;
        }
    }

    private Dimensions getImageDimensions(File file) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        final int rotation = getRotation(file);
        final boolean rotated = rotation == 90 || rotation == 270;
        final int imageHeight = rotated ? options.outWidth : options.outHeight;
        final int imageWidth = rotated ? options.outHeight : options.outWidth;
        return new Dimensions(imageHeight, imageWidth);
    }

    private Dimensions getVideoDimensions(final File file) throws NotAVideoFile, IOException {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        try {
            metadataRetriever.setDataSource(file.getAbsolutePath());
        } catch (RuntimeException e) {
            throw new NotAVideoFile(e);
        }
        return getVideoDimensions(metadataRetriever);
    }

    private Dimensions getPdfDocumentDimensions(final File file) {
        final ParcelFileDescriptor fileDescriptor;
        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            if (fileDescriptor == null) {
                return new Dimensions(0, 0);
            }
        } catch (final FileNotFoundException e) {
            return new Dimensions(0, 0);
        }
        try {
            final PdfRenderer pdfRenderer = new PdfRenderer(fileDescriptor);
            final PdfRenderer.Page page = pdfRenderer.openPage(0);
            final int height = page.getHeight();
            final int width = page.getWidth();
            page.close();
            pdfRenderer.close();
            return scalePdfDimensions(new Dimensions(height, width));
        } catch (final IOException | SecurityException e) {
            Log.d(Config.LOGTAG, "unable to get dimensions for pdf document", e);
            return new Dimensions(0, 0);
        }
    }

    private Dimensions scalePdfDimensions(Dimensions in) {
        final DisplayMetrics displayMetrics =
                mXmppConnectionService.getResources().getDisplayMetrics();
        final int target = (int) (displayMetrics.density * 288);
        return scalePdfDimensions(in, target, true);
    }

    private static Dimensions scalePdfDimensions(
            final Dimensions in, final int target, final boolean fit) {
        final int w, h;
        if (fit == (in.width <= in.height)) {
            w = Math.max((int) (in.width / ((double) in.height / target)), 1);
            h = target;
        } else {
            w = target;
            h = Math.max((int) (in.height / ((double) in.width / target)), 1);
        }
        return new Dimensions(h, w);
    }

    public Bitmap getAvatar(final String avatar, final int size) {
        if (Strings.isNullOrEmpty(avatar)) {
            return null;
        }
        return cropCenterSquare(mXmppConnectionService, getAvatarUri(avatar), size);
    }

    public ListenableFuture<List<Void>> saveInternalToExternal(
            final Collection<Message.StorageLocation> storageLocations) {
        final var internalOnly =
                Collections2.filter(storageLocations, sl -> sl != null && !sl.sharedStorage());
        final var futures =
                Collections2.transform(
                        internalOnly, sl -> saveInternalToExternal(Objects.requireNonNull(sl)));
        return Futures.successfulAsList(futures);
    }

    public ListenableFuture<Void> saveInternalToExternal(
            final Message.StorageLocation storageLocation) {
        Preconditions.checkArgument(!storageLocation.sharedStorage());
        final var internal = storageLocation.file();
        final var external =
                getSharedStorageLocation(internal.getName(), MimeUtils.getMimeType(internal));
        if (external.exists() && internal.length() == external.length()) {
            Log.i(Config.LOGTAG, "file is already available on shared storage");
            updateMediaScanner(external);
            return Futures.immediateVoidFuture();
        }
        final var parent = external.getParentFile();
        if (parent != null && parent.mkdirs()) {
            Log.d(Config.LOGTAG, "created parent directory: " + parent.getAbsolutePath());
            mXmppConnectionService.restartFileObserver();
        }
        return Futures.submit(
                () -> {
                    try {
                        if (external.createNewFile()) {
                            Log.d(
                                    Config.LOGTAG,
                                    "created empty file " + external.getAbsolutePath());
                        }
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                    try (final var is = new FileInputStream(internal);
                            final var os = new FileOutputStream(external)) {
                        ByteStreams.copy(is, os);
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                    updateMediaScanner(external);
                },
                XmppConnectionService.FILE_ATTACHMENT_EXECUTOR);
    }

    public static class Cache {

        private static final DateTimeFormatter TIMESTAMP_FORMATTER =
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS").withZone(ZoneId.systemDefault());

        private static final Pattern RECORDING_FILENAME_PATTERN =
                Pattern.compile(
                        "^RECORDING_\\d{4}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])_([01]\\d|2[0-3])([0-5]\\d)([0-5]\\d)(\\d{3})\\.(mp4|m4a|oga)$");

        private static final Pattern IMAGE_FILENAME_PATTERN =
                Pattern.compile(
                        "^IMG_\\d{4}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])_([01]\\d|2[0-3])([0-5]\\d)([0-5]\\d)(\\d{3})\\.jpg$");

        private static final String DIRECTORY_CAMERA = "Camera";
        private static final String DIRECTORY_RECORDINGS = "Recordings";

        private final Context context;

        public Cache(final Context context) {
            this.context = context;
        }

        public File takePicture() {
            final String filename =
                    String.format("IMG_%s.%s", TIMESTAMP_FORMATTER.format(Instant.now()), "jpg");
            final var cameraDirectory = new File(context.getCacheDir(), DIRECTORY_CAMERA);
            final var file = new File(cameraDirectory, filename);
            if (cameraDirectory.mkdirs()) {
                Log.d(Config.LOGTAG, "create directory " + cameraDirectory.getAbsolutePath());
            }
            return file;
        }

        public File recording(final int outputFormat) {
            final String extension;
            if (outputFormat == MediaRecorder.OutputFormat.MPEG_4) {
                extension = "m4a";
            } else if (outputFormat == MediaRecorder.OutputFormat.OGG) {
                extension = "oga";
            } else {
                throw new IllegalStateException("Unrecognized output format");
            }
            final String filename =
                    String.format(
                            "RECORDING_%s.%s",
                            TIMESTAMP_FORMATTER.format(Instant.now()), extension);
            final var recordingsDirectory = new File(context.getCacheDir(), DIRECTORY_RECORDINGS);
            final var file = new File(recordingsDirectory, filename);
            if (recordingsDirectory.mkdirs()) {
                Log.d(Config.LOGTAG, "create directory " + recordingsDirectory.getAbsolutePath());
            }
            return file;
        }

        public boolean isCachedFile(final File file) {
            final var directory = file.getParentFile();
            if (directory == null) {
                return false;
            }
            final var parent = directory.getParentFile();
            if (parent == null) {
                return false;
            }
            if (Arrays.asList(DIRECTORY_CAMERA, DIRECTORY_RECORDINGS)
                    .contains(directory.getName())) {
                return parent.equals(context.getCacheDir());
            } else {
                return false;
            }
        }

        public static boolean isRecordingFilenamePattern(final String filename) {
            return RECORDING_FILENAME_PATTERN.matcher(filename).matches();
        }

        public static boolean isImageFilenamePattern(final String filename) {
            return IMAGE_FILENAME_PATTERN.matcher(filename).matches();
        }
    }

    private record Dimensions(int height, int width) {

        public int getMin() {
            return Math.min(width, height);
        }

        public boolean valid() {
            return width > 0 && height > 0;
        }
    }

    private static class NotAVideoFile extends Exception {
        public NotAVideoFile(Throwable t) {
            super(t);
        }

        public NotAVideoFile() {
            super();
        }
    }

    public static class ImageCompressionException extends IllegalStateException {

        ImageCompressionException(String message) {
            super(message);
        }
    }

    public static class FileCopyException extends IllegalStateException {
        private final int resId;

        private FileCopyException(@StringRes int resId) {
            this.resId = resId;
        }

        public @StringRes int getResId() {
            return resId;
        }
    }
}
