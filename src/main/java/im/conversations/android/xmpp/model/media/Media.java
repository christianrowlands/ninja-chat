package im.conversations.android.xmpp.model.media;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import de.gultsch.common.MiniUri;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;

@XmlElement
public class Media extends Extension {

    public Media() {
        super(Media.class);
    }

    public Collection<MiniUri> getUris() {
        final var uris =
                Collections2.transform(
                        this.getExtensions(Uri.class),
                        uri -> {
                            final var content = uri == null ? null : uri.getContent();
                            if (Strings.isNullOrEmpty(content)) {
                                return null;
                            }
                            try {
                                return MiniUri.tryInternalParse(content);
                            } catch (final IllegalArgumentException e) {
                                return null;
                            }
                        });
        return Collections2.filter(uris, Objects::nonNull);
    }
}
