package im.conversations.android.model;

import de.gultsch.common.MiniUri;
import eu.siacs.conversations.xmpp.Jid;

public sealed interface SearchSuggestion {

    record Text(String text) implements SearchSuggestion {}

    record Note() implements SearchSuggestion {}

    sealed interface Sortable extends SearchSuggestion {
        Jid address();
    }

    record Contact(String uuid, Jid address, String name) implements Sortable {}

    record Bookmark(String uuid, Jid address, String name) implements Sortable {}

    record Uri(MiniUri.Xmpp uri) implements SearchSuggestion {}
}
