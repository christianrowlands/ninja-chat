package im.conversations.android.xmpp.model;

public abstract class AuthenticationChallenge extends StreamElement implements ByteContent {

    protected AuthenticationChallenge(Class<? extends AuthenticationChallenge> clazz) {
        super(clazz);
    }
}
