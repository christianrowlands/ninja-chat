package im.conversations.android.xmpp.model.bind2;

import com.google.common.collect.Collections2;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;

@XmlElement
public class Inline extends Extension {

    public Inline() {
        super(Inline.class);
    }

    public Collection<String> getFeatures() {
        return Collections2.filter(
                Collections2.transform(
                        this.getExtensions(Feature.class), f -> Objects.requireNonNull(f).getVar()),
                Objects::nonNull);
    }
}
