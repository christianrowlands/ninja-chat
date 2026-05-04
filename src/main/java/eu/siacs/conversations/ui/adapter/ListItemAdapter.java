package eu.siacs.conversations.ui.adapter;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.DataBindingUtil;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ItemContactBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.BlocklistActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.model.DynamicTag;
import java.util.List;
import java.util.function.Consumer;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

    protected XmppActivity activity;
    private boolean showDynamicTags = false;
    private final Consumer<DynamicTag> mOnTagClickedListener;
    private final boolean isBlockNoteworthy;

    public ListItemAdapter(final XmppActivity activity, final List<ListItem> objects) {
        this(activity, objects, null);
    }

    public ListItemAdapter(
            final XmppActivity activity,
            final List<ListItem> objects,
            final Consumer<DynamicTag> onTagClickedListener) {
        super(activity, 0, objects);
        this.activity = activity;
        this.mOnTagClickedListener = onTagClickedListener;
        this.isBlockNoteworthy = !(activity instanceof BlocklistActivity);
    }

    public void refreshSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        this.showDynamicTags = preferences.getBoolean(AppSettings.SHOW_DYNAMIC_TAGS, false);
    }

    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        LayoutInflater inflater = activity.getLayoutInflater();
        final ListItem item = getItem(position);
        ViewHolder viewHolder;
        if (view == null) {
            final ItemContactBinding binding =
                    DataBindingUtil.inflate(inflater, R.layout.item_contact, parent, false);
            viewHolder = ViewHolder.get(binding);
            view = binding.getRoot();
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        if (view.isActivated()) {
            Log.d(Config.LOGTAG, "item " + item.getDisplayName() + " is activated");
        }
        // view.setBackground(StyledAttributes.getDrawable(view.getContext(),R.attr.list_item_background));
        final var tags = item.getTags();
        if ((isBlockNoteworthy && Contact.isNoteworthy(tags)) || this.showDynamicTags) {
            UserAdapter.setHats(viewHolder.tags, tags, mOnTagClickedListener);
        } else {
            viewHolder.tags.setVisibility(View.GONE);
        }
        final Jid jid = item.getAddress();
        if (jid != null) {
            viewHolder.jid.setVisibility(View.VISIBLE);
            viewHolder.jid.setText(IrregularUnicodeDetector.style(activity, jid));
        } else {
            viewHolder.jid.setVisibility(View.GONE);
        }
        viewHolder.name.setText(item.getDisplayName());
        AvatarWorkerTask.loadAvatar(item, viewHolder.avatar, R.dimen.avatar);
        return view;
    }

    private static class ViewHolder {
        private TextView name;
        private TextView jid;
        private ImageView avatar;
        private ConstraintLayout tags;
        private Flow flowWidget;

        private ViewHolder() {}

        public static ViewHolder get(final ItemContactBinding binding) {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.name = binding.contactDisplayName;
            viewHolder.jid = binding.contactJid;
            viewHolder.avatar = binding.contactPhoto;
            viewHolder.tags = binding.tags;
            viewHolder.flowWidget = binding.flowWidget;
            binding.getRoot().setTag(viewHolder);
            return viewHolder;
        }
    }
}
