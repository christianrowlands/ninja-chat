package im.conversations.android.xmpp.model.time;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.time.ZonedDateTime;

@XmlElement
public class Time extends Extension {

    public Time() {
        super(Time.class);
    }

    public Time(final ZonedDateTime zonedDateTime) {
        this();
        this.addExtension(new UniversalTime(zonedDateTime.toInstant()));
        this.addExtension(new TimeZoneOffset(zonedDateTime.getOffset()));
    }

    public ZonedDateTime asZonedDateTime() {
        final var universalTime = this.getOnlyExtension(UniversalTime.class);
        final var timeZoneOffset = this.getOnlyExtension(TimeZoneOffset.class);
        if (universalTime == null || timeZoneOffset == null) {
            return null;
        }
        final var instant = universalTime.asInstant();
        final var zoneOffset = timeZoneOffset.asZoneOffset();
        if (instant == null || zoneOffset == null) {
            return null;
        }
        return instant.atZone(zoneOffset);
    }
}
