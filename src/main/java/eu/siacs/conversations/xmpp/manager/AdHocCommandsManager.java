package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.commands.Command;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class AdHocCommandsManager extends AbstractManager {
    public AdHocCommandsManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Stage> command(final Jid address, final String node) {
        return command(address, node, Command.Action.EXECUTE, null, null);
    }

    public ListenableFuture<Stage> command(
            final Jid address,
            final String node,
            final Command.Action action,
            @Nullable final String sessionId,
            @Nullable final Map<String, Object> data) {
        final var iq = new Iq(Iq.Type.SET, address, new Command(node, action, sessionId, data));
        return Futures.transform(
                this.connection.sendIqPacket(iq),
                response -> {
                    final var command =
                            Objects.requireNonNull(response).getOnlyExtension(Command.class);
                    if (command == null) {
                        throw new IllegalStateException("Expected command in response");
                    }
                    final var resultSessionId = command.getSessionId();
                    final var status = command.getStatus();
                    if (status == null) {
                        throw new IllegalStateException("There must be a valid status");
                    }
                    final var resultData = command.getData();
                    final var actions = command.getActions();
                    return switch (status) {
                        case EXECUTING -> {
                            if (actions == null) {
                                // actions is only a SHOULD. default both execute and actions to
                                // 'next'
                                yield new Executing(
                                        resultSessionId,
                                        Command.Action.NEXT,
                                        ImmutableSet.of(Command.Action.NEXT),
                                        resultData);
                            } else {
                                yield new Executing(
                                        resultSessionId,
                                        actions.getExecute(),
                                        actions.getActions(),
                                        resultData);
                            }
                        }
                        case CANCELED -> new Cancelled(resultSessionId);
                        case COMPLETED -> new Completed(resultSessionId, resultData);
                    };
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Data> commandComplete(final Jid address, final String node) {
        return commandComplete(address, node, null);
    }

    public ListenableFuture<Data> commandComplete(
            final Jid address, final String node, @Nullable final Map<String, Object> data) {
        final var future = command(address, node, Command.Action.COMPLETE, null, data);
        return Futures.transform(
                future, AdHocCommandsManager::completedData, MoreExecutors.directExecutor());
    }

    public static Data completedData(final AdHocCommandsManager.Stage stage) {
        if (stage instanceof AdHocCommandsManager.Completed completed) {
            final var data = completed.data();
            if (data == null) {
                throw new IllegalStateException("Missing data from completed stage");
            }
            return data;
        }
        throw new IllegalStateException("Command did not complete");
    }

    public sealed interface Stage {
        String sessionId();
    }

    public record Cancelled(String sessionId) implements Stage {}

    public record Executing(
            String sessionId, Command.Action execute, Set<Command.Action> actions, Data data)
            implements Stage {}

    public record Completed(String sessionId, Data data) implements Stage {}
}
