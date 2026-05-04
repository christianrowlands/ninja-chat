package eu.siacs.conversations.entities;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.model.DynamicTag;
import java.util.List;
import java.util.Locale;

public interface ListItem extends Comparable<ListItem>, AvatarService.Avatar {
    String getDisplayName();

    Jid getAddress();

    List<DynamicTag> getTags();

    default boolean match(final String needle) {
        if (Strings.isNullOrEmpty(needle)) {
            return true;
        }
        final var parts =
                Splitter.on(CharMatcher.whitespace())
                        .omitEmptyStrings()
                        .trimResults()
                        .splitToList(needle.toLowerCase(Locale.ROOT));
        if (parts.size() == 1) {
            return matchInItem(Iterables.getOnlyElement(parts));
        } else {
            for (final var part : parts) {
                if (!matchInItem(part)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    default int compareTo(final ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(another.getDisplayName());
    }

    private boolean matchInItem(final String needle) {
        return getAddress().toString().contains(needle)
                || getDisplayName().toLowerCase(Locale.US).contains(needle)
                || matchInTag(needle);
    }

    private boolean matchInTag(final String needle) {
        for (final DynamicTag tag : this.getTags()) {
            if (tag instanceof DynamicTag.RosterGroup(String name)
                    && Strings.nullToEmpty(name)
                            .toLowerCase(Locale.getDefault())
                            .contains(needle)) {
                return true;
            }
            // TODO match for hat and availability
        }
        return false;
    }
}
