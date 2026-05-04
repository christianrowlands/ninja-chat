package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import de.gultsch.common.MiniUri;
import eu.siacs.conversations.utils.EasyOnboardingInvite;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.commands.Command;
import java.util.Map;
import java.util.Objects;
import okhttp3.HttpUrl;

public class EasyOnboardingManager extends AbstractManager {

    public EasyOnboardingManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<EasyOnboardingInvite> invite() {
        final var optional = getAddressForInviteCommand();
        final Jid address;
        if (optional.isPresent()) {
            address = optional.get();
        } else {
            return Futures.immediateFailedFuture(
                    new UnsupportedOperationException(
                            "Server does not support generating easy onboarding invites"));
        }

        final var future =
                this.getManager(AdHocCommandsManager.class)
                        .command(address, Namespace.EASY_ONBOARDING_INVITE);
        return Futures.transform(
                future, this::getEasyOnboardingInvite, MoreExecutors.directExecutor());
    }

    public ListenableFuture<EasyOnboardingInvite> createAccount() {
        final var optional = getAddressForInviteCommand();
        final Jid address;
        if (optional.isPresent()) {
            address = optional.get();
        } else {
            return Futures.immediateFailedFuture(
                    new UnsupportedOperationException("Server does not support account creation"));
        }

        final var future =
                this.getManager(AdHocCommandsManager.class)
                        .command(address, Namespace.EASY_ONBOARDING_CREATE_ACCOUNT);
        return Futures.transformAsync(
                future,
                stage -> {
                    if (stage instanceof AdHocCommandsManager.Executing executing) {
                        final var data = executing.data();
                        if (data == null) {
                            throw new IllegalStateException("Missing data in executing stage");
                        }
                        final var sessionId = executing.sessionId();
                        if (Strings.isNullOrEmpty(sessionId)) {
                            throw new IllegalStateException("Missing sessionId in executing stage");
                        }
                        final var username = data.getFieldByName("username");
                        if (username != null && username.isRequired()) {
                            throw new IllegalStateException("Username is required");
                        }
                        final var rosterSubscription =
                                Objects.nonNull(data.getFieldByName("roster-subscription"));
                        return createAccount(address, sessionId, rosterSubscription);
                    } else {
                        throw new IllegalStateException("Unexpected stage");
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<EasyOnboardingInvite> createAccount(
            final Jid address, final String sessionId, final boolean rosterSubscription) {
        final Map<String, Object> data =
                rosterSubscription ? Map.of("roster-subscription", true) : Map.of();
        final var future =
                getManager(AdHocCommandsManager.class)
                        .command(
                                address,
                                Namespace.EASY_ONBOARDING_CREATE_ACCOUNT,
                                Command.Action.EXECUTE,
                                sessionId,
                                data);
        return Futures.transform(
                future, this::getEasyOnboardingInvite, MoreExecutors.directExecutor());
    }

    private EasyOnboardingInvite getEasyOnboardingInvite(final AdHocCommandsManager.Stage stage) {
        final var data = AdHocCommandsManager.completedData(stage);
        final MiniUri.Xmpp uri;
        if (MiniUri.getOrNull(data.getValue("uri")) instanceof MiniUri.Xmpp xmpp) {
            uri = xmpp;
        } else {
            throw new IllegalStateException("Did not find valid XMPP uri");
        }
        final var landingUrl = data.getValue("landing-url");
        if (Strings.isNullOrEmpty(landingUrl)) {
            return new EasyOnboardingInvite(getAccount().getDomain(), uri);
        }
        // HttpUrl.get will throw on invalid URL
        return new EasyOnboardingInvite(getAccount().getDomain(), uri, HttpUrl.get(landingUrl));
    }

    public boolean hasFeature() {
        return getAddressForCreateAccountCommand().isPresent();
    }

    private Optional<Jid> getAddressForCreateAccountCommand() {
        return getAddressForCommand(Namespace.EASY_ONBOARDING_CREATE_ACCOUNT);
    }

    private Optional<Jid> getAddressForInviteCommand() {
        return getAddressForCommand(Namespace.EASY_ONBOARDING_INVITE);
    }

    private Optional<Jid> getAddressForCommand(final String command) {
        final var discoManager = this.getManager(DiscoManager.class);
        final var address = discoManager.getAddressForCommand(command);
        return Optional.fromNullable(address);
    }
}
