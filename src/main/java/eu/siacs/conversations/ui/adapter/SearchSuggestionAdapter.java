package eu.siacs.conversations.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ItemSearchSuggestionBinding;
import im.conversations.android.model.SearchSuggestion;
import java.util.Objects;
import java.util.function.Consumer;

public class SearchSuggestionAdapter
        extends ListAdapter<SearchSuggestion, SearchSuggestionAdapter.SearchSuggestionViewHolder> {
    private static final DiffUtil.ItemCallback<SearchSuggestion> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull SearchSuggestion oldItem, @NonNull SearchSuggestion newItem) {
                    if (oldItem instanceof SearchSuggestion.Text
                            && newItem instanceof SearchSuggestion.Text) {
                        return true;
                    } else {
                        return Objects.equals(oldItem, newItem);
                    }
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull SearchSuggestion oldItem, @NonNull SearchSuggestion newItem) {
                    return Objects.equals(oldItem, newItem);
                }
            };

    private Consumer<SearchSuggestion> onSearchSuggestionClicked;

    public SearchSuggestionAdapter() {
        super(DIFF);
    }

    public void setOnSearchSuggestionClicked(final Consumer<SearchSuggestion> consumer) {
        this.onSearchSuggestionClicked = consumer;
    }

    @NonNull
    @Override
    public SearchSuggestionAdapter.SearchSuggestionViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        ItemSearchSuggestionBinding binding =
                DataBindingUtil.inflate(
                        layoutInflater, R.layout.item_search_suggestion, parent, false);
        return new SearchSuggestionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull SearchSuggestionAdapter.SearchSuggestionViewHolder holder, int position) {
        final var searchSuggestion = getItem(position);
        holder.binding.wrapper.setOnClickListener(
                v -> onSearchSuggestionClicked.accept(searchSuggestion));
        if (searchSuggestion instanceof SearchSuggestion.Text(String text)) {
            holder.binding.searchSuggestion.setMaxLines(2);
            holder.binding.searchSuggestion.setText(
                    holder.binding
                            .searchSuggestion
                            .getResources()
                            .getString(R.string.search_for_x_in_chats, text));
            holder.binding.address.setVisibility(ViewGroup.GONE);
            holder.binding.icon.setImageResource(R.drawable.ic_manage_search_24dp);
        } else if (searchSuggestion instanceof SearchSuggestion.Contact contact) {
            holder.binding.searchSuggestion.setMaxLines(1);
            holder.binding.searchSuggestion.setText(contact.name());
            holder.binding.address.setText(contact.address().toString());
            holder.binding.address.setVisibility(View.VISIBLE);
            holder.binding.icon.setImageResource(R.drawable.ic_person_24dp);
        } else if (searchSuggestion instanceof SearchSuggestion.Bookmark bookmark) {
            holder.binding.searchSuggestion.setMaxLines(1);
            holder.binding.searchSuggestion.setText(bookmark.name());
            holder.binding.address.setText(bookmark.address().toString());
            holder.binding.address.setVisibility(View.VISIBLE);
            holder.binding.icon.setImageResource(R.drawable.ic_group_24dp);
        } else if (searchSuggestion
                instanceof SearchSuggestion.Uri(de.gultsch.common.MiniUri.Xmpp uri)) {
            holder.binding.searchSuggestion.setMaxLines(2);
            holder.binding.searchSuggestion.setText(uri.asJid());
            holder.binding.address.setVisibility(ViewGroup.GONE);
            holder.binding.icon.setImageResource(R.drawable.ic_link_24dp);
        } else if (searchSuggestion instanceof SearchSuggestion.Note) {
            holder.binding.searchSuggestion.setMaxLines(1);
            holder.binding.searchSuggestion.setText(R.string.note_to_self);
            holder.binding.address.setVisibility(ViewGroup.GONE);
            holder.binding.icon.setImageResource(R.drawable.ic_sticky_note_24dp);
        }
    }

    public static class SearchSuggestionViewHolder extends RecyclerView.ViewHolder {

        private final ItemSearchSuggestionBinding binding;

        public SearchSuggestionViewHolder(@NonNull ItemSearchSuggestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
