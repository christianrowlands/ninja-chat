package eu.siacs.conversations.crypto.sasl;

import com.google.common.base.Preconditions;
import eu.siacs.conversations.entities.Account;
import javax.net.ssl.SSLSocket;

public class External extends SaslMechanism {

    public static final String MECHANISM = "EXTERNAL";

    public External(final Account account) {
        super(account);
    }

    @Override
    public int getPriority() {
        return 25;
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
        final String message = account.getJid().asBareJid().toString();
        return message.getBytes();
    }

    @Override
    public byte[] getResponse(byte[] challenge, SSLSocket sslSocket)
            throws AuthenticationException {
        if (this.state != State.AUTH_TEXT_SENT) {
            throw new InvalidStateException(this.state);
        }
        this.state = State.VALID_SERVER_RESPONSE;
        return new byte[0];
    }
}
