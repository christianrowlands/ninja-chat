package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.app.Application;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import eu.siacs.conversations.AppSettings;

public class SettingsUtils {
    public static void applyScreenshotSetting(final Activity activity) {
        final var appSettings = new AppSettings(activity);
        final Window activityWindow = activity.getWindow();
        if (appSettings.isAllowScreenshots()) {
            activityWindow.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            activityWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    public static void applyThemeSettings(final Application application) {
        AppCompatDelegate.setDefaultNightMode(new AppSettings(application).getDesiredNightMode());
        var dynamicColorsOptions =
                new DynamicColorsOptions.Builder()
                        .setPrecondition(
                                (activity, t) -> new AppSettings(activity).isDynamicColorsDesired())
                        .build();
        DynamicColors.applyToActivitiesIfAvailable(application, dynamicColorsOptions);
    }
}
