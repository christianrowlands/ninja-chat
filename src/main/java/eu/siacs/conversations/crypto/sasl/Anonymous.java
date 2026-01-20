package eu.siacs.conversations.crypto.sasl;

import com.google.common.base.Preconditions;
import eu.siacs.conversations.entities.Account;
import javax.net.ssl.SSLSocket;

public class Anonymous extends SaslMechanism {

    public static final String MECHANISM = "ANONYMOUS";

    public Anonymous(final Account account) {
        super(account);
    }

    @Override
    public int getPriority() {
        return 0;
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
        return new byte[0];
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
