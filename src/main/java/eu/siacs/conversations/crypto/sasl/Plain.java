package eu.siacs.conversations.crypto.sasl;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import eu.siacs.conversations.entities.Account;
import javax.net.ssl.SSLSocket;

public class Plain extends SaslMechanism {

    public static final String MECHANISM = "PLAIN";

    public Plain(final Account account) {
        super(account);
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }

    @Override
    public byte[] getClientFirstMessage(final SSLSocket sslSocket) {
        Preconditions.checkState(
                this.state == State.INITIAL, "Calling getClientFirstMessage from invalid state");
        this.state = State.AUTH_TEXT_SENT;
        return getMessage(account.getUsername(), account.getPassword());
    }

    private static byte[] getMessage(final String username, final String password) {
        final String message = '\u0000' + username + '\u0000' + password;
        return message.getBytes();
    }

    public static String getAuthorization(final String username, final String password) {
        return BaseEncoding.base64().encode(getMessage(username, password));
    }

    @Override
    public byte[] getResponse(final byte[] challenge, final SSLSocket sslSocket)
            throws AuthenticationException {
        checkState(State.AUTH_TEXT_SENT);
        if (challenge.length == 0) {
            this.state = State.VALID_SERVER_RESPONSE;
            return new byte[0];
        }
        throw new AuthenticationException("Unexpected server response");
    }
}
