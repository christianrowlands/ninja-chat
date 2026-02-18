package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.DialogMucUserBanBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.ui.ConferenceDetailsActivity;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.MucUsersActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.ModerationManager;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.Role;
import java.util.Collections;
import java.util.Set;

public final class MucDetailsContextMenuHelper {

    public static void onCreateContextMenu(ContextMenu menu, View v) {
        final XmppActivity activity = XmppActivity.find(v);
        final Object tag = v.getTag();
        if (tag instanceof User user && activity != null) {
            activity.getMenuInflater().inflate(R.menu.muc_details_context, menu);
            menu.setHeaderTitle(user.getDisplayName());
            MucDetailsContextMenuHelper.configureMucDetailsContextMenu(
                    activity, menu, user.getConversation(), user);
        }
    }

    public static void configureMucDetailsContextMenu(
            Activity activity, Menu menu, Conversation conversation, User user) {
        final MucOptions mucOptions = conversation.getMucOptions();
        final boolean advancedMode =
                PreferenceManager.getDefaultSharedPreferences(activity)
                        .getBoolean("advanced_muc_mode", false);
        final boolean isGroupChat = mucOptions.isPrivateAndNonAnonymous();
        MenuItem sendPrivateMessage = menu.findItem(R.id.send_private_message);
        if (user != null && user.getRealJid() != null) {
            MenuItem showContactDetails = menu.findItem(R.id.action_contact_details);
            MenuItem startConversation = menu.findItem(R.id.start_conversation);
            MenuItem giveMembership = menu.findItem(R.id.give_membership);
            MenuItem removeMembership = menu.findItem(R.id.remove_membership);
            MenuItem giveAdminPrivileges = menu.findItem(R.id.give_admin_privileges);
            MenuItem giveOwnerPrivileges = menu.findItem(R.id.give_owner_privileges);
            MenuItem removeOwnerPrivileges = menu.findItem(R.id.revoke_owner_privileges);
            MenuItem removeAdminPrivileges = menu.findItem(R.id.remove_admin_privileges);
            MenuItem removeFromRoom = menu.findItem(R.id.remove_from_room);
            MenuItem managePermissions = menu.findItem(R.id.manage_permissions);
            removeFromRoom.setTitle(
                    isGroupChat ? R.string.remove_from_room : R.string.remove_from_channel);
            MenuItem invite = menu.findItem(R.id.invite);
            final User self = conversation.getMucOptions().getSelf();
            if (user.realJidMatchesAccount()) {
                showContactDetails.setVisible(true);
                showContactDetails.setTitle(R.string.account_details);
            } else {
                showContactDetails.setVisible(true);
                startConversation.setVisible(true);
                showContactDetails.setTitle(R.string.action_contact_details);
            }
            if ((activity instanceof ConferenceDetailsActivity
                            || activity instanceof MucUsersActivity)
                    && user.getRole() == Role.NONE) {
                invite.setVisible(true);
            }
            boolean managePermissionsVisible = false;
            if ((self.ranks(Affiliation.ADMIN) && self.outranks(user.getAffiliation()))
                    || self.getAffiliation() == Affiliation.OWNER) {
                if (advancedMode) {
                    if (!user.ranks(Affiliation.MEMBER)) {
                        managePermissionsVisible = true;
                        giveMembership.setVisible(true);
                    } else if (user.getAffiliation() == Affiliation.MEMBER) {
                        managePermissionsVisible = true;
                        removeMembership.setVisible(true);
                    }
                }
                removeFromRoom.setVisible(true);
            }
            if (self.ranks(Affiliation.OWNER)) {
                if (isGroupChat || advancedMode || user.getAffiliation() == Affiliation.OWNER) {
                    if (!user.ranks(Affiliation.OWNER)) {
                        managePermissionsVisible = true;
                        giveOwnerPrivileges.setVisible(true);
                    } else if (user.getAffiliation() == Affiliation.OWNER) {
                        managePermissionsVisible = true;
                        removeOwnerPrivileges.setVisible(true);
                    }
                }
                if (!isGroupChat || advancedMode || user.getAffiliation() == Affiliation.ADMIN) {
                    if (!user.ranks(Affiliation.ADMIN)) {
                        managePermissionsVisible = true;
                        giveAdminPrivileges.setVisible(true);
                    } else if (user.getAffiliation() == Affiliation.ADMIN) {
                        managePermissionsVisible = true;
                        removeAdminPrivileges.setVisible(true);
                    }
                }
            }
            managePermissions.setVisible(managePermissionsVisible);
            sendPrivateMessage.setVisible(
                    !isGroupChat && mucOptions.allowPm() && user.ranks(Role.VISITOR));
        } else {
            sendPrivateMessage.setVisible(true);
            sendPrivateMessage.setEnabled(
                    user != null && mucOptions.allowPm() && user.ranks(Role.VISITOR));
        }
    }

    public static boolean onContextItemSelected(MenuItem item, User user, XmppActivity activity) {
        Log.d(Config.LOGTAG, "occupant id of " + user.getFullJid() + ": " + user.getOccupantId());
        return onContextItemSelected(item, user, activity, null);
    }

    public static boolean onContextItemSelected(
            final MenuItem item, User user, final XmppActivity activity, final String fingerprint) {
        final Conversation conversation = user.getConversation();
        Jid jid = user.getRealJid();
        switch (item.getItemId()) {
            case R.id.action_contact_details:
                final Jid realJid = user.getRealJid();
                final Account account = conversation.getAccount();
                final Contact contact =
                        realJid == null ? null : account.getRoster().getContact(realJid);
                if (contact != null) {
                    if (contact.isSelf()) {
                        activity.switchToAccount(account);
                    } else {
                        activity.switchToContactDetails(contact, fingerprint);
                    }
                }
                return true;
            case R.id.start_conversation:
                startConversation(user, activity);
                return true;
            case R.id.give_admin_privileges:
                changeAffiliationInConference(activity, conversation, jid, Affiliation.ADMIN);
                return true;
            case R.id.give_membership:
            case R.id.remove_admin_privileges:
            case R.id.revoke_owner_privileges:
                changeAffiliationInConference(activity, conversation, jid, Affiliation.MEMBER);
                return true;
            case R.id.give_owner_privileges:
                changeAffiliationInConference(activity, conversation, jid, Affiliation.OWNER);
                return true;
            case R.id.remove_membership:
                changeAffiliationInConference(activity, conversation, jid, Affiliation.NONE);
                return true;
            case R.id.remove_from_room:
                removeFromRoom(user, activity);
                return true;
            case R.id.send_private_message:
                if (activity instanceof ConversationsActivity) {
                    ConversationFragment conversationFragment = ConversationFragment.get(activity);
                    if (conversationFragment != null) {
                        conversationFragment.privateMessageWith(user.getFullJid());
                        return true;
                    }
                }
                activity.privateMsgInMuc(conversation, user.resource());
                return true;
            case R.id.invite:
                // TODO use direct invites for public conferences
                if (user.ranks(Affiliation.MEMBER)) {
                    activity.xmppConnectionService.directInvite(conversation, jid.asBareJid());
                } else {
                    activity.xmppConnectionService.invite(conversation, jid);
                }
                return true;
            default:
                return false;
        }
    }

    private static void changeAffiliationInConference(
            final XmppActivity activity,
            final Conversation conversation,
            final Jid user,
            final Affiliation affiliation) {
        final var account = conversation.getAccount();
        final var future =
                account.getXmppConnection()
                        .getManager(MultiUserChatManager.class)
                        .setAffiliation(conversation, affiliation, user);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        activity.refreshUi();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not change affiliation", t);
                        Toast.makeText(
                                        activity,
                                        R.string.could_not_change_affiliation,
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                },
                ContextCompat.getMainExecutor(activity));
    }

    private static void removeFromRoom(final User user, final XmppActivity activity) {
        final var mucOptions = user.getMucOptions();
        final Conversation conversation = mucOptions.getConversation();
        final var messages =
                conversation.findMessagesWithOccupantIdOrRealJid(
                        user.getRealJid(), user.getOccupantId());
        final boolean isGroupChat = mucOptions.isPrivateAndNonAnonymous();
        final DialogMucUserBanBinding binding =
                DataBindingUtil.inflate(
                        activity.getLayoutInflater(), R.layout.dialog_muc_user_ban, null, false);
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(isGroupChat ? R.string.remove_from_room : R.string.remove_from_channel);
        if (!mucOptions.isPrivateAndNonAnonymous()
                && mucOptions.moderation()
                && !messages.isEmpty()) {
            binding.deleteMessage.setText(
                    activity.getResources()
                            .getQuantityString(
                                    R.plurals.delete_n_messages, messages.size(), messages.size()));
        } else {
            binding.deleteMessage.setVisibility(View.GONE);
        }
        binding.jid.setText(user.getRealJid().asBareJid().toString());
        builder.setView(binding.getRoot());
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(
                R.string.continue_btn,
                (dialog, which) -> {
                    if (!mucOptions.isPrivateAndNonAnonymous()
                            && mucOptions.moderation()
                            && binding.deleteMessage.isChecked()) {
                        banUserDeleteMessages(activity, user, messages);
                    } else {
                        banUserDeleteMessages(activity, user, Collections.emptySet());
                    }
                });
        builder.create().show();
    }

    private static void banUserDeleteMessages(
            final XmppActivity activity, final User user, final Set<Message> messages) {
        final var account = user.getAccount();
        final var conversation = user.getConversation();
        final var future =
                account.getXmppConnection()
                        .getManager(MultiUserChatManager.class)
                        .setAffiliation(conversation, Affiliation.OUTCAST, user.getRealJid());
        account.getXmppConnection()
                .getManager(MultiUserChatManager.class)
                .setRole(conversation.getAddress().asBareJid(), Role.NONE, user.resource());
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(Config.LOGTAG, "affiliation change success");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.d(Config.LOGTAG, "could not change affiliation", t);
                        Toast.makeText(
                                        activity,
                                        R.string.could_not_change_affiliation,
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                },
                ContextCompat.getMainExecutor(activity));
        for (final var message : messages) {
            account.getXmppConnection().getManager(ModerationManager.class).moderate(message);
        }
    }

    private static void startConversation(final User user, final XmppActivity activity) {
        if (user.getRealJid() != null) {
            Conversation newConversation =
                    activity.xmppConnectionService.findOrCreateConversation(
                            user.getAccount(), user.getRealJid().asBareJid(), false, true);
            activity.switchToConversation(newConversation);
        }
    }
}
