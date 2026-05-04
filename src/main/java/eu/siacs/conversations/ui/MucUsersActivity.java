package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMucUsersBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.UserAdapter;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

public class MucUsersActivity extends XmppActivity
        implements XmppConnectionService.OnMucRosterUpdate,
                MenuItem.OnActionExpandListener,
                TextWatcher {

    private UserAdapter userAdapter;

    private Conversation mConversation = null;

    private EditText mSearchEditText;

    private Collection<MucOptions.User> allUsers = Collections.emptyList();

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        final Intent intent = getIntent();
        final String uuid = intent == null ? null : intent.getStringExtra("uuid");
        if (uuid != null) {
            mConversation = xmppConnectionService.findConversationByUuid(uuid);
        }
        loadAndSubmitUsers();
    }

    private void loadAndSubmitUsers() {
        if (mConversation != null) {
            allUsers = mConversation.getMucOptions().getUsers();
            submitFilteredList(
                    mSearchEditText != null ? mSearchEditText.getText().toString() : null);
        }
    }

    private void submitFilteredList(final String search) {
        if (Strings.isNullOrEmpty(search)) {
            userAdapter.submitList(Ordering.natural().immutableSortedCopy(allUsers));
        } else {
            final String needle = search.toLowerCase(Locale.getDefault());
            userAdapter.submitList(
                    Ordering.natural()
                            .immutableSortedCopy(
                                    Collections2.filter(
                                            this.allUsers,
                                            user ->
                                                    contains(
                                                            Objects.requireNonNull(user),
                                                            needle))));
        }
    }

    private static boolean contains(final MucOptions.User user, final String needle) {
        final String name = user.getDisplayName();
        final var resource = user.resource();
        final var realAddress = user.getRealJid();
        final var hats =
                Collections2.transform(
                        user.getHats(),
                        h -> Objects.requireNonNull(h).title().toLowerCase(Locale.getDefault()));
        return name.toLowerCase(Locale.getDefault()).contains(needle)
                || Strings.nullToEmpty(resource).toLowerCase(Locale.getDefault()).contains(needle)
                || (realAddress != null && realAddress.toString().contains(needle))
                || Iterables.any(hats, h -> Objects.requireNonNull(h).contains(needle));
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (!MucDetailsContextMenuHelper.onContextItemSelected(
                item, userAdapter.getSelectedUser(), this)) {
            return super.onContextItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivityMucUsersBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_muc_users);
        setSupportActionBar(binding.toolbar);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        configureActionBar(getSupportActionBar(), true);
        this.userAdapter = new UserAdapter();
        binding.list.setAdapter(this.userAdapter);
    }

    @Override
    public void onMucRosterUpdate() {
        loadAndSubmitUsers();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.muc_users_activity, menu);
        final MenuItem menuSearchView = menu.findItem(R.id.action_search);
        final View mSearchView = menuSearchView.getActionView();
        mSearchEditText = mSearchView.findViewById(R.id.search_field);
        mSearchEditText.addTextChangedListener(this);
        mSearchEditText.setHint(R.string.search_participants);
        menuSearchView.setOnActionExpandListener(this);
        return true;
    }

    @Override
    public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
        mSearchEditText.post(
                () -> {
                    mSearchEditText.requestFocus();
                    final InputMethodManager imm =
                            (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT);
                });
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(
                mSearchEditText.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        mSearchEditText.setText("");
        submitFilteredList("");
        return true;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        submitFilteredList(s.toString());
    }
}
