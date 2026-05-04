package im.conversations.android.xmpp.model.commands;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Objects;
import java.util.Set;

@XmlElement
public class Actions extends Extension {

    public Actions() {
        super(Actions.class);
    }

    public Command.Action getExecute() {
        final var execute = this.getAttribute("execute");
        // If the "execute" attribute is absent, it defaults to "next"
        if (Strings.isNullOrEmpty(execute)) {
            return Command.Action.NEXT;
        }
        try {
            return Command.Action.valueOf(
                    CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, execute));
        } catch (final IllegalArgumentException e) {
            return Command.Action.NEXT;
        }
    }

    public Set<Command.Action> getActions() {
        final var actions = this.getExtensions(Action.class);
        return ImmutableSet.copyOf(
                Collections2.transform(
                        actions,
                        a ->
                                switch (Objects.requireNonNull(a)) {
                                    case Action.Complete c -> Command.Action.COMPLETE;
                                    case Action.Next n -> Command.Action.NEXT;
                                    case Action.Prev p -> Command.Action.PREV;
                                }));
    }
}
