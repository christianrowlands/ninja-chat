package im.conversations.android.xmpp.model.hats;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.primitives.Doubles;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Hat extends Extension {

    public Hat() {
        super(Hat.class);
    }

    public String getTitle() {
        return this.getAttribute("title");
    }

    public String getUri() {
        return this.getAttribute("uri");
    }

    public Optional<Double> getHue() {
        final var hue = this.getAttribute("hue");
        if (Strings.isNullOrEmpty(hue)) {
            return Optional.absent();
        }
        return Optional.fromNullable(Doubles.tryParse(hue));
    }
}
