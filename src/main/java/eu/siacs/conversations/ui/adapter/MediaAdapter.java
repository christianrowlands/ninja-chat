package eu.siacs.conversations.ui.adapter;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ItemMediaBinding;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.worker.ExportBackupWorker;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

public class MediaAdapter extends ListAdapter<Attachment, MediaAdapter.MediaViewHolder> {

    private static final DiffUtil.ItemCallback<Attachment> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull Attachment oldItem, @NonNull Attachment newItem) {
                    return Objects.equals(oldItem.getUuid(), newItem.getUuid());
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull Attachment oldItem, @NonNull Attachment newItem) {
                    return Objects.equals(oldItem, newItem);
                }
            };

    public static final List<String> DOCUMENT_MIMES =
            new ImmutableList.Builder<String>()
                    .add("application/pdf")
                    .add("text/x-tex")
                    .add("text/plain")
                    .addAll(MimeUtils.WORD_DOCUMENT_MIMES)
                    .build();

    public static final List<String> EBOOK_MIMES =
            Arrays.asList("application/epub+zip", "application/vnd.amazon.mobi8-ebook");

    public static final List<String> SPREAD_SHEET_MIMES =
            Arrays.asList(
                    "text/comma-separated-values",
                    "application/vnd.ms-excel",
                    "application/vnd.stardivision.calc",
                    "application/vnd.oasis.opendocument.spreadsheet",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public static final List<String> SLIDE_SHOW_MIMES =
            Arrays.asList(
                    "application/vnd.ms-powerpoint",
                    "application/vnd.stardivision.impress",
                    "application/vnd.oasis.opendocument.presentation",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.openxmlformats-officedocument.presentationml.slideshow");

    public static final Collection<String> ALL_DOCUMENT_MIMES =
            new ImmutableSet.Builder<String>()
                    .addAll(DOCUMENT_MIMES)
                    .addAll(EBOOK_MIMES)
                    .addAll(SPREAD_SHEET_MIMES)
                    .addAll(SLIDE_SHOW_MIMES)
                    .build();

    public static final Collection<String> SHEET_MUSIC =
            Arrays.asList(
                    "application/vnd.recordare.musicxml", "application/vnd.recordare.musicxml+xml");

    private static final List<String> ARCHIVE_MIMES =
            Arrays.asList(
                    "application/x-7z-compressed",
                    "application/zip",
                    "application/rar",
                    "application/x-gtar",
                    "application/x-tar");
    public static final List<String> CODE_MIMES = Arrays.asList("text/html", "text/xml");

    private final Set<UUID> selectedAttachments = new HashSet<>();

    private final XmppActivity activity;

    private Consumer<Attachment> onAttachmentClicked = null;
    private Function<Attachment, Boolean> onAttachmentLongClicked = attachment -> false;

    private int mediaSize = 0;

    public MediaAdapter(final XmppActivity activity, final @DimenRes int mediaSize) {
        super(DIFF);
        this.activity = activity;
        this.mediaSize = Math.round(activity.getResources().getDimension(mediaSize));
        this.onAttachmentClicked = attachment -> ViewUtil.view(activity, attachment);
    }

    public void setOnAttachmentClicked(final Consumer<Attachment> callback) {
        Preconditions.checkNotNull(callback);
        this.onAttachmentClicked = callback;
    }

    public void setOnAttachmentLongClicked(final Function<Attachment, Boolean> callback) {
        Preconditions.checkNotNull(callback);
        this.onAttachmentLongClicked = callback;
    }

    public static void setMediaSize(final RecyclerView recyclerView, final int mediaSize) {
        if (recyclerView.getAdapter() instanceof MediaAdapter mediaAdapter) {
            mediaAdapter.setMediaSize(mediaSize);
        }
    }

    public static @DrawableRes int getImageDrawable(final Attachment attachment) {
        if (attachment.getType() == Attachment.Type.LOCATION) {
            return R.drawable.ic_location_pin_48dp;
        } else if (attachment.getType() == Attachment.Type.RECORDING) {
            return R.drawable.ic_mic_48dp;
        } else {
            return getImageDrawable(attachment.getMime());
        }
    }

    private static @DrawableRes int getImageDrawable(final String mime) {
        if (Strings.isNullOrEmpty(mime)) {
            return R.drawable.ic_help_center_48dp;
        } else if (mime.equals("audio/x-m4b")) {
            return R.drawable.ic_play_lesson_48dp;
        } else if (mime.startsWith("audio/")) {
            return R.drawable.ic_headphones_48dp;
        } else if (mime.equals("text/calendar") || (mime.equals("text/x-vcalendar"))) {
            return R.drawable.ic_event_48dp;
        } else if (mime.equals("text/x-vcard")) {
            return R.drawable.ic_person_48dp;
        } else if (mime.equals("application/vnd.android.package-archive")) {
            return R.drawable.ic_adb_48dp;
        } else if (mime.equals("application/vnd.apple.pkpass")) {
            return R.drawable.ic_mobile_ticket_48dp;
        } else if (ARCHIVE_MIMES.contains(mime)) {
            return R.drawable.ic_archive_48dp;
        } else if (EBOOK_MIMES.contains(mime)) {
            return R.drawable.ic_book_48dp;
        } else if (mime.equals(ExportBackupWorker.MIME_TYPE)) {
            return R.drawable.ic_backup_48dp;
        } else if (DOCUMENT_MIMES.contains(mime)) {
            return R.drawable.ic_description_48dp;
        } else if (SPREAD_SHEET_MIMES.contains(mime)) {
            return R.drawable.ic_table_48dp;
        } else if (SLIDE_SHOW_MIMES.contains(mime)) {
            return R.drawable.ic_slideshow_48dp;
        } else if (mime.equals("application/gpx+xml")) {
            return R.drawable.ic_tour_48dp;
        } else if (mime.startsWith("image/")) {
            return R.drawable.ic_image_48dp;
        } else if (mime.startsWith("video/")) {
            return R.drawable.ic_movie_48dp;
        } else if (CODE_MIMES.contains(mime)) {
            return R.drawable.ic_code_48dp;
        } else if (mime.equals("message/rfc822")) {
            return R.drawable.ic_email_48dp;
        } else if (Arrays.asList("application/x-pcapng", "application/vnd.tcpdump.pcap")
                .contains(mime)) {
            return R.drawable.ic_lan_24dp;
        } else if (SHEET_MUSIC.contains(mime)) {
            return R.drawable.ic_audio_file_48dp;

        } else {
            return R.drawable.ic_help_center_48dp;
        }
    }

    static void renderPreview(final Attachment attachment, final ImageView imageView) {
        ImageViewCompat.setImageTintList(
                imageView,
                ColorStateList.valueOf(
                        MaterialColors.getColor(
                                imageView, com.google.android.material.R.attr.colorOnSurface)));
        imageView.setImageResource(getImageDrawable(attachment));
        imageView.setBackgroundColor(
                MaterialColors.getColor(
                        imageView, com.google.android.material.R.attr.colorSurfaceContainer));
    }

    private static boolean cancelPotentialWork(Attachment attachment, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Attachment oldAttachment = bitmapWorkerTask.attachment;
            if (oldAttachment == null || !oldAttachment.equals(attachment)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable asyncDrawable) {
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ItemMediaBinding binding =
                DataBindingUtil.inflate(layoutInflater, R.layout.item_media, parent, false);
        return new MediaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        final var attachment = getItem(position);
        if (attachment.renderThumbnail()) {
            loadPreview(attachment, holder.binding.media);
        } else {
            cancelPotentialWork(attachment, holder.binding.media);
            renderPreview(attachment, holder.binding.media);
        }
        holder.binding.getRoot().setOnClickListener(v -> onAttachmentClicked.accept(attachment));
        holder.binding
                .getRoot()
                .setOnLongClickListener(
                        v -> {
                            final var wrapper = v.findViewById(R.id.wrapper);
                            if (wrapper != null
                                    && wrapper.getBackground() instanceof Drawable drawable) {
                                drawable.jumpToCurrentState();
                            }
                            return onAttachmentLongClicked.apply(attachment);
                        });
        if (selectedAttachments.contains(attachment.getUuid())) {
            holder.binding.selectionIndicator.setVisibility(ImageView.VISIBLE);
            holder.binding
                    .getRoot()
                    .setBackgroundColor(
                            MaterialColors.getColor(
                                    holder.binding.getRoot(),
                                    com.google.android.material.R.attr
                                            .colorSurfaceContainerHighest));
        } else {
            holder.binding.selectionIndicator.setVisibility(ImageView.INVISIBLE);
            holder.binding.getRoot().setBackground(null);
        }
    }

    public boolean toggleSelection(final Attachment attachment) {
        final var attachments = getCurrentList();
        final var position = attachments.indexOf(attachment);
        final var uuid = attachment.getUuid();
        final boolean hasSelections;
        if (this.selectedAttachments.remove(uuid)) {
            hasSelections = !this.selectedAttachments.isEmpty();
        } else {
            this.selectedAttachments.add(uuid);
            hasSelections = true;
        }
        if (position >= 0) {
            notifyItemChanged(position);
        }
        return hasSelections;
    }

    public void clearSelection() {
        synchronized (this.selectedAttachments) {
            final var attachments = getCurrentList();
            for (int i = 0; i < attachments.size(); ++i) {
                final var attachment = attachments.get(i);
                if (this.selectedAttachments.remove(attachment.getUuid())) {
                    notifyItemChanged(i);
                }
            }
        }
    }

    public void selectAll() {
        synchronized (this.selectedAttachments) {
            final var attachments = getCurrentList();
            for (int i = 0; i < attachments.size(); ++i) {
                final var attachment = attachments.get(i);
                if (this.selectedAttachments.contains(attachment.getUuid())) {
                    continue;
                }
                if (this.selectedAttachments.add(attachment.getUuid())) {
                    notifyItemChanged(i);
                }
            }
        }
    }

    private void setMediaSize(final int mediaSize) {
        this.mediaSize = mediaSize;
    }

    private void loadPreview(Attachment attachment, ImageView imageView) {
        if (cancelPotentialWork(attachment, imageView)) {
            final Bitmap bm =
                    activity.xmppConnectionService
                            .getFileBackend()
                            .getPreviewForUri(attachment, mediaSize, true);
            if (bm != null) {
                cancelPotentialWork(attachment, imageView);
                setImageBitmap(imageView, bm);
            } else {
                // TODO consider if this is still a good, general purpose loading color
                imageView.setBackgroundColor(0xff333333);
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(mediaSize, imageView);
                final AsyncDrawable asyncDrawable =
                        new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(attachment);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    public static void setImageBitmap(final ImageView imageView, final Bitmap bitmap) {
        imageView.setImageTintList(null);
        imageView.setImageBitmap(bitmap);
        imageView.invalidate();
        imageView.setBackgroundColor(Color.TRANSPARENT);
    }

    public int countSelections() {
        return this.selectedAttachments.size();
    }

    public List<Attachment> getSelectedAttachments() {
        final var attachments = getCurrentList();
        return ImmutableList.copyOf(
                Collections2.filter(
                        attachments,
                        a ->
                                this.selectedAttachments.contains(
                                        Objects.requireNonNull(a).getUuid())));
    }

    public void setSelection(final Collection<UUID> selection) {
        this.selectedAttachments.clear();
        this.selectedAttachments.addAll(selection);
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    public static class MediaViewHolder extends RecyclerView.ViewHolder {

        private final ItemMediaBinding binding;

        MediaViewHolder(ItemMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class BitmapWorkerTask extends AsyncTask<Attachment, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Attachment attachment = null;
        private final int mediaSize;

        BitmapWorkerTask(int mediaSize, ImageView imageView) {
            this.mediaSize = mediaSize;
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(final Attachment... params) {
            this.attachment = params[0];
            final XmppActivity activity = XmppActivity.find(imageViewReference);
            if (activity == null) {
                return null;
            }
            return activity.xmppConnectionService
                    .getFileBackend()
                    .getPreviewForUri(this.attachment, mediaSize, false);
        }

        @Override
        protected void onPostExecute(final Bitmap bitmap) {
            if (bitmap == null || isCancelled()) {
                return;
            }
            final var imageView = imageViewReference.get();
            if (imageView == null) {
                return;
            }
            setImageBitmap(imageView, bitmap);
        }
    }
}
