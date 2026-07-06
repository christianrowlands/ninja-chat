package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMediaBrowserBinding;
import eu.siacs.conversations.databinding.DialogFiltersBinding;
import eu.siacs.conversations.databinding.ItemFilterTypeBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.GridManager;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.xmpp.Jid;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class MediaBrowserActivity extends XmppActivity implements OnMediaLoaded {

    private static final AttachmentFilter IMAGE_FILTER =
            new AttachmentFilter(
                    R.string.filter_type_images,
                    R.drawable.ic_image_24dp,
                    a -> Strings.nullToEmpty(a.getMime()).startsWith("image/"));
    private static final AttachmentFilter VIDEO_FILTER =
            new AttachmentFilter(
                    R.string.filter_type_videos,
                    R.drawable.ic_movie_24dp,
                    a -> Strings.nullToEmpty(a.getMime()).startsWith("video/"));
    private static final AttachmentFilter AUDIO_FILTER =
            new AttachmentFilter(
                    R.string.filter_type_audio,
                    R.drawable.ic_headphones_48dp,
                    a -> Strings.nullToEmpty(a.getMime()).startsWith("audio/"));
    private static final AttachmentFilter DOCUMENT_FILTER =
            new AttachmentFilter(
                    R.string.filter_type_documents,
                    R.drawable.ic_description_24dp,
                    a -> MediaAdapter.ALL_DOCUMENT_MIMES.contains(a.getMime()));
    private static final AttachmentFilter OTHER_FILTER =
            new AttachmentFilter(
                    R.string.filter_type_other,
                    R.drawable.ic_category_24dp,
                    a ->
                            !IMAGE_FILTER.condition.apply(a)
                                    && !VIDEO_FILTER.condition.apply(a)
                                    && !AUDIO_FILTER.condition.apply(a)
                                    && !DOCUMENT_FILTER.condition.apply(a));

    private static final Map<FilterType, AttachmentFilter> FILTERS;

    static {
        FILTERS =
                Maps.immutableEnumMap(
                        new ImmutableMap.Builder<FilterType, AttachmentFilter>()
                                .put(FilterType.IMAGES, IMAGE_FILTER)
                                .put(FilterType.VIDEOS, VIDEO_FILTER)
                                .put(FilterType.AUDIO, AUDIO_FILTER)
                                .put(FilterType.DOCUMENTS, DOCUMENT_FILTER)
                                .put(FilterType.OTHER, OTHER_FILTER)
                                .buildOrThrow());
    }

    private static final String EXTRA_SELECTION = "selection";
    private static final String EXTRA_APPLIED_FILTER = "applied-filter";

    private final ActionMode.Callback actionModeCallBack =
            new ActionMode.Callback() {

                @Override
                public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                    mode.getMenuInflater().inflate(R.menu.media_browser_action_mode, menu);
                    actionMode = mode;
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                    mode.setTitle(String.valueOf(mMediaAdapter.countSelections()));
                    final var service = xmppConnectionService;
                    if (service == null) {
                        return true;
                    }
                    final var fileBackend = service.getFileBackend();
                    final var storageLocations =
                            fileBackend.inferStorageLocation(
                                    mMediaAdapter.getSelectedAttachments());
                    final var internal =
                            Collections2.filter(
                                    storageLocations, sl -> sl != null && !sl.sharedStorage());
                    final var saveMenuItem = menu.findItem(R.id.action_save);
                    saveMenuItem.setVisible(!internal.isEmpty());
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    if (item.getItemId() == R.id.action_share) {
                        shareSelectedAttachments();
                        return true;
                    }
                    if (item.getItemId() == R.id.action_delete) {
                        deleteSelectedAttachments();
                        return true;
                    }
                    if (item.getItemId() == R.id.action_save) {
                        saveSelectedAttachments();
                        return true;
                    }
                    if (item.getItemId() == R.id.action_select_all) {
                        selectAllAttachments();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    final var mediaAdapter = mMediaAdapter;
                    if (mediaAdapter != null) {
                        mediaAdapter.clearSelection();
                    }
                    actionMode = null;
                }
            };

    private MediaAdapter mMediaAdapter;
    private ActionMode actionMode;
    private EnumSet<FilterType> appliedFilter = EnumSet.allOf(FilterType.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivityMediaBrowserBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_media_browser);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        if (savedInstanceState != null) {
            final String[] selection = savedInstanceState.getStringArray(EXTRA_SELECTION);
            final String[] filters = savedInstanceState.getStringArray(EXTRA_APPLIED_FILTER);
            if (selection != null) {
                final var uuids = Lists.transform(Arrays.asList(selection), UUID::fromString);
                this.mMediaAdapter.setSelection(uuids);
                if (!uuids.isEmpty()) {
                    startSupportActionMode(this.actionModeCallBack);
                }
            }
            if (filters != null) {
                final Collection<FilterType> test =
                        Collections2.transform(
                                Arrays.asList(filters),
                                f -> Enums.stringConverter(FilterType.class).convert(f));
                this.appliedFilter = EnumSet.copyOf(test);
            }
        }
        this.mMediaAdapter.setOnAttachmentClicked(
                attachment -> {
                    if (actionMode == null) {
                        ViewUtil.view(this, attachment);
                    } else {
                        toggleSelection(attachment);
                    }
                });
        this.mMediaAdapter.setOnAttachmentLongClicked(
                attachment -> {
                    toggleSelection(attachment);
                    return true;
                });
        binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, binding.media, R.dimen.browser_media_size);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.activity_media_browser, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final var itemItem = item.getItemId();
        if (itemItem == R.id.action_filter) {
            showFilterSelection();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void showFilterSelection() {
        final var dialogBuilder = new MaterialAlertDialogBuilder(this);
        dialogBuilder.setTitle(R.string.filter_media);
        final DialogFiltersBinding dialogViewBinding =
                DataBindingUtil.inflate(getLayoutInflater(), R.layout.dialog_filters, null, false);
        final HashSet<FilterType> current = new HashSet<>(this.appliedFilter);
        final AtomicReference<Button> positiveButton = new AtomicReference<>();
        for (final var entry : FILTERS.entrySet()) {
            final var filterType = entry.getKey();
            final var attachmentFilter = entry.getValue();
            final ItemFilterTypeBinding filterItemBinding =
                    DataBindingUtil.inflate(
                            getLayoutInflater(), R.layout.item_filter_type, null, false);
            filterItemBinding.icon.setImageResource(attachmentFilter.icon());
            filterItemBinding.name.setText(attachmentFilter.name());
            dialogViewBinding.filterContainer.addView(filterItemBinding.getRoot());
            filterItemBinding.checkbox.setChecked(current.contains(filterType));
            filterItemBinding
                    .getRoot()
                    .setOnClickListener(v -> filterItemBinding.checkbox.toggle());
            filterItemBinding.checkbox.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        final var button = positiveButton.get();
                        if (isChecked) {
                            current.add(filterType);
                        } else {
                            current.remove(filterType);
                        }
                        button.setEnabled(!current.isEmpty());
                    });
        }
        dialogBuilder.setView(dialogViewBinding.getRoot());
        dialogBuilder.setPositiveButton(
                R.string.apply_filter,
                (dialog, which) -> {
                    this.appliedFilter = EnumSet.copyOf(current);
                    reloadMediaFiles();
                });
        dialogBuilder.setNegativeButton(R.string.cancel, null);
        final var dialog = dialogBuilder.create();
        dialog.setOnShowListener(
                d -> {
                    if (d instanceof AlertDialog ad) {
                        positiveButton.set(ad.getButton(AlertDialog.BUTTON_POSITIVE));
                    }
                });
        dialog.show();
    }

    private void toggleSelection(final Attachment attachment) {
        final var actionMode = this.actionMode;
        if (actionMode == null) {
            if (this.mMediaAdapter.toggleSelection(attachment)) {
                this.actionMode = startSupportActionMode(this.actionModeCallBack);
            }
        } else {
            if (this.mMediaAdapter.toggleSelection(attachment)) {
                actionMode.invalidate();
            } else {
                actionMode.finish();
            }
        }
    }

    private void saveSelectedAttachments() {
        final var fileBackend = xmppConnectionService.getFileBackend();
        final var storageLocations =
                fileBackend.inferStorageLocation(this.mMediaAdapter.getSelectedAttachments());
        if (storageLocations.isEmpty()) {
            return;
        }
        final var actionMode = this.actionMode;
        if (actionMode != null) {
            actionMode.finish();
            this.mMediaAdapter.clearSelection();
        }
        final var future = fileBackend.saveInternalToExternal(storageLocations);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(List<Void> result) {
                        Toast.makeText(
                                        getApplicationContext(),
                                        getResources()
                                                .getQuantityString(
                                                        R.plurals.attachments_saved,
                                                        result.size(),
                                                        result.size()),
                                        Toast.LENGTH_LONG)
                                .show();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(Config.LOGTAG, "could not save attachments", t);
                        Toast.makeText(
                                        getApplicationContext(),
                                        R.string.could_not_save_files,
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void deleteSelectedAttachments() {
        final var attachments = this.mMediaAdapter.getSelectedAttachments();
        final var files =
                Collections2.transform(
                        Collections2.filter(
                                attachments,
                                a -> "file".equals(Objects.requireNonNull(a).getUri().getScheme())),
                        a ->
                                new File(
                                        Objects.requireNonNull(
                                                Objects.requireNonNull(a).getUri().getPath())));
        if (files.isEmpty()) {
            return;
        }
        final var builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(
                getResources()
                        .getQuantityString(
                                R.plurals.delete_files_dialog, files.size(), files.size()));
        builder.setMessage(
                getResources().getQuantityString(R.plurals.delete_files_dialog_msg, files.size()));
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.confirm, (dialog, which) -> deleteMediaFiles(files));
        builder.create().show();
    }

    private void selectAllAttachments() {
        final var actionMode = this.actionMode;
        if (actionMode == null) {
            // if action mode does not exist we can get here
            return;
        }
        this.mMediaAdapter.selectAll();
        actionMode.invalidate();
    }

    private void deleteMediaFiles(final Collection<File> files) {
        final var actionMode = this.actionMode;
        if (actionMode != null) {
            actionMode.finish();
            this.mMediaAdapter.clearSelection();
        }
        final var future = xmppConnectionService.deleteMediaFiles(files);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        reloadMediaFiles();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(Config.LOGTAG, "could not delete files", t);
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void shareSelectedAttachments() {
        final var attachments = this.mMediaAdapter.getSelectedAttachments();
        if (attachments.isEmpty()) {
            return;
        }
        final var mimes =
                ImmutableSet.copyOf(
                        Lists.transform(
                                attachments,
                                a -> ViewUtil.nullToWildcard(Objects.requireNonNull(a).getMime())));
        final var uris =
                ImmutableList.copyOf(
                        Lists.transform(
                                attachments,
                                a ->
                                        FileBackend.getUriForUri(
                                                        this, Objects.requireNonNull(a).getUri())
                                                .buildUpon()
                                                .appendQueryParameter(
                                                        "uuid", a.getUuid().toString())
                                                .build()));
        final var intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        if (mimes.size() == 1) {
            intent.setType(Iterables.getOnlyElement(mimes));
        } else {
            intent.setType(ViewUtil.WILDCARD);
        }
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, null));
    }

    public void onSaveInstanceState(@NonNull final Bundle bundle) {
        bundle.putStringArray(
                EXTRA_SELECTION,
                Lists.transform(
                                this.mMediaAdapter.getSelectedAttachments(),
                                a -> Objects.requireNonNull(a).getUuid().toString())
                        .toArray(new String[0]));
        bundle.putStringArray(
                EXTRA_APPLIED_FILTER,
                Collections2.transform(
                                this.appliedFilter, f -> Objects.requireNonNull(f).toString())
                        .toArray(new String[0]));
        super.onSaveInstanceState(bundle);
    }

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        this.reloadMediaFiles();
    }

    private void reloadMediaFiles() {
        final var service = xmppConnectionService;
        if (service == null) {
            return;
        }
        final var gallery = gallery(getIntent());
        service.getAttachments(gallery.account, gallery.contact, 0, this);
    }

    private static Gallery gallery(final Intent intent) {
        String account = intent == null ? null : intent.getStringExtra("account");
        String jid = intent == null ? null : intent.getStringExtra("jid");
        if (account != null && jid != null) {
            return new Gallery(account, Jid.of(jid));
        } else {
            throw new IllegalStateException("Activity started with incomplete extras");
        }
    }

    public static void launch(Context context, Contact contact) {
        launch(context, contact.getAccount(), contact.getAddress().asBareJid().toString());
    }

    public static void launch(Context context, Conversation conversation) {
        launch(
                context,
                conversation.getAccount(),
                conversation.getAddress().asBareJid().toString());
    }

    private static void launch(Context context, Account account, String jid) {
        final Intent intent = new Intent(context, MediaBrowserActivity.class);
        intent.putExtra("account", account.getUuid());
        intent.putExtra("jid", jid);
        context.startActivity(intent);
    }

    @Override
    public void onMediaLoaded(final List<Attachment> attachments) {
        runOnUiThread(
                () -> {
                    final var actionMode = this.actionMode;
                    setFilteredAttachments(attachments);
                    if (actionMode != null) {
                        actionMode.invalidate();
                    }
                });
    }

    private void setFilteredAttachments(final List<Attachment> attachments) {
        final var applied = this.appliedFilter;
        if (EnumSet.complementOf(applied).isEmpty()) {
            mMediaAdapter.submitList(attachments);
        } else {
            final var filters = Maps.filterKeys(FILTERS, applied::contains).values();
            final var filteredAttachments =
                    ImmutableList.copyOf(
                            Collections2.filter(
                                    attachments,
                                    a -> {
                                        for (final var filter : filters) {
                                            if (filter.condition.apply(a)) {
                                                return true;
                                            }
                                        }
                                        return false;
                                    }));
            Log.d(
                    Config.LOGTAG,
                    "applying filters "
                            + applied
                            + " "
                            + attachments.size()
                            + "->"
                            + filteredAttachments.size());
            mMediaAdapter.submitList(filteredAttachments);
        }
    }

    private record Gallery(String account, Jid contact) {}

    private record AttachmentFilter(
            @StringRes int name, @DrawableRes int icon, Function<Attachment, Boolean> condition) {}

    public enum FilterType {
        IMAGES,
        VIDEOS,
        AUDIO,
        DOCUMENTS,
        OTHER
    }
}
