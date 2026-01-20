package im.conversations.android.xmpp.model.up;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.time.Instant;
import okhttp3.HttpUrl;

@XmlElement
public class Registered extends Extension {

    public Registered() {
        super(Registered.class);
    }

    public HttpUrl getEndpoint() {
        final var endpoint = this.getAttribute("endpoint");
        if (Strings.isNullOrEmpty(endpoint)) {
            return null;
        }
        return HttpUrl.get(endpoint);
    }

    public Instant getExpiration() {
        return this.getAttributeAsInstant("expiration");
    }
}
