package im.conversations.android.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public record AttachmentChoice(
        @DrawableRes int icon, @StringRes int name, Type type, boolean quickAction) {

    public enum Type {
        RECORDING,
        LOCATION,
        CAMERA,
        PICTURE,
        FILE,
        VIDEO,
        CONTACT
    }
}
