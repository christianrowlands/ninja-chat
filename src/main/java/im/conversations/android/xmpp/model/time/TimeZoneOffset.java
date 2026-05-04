package im.conversations.android.xmpp.model.time;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.time.ZoneOffset;
import java.util.Locale;

@XmlElement(name = "tzo")
public class TimeZoneOffset extends Extension {

    public TimeZoneOffset() {
        super(TimeZoneOffset.class);
    }

    public TimeZoneOffset(final ZoneOffset offset) {
        this();
        final int hours = offset.getTotalSeconds() / 3600;
        final int minutes = (Math.abs(offset.getTotalSeconds()) % 3600) / 60;
        final String tzoAsString;
        if (hours < 0) {
            tzoAsString = String.format(Locale.US, "%03d:%02d", hours, minutes);
        } else {
            tzoAsString = String.format(Locale.US, "+%02d:%02d", hours, minutes);
        }
        this.setContent(tzoAsString);
    }

    public ZoneOffset asZoneOffset() {
        final var content = getContent();
        if (Strings.isNullOrEmpty(content)) {
            return null;
        }
        if (content.equals("Z")) {
            return ZoneOffset.UTC;
        }
        final var parts = Splitter.on(':').splitToList(content);
        if (parts.size() != 2) {
            return null;
        }
        try {
            return ZoneOffset.ofHoursMinutes(
                    Integer.parseInt(parts.get(0)), Integer.parseInt(parts.get(1)));
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
