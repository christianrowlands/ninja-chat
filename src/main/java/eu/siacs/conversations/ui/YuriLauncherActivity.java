package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import de.gultsch.common.MiniUri;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.XmppUriLauncher;

public class YuriLauncherActivity extends AppCompatActivity {

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final var intent = getIntent();
        final var data = intent == null ? null : intent.getData();
        if (data == null) {
            finish();
            return;
        }
        final MiniUri uri;
        try {
            uri = MiniUri.tryInternalParse(data.toString());
        } catch (final IllegalArgumentException e) {
            Log.d(Config.LOGTAG, "could not parse mini uri", e);
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final MiniUri.Xmpp xmpp;
        if (uri instanceof MiniUri.Xmpp x) {
            xmpp = x;
        } else if (uri instanceof MiniUri.Transformable t
                && t.transform() instanceof MiniUri.Xmpp x) {
            xmpp = x;
        } else {
            Log.d(Config.LOGTAG, "mini uri is of unknown type: " + uri.getClass().getSimpleName());
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final var launcher = new XmppUriLauncher(this);
        launcher.launch(xmpp);
        finish();
    }
}
