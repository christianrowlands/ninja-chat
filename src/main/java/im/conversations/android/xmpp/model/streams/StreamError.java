package im.conversations.android.xmpp.model.streams;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement(name = "error")
public class StreamError extends StreamElement {

    public StreamError() {
        super(StreamError.class);
    }

    public StreamErrorCondition getCondition() {
        return getOnlyExtension(StreamErrorCondition.class);
    }

    public String getText() {
        final var streamErrorText = this.getOnlyExtension(StreamErrorText.class);
        return streamErrorText != null ? streamErrorText.getContent() : null;
    }
}
