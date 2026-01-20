package im.conversations.android.xmpp.model;

public abstract class AuthenticationResponse extends StreamElement implements ByteContent {

    protected AuthenticationResponse(Class<? extends AuthenticationResponse> clazz) {
        super(clazz);
    }
}
