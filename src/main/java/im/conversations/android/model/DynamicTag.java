package im.conversations.android.model;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.Role;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.Collection;
import java.util.List;

public sealed interface DynamicTag {

    record Hat(String uri, String title, Double hue) implements DynamicTag {}

    record Attributes(Affiliation affiliation, Role role) implements DynamicTag {}

    record Status(Presence.Availability availability) implements DynamicTag {}

    record RosterGroup(String name) implements DynamicTag {
        public static List<DynamicTag> of(Collection<String> groups) {
            return ImmutableList.copyOf(Collections2.transform(groups, RosterGroup::new));
        }
    }

    record Blocked() implements DynamicTag {}
}
