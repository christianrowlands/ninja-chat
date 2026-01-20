package eu.siacs.conversations.generator;

import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.correction.Replace;
import im.conversations.android.xmpp.model.hints.Store;
import im.conversations.android.xmpp.model.markers.Markable;
import im.conversations.android.xmpp.model.unique.OriginId;

public class MessageGenerator extends AbstractGenerator {
    private static final String OMEMO_FALLBACK_MESSAGE =
            "I sent you an OMEMO encrypted message but your client doesn’t seem to support that."
                    + " Find more information on https://conversations.im/omemo";
    private static final String PGP_FALLBACK_MESSAGE =
            "I sent you a PGP encrypted message but your client doesn’t seem to support that.";

    public MessageGenerator(XmppConnectionService service) {
        super(service);
    }

    private im.conversations.android.xmpp.model.stanza.Message preparePacket(
            final Message message) {
        Conversation conversation = (Conversation) message.getConversation();
        Account account = conversation.getAccount();
        im.conversations.android.xmpp.model.stanza.Message packet =
                new im.conversations.android.xmpp.model.stanza.Message();
        final boolean isWithSelf = conversation.getContact().isSelf();
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            packet.setTo(message.getCounterpart());
            packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.CHAT);
            if (!isWithSelf) {
                packet.addChild("request", "urn:xmpp:receipts");
            }
        } else if (message.isPrivateMessage()) {
            packet.setTo(message.getCounterpart());
            packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.CHAT);
            packet.addChild("x", "http://jabber.org/protocol/muc#user");
            packet.addChild("request", "urn:xmpp:receipts");
        } else {
            packet.setTo(message.getCounterpart().asBareJid());
            packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT);
        }
        if (conversation.isSingleOrPrivateAndNonAnonymous() && !message.isPrivateMessage()) {
            packet.addExtension(new Markable());
        }
        packet.setFrom(account.getJid());
        packet.setId(message.getUuid());
        if (conversation.getMode() == Conversational.MODE_MULTI
                && !message.isPrivateMessage()
                && !conversation.getMucOptions().stableId()) {
            packet.addExtension(new OriginId(message.getUuid()));
        }
        if (message.edited()) {
            packet.addExtension(new Replace(message.getEditedIdWireFormat()));
        }
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Message generateAxolotlChat(
            Message message, XmppAxolotlMessage axolotlMessage) {
        im.conversations.android.xmpp.model.stanza.Message packet = preparePacket(message);
        if (axolotlMessage == null) {
            return null;
        }
        packet.setAxolotlMessage(axolotlMessage.toElement());
        packet.setBody(OMEMO_FALLBACK_MESSAGE);
        packet.addExtension(new Store());
        packet.addChild("encryption", "urn:xmpp:eme:0")
                .setAttribute("name", "OMEMO")
                .setAttribute("namespace", AxolotlService.PEP_PREFIX);
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Message generateKeyTransportMessage(
            Jid to, XmppAxolotlMessage axolotlMessage) {
        im.conversations.android.xmpp.model.stanza.Message packet =
                new im.conversations.android.xmpp.model.stanza.Message();
        packet.setType(im.conversations.android.xmpp.model.stanza.Message.Type.CHAT);
        packet.setTo(to);
        packet.setAxolotlMessage(axolotlMessage.toElement());
        packet.addChild(new Store());
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Message generateChat(Message message) {
        im.conversations.android.xmpp.model.stanza.Message packet = preparePacket(message);
        String content;
        if (message.hasFileOnRemoteHost()) {
            final Message.FileParams fileParams = message.getFileParams();
            content = fileParams.url;
            packet.addChild("x", Namespace.OOB).addChild("url").setContent(content);
        } else {
            content = message.getBody();
        }
        packet.setBody(content);
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Message generatePgpChat(Message message) {
        final im.conversations.android.xmpp.model.stanza.Message packet = preparePacket(message);
        if (message.hasFileOnRemoteHost()) {
            Message.FileParams fileParams = message.getFileParams();
            final String url = fileParams.url;
            packet.setBody(url);
            packet.addChild("x", Namespace.OOB).addChild("url").setContent(url);
        } else {
            packet.setBody(PGP_FALLBACK_MESSAGE);
            if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                packet.addChild("x", "jabber:x:encrypted").setContent(message.getEncryptedBody());
            } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                packet.addChild("x", "jabber:x:encrypted").setContent(message.getBody());
            }
            packet.addChild("encryption", "urn:xmpp:eme:0")
                    .setAttribute("namespace", "jabber:x:encrypted");
        }
        return packet;
    }
}
