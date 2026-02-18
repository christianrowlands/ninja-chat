package eu.siacs.conversations.ui.widget;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.EasyOnboardingManager;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class AccountPickerDialog {

    private final XmppActivity xmppActivity;
    private final Function<XmppConnection, Boolean> criteria;

    private AccountPickerDialog(
            final XmppActivity xmppActivity, final Function<XmppConnection, Boolean> criteria) {
        this.xmppActivity = xmppActivity;
        this.criteria = criteria;
    }

    private List<Account> getFilteredAccounts() {
        final var service = this.xmppActivity.xmppConnectionService;
        if (service == null) {
            return Collections.emptyList();
        }
        final var unfiltered = service.getAccounts();
        final var enabled =
                Collections2.filter(unfiltered, a -> Objects.requireNonNull(a).isEnabled());
        return ImmutableList.copyOf(
                Collections2.filter(
                        enabled,
                        a -> {
                            final var c = a != null ? a.getXmppConnection() : null;
                            return c != null && criteria.apply(c);
                        }));
    }

    public boolean hasAnyAccounts() {
        return !this.getFilteredAccounts().isEmpty();
    }

    public void pick(final Consumer<Account> picked) {
        final var accounts = getFilteredAccounts();
        if (accounts.isEmpty()) {
            // TODO show toast
            return;
        }
        if (accounts.size() == 1) {
            picked.accept(Iterables.getOnlyElement(accounts));
            return;
        }
        final var selectedAccount = new AtomicReference<>(accounts.get(0));
        final var alertDialogBuilder = new MaterialAlertDialogBuilder(xmppActivity);
        alertDialogBuilder.setTitle(R.string.choose_account);
        final var asStrings = asStrings(accounts);
        alertDialogBuilder.setSingleChoiceItems(
                asStrings, 0, (dialog, which) -> selectedAccount.set(accounts.get(which)));
        alertDialogBuilder.setNegativeButton(R.string.cancel, null);
        alertDialogBuilder.setPositiveButton(
                R.string.ok, (dialog, which) -> picked.accept(selectedAccount.get()));
        alertDialogBuilder.create().show();
    }

    private static String[] asStrings(final Collection<Account> accounts) {
        return Collections2.transform(accounts, a -> a.getJid().asBareJid().toString())
                .toArray(new String[0]);
    }

    public static class EasyInvite extends AccountPickerDialog {

        public EasyInvite(final XmppActivity xmppActivity) {
            super(
                    xmppActivity,
                    xmppConnection ->
                            xmppConnection.getManager(EasyOnboardingManager.class).hasFeature());
        }
    }

    public static class Enabled extends AccountPickerDialog {
        public Enabled(final XmppActivity xmppActivity) {
            super(xmppActivity, xmppConnection -> true);
        }
    }
}
