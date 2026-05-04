package im.conversations.android.xmpp;

import eu.siacs.conversations.xmpp.manager.EntityTimeManager;
import im.conversations.android.xml.XmlElementReader;
import im.conversations.android.xmpp.model.time.Time;
import java.io.IOException;
import java.time.ZonedDateTime;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class EntityTimeTest {

    @Test
    public void timeToXmlTest() throws IOException {
        final var zonedDateTime = ZonedDateTime.parse("2006-12-19T11:58:35-06:00");
        final var time = new Time(zonedDateTime);
        final var xml = StreamElementWriter.asString(time);
        Assert.assertEquals(
"""
<time xmlns="urn:xmpp:time"><utc>2006-12-19T17:58:35Z</utc><tzo>-06:00</tzo></time>\
""",
                xml);
    }

    @Test
    public void dayTimeTest() {
        Assert.assertFalse(
                EntityTimeManager.isNightTime(ZonedDateTime.parse("2006-12-19T11:58:35-06:00")));
    }

    @Test
    public void nightTimeTest() {
        Assert.assertTrue(
                EntityTimeManager.isNightTime(ZonedDateTime.parse("2006-12-19T05:23:42-06:00")));
    }

    @Test
    public void xmlToTimeTest() throws IOException {
        final var xml =
"""
<time xmlns='urn:xmpp:time'>
    <tzo>-06:00</tzo>
    <utc>2006-12-19T17:58:35Z</utc>
  </time>\
""";
        final var time = XmlElementReader.read(xml, Time.class);
        Assert.assertEquals(
                ZonedDateTime.parse("2006-12-19T11:58:35-06:00"), time.asZonedDateTime());
    }

    @Test
    public void shenzhenRead() throws IOException {
        final var xml =
"""
<time xmlns="urn:xmpp:time"><utc>2026-03-14T04:28:20Z</utc><tzo>+08:00</tzo></time>\
""";
        final var time = XmlElementReader.read(xml, Time.class);
        final var zonedDateTime = time.asZonedDateTime();
        Assert.assertEquals(ZonedDateTime.parse("2026-03-14T12:28:20+08:00"), zonedDateTime);
        final var serialized = StreamElementWriter.asString(new Time(zonedDateTime));
        Assert.assertEquals(xml, serialized);
    }

    @Test
    public void zuluRead() throws IOException {
        final var xml =
                """
                <time xmlns="urn:xmpp:time"><utc>2026-01-01T00:00:00Z</utc><tzo>Z</tzo></time>\
                """;
        final var time = XmlElementReader.read(xml, Time.class);
        final var zonedDateTime = time.asZonedDateTime();
        Assert.assertEquals(ZonedDateTime.parse("2026-01-01T00:00:00+00:00"), zonedDateTime);
        final var serialized = StreamElementWriter.asString(new Time(zonedDateTime));
        final var expected =
                """
                <time xmlns="urn:xmpp:time"><utc>2026-01-01T00:00:00Z</utc><tzo>+00:00</tzo></time>\
                """;
        Assert.assertEquals(expected, serialized);
    }

    @Test
    public void shenzhenMissingPlusRead() throws IOException {
        final var xml =
                """
                <time xmlns="urn:xmpp:time"><utc>2026-03-14T04:28:20Z</utc><tzo>08:00</tzo></time>\
                """;
        final var time = XmlElementReader.read(xml, Time.class);
        Assert.assertEquals(
                ZonedDateTime.parse("2026-03-14T12:28:20+08:00"), time.asZonedDateTime());
    }
}
