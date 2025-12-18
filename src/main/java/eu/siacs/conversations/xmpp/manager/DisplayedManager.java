package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.ReadByMarker;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.hints.Store;
import im.conversations.android.xmpp.model.markers.Displayed;
import java.util.List;

public class DisplayedManager extends AbstractManager {

    private final XmppConnectionService service;
    private final AppSettings appSettings;

    public DisplayedManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.appSettings = new AppSettings(service.getApplicationContext());
        this.service = service;
    }

    public void processDisplayed(
            final im.conversations.android.xmpp.model.stanza.Message packet,
            final boolean selfAddressed,
            final Jid counterpart,
            final MessageArchiveManager.Query query) {
        final var account = getAccount();
        final var isTypeGroupChat =
                packet.getType()
                        == im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT;
        final var from = packet.getFrom();
        final var displayed = packet.getExtension(Displayed.class);
        final var id = displayed.getId();
        if (packet.fromAccount(account) && !selfAddressed) {
            final Conversation c = this.service.find(account, counterpart.asBareJid());
            final Message message =
                    (c == null || id == null) ? null : c.findReceivedWithRemoteId(id);
            if (message != null && (query == null || query.isCatchup())) {
                this.service.markReadUpTo(c, message);
            }
            if (query == null) {
                getManager(ActivityManager.class)
                        .record(from, ActivityManager.ActivityType.DISPLAYED);
            }
        } else if (isTypeGroupChat) {
            final var conversation = this.service.find(account, counterpart.asBareJid());
            final Message message;
            if (conversation != null && id != null) {
                message = conversation.findMessageWithServerMsgId(id);
            } else {
                message = null;
            }
            if (message != null) {
                final var user = getManager(MultiUserChatManager.class).getMucUser(packet, query);
                if (user != null && user.getMucOptions().isOurAccount(user)) {
                    if (!message.isRead()
                            && (query == null || query.isCatchup())) { // checking if message is
                        // unread fixes race conditions
                        // with reflections
                        this.service.markReadUpTo(conversation, message);
                    }
                } else if (!counterpart.isBareJid() && user != null && user.getRealJid() != null) {
                    final ReadByMarker readByMarker = ReadByMarker.from(user);
                    if (message.addReadByMarker(readByMarker)) {
                        final var mucOptions =
                                getManager(MultiUserChatManager.class)
                                        .getOrCreateState(conversation);
                        final var everyone = mucOptions.getMembers();
                        final var readyBy = message.getReadyByTrue();
                        final var mStatus = message.getStatus();
                        if (mucOptions.isPrivateAndNonAnonymous()
                                && (mStatus == Message.STATUS_SEND_RECEIVED
                                        || mStatus == Message.STATUS_SEND)
                                && readyBy.containsAll(everyone)) {
                            message.setStatus(Message.STATUS_SEND_DISPLAYED);
                        }
                        this.getDatabase().updateMessage(message, false);
                        this.service.updateConversationUi();
                    }
                }
            }
        } else {
            final Message displayedMessage =
                    this.service.markMessage(
                            account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
            Message message = displayedMessage == null ? null : displayedMessage.prev();
            while (message != null
                    && message.getStatus() == Message.STATUS_SEND_RECEIVED
                    && message.getTimeSent() < displayedMessage.getTimeSent()) {
                this.service.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                message = message.prev();
            }
            if (displayedMessage != null && selfAddressed) {
                dismissNotification(counterpart, query, id);
            }
        }
    }

    private void dismissNotification(
            final Jid counterpart, final MessageArchiveManager.Query query, final String id) {
        final var account = getAccount();
        final var conversation = this.service.find(account, counterpart.asBareJid());
        if (conversation != null && (query == null || query.isCatchup())) {
            final String displayableId = conversation.findMostRecentRemoteDisplayableId();
            if (displayableId != null && displayableId.equals(id)) {
                this.service.markRead(conversation);
            } else {
                Log.w(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": received dismissing display marker that did not match our last"
                                + " id in that conversation");
            }
        }
    }

    public void displayed(final List<Message> readMessages) {
        final var last =
                Iterables.getLast(
                        Collections2.filter(
                                readMessages,
                                m ->
                                        !m.isPrivateMessage()
                                                && m.getStatus() == Message.STATUS_RECEIVED),
                        null);
        if (last == null) {
            return;
        }

        final Conversation conversation;
        if (last.getConversation() instanceof Conversation c) {
            conversation = c;
        } else {
            return;
        }

        final boolean isPrivateAndNonAnonymousMuc =
                conversation.getMode() == Conversation.MODE_MULTI
                        && conversation.isPrivateAndNonAnonymous();

        final boolean sendDisplayedMarker =
                appSettings.isReadReceipts()
                        && (last.trusted() || isPrivateAndNonAnonymousMuc)
                        && ((last.getConversation().getMode() == Conversation.MODE_SINGLE
                                        && last.getRemoteMsgId() != null)
                                || (last.getConversation().getMode() == Conversational.MODE_MULTI
                                        && last.getServerMsgId() != null))
                        && (last.markable || isPrivateAndNonAnonymousMuc);

        final String stanzaId = last.getServerMsgId();

        final boolean serverAssist =
                stanzaId != null
                        && connection
                                .getManager(MessageDisplayedSynchronizationManager.class)
                                .hasServerAssist();

        if (sendDisplayedMarker && serverAssist) {
            final var displayedMessage = displayedMessage(last);
            displayedMessage.addExtension(
                    MessageDisplayedSynchronizationManager.displayed(stanzaId, conversation));
            displayedMessage.setTo(displayedMessage.getTo().asBareJid());
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid().asBareJid() + ": server assisted " + displayedMessage);
            this.connection.sendMessagePacket(displayedMessage);
        } else {
            getManager(MessageDisplayedSynchronizationManager.class).displayed(last);
            // read markers will be sent after MDS to flush the CSI stanza queue
            if (sendDisplayedMarker) {
                final var displayedMessage = displayedMessage(last);
                Log.d(
                        Config.LOGTAG,
                        getAccount().getJid().asBareJid()
                                + ": sending displayed marker to "
                                + displayedMessage.getTo());
                this.connection.sendMessagePacket(displayedMessage);
            }
        }
    }

    private static im.conversations.android.xmpp.model.stanza.Message displayedMessage(
            final Message message) {
        final boolean groupChat = message.getConversation().getMode() == Conversational.MODE_MULTI;
        final Jid to = message.getCounterpart();
        final var packet = new im.conversations.android.xmpp.model.stanza.Message();
        packet.setType(
                groupChat
                        ? im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT
                        : im.conversations.android.xmpp.model.stanza.Message.Type.CHAT);
        packet.setTo(groupChat ? to.asBareJid() : to);
        final var displayed = packet.addExtension(new Displayed());
        if (groupChat) {
            displayed.setId(message.getServerMsgId());
        } else {
            displayed.setId(message.getRemoteMsgId());
        }
        packet.addExtension(new Store());
        return packet;
    }
}
