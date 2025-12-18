package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;

public class UnifiedPushDistributor extends AppCompatActivity {

    private static final String DUMMY_APP = "org.unifiedpush.dummy_app";
    private static final String EXTRA_PENDING_INTENT = "pi";

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final var intent = getIntent();
        final var callingPackage = getCallingPackage();
        final var result = new Intent();
        if (intent == null || Strings.isNullOrEmpty(callingPackage)) {
            setResult(RESULT_CANCELED, result);
            finish();
            ;
        }
        Log.d(Config.LOGTAG, "a package (" + callingPackage + ") called our link activity");
        final var pendingIntent =
                PendingIntent.getBroadcast(
                        this, 0, new Intent(DUMMY_APP), PendingIntent.FLAG_IMMUTABLE);
        result.putExtra(EXTRA_PENDING_INTENT, pendingIntent);
        setResult(RESULT_OK, result);
        finish();
    }
}
