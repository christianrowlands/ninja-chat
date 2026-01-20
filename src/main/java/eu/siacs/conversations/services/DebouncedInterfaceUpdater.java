package eu.siacs.conversations.services;

import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicLong;

public class DebouncedInterfaceUpdater implements Runnable {

    private static final int UI_REFRESH_THRESHOLD = 250;
    private static final AtomicLong LAST_UI_UPDATE_CALL = new AtomicLong(0);

    private final XmppConnectionService service;

    public DebouncedInterfaceUpdater(final XmppConnectionService service) {
        this.service = service;
    }

    @Override
    public void run() {
        synchronized (LAST_UI_UPDATE_CALL) {
            if (SystemClock.elapsedRealtime() - LAST_UI_UPDATE_CALL.get() >= UI_REFRESH_THRESHOLD) {
                LAST_UI_UPDATE_CALL.set(SystemClock.elapsedRealtime());
                this.service.updateConversationUi();
            }
        }
    }
}
