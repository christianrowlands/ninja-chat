package eu.siacs.conversations.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ConversationsFileObserver {
    private static final Executor EVENT_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int MASK = FileObserver.DELETE | FileObserver.MOVED_FROM;

    private static final List<String> STORAGE_TYPES;

    static {
        final ImmutableList.Builder<String> builder =
                new ImmutableList.Builder<String>()
                        .add(
                                Environment.DIRECTORY_DOWNLOADS,
                                Environment.DIRECTORY_PICTURES,
                                Environment.DIRECTORY_MOVIES,
                                Environment.DIRECTORY_DOCUMENTS,
                                Environment.DIRECTORY_DCIM);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.add(Environment.DIRECTORY_RECORDINGS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.add(Environment.DIRECTORY_AUDIOBOOKS);
        }
        STORAGE_TYPES = builder.build();
    }

    private final Context context;
    private final Consumer<File> onFileDeleted;
    private final ArrayList<FileObserver> fileObservers = new ArrayList<>();

    public ConversationsFileObserver(final Context context, final Consumer<File> onFileDeleted) {
        this.context = context;
        this.onFileDeleted = onFileDeleted;
    }

    public void restartWatching() {
        synchronized (this.fileObservers) {
            for (final var observer : this.fileObservers) {
                observer.stopWatching();
            }
            this.fileObservers.clear();
            for (final var observer : getFileObservers()) {
                this.fileObservers.add(observer);
                observer.startWatching();
            }
        }
    }

    public void stopWatching() {
        synchronized (this.fileObservers) {
            for (final var observer : this.fileObservers) {
                observer.stopWatching();
            }
        }
    }

    private File getInternalStorageLocation() {
        return new File(context.getFilesDir(), "attachments");
    }

    private Collection<FileObserver> getFileObservers() {
        final var locations =
                new ImmutableList.Builder<File>()
                        .add(getInternalStorageLocation())
                        .addAll(getSharedStorageLocations())
                        .build();
        final var existing =
                Collections2.filter(
                        locations, location -> location.exists() && location.isDirectory());
        return Collections2.transform(
                existing, location -> new SingleDirectoryObserver(location, onFileDeleted));
    }

    private List<File> getSharedStorageLocations() {
        return Lists.transform(
                STORAGE_TYPES,
                type -> {
                    final var parent = Environment.getExternalStoragePublicDirectory(type);
                    return new File(parent, context.getString(R.string.app_name));
                });
    }

    private static final class SingleDirectoryObserver extends FileObserver {

        private final File directory;
        private final Consumer<File> onFileDeleted;

        public SingleDirectoryObserver(
                @NonNull File directory, final Consumer<File> onFileDeleted) {
            super(directory.getAbsolutePath(), MASK);
            this.directory = directory;
            this.onFileDeleted = onFileDeleted;
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            if (Strings.isNullOrEmpty(path)) {
                return;
            }
            onFileDeleted.accept(new File(directory, path));
        }

        @Override
        public void startWatching() {
            super.startWatching();
            if (directory.exists() && directory.isDirectory()) {
                Log.d(Config.LOGTAG, "started to watch " + directory.getAbsolutePath());
            }
        }
    }
}
