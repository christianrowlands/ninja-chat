package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.csi.Active;
import im.conversations.android.xmpp.model.csi.Inactive;

public class ClientStateIndicationManager extends AbstractManager {

    public ClientStateIndicationManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public boolean hasFeature() {
        final var streamFeatures = this.connection.getStreamFeatures();
        return streamFeatures != null && streamFeatures.clientStateIndication();
    }

    public void indicateActive() {
        Log.d(Config.LOGTAG, getAccount().getJid().asBareJid() + " sending csi//active");
        this.connection.send(new Active());
    }

    public void indicateInactive() {
        Log.d(Config.LOGTAG, getAccount().getJid().asBareJid() + " sending csi//inactive");
        this.connection.send(new Inactive());
    }
}
