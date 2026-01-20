package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.AuthenticationResponse;

@XmlElement
public class Response extends AuthenticationResponse {

    public Response() {
        super(Response.class);
    }
}
