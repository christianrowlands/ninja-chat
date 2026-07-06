package eu.siacs.conversations.ui;

import android.text.Editable;
import android.text.TextWatcher;
import eu.siacs.conversations.utils.CharSequences;
import java.util.function.Consumer;

public class TextChangeListener implements TextWatcher {

    private final Consumer<String> consumer;

    public TextChangeListener(Consumer<String> callback) {
        this.consumer = callback;
    }

    @Override
    public void afterTextChanged(Editable s) {
        consumer.accept(CharSequences.nullToEmpty(s));
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}
}
