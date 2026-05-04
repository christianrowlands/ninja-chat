package im.conversations.android.xmpp.model.time;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@XmlElement(name = "utc")
public class UniversalTime extends Extension {

    public UniversalTime() {
        super(UniversalTime.class);
    }

    public UniversalTime(final Instant instant) {
        this();
        this.setContent(instant.truncatedTo(ChronoUnit.SECONDS).toString());
    }

    public Instant asInstant() {
        final var content = getContent();
        if (Strings.isNullOrEmpty(content)) {
            return null;
        }
        try {
            return Instant.parse(content);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }
}
