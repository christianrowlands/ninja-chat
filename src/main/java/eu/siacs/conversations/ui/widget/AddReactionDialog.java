package eu.siacs.conversations.ui.widget;

import android.app.Activity;
import android.content.Intent;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.DialogAddReactionBinding;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Reaction;
import eu.siacs.conversations.ui.AddReactionActivity;
import eu.siacs.conversations.utils.CharSequences;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import im.conversations.android.xmpp.model.reactions.Restrictions;
import java.util.Collection;
import java.util.function.Consumer;
import net.fellbaum.jemoji.Emoji;
import net.fellbaum.jemoji.EmojiManager;

public class AddReactionDialog {

    private final Message message;
    private final Consumer<Collection<String>> callback;

    private static final InputFilter EMOJI_INPUT_FILTER =
            (source, start, end, dest, dstart, dend) -> {
                final var emojis = EmojiManager.extractEmojisInOrder(source.toString());
                return Joiner.on("").join(Lists.transform(emojis, Emoji::getEmoji));
            };

    public AddReactionDialog(final Message message, Consumer<Collection<String>> callback) {
        this.message = message;
        this.callback = callback;
    }

    public AlertDialog create(final Activity activity) {
        final var account = message.getConversation().getAccount();
        final var conversation = message.getConversation();
        final Restrictions restrictions;
        if (conversation.getMode() == Conversational.MODE_SINGLE) {
            restrictions = null;
        } else {
            final var mucOptions =
                    account.getXmppConnection()
                            .getManager(MultiUserChatManager.class)
                            .getState(conversation.getAddress().asBareJid());
            restrictions = mucOptions == null ? null : mucOptions.getReactionsRestrictions();
        }
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        final var layoutInflater = activity.getLayoutInflater();
        final DialogAddReactionBinding viewBinding =
                DataBindingUtil.inflate(layoutInflater, R.layout.dialog_add_reaction, null, false);
        builder.setView(viewBinding.getRoot());
        viewBinding.emojiTextInput.setFilters(new InputFilter[] {EMOJI_INPUT_FILTER});
        final var dialog = builder.create();
        final boolean emojiChoiceRestricted =
                restrictions != null
                        && restrictions.allowList() != null
                        && !restrictions.allowList().isEmpty()
                        && restrictions.allowList().size() <= 6;
        final Collection<String> shortcutEmojis;
        if (emojiChoiceRestricted) {
            shortcutEmojis = restrictions.allowList();
        } else {
            shortcutEmojis = Reaction.SUGGESTIONS;
        }
        for (final String emoji : shortcutEmojis) {
            final Button button =
                    (Button)
                            layoutInflater.inflate(
                                    R.layout.item_emoji_button, viewBinding.emojis, false);
            viewBinding.emojis.addView(button);
            button.setText(emoji);
            button.setOnClickListener(new EmojiSubmitter(dialog, emoji));
        }
        viewBinding.emojiTextInput.setOnEditorActionListener(new EditorActionListener(dialog));
        if (emojiChoiceRestricted) {
            viewBinding.more.setVisibility(View.GONE);
            viewBinding.keyboard.setVisibility(View.GONE);
        } else {
            viewBinding.more.setVisibility(View.VISIBLE);
            viewBinding.keyboard.setOnClickListener(
                    new KeyboardButtonListener(dialog, viewBinding));
            viewBinding.more.setOnClickListener(new ActivityLauncher(dialog, message));
        }
        viewBinding.emojiTextInput.addTextChangedListener(new EmojiTextWatcher(viewBinding));
        return dialog;
    }

    private final class EditorActionListener extends TextInputSubmitter
            implements TextView.OnEditorActionListener {

        private EditorActionListener(AlertDialog dialog) {
            super(dialog);
        }

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitTextInputEmoji(v.getEditableText());
                return true;
            }
            return false;
        }
    }

    private static class EmojiTextWatcher implements TextWatcher {

        private final DialogAddReactionBinding viewBinding;

        private EmojiTextWatcher(final DialogAddReactionBinding viewBinding) {
            this.viewBinding = viewBinding;
        }

        @Override
        public void afterTextChanged(final Editable s) {
            if (this.viewBinding.emojiInputLayout.getVisibility() != View.VISIBLE) {
                return;
            }
            if (CharSequences.isEmpty(s)) {
                this.viewBinding.keyboard.setIconResource(R.drawable.ic_close_24dp);
            } else {
                this.viewBinding.keyboard.setIconResource(R.drawable.ic_send_24dp);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private class EmojiSubmitter implements View.OnClickListener {

        private final AlertDialog dialog;
        private final String emoji;

        public EmojiSubmitter(final AlertDialog dialog, final String emoji) {
            this.dialog = dialog;
            this.emoji = emoji;
        }

        @Override
        public void onClick(View v) {
            final var aggregated = message.getAggregatedReactions();
            if (aggregated.ourReactions.contains(emoji)) {
                callback.accept(aggregated.ourReactions);
            } else {
                final ImmutableSet.Builder<String> reactionBuilder = new ImmutableSet.Builder<>();
                reactionBuilder.addAll(aggregated.ourReactions);
                reactionBuilder.add(emoji);
                callback.accept(reactionBuilder.build());
            }
            this.dialog.dismiss();
            ;
        }
    }

    private abstract class TextInputSubmitter {
        private final AlertDialog dialog;

        private TextInputSubmitter(AlertDialog dialog) {
            this.dialog = dialog;
        }

        protected void submitTextInputEmoji(final Editable text) {
            final var emojis = EmojiManager.extractEmojis(CharSequences.nullToEmpty(text));
            final var aggregated = message.getAggregatedReactions();
            final ImmutableSet.Builder<String> reactionBuilder = new ImmutableSet.Builder<>();
            reactionBuilder.addAll(aggregated.ourReactions);
            reactionBuilder.addAll(Collections2.transform(emojis, Emoji::getEmoji));
            callback.accept(reactionBuilder.build());
            if (text != null) {
                text.clear();
            }
            dialog.dismiss();
        }
    }

    private static class ActivityLauncher implements View.OnClickListener {

        private final AlertDialog dialog;
        private final Message message;

        public ActivityLauncher(final AlertDialog alertDialog, final Message message) {
            this.dialog = alertDialog;
            this.message = message;
        }

        @Override
        public void onClick(final View v) {
            final var context = v.getContext();
            dialog.dismiss();
            final var intent = new Intent(context, AddReactionActivity.class);
            intent.putExtra("conversation", message.getConversation().getUuid());
            intent.putExtra("message", message.getUuid());
            context.startActivity(intent);
        }
    }

    private class KeyboardButtonListener extends TextInputSubmitter
            implements View.OnClickListener {

        private final DialogAddReactionBinding viewBinding;

        public KeyboardButtonListener(
                final AlertDialog dialog, final DialogAddReactionBinding binding) {
            super(dialog);
            this.viewBinding = binding;
        }

        @Override
        public void onClick(final View v) {
            if (viewBinding.emojis.getVisibility() == View.VISIBLE) {
                viewBinding.emojis.setVisibility(View.GONE);
                viewBinding.emojiInputLayout.setVisibility(View.VISIBLE);
                viewBinding.emojiTextInput.post(viewBinding.emojiTextInput::requestFocus);
                if (CharSequences.isEmpty(viewBinding.emojiTextInput.getEditableText())) {
                    this.viewBinding.keyboard.setIconResource(R.drawable.ic_close_24dp);
                } else {
                    this.viewBinding.keyboard.setIconResource(R.drawable.ic_send_24dp);
                }
            } else {
                final var text = viewBinding.emojiTextInput.getText();
                if (CharSequences.isEmpty(text)) {
                    viewBinding.emojis.setVisibility(View.VISIBLE);
                    viewBinding.emojiInputLayout.setVisibility(View.GONE);
                    viewBinding.keyboard.setIconResource(R.drawable.ic_keyboard_24dp);
                } else {
                    submitTextInputEmoji(viewBinding.emojiTextInput.getText());
                }
            }
        }
    }
}
