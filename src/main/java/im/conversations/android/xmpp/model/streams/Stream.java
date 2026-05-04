package im.conversations.android.xmpp.model.streams;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Stream extends Extension {

    public static final ExtensionFactory.Id ID =
            new ExtensionFactory.Id("stream:stream", Namespace.JABBER_CLIENT);

    public Stream() {
        super(Stream.class);
    }
}
