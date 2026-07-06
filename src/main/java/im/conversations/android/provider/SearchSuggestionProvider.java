package im.conversations.android.provider;

import com.google.common.collect.ImmutableList;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.manager.BookmarkManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import im.conversations.android.model.SearchSuggestion;
import java.util.List;

public class SearchSuggestionProvider {

    private final List<Account> accounts;

    public SearchSuggestionProvider(final List<Account> accounts) {
        this.accounts = accounts;
    }

    public List<SearchSuggestion.Sortable> suggest(final String term) {
        final var builder = new ImmutableList.Builder<SearchSuggestion.Sortable>();
        for (final var account : this.accounts) {
            if (account.isEnabled()) {
                final var connection = account.getXmppConnection();
                final var rosterManager = connection.getManager(RosterManager.class);
                for (final var contact : rosterManager.getContacts()) {
                    if (contact.showInContactList() && contact.match(term)) {
                        builder.add(
                                new SearchSuggestion.Contact(
                                        account.getUuid(),
                                        contact.getAddress(),
                                        contact.getDisplayName()));
                    }
                }
                final var bookmarkManager = connection.getManager(BookmarkManager.class);
                for (final var bookmark : bookmarkManager.getBookmarks()) {
                    if (bookmark.match(term)) {
                        builder.add(
                                new SearchSuggestion.Bookmark(
                                        account.getUuid(),
                                        bookmark.getAddress(),
                                        bookmark.getDisplayName()));
                    }
                }
            }
        }
        return builder.build();
    }
}
