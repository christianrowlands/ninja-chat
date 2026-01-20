package im.conversations.android.xmpp.model.bind2;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@XmlElement
public class Bind extends Extension {

    public static final Collection<String> QUICKSTART_FEATURES =
            Arrays.asList(Namespace.CARBONS, Namespace.STREAM_MANAGEMENT);

    public Bind() {
        super(Bind.class);
    }

    public Inline getInline() {
        return this.getExtension(Inline.class);
    }

    public Collection<String> getInlineFeatures() {
        final var inline = getInline();
        return inline == null ? Collections.emptyList() : inline.getFeatures();
    }

    public void setTag(final String tag) {
        this.addExtension(new Tag(tag));
    }
}
