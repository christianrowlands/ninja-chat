package im.conversations.android.xmpp.model.streams;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "text", namespace = Namespace.XMPP_STREAMS)
public class StreamErrorText extends Extension {

    public StreamErrorText() {
        super(StreamErrorText.class);
    }
}
