package eu.siacs.conversations.ui.fragment.settings;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.R;

public class AvailabilitySettingsFragment extends XmppPreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_availability, rootKey);
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        switch (key) {
            case AppSettings.AWAY_WHEN_SCREEN_IS_OFF, AppSettings.MANUALLY_CHANGE_PRESENCE -> {
                requireService().toggleScreenEventReceiver();
                requireService().refreshAllPresences();
            }
            case AppSettings.DND_SYNC_SYSTEM, AppSettings.DND_INCLUDE_SILENT_MODES ->
                    requireService().refreshAllPresences();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_presence_settings);
    }
}
