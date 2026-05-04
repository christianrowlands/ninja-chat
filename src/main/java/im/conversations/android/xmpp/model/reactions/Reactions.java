package im.conversations.android.xmpp.model.reactions;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import net.fellbaum.jemoji.EmojiManager;

@XmlElement
public class Reactions extends Extension {

    public Reactions() {
        super(Reactions.class);
    }

    public Collection<String> getReactions() {
        return ImmutableSet.copyOf(
                Collections2.filter(
                        Collections2.transform(
                                getExtensions(Reaction.class),
                                reaction -> reaction != null ? reaction.getContent() : null),
                        r -> {
                            if (Strings.isNullOrEmpty(r)) {
                                return false;
                            }
                            return EmojiManager.isEmoji(r);
                        }));
    }

    public String getId() {
        return Strings.emptyToNull(this.getAttribute("id"));
    }

    public void setId(String id) {
        this.setAttribute("id", id);
    }

    public static Reactions to(final String id) {
        final var reactions = new Reactions();
        reactions.setId(id);
        return reactions;
    }
}
