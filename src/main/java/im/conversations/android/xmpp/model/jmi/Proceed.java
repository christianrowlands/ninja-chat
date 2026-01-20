package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Proceed extends JingleMessage {

    public Proceed() {
        super(Proceed.class);
    }

    public Proceed(final String sessionId) {
        this();
        this.setSessionId(sessionId);
    }

    public Integer getDeviceId() {
        final var device = this.getOnlyExtension(Device.class);
        if (device == null) {
            return null;
        }
        return device.getId();
    }
}
