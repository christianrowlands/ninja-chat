package eu.siacs.conversations.parser;

import android.util.Log;
import android.util.Pair;
import com.google.common.base.Strings;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.BrokenSessionException;
import eu.siacs.conversations.crypto.axolotl.NotEncryptedForThisDeviceException;
import eu.siacs.conversations.crypto.axolotl.OutdatedSenderException;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.manager.ActivityManager;
import eu.siacs.conversations.xmpp.manager.ChatStateManager;
import eu.siacs.conversations.xmpp.manager.DeliveryReceiptManager;
import eu.siacs.conversations.xmpp.manager.DisplayedManager;
import eu.siacs.conversations.xmpp.manager.JingleManager;
import eu.siacs.conversations.xmpp.manager.JingleMessageManager;
import eu.siacs.conversations.xmpp.manager.MessageArchiveManager;
import eu.siacs.conversations.xmpp.manager.ModerationManager;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import eu.siacs.conversations.xmpp.manager.PubSubManager;
import eu.siacs.conversations.xmpp.manager.ReactionManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import eu.siacs.conversations.xmpp.manager.StanzaIdManager;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.axolotl.Payload;
import im.conversations.android.xmpp.model.carbons.Received;
import im.conversations.android.xmpp.model.carbons.Sent;
import im.conversations.android.xmpp.model.conference.DirectInvite;
import im.conversations.android.xmpp.model.correction.Replace;
import im.conversations.android.xmpp.model.fallback.Body;
import im.conversations.android.xmpp.model.fallback.Fallback;
import im.conversations.android.xmpp.model.forward.Forwarded;
import im.conversations.android.xmpp.model.jmi.JingleMessage;
import im.conversations.android.xmpp.model.mam.Result;
import im.conversations.android.xmpp.model.markers.Displayed;
import im.conversations.android.xmpp.model.markers.Markable;
import im.conversations.android.xmpp.model.muc.user.MucUser;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.occupant.OccupantId;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import im.conversations.android.xmpp.model.pubsub.event.Event;
import im.conversations.android.xmpp.model.reactions.Reactions;
import im.conversations.android.xmpp.model.retraction.Retract;
import java.util.UUID;
import java.util.function.Consumer;

public class MessageParser extends AbstractParser
        implements Consumer<im.conversations.android.xmpp.model.stanza.Message> {

    public MessageParser(final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
    }

    private Message parseAxolotlChat(
            final Encrypted axolotlMessage,
            final Jid from,
            final Conversation conversation,
            final int status,
            final boolean checkedForDuplicates,
            final boolean postpone) {
        final AxolotlService service = conversation.getAccount().getAxolotlService();
        final XmppAxolotlMessage xmppAxolotlMessage;
        try {
            xmppAxolotlMessage = XmppAxolotlMessage.fromElement(axolotlMessage, from.asBareJid());
        } catch (final Exception e) {
            Log.d(
                    Config.LOGTAG,
                    conversation.getAccount().getJid().asBareJid()
                            + ": invalid omemo message received "
                            + e.getMessage());
            return null;
        }
        if (xmppAxolotlMessage.hasPayload()) {
            final XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage;
            try {
                plaintextMessage =
                        service.processReceivingPayloadMessage(xmppAxolotlMessage, postpone);
            } catch (BrokenSessionException e) {
                if (checkedForDuplicates) {
                    if (service.trustedOrPreviouslyResponded(from.asBareJid())) {
                        service.reportBrokenSessionException(e, postpone);
                        return new Message(
                                conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                "ignoring broken session exception because contact was not"
                                        + " trusted");
                        return new Message(
                                conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
                    }
                } else {
                    Log.d(
                            Config.LOGTAG,
                            "ignoring broken session exception because checkForDuplicates failed");
                    return null;
                }
            } catch (NotEncryptedForThisDeviceException e) {
                return new Message(
                        conversation, "", Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE, status);
            } catch (OutdatedSenderException e) {
                return new Message(conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
            }
            if (plaintextMessage != null) {
                Message finishedMessage =
                        new Message(
                                conversation,
                                plaintextMessage.getPlaintext(),
                                Message.ENCRYPTION_AXOLOTL,
                                status);
                finishedMessage.setFingerprint(plaintextMessage.getFingerprint());
                Log.d(
                        Config.LOGTAG,
                        AxolotlService.getLogprefix(finishedMessage.getConversation().getAccount())
                                + " Received Message with session fingerprint: "
                                + plaintextMessage.getFingerprint());
                return finishedMessage;
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    conversation.getAccount().getJid().asBareJid()
                            + ": received OMEMO key transport message");
            service.processReceivingKeyTransportMessage(xmppAxolotlMessage, postpone);
        }
        return null;
    }

    private boolean handleErrorMessage(
            final Account account,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        if (packet.getType() == im.conversations.android.xmpp.model.stanza.Message.Type.ERROR) {
            if (packet.fromServer(account)) {
                final var forwarded =
                        getForwardedMessagePacket(packet, "received", Namespace.CARBONS);
                if (forwarded != null) {
                    return handleErrorMessage(account, forwarded.first);
                }
            }
            final Jid from = packet.getFrom();
            final String id = packet.getId();
            if (from != null && id != null) {
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
                    final String sessionId =
                            id.substring(
                                    JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
                    getManager(JingleManager.class)
                            .updateProposedSessionDiscovered(
                                    from, sessionId, JingleManager.DeviceDiscoveryState.FAILED);
                    return true;
                }
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX)) {
                    final String sessionId =
                            id.substring(
                                    JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX.length());
                    final String message = extractErrorMessage(packet);
                    getManager(JingleManager.class).failProceed(from, sessionId, message);
                    return true;
                }
                mXmppConnectionService.markMessage(
                        account,
                        from.asBareJid(),
                        id,
                        Message.STATUS_SEND_FAILED,
                        extractErrorMessage(packet));
                final Element error = packet.findChild("error");
                final boolean pingWorthyError =
                        error != null
                                && (error.hasChild("not-acceptable")
                                        || error.hasChild("remote-server-timeout")
                                        || error.hasChild("remote-server-not-found"));
                if (pingWorthyError) {
                    Conversation conversation = mXmppConnectionService.find(account, from);
                    if (conversation != null
                            && conversation.getMode() == Conversational.MODE_MULTI) {
                        if (getManager(MultiUserChatManager.class)
                                .getOrCreateState(conversation)
                                .online()) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": received ping worthy error for seemingly online"
                                            + " muc at "
                                            + from);
                            getManager(MultiUserChatManager.class).pingAndRejoin(conversation);
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void accept(final im.conversations.android.xmpp.model.stanza.Message original) {
        final var originalFrom = original.getFrom();
        final var account = this.getAccount();
        if (handleErrorMessage(account, original)) {
            return;
        }
        final im.conversations.android.xmpp.model.stanza.Message packet;
        Long timestamp = null;
        boolean isCarbon = false;
        String serverMsgId = null;
        final var result = original.getExtension(Result.class);
        final String queryId = result == null ? null : result.getQueryId();
        final MessageArchiveManager.Query query =
                queryId == null ? null : getManager(MessageArchiveManager.class).findQuery(queryId);
        final boolean offlineMessagesRetrieved = connection.isOfflineMessagesRetrieved();
        if (query != null
                && getManager(MessageArchiveManager.class).validFrom(query, original.getFrom())) {
            final var f = result.getForwarded();
            final var stamp = f == null ? null : f.getStamp();
            final var m = f == null ? null : f.getMessage();
            if (stamp == null || m == null) {
                return;
            }

            timestamp = stamp.toEpochMilli();
            packet = m;
            serverMsgId = result.getId();
            query.incrementMessageCount();

            if (query.isImplausibleFrom(packet.getFrom())) {
                Log.d(Config.LOGTAG, "found implausible from in MUC MAM archive");
                return;
            }

            if (handleErrorMessage(account, packet)) {
                return;
            }
        } else if (query != null) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": received mam result with invalid from ("
                            + original.getFrom()
                            + ") or queryId ("
                            + queryId
                            + ")");
            return;
        } else if (original.fromServer(account)
                && original.getType()
                        != im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT) {
            Pair<im.conversations.android.xmpp.model.stanza.Message, Long> f;
            f = getForwardedMessagePacket(original, Received.class);
            f = f == null ? getForwardedMessagePacket(original, Sent.class) : f;
            packet = f != null ? f.first : original;
            if (handleErrorMessage(account, packet)) {
                return;
            }
            timestamp = f != null ? f.second : null;
            isCarbon = f != null;
        } else {
            packet = original;
        }

        if (timestamp == null) {
            timestamp =
                    AbstractParser.parseTimestamp(original, AbstractParser.parseTimestamp(packet));
        }
        final LocalizedContent body = packet.getBody();
        final Element mucUserElement = packet.findChild("x", Namespace.MUC_USER);
        final boolean isTypeGroupChat =
                packet.getType()
                        == im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT;
        final var encrypted =
                packet.getOnlyExtension(im.conversations.android.xmpp.model.pgp.Encrypted.class);
        final String pgpEncrypted = encrypted == null ? null : encrypted.getContent();

        final var oob = packet.getExtension(OutOfBandData.class);
        final String oobUrl = oob != null ? oob.getURL() : null;
        final var replace = packet.getExtension(Replace.class);
        final var replacementId = replace == null ? null : replace.getId();
        final var axolotlEncrypted = packet.getOnlyExtension(Encrypted.class);
        // TODO this can probably be refactored to be final
        int status;
        final Jid counterpart;
        final Jid to = packet.getTo();
        final Jid from = packet.getFrom();
        final Element originId = packet.findChild("origin-id", Namespace.STANZA_IDS);
        final String remoteMsgId;
        if (originId != null && originId.getAttribute("id") != null) {
            remoteMsgId = originId.getAttribute("id");
        } else {
            remoteMsgId = packet.getId();
        }
        boolean notify = false;

        if (from == null || !Jid.Invalid.isValid(from) || !Jid.Invalid.isValid(to)) {
            Log.e(Config.LOGTAG, "encountered invalid message from='" + from + "' to='" + to + "'");
            return;
        }
        if (query != null && !query.muc() && isTypeGroupChat) {
            Log.e(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": received group chat ("
                            + from
                            + ") message on regular MAM request. skipping");
            return;
        }
        final boolean selfAddressed;
        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
            selfAddressed = to == null || account.getJid().asBareJid().equals(to.asBareJid());
            if (selfAddressed) {
                counterpart = from;
            } else {
                counterpart = to;
            }
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = from;
            selfAddressed = false;
        }

        if (packet.hasExtension(MucUser.class)
                && packet.getExtension(MucUser.class)
                        .hasExtension(im.conversations.android.xmpp.model.muc.user.Invite.class)) {
            if (getManager(MultiUserChatManager.class).handleMediatedInvite(packet)) {
                return;
            }
        }
        if (packet.hasExtension(DirectInvite.class)) {
            if (getManager(MultiUserChatManager.class).handleDirectInvite(packet)) {
                return;
            }
        }

        if (original.hasExtension(MucUser.class)) {
            if (getManager(MultiUserChatManager.class).handleStatusMessage(original)) {
                return;
            }
        }
        final boolean bodyIsFallback;
        if (body != null && packet.hasExtension(Reactions.class)) {
            final var range = Fallback.get(packet, Reactions.class, Body.class);
            bodyIsFallback = range.isPresent() && range.get().isEntire(body);
        } else if (body != null && packet.hasExtension(Retract.class)) {
            final var range = Fallback.get(packet, Retract.class, Body.class);
            bodyIsFallback = range.isPresent() && range.get().isEntire(body);
        } else {
            bodyIsFallback = false;
        }

        if ((body != null && !bodyIsFallback)
                || pgpEncrypted != null
                || (axolotlEncrypted != null && axolotlEncrypted.hasExtension(Payload.class))
                || oobUrl != null) {
            final boolean conversationIsProbablyMuc =
                    isTypeGroupChat
                            || mucUserElement != null
                            || connection
                                    .getMucServersWithholdAccount()
                                    .contains(counterpart.getDomain());
            final Conversation conversation =
                    mXmppConnectionService.findOrCreateConversation(
                            account,
                            counterpart.asBareJid(),
                            conversationIsProbablyMuc,
                            false,
                            query,
                            false);
            final boolean conversationMultiMode = conversation.getMode() == Conversation.MODE_MULTI;

            if (serverMsgId == null) {
                serverMsgId =
                        getManager(StanzaIdManager.class)
                                .get(packet, isTypeGroupChat, conversation);
            }

            if (selfAddressed) {
                // don’t store serverMsgId on reflections for edits
                final var reflectedServerMsgId =
                        Strings.isNullOrEmpty(replacementId) ? serverMsgId : null;
                if (mXmppConnectionService.markMessage(
                        conversation,
                        remoteMsgId,
                        Message.STATUS_SEND_RECEIVED,
                        reflectedServerMsgId)) {
                    return;
                }
                status = Message.STATUS_RECEIVED;
                if (remoteMsgId != null
                        && conversation.findMessageWithRemoteId(remoteMsgId, counterpart) != null) {
                    return;
                }
            }

            if (isTypeGroupChat) {
                // this should probably remain a counterpart check
                if (getManager(MultiUserChatManager.class)
                        .getOrCreateState(conversation)
                        .isSelf(counterpart)) {
                    status = Message.STATUS_SEND_RECEIVED;
                    isCarbon = true; // not really carbon but received from another resource
                    // don’t store serverMsgId on reflections for edits
                    final var reflectedServerMsgId =
                            Strings.isNullOrEmpty(replacementId) ? serverMsgId : null;
                    if (mXmppConnectionService.markMessage(
                            conversation, remoteMsgId, status, reflectedServerMsgId, body)) {
                        return;
                    } else if (remoteMsgId == null || Config.IGNORE_ID_REWRITE_IN_MUC) {
                        if (body != null) {
                            Message message = conversation.findSentMessageWithBody(body.content);
                            if (message != null) {
                                mXmppConnectionService.markMessage(message, status);
                                return;
                            }
                        }
                    }
                } else {
                    final var user =
                            getManager(MultiUserChatManager.class).getMucUser(packet, query);
                    if (user != null) {
                        final var mucOptions =
                                getManager(MultiUserChatManager.class).getState(from.asBareJid());
                        if (mucOptions != null && mucOptions.isOurAccount(user)) {
                            status = Message.STATUS_SEND_RECEIVED;
                            isCarbon = true;
                        } else {
                            status = Message.STATUS_RECEIVED;
                        }
                    } else {
                        status = Message.STATUS_RECEIVED;
                    }
                }
            }
            final Message message;
            if (pgpEncrypted != null) {
                message = new Message(conversation, pgpEncrypted, Message.ENCRYPTION_PGP, status);
            } else if (axolotlEncrypted != null) {
                final Jid origin;
                if (conversationMultiMode) {
                    final var user =
                            getManager(MultiUserChatManager.class).getMucUser(packet, query);
                    origin = user == null ? null : user.getRealJid();
                    if (origin == null) {
                        Log.d(Config.LOGTAG, "received omemo message in anonymous conference");
                        return;
                    }

                } else {
                    origin = from;
                }

                final boolean liveMessage =
                        query == null && !isTypeGroupChat && mucUserElement == null;
                final boolean checkedForDuplicates =
                        liveMessage
                                || (serverMsgId != null
                                        && remoteMsgId != null
                                        && !conversation.possibleDuplicate(
                                                serverMsgId, remoteMsgId));

                message =
                        parseAxolotlChat(
                                axolotlEncrypted,
                                origin,
                                conversation,
                                status,
                                checkedForDuplicates,
                                query != null);
                if (message == null) {
                    if (query != null) {
                        getManager(ChatStateManager.class).process(packet);
                    }
                    if (query != null && status == Message.STATUS_SEND && remoteMsgId != null) {
                        Message previouslySent = conversation.findSentMessageWithUuid(remoteMsgId);
                        if (previouslySent != null
                                && previouslySent.getServerMsgId() == null
                                && serverMsgId != null) {
                            previouslySent.setServerMsgId(serverMsgId);
                            mXmppConnectionService.databaseBackend.updateMessage(
                                    previouslySent, false);
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": encountered previously sent OMEMO message without"
                                            + " serverId. updating...");
                        }
                    }
                    return;
                }
                if (conversationMultiMode) {
                    message.setTrueCounterpart(origin);
                }
            } else if (body == null && oobUrl != null) {
                message = new Message(conversation, oobUrl, Message.ENCRYPTION_NONE, status);
                message.setOob(true);
                if (CryptoHelper.isPgpEncryptedUrl(oobUrl)) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                }
            } else {
                message = new Message(conversation, body.content, Message.ENCRYPTION_NONE, status);
                if (body.count > 1) {
                    message.setBodyLanguage(body.language);
                }
            }

            message.setCounterpart(counterpart);
            message.setRemoteMsgId(remoteMsgId);
            message.setServerMsgId(serverMsgId);
            message.setCarbon(isCarbon);
            message.setTime(timestamp);
            if (body != null && body.content != null && body.content.equals(oobUrl)) {
                message.setOob(true);
                if (CryptoHelper.isPgpEncryptedUrl(oobUrl)) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                }
            }
            message.markable = packet.hasExtension(Markable.class);
            if (conversationMultiMode) {
                final var mucOptions =
                        getManager(MultiUserChatManager.class).getOrCreateState(conversation);
                final var occupantId =
                        mucOptions.occupantId() ? packet.getOnlyExtension(OccupantId.class) : null;
                if (occupantId != null) {
                    message.setOccupantId(occupantId.getId());
                }
                final var user = getManager(MultiUserChatManager.class).getMucUser(packet, query);
                final var trueCounterpart = user == null ? null : user.getRealJid();
                message.setTrueCounterpart(trueCounterpart);
                if (!isTypeGroupChat) {
                    message.setType(Message.TYPE_PRIVATE);
                }
            } else {
                updateLastseen(account, from);
            }

            if (replacementId != null && mXmppConnectionService.allowMessageCorrection()) {
                final Message replacedMessage =
                        conversation.findMessageWithRemoteIdAndCounterpart(
                                replacementId,
                                counterpart,
                                message.getStatus() == Message.STATUS_RECEIVED,
                                message.isCarbon());
                if (replacedMessage != null) {
                    final boolean fingerprintsMatch =
                            replacedMessage.getFingerprint() == null
                                    || replacedMessage
                                            .getFingerprint()
                                            .equals(message.getFingerprint());
                    final boolean trueCountersMatch =
                            replacedMessage.getTrueCounterpart() != null
                                    && message.getTrueCounterpart() != null
                                    && replacedMessage
                                            .getTrueCounterpart()
                                            .asBareJid()
                                            .equals(message.getTrueCounterpart().asBareJid());
                    final boolean occupantIdMatch =
                            replacedMessage.getOccupantId() != null
                                    && replacedMessage
                                            .getOccupantId()
                                            .equals(message.getOccupantId());
                    final boolean duplicate = conversation.hasDuplicateMessage(message);
                    if (fingerprintsMatch
                            && (trueCountersMatch || occupantIdMatch || !conversationMultiMode)
                            && !duplicate) {
                        synchronized (replacedMessage) {
                            final String uuid = replacedMessage.getUuid();
                            replacedMessage.setUuid(UUID.randomUUID().toString());
                            replacedMessage.setBody(message.getBody());
                            // we store the IDs of the replacing message. This is essentially unused
                            // today (only the fact that there are _some_ edits causes the edit icon
                            // to appear)
                            replacedMessage.putEdited(
                                    message.getRemoteMsgId(), message.getServerMsgId());

                            // we used to call
                            // `replacedMessage.setServerMsgId(message.getServerMsgId());` so during
                            // catchup we could start from the edit; not the original message
                            // however this caused problems for things like reactions that refer to
                            // the serverMsgId

                            replacedMessage.setEncryption(message.getEncryption());
                            if (replacedMessage.getStatus() == Message.STATUS_RECEIVED) {
                                replacedMessage.markUnread();
                            }
                            getManager(ChatStateManager.class).process(packet);
                            mXmppConnectionService.updateMessage(replacedMessage, uuid);
                            if (replacedMessage.getStatus() == Message.STATUS_RECEIVED
                                    && (replacedMessage.trusted()
                                            || replacedMessage
                                                    .isPrivateMessage()) // TODO do we really want
                                    // to send receipts for all
                                    // PMs?
                                    && remoteMsgId != null
                                    && !selfAddressed
                                    && !isTypeGroupChat) {
                                getManager(DeliveryReceiptManager.class)
                                        .processRequest(packet, query);
                            }
                            if (replacedMessage.getEncryption() == Message.ENCRYPTION_PGP) {
                                conversation
                                        .getAccount()
                                        .getPgpDecryptionService()
                                        .discard(replacedMessage);
                                conversation
                                        .getAccount()
                                        .getPgpDecryptionService()
                                        .decrypt(replacedMessage, false);
                            }
                        }
                        mXmppConnectionService.getNotificationService().updateNotification();
                        return;
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": received message correction but verification didn't"
                                        + " check out");
                    }
                }
            }

            long deletionDate = mXmppConnectionService.getAutomaticMessageDeletionDate();
            if (deletionDate != 0 && message.getTimeSent() < deletionDate) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": skipping message from "
                                + message.getCounterpart().toString()
                                + " because it was sent prior to our deletion date");
                return;
            }

            boolean checkForDuplicates =
                    (isTypeGroupChat && packet.hasChild("delay", "urn:xmpp:delay"))
                            || message.isPrivateMessage()
                            || message.getServerMsgId() != null
                            || (query == null
                                    && getManager(MessageArchiveManager.class)
                                            .isCatchupInProgress(conversation));
            if (checkForDuplicates) {
                final Message duplicate = conversation.findDuplicateMessage(message);
                if (duplicate != null) {
                    final boolean serverMsgIdUpdated;
                    if (duplicate.getStatus() != Message.STATUS_RECEIVED
                            && duplicate.getUuid().equals(message.getRemoteMsgId())
                            && duplicate.getServerMsgId() == null
                            && message.getServerMsgId() != null) {
                        duplicate.setServerMsgId(message.getServerMsgId());
                        if (mXmppConnectionService.databaseBackend.updateMessage(
                                duplicate, false)) {
                            serverMsgIdUpdated = true;
                        } else {
                            serverMsgIdUpdated = false;
                            Log.e(Config.LOGTAG, "failed to update message");
                        }
                    } else {
                        serverMsgIdUpdated = false;
                    }
                    Log.d(
                            Config.LOGTAG,
                            "skipping duplicate message with "
                                    + message.getCounterpart()
                                    + ". serverMsgIdUpdated="
                                    + serverMsgIdUpdated);
                    return;
                }
            }

            if (query != null
                    && query.getPagingOrder() == MessageArchiveManager.PagingOrder.REVERSE) {
                conversation.prepend(query.getActualInThisQuery(), message);
            } else {
                conversation.add(message);
            }
            if (query != null) {
                query.incrementActualMessageCount();
            }

            if (query == null || query.isCatchup()) { // either no mam or catchup
                if (status == Message.STATUS_SEND || status == Message.STATUS_SEND_RECEIVED) {
                    mXmppConnectionService.markRead(conversation);
                    if (query == null) {
                        getManager(ActivityManager.class)
                                .record(from, ActivityManager.ActivityType.MESSAGE);
                    }
                } else {
                    message.markUnread();
                    notify = true;
                }
            }

            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                notify =
                        conversation
                                .getAccount()
                                .getPgpDecryptionService()
                                .decrypt(message, notify);
            } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE
                    || message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                notify = false;
            }

            if (query == null) {
                getManager(ChatStateManager.class).process(packet);
            }

            if (message.getStatus() == Message.STATUS_RECEIVED
                    && (message.trusted() || message.isPrivateMessage())
                    && remoteMsgId != null
                    && !selfAddressed
                    && !isTypeGroupChat) {
                getManager(DeliveryReceiptManager.class).processRequest(packet, query);
            }

            mXmppConnectionService.databaseBackend.createMessage(message);
            final HttpConnectionManager manager =
                    this.mXmppConnectionService.getHttpConnectionManager();
            final var autoAcceptFileSize =
                    new AppSettings(mXmppConnectionService).getAutoAcceptFileSize();
            if (message.trusted()
                    && message.treatAsDownloadable()
                    && autoAcceptFileSize.isPresent()) {
                manager.createNewDownloadConnection(message);
            } else if (notify) {
                if (query != null && query.isCatchup()) {
                    mXmppConnectionService.getNotificationService().pushFromBacklog(message);
                } else {
                    mXmppConnectionService.getNotificationService().push(message);
                }
            }
            this.mXmppConnectionService.updateConversationUi();
        } else { // no body

            final var conversation = mXmppConnectionService.find(account, counterpart.asBareJid());
            if (axolotlEncrypted != null) {
                final Jid origin;
                if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                    final var user =
                            getManager(MultiUserChatManager.class).getMucUser(packet, query);
                    origin = user == null ? null : user.getRealJid();
                    if (origin == null) {
                        Log.d(
                                Config.LOGTAG,
                                "omemo key transport message in anonymous conference received");
                        return;
                    }
                } else if (isTypeGroupChat) {
                    return;
                } else {
                    origin = from;
                }
                try {
                    final XmppAxolotlMessage xmppAxolotlMessage =
                            XmppAxolotlMessage.fromElement(axolotlEncrypted, origin.asBareJid());
                    account.getAxolotlService()
                            .processReceivingKeyTransportMessage(xmppAxolotlMessage, query != null);
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": omemo key transport message received from "
                                    + origin);
                } catch (Exception e) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": invalid omemo key transport message received "
                                    + e.getMessage());
                    return;
                }
            }

            if (query == null) {
                getManager(ChatStateManager.class).process(packet);
            }

            if (isTypeGroupChat) {
                if (packet.hasChild("subject")
                        && !packet.hasChild("thread")) { // We already know it has no body per above
                    if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                        conversation.setHasMessagesLeftOnServer(conversation.countMessages() > 0);
                        final LocalizedContent subject = packet.getSubject();
                        if (subject != null
                                && getManager(MultiUserChatManager.class)
                                        .getOrCreateState(conversation)
                                        .setSubject(subject.content)) {
                            mXmppConnectionService.updateConversation(conversation);
                        }
                        mXmppConnectionService.updateConversationUi();
                        return;
                    }
                }
            }

            // begin JMI parsing
            if (packet.hasExtension(JingleMessage.class)) {
                getManager(JingleMessageManager.class)
                        .processJingleMessage(
                                packet,
                                counterpart,
                                query,
                                offlineMessagesRetrieved,
                                serverMsgId,
                                timestamp,
                                status);
            }

            if (packet.hasExtension(im.conversations.android.xmpp.model.receipts.Received.class)) {
                getManager(DeliveryReceiptManager.class).processReceived(packet, query);
            }

            if (packet.hasExtension(Displayed.class)) {
                getManager(DisplayedManager.class)
                        .processDisplayed(packet, selfAddressed, counterpart, query);
            }

            if (packet.hasExtension(Reactions.class)) {
                getManager(ReactionManager.class).processReactions(packet, counterpart, query);
            }

            if (original.hasExtension(Retract.class)
                    && originalFrom != null
                    && originalFrom.isBareJid()) {
                getManager(ModerationManager.class).handleRetraction(original);
            }

            // end no body
        }

        if (original.hasExtension(Event.class)) {
            getManager(PubSubManager.class).handleEvent(original);
        }

        final var nick = packet.getExtension(Nick.class);
        if (nick != null && Jid.Invalid.isValid(from)) {
            if (getManager(MultiUserChatManager.class).isMuc(from)) {
                return;
            }
            final Contact contact = account.getRoster().getContact(from);
            if (contact.setPresenceName(nick.getContent())) {
                connection.getManager(RosterManager.class).writeToDatabaseAsync();
                mXmppConnectionService.getAvatarService().clear(contact);
            }
        }
    }

    private static Pair<im.conversations.android.xmpp.model.stanza.Message, Long>
            getForwardedMessagePacket(
                    final im.conversations.android.xmpp.model.stanza.Message original,
                    Class<? extends Extension> clazz) {
        final var extension = original.getExtension(clazz);
        final var forwarded = extension == null ? null : extension.getExtension(Forwarded.class);
        if (forwarded == null) {
            return null;
        }
        final Long timestamp = AbstractParser.parseTimestamp(forwarded, null);
        final var forwardedMessage = forwarded.getMessage();
        if (forwardedMessage == null) {
            return null;
        }
        return new Pair<>(forwardedMessage, timestamp);
    }

    private static Pair<im.conversations.android.xmpp.model.stanza.Message, Long>
            getForwardedMessagePacket(
                    final im.conversations.android.xmpp.model.stanza.Message original,
                    final String name,
                    final String namespace) {
        final Element wrapper = original.findChild(name, namespace);
        final var forwardedElement =
                wrapper == null ? null : wrapper.findChild("forwarded", Namespace.FORWARD);
        if (forwardedElement instanceof Forwarded forwarded) {
            final Long timestamp = AbstractParser.parseTimestamp(forwarded, null);
            final var forwardedMessage = forwarded.getMessage();
            if (forwardedMessage == null) {
                return null;
            }
            return new Pair<>(forwardedMessage, timestamp);
        }
        return null;
    }
}
