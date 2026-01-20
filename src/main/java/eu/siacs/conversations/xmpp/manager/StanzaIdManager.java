package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.stanza.Message;
import im.conversations.android.xmpp.model.unique.StanzaId;

public class StanzaIdManager extends AbstractManager {

    public StanzaIdManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public boolean hasFeature() {
        return getManager(DiscoManager.class).hasAccountFeature(Namespace.STANZA_IDS);
    }

    public String get(
            final Message packet, final boolean isTypeGroupChat, final Conversation conversation) {
        final Jid by;
        final boolean safeToExtract;
        if (isTypeGroupChat) {
            by = conversation.getAddress().asBareJid();
            final var state = getManager(MultiUserChatManager.class).getState(by);
            safeToExtract = state != null && state.hasFeature(Namespace.STANZA_IDS);
        } else {
            by = getAccount().getJid().asBareJid();
            safeToExtract = hasFeature();
        }
        return safeToExtract ? StanzaId.get(packet, by) : null;
    }

    public String get(final Message packet) {
        final boolean safeToExtract = hasFeature();
        return safeToExtract ? StanzaId.get(packet, getAccount().getJid().asBareJid()) : null;
    }
}
