package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import im.conversations.android.xmpp.model.hints.Store;
import im.conversations.android.xmpp.model.jingle.Jingle;
import im.conversations.android.xmpp.model.jingle.Reason;
import im.conversations.android.xmpp.model.jingle.apps.rtp.Description;
import im.conversations.android.xmpp.model.jmi.Accept;
import im.conversations.android.xmpp.model.jmi.Device;
import im.conversations.android.xmpp.model.jmi.Finish;
import im.conversations.android.xmpp.model.jmi.JingleMessage;
import im.conversations.android.xmpp.model.jmi.Proceed;
import im.conversations.android.xmpp.model.jmi.Propose;
import im.conversations.android.xmpp.model.jmi.Reject;
import im.conversations.android.xmpp.model.jmi.Retract;
import im.conversations.android.xmpp.model.jmi.Ringing;
import im.conversations.android.xmpp.model.receipts.Request;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Set;

public class JingleMessageManager extends AbstractManager {

    private final XmppConnectionService service;

    public JingleMessageManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public void processJingleMessage(
            final Message packet,
            final Jid counterpart,
            final MessageArchiveManager.Query query,
            final boolean offlineMessagesRetrieved,
            final String serverMsgId,
            final Long timestamp,
            final int status) {
        if (getManager(MultiUserChatManager.class).isMuc(packet)) {
            Log.d(Config.LOGTAG, "ignore JMI from MUC");
            return;
        }
        final var jingleMessage = packet.getExtension(JingleMessage.class);
        final String sessionId = jingleMessage.getSessionId();
        if (sessionId == null) {
            return;
        }
        final var remoteMsgId = packet.getId();
        final var from = packet.getFrom();
        if (query == null && offlineMessagesRetrieved) {
            // those are 'live' messages and should be routed to the JingleManager
            getManager(JingleManager.class).deliverMessage(packet, timestamp);
            final Contact contact = getAccount().getRoster().getContact(from);
            // this is the same condition that is found in JingleRtpConnection for
            // the 'ringing' response. Responding with delivery receipts predates
            // the 'ringing' spec'd
            final boolean sendReceipts =
                    contact.showInContactList() || Config.JINGLE_MESSAGE_INIT_STRICT_OFFLINE_CHECK;
            if (remoteMsgId != null && !contact.isSelf() && sendReceipts) {
                getManager(DeliveryReceiptManager.class).processRequest(packet, null);
            }
        } else if ((query != null && query.isCatchup()) || !offlineMessagesRetrieved) {
            if (jingleMessage instanceof Propose propose) {
                final Element description = jingleMessage.findChild("description");
                final String namespace = description == null ? null : description.getNamespace();
                if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                    final Conversation c =
                            this.service.findOrCreateConversation(
                                    getAccount(), counterpart.asBareJid(), false, false);
                    final eu.siacs.conversations.entities.Message preExistingMessage =
                            c.findRtpSession(sessionId, status);
                    if (preExistingMessage != null) {
                        preExistingMessage.setServerMsgId(serverMsgId);
                        getDatabase().updateMessage(preExistingMessage, true);
                        this.service.updateConversationUi();
                        return;
                    }
                    final eu.siacs.conversations.entities.Message message =
                            new eu.siacs.conversations.entities.Message(
                                    c,
                                    status,
                                    eu.siacs.conversations.entities.Message.TYPE_RTP_SESSION,
                                    sessionId);
                    message.setServerMsgId(serverMsgId);
                    message.setTime(timestamp);
                    message.setBody(new RtpSessionStatus(false, 0).toString());
                    c.add(message);
                    getDatabase().createMessage(message);
                }
            } else if (jingleMessage instanceof Proceed proceed) {
                // status needs to be flipped to find the original propose
                final Conversation c =
                        this.service.findOrCreateConversation(
                                getAccount(), counterpart.asBareJid(), false, false);
                final int s =
                        packet.fromAccount(getAccount())
                                ? eu.siacs.conversations.entities.Message.STATUS_RECEIVED
                                : eu.siacs.conversations.entities.Message.STATUS_SEND;
                final eu.siacs.conversations.entities.Message message =
                        c.findRtpSession(sessionId, s);
                if (message != null) {
                    message.setBody(new RtpSessionStatus(true, 0).toString());
                    if (serverMsgId != null) {
                        message.setServerMsgId(serverMsgId);
                    }
                    message.setTime(timestamp);
                    getDatabase().updateMessage(message, true);
                    this.service.updateConversationUi();
                } else {
                    Log.d(
                            Config.LOGTAG,
                            "unable to find original rtp session message for"
                                    + " received propose");
                }

            } else if (jingleMessage instanceof Finish finish) {
                Log.d(
                        Config.LOGTAG,
                        "received JMI 'finish' during MAM catch-up. Can be used to"
                                + " update success/failure and duration");
            }
        } else {
            // MAM reloads (non catchup)
            if (jingleMessage instanceof Propose propose) {
                final Element description = jingleMessage.findChild("description");
                final String namespace = description == null ? null : description.getNamespace();
                if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                    final Conversation c =
                            this.service.findOrCreateConversation(
                                    getAccount(), counterpart.asBareJid(), false, false);
                    final eu.siacs.conversations.entities.Message preExistingMessage =
                            c.findRtpSession(sessionId, status);
                    if (preExistingMessage != null) {
                        preExistingMessage.setServerMsgId(serverMsgId);
                        getDatabase().updateMessage(preExistingMessage, true);
                        this.service.updateConversationUi();
                        return;
                    }
                    final eu.siacs.conversations.entities.Message message =
                            new eu.siacs.conversations.entities.Message(
                                    c,
                                    status,
                                    eu.siacs.conversations.entities.Message.TYPE_RTP_SESSION,
                                    sessionId);
                    message.setServerMsgId(serverMsgId);
                    message.setTime(timestamp);
                    message.setBody(new RtpSessionStatus(true, 0).toString());
                    if (query.getPagingOrder() == MessageArchiveManager.PagingOrder.REVERSE) {
                        c.prepend(query.getActualInThisQuery(), message);
                    } else {
                        c.add(message);
                    }
                    query.incrementActualMessageCount();
                    getDatabase().createMessage(message);
                }
            }
        }
    }

    public void propose(final Jid with, final String sessionId, final Set<Media> media) {
        final var packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setTo(with);
        packet.setId(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX + sessionId);
        final var propose = packet.addExtension(new Propose(sessionId));
        for (final var m : media) {
            final var description = propose.addExtension(new Description());
            description.setMedia(m);
        }
        packet.addExtension(new Request());
        packet.addExtension(new Store());
        this.connection.sendMessagePacket(packet);
    }

    public void retract(final Jid with, final String sessionId) {
        final var packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setTo(with);
        packet.addExtension(new Retract(sessionId));
        packet.addExtension(new Store());
        this.connection.sendMessagePacket(packet);
    }

    public void reject(final Jid with, final String sessionId) {
        final var packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setTo(with);
        packet.addExtension(new Reject(sessionId));
        packet.addExtension(new Store());
        this.connection.sendMessagePacket(packet);
    }

    public void ringing(final Jid with, final String sessionId) {
        final var packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setTo(with);
        packet.addExtension(new Ringing(sessionId));
        packet.addExtension(new Store());
        this.connection.sendMessagePacket(packet);
    }

    public void accept(final String sessionId) {
        final var packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setTo(getAccount().getJid().asBareJid());
        packet.addExtension(new Accept(sessionId));
        packet.addExtension(new Store());
        this.connection.sendMessagePacket(packet);
    }

    public void proceed(final Jid with, final String sessionId, final Integer deviceId) {
        final var packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setId(JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX + sessionId);
        packet.setTo(with);
        final var proceed = packet.addExtension(new Proceed(sessionId));
        if (deviceId != null) {
            final var device = proceed.addExtension(new Device());
            device.setId(deviceId);
        }
        packet.addExtension(new Store());
        this.connection.sendMessagePacket(packet);
    }

    public void finish(final Jid with, final String sessionId, final Reason reason) {
        final var packet = new Message();
        packet.setType(Message.Type.CHAT);
        packet.setTo(with);
        final var finish = packet.addExtension(new Finish(sessionId));
        final var wrapper = finish.addExtension(new Jingle.Reason());
        wrapper.addExtension(reason);
        packet.addExtension(new Store());
        this.connection.sendMessagePacket(packet);
    }
}
