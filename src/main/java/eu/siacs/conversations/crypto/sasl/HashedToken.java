package eu.siacs.conversations.crypto.sasl;

import android.util.Log;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.HashFunction;
import com.google.common.primitives.Bytes;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.SSLSockets;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLSocket;

public abstract class HashedToken extends SaslMechanism implements ChannelBindingMechanism {

    private static final String PREFIX = "HT";

    private static final List<String> HASH_FUNCTIONS = Arrays.asList("SHA-512", "SHA-256");
    private static final byte[] INITIATOR = "Initiator".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONDER = "Responder".getBytes(StandardCharsets.UTF_8);

    protected final ChannelBinding channelBinding;

    protected HashedToken(final Account account, final ChannelBinding channelBinding) {
        super(account);
        this.channelBinding = channelBinding;
    }

    @Override
    public int getPriority() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getClientFirstMessage(final SSLSocket sslSocket) {
        final String token = Strings.nullToEmpty(this.account.getFastToken());
        final HashFunction hashing = getHashFunction(token.getBytes(StandardCharsets.UTF_8));
        final byte[] cbData = getChannelBindingData(sslSocket);
        final byte[] initiatorHashedToken =
                hashing.hashBytes(Bytes.concat(INITIATOR, cbData)).asBytes();
        return Bytes.concat(
                account.getUsername().getBytes(StandardCharsets.UTF_8),
                new byte[] {0x00},
                initiatorHashedToken);
    }

    private byte[] getChannelBindingData(final SSLSocket sslSocket) {
        if (this.channelBinding == ChannelBinding.NONE) {
            return new byte[0];
        }
        try {
            return ChannelBindingMechanism.getChannelBindingData(sslSocket, this.channelBinding);
        } catch (final AuthenticationException e) {
            Log.e(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": unable to retrieve channel binding data for "
                            + getMechanism(),
                    e);
            return new byte[0];
        }
    }

    @Override
    public byte[] getResponse(final byte[] challenge, final SSLSocket socket)
            throws AuthenticationException {
        final String token = Strings.nullToEmpty(this.account.getFastToken());
        final HashFunction hashing = getHashFunction(token.getBytes(StandardCharsets.UTF_8));
        final byte[] cbData = getChannelBindingData(socket);
        final byte[] expectedResponderMessage =
                hashing.hashBytes(Bytes.concat(RESPONDER, cbData)).asBytes();
        // TODO handle the 0x00 prefix for success responses
        // we know the length of the hmac and if the response is exactly one byte longer and is 00
        // then it's fine
        if (Arrays.equals(challenge, expectedResponderMessage)) {
            return new byte[0];
        }
        throw new AuthenticationException("Responder message did not match");
    }

    protected abstract HashFunction getHashFunction(final byte[] key);

    public abstract Mechanism getTokenMechanism();

    @Override
    public String getMechanism() {
        return getTokenMechanism().name();
    }

    public record Mechanism(String hashFunction, ChannelBinding channelBinding) {

        public static Mechanism of(final String mechanism) {
            final int first = mechanism.indexOf('-');
            final int last = mechanism.lastIndexOf('-');
            if (first == -1 || last == -1 || last < first) {
                throw new IllegalArgumentException("Not a valid HashedToken name");
            }
            if (mechanism.substring(0, first).equals(PREFIX)) {
                final String hashFunction = mechanism.substring(first + 1, last);
                final String cbShortName = mechanism.substring(last + 1);
                final ChannelBinding channelBinding =
                        ChannelBinding.SHORT_NAMES.inverse().get(cbShortName);
                if (channelBinding == null) {
                    throw new IllegalArgumentException("Unknown channel binding " + cbShortName);
                }
                return new Mechanism(hashFunction, channelBinding);
            } else {
                throw new IllegalArgumentException("HashedToken name does not start with HT");
            }
        }

        public static Mechanism ofOrNull(final String mechanism) {
            try {
                return mechanism == null ? null : of(mechanism);
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }

        public static Multimap<String, ChannelBinding> of(final Collection<String> mechanisms) {
            final ImmutableMultimap.Builder<String, ChannelBinding> builder =
                    ImmutableMultimap.builder();
            for (final String name : mechanisms) {
                try {
                    final Mechanism mechanism = Mechanism.of(name);
                    builder.put(mechanism.hashFunction, mechanism.channelBinding);
                } catch (final IllegalArgumentException ignored) {
                }
            }
            return builder.build();
        }

        public static Mechanism best(
                final Collection<String> mechanisms, final SSLSockets.Version sslVersion) {
            final Multimap<String, ChannelBinding> multimap = of(mechanisms);
            for (final String hashFunction : HASH_FUNCTIONS) {
                final Collection<ChannelBinding> channelBindings = multimap.get(hashFunction);
                if (channelBindings.isEmpty()) {
                    continue;
                }
                final ChannelBinding cb = ChannelBinding.best(channelBindings, sslVersion);
                return new Mechanism(hashFunction, cb);
            }
            return null;
        }

        public String name() {
            return String.format(
                    "%s-%s-%s",
                    PREFIX, hashFunction, ChannelBinding.SHORT_NAMES.get(channelBinding));
        }
    }

    public ChannelBinding getChannelBinding() {
        return this.channelBinding;
    }
}
