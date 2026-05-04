package eu.siacs.conversations.services;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Longs;
import dev.paseto.jpaseto.Pasetos;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.android.PhoneNumberContact;
import eu.siacs.conversations.crypto.sasl.Plain;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Entry;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.NetworkManager;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import eu.siacs.conversations.utils.QuicksyAuthentication;
import eu.siacs.conversations.utils.SerialSingleThreadExecutor;
import eu.siacs.conversations.utils.SmsRetrieverWrapper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import im.conversations.android.xmpp.model.stanza.Iq;
import io.michaelrocks.libphonenumber.android.Phonenumber;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class QuickConversationsService extends AbstractQuickConversationsService {

    public static final int API_ERROR_OTHER = -1;
    public static final int API_ERROR_UNKNOWN_HOST = -2;
    public static final int API_ERROR_CONNECT = -3;
    public static final int API_ERROR_SSL_HANDSHAKE = -4;
    public static final int API_ERROR_AIRPLANE_MODE = -5;
    public static final int API_ERROR_SSL_CERTIFICATE = -6;
    public static final int API_ERROR_SSL_GENERAL = -7;
    public static final int API_ERROR_TIMEOUT = -8;

    private static final String API_DOMAIN = "api." + Config.QUICKSY_DOMAIN;

    private static final HttpUrl BASE_URL =
            new HttpUrl.Builder().host(API_DOMAIN).scheme("https").build();

    private final Set<OnVerificationRequested> mOnVerificationRequested =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<OnVerification> mOnVerification =
            Collections.newSetFromMap(new WeakHashMap<>());

    private final AtomicBoolean mVerificationInProgress = new AtomicBoolean(false);
    private final AtomicBoolean mVerificationRequestInProgress = new AtomicBoolean(false);
    private final AtomicInteger mRunningSyncJobs = new AtomicInteger(0);
    private CountDownLatch awaitingAccountStateChange;

    private Attempt mLastSyncAttempt = Attempt.NULL;

    private final SerialSingleThreadExecutor mSerialSingleThreadExecutor =
            new SerialSingleThreadExecutor(QuickConversationsService.class.getSimpleName());

    QuickConversationsService(XmppConnectionService xmppConnectionService) {
        super(xmppConnectionService);
    }

    private static long retryAfter(final Response response) {
        final var retryAfter = response.header("Retry-After");
        if (Strings.isNullOrEmpty(retryAfter)) {
            return 0;
        }
        final var retryAfterLong = Longs.tryParse(retryAfter);
        if (retryAfterLong == null) {
            return 0;
        }
        return SystemClock.elapsedRealtime() + (retryAfterLong * 1000L);
    }

    public void addOnVerificationRequestedListener(
            OnVerificationRequested onVerificationRequested) {
        synchronized (mOnVerificationRequested) {
            mOnVerificationRequested.add(onVerificationRequested);
        }
    }

    public void removeOnVerificationRequestedListener(
            OnVerificationRequested onVerificationRequested) {
        synchronized (mOnVerificationRequested) {
            mOnVerificationRequested.remove(onVerificationRequested);
        }
    }

    public void addOnVerificationListener(OnVerification onVerification) {
        synchronized (mOnVerification) {
            mOnVerification.add(onVerification);
        }
    }

    public void removeOnVerificationListener(OnVerification onVerification) {
        synchronized (mOnVerification) {
            mOnVerification.remove(onVerification);
        }
    }

    public void requestVerification(final Phonenumber.PhoneNumber phoneNumber) {
        final String e164 = PhoneNumberUtilWrapper.normalize(service, phoneNumber);
        if (mVerificationRequestInProgress.compareAndSet(false, true)) {
            SmsRetrieverWrapper.start(service);
            final var okHttp = HttpConnectionManager.okHttpClient(service);
            final var requestBuilder =
                    new Request.Builder()
                            .url(
                                    BASE_URL.newBuilder()
                                            .addPathSegment("authentication")
                                            .addPathSegment(e164)
                                            .build())
                            .addHeader("Installation-Id", getInstallationId())
                            .addHeader("Accept-Language", Locale.getDefault().getLanguage())
                            .get();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && QuicksyAuthentication.hasSharedSecret()) {
                final String token =
                        Pasetos.V2
                                .LOCAL
                                .builder()
                                .setSharedSecret(QuicksyAuthentication.getSharedSecret())
                                .setSubject(e164)
                                .claim("Installation-Id", getInstallationId())
                                .claim("User-Agent", HttpConnectionManager.getUserAgent())
                                .claim(
                                        "Device",
                                        String.format("%s %s", Build.MANUFACTURER, Build.MODEL))
                                .compact();
                requestBuilder.addHeader("Authorization", token);
            }
            okHttp.newCall(requestBuilder.build())
                    .enqueue(
                            new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    final int code = getApiErrorCode(e);
                                    synchronized (mOnVerificationRequested) {
                                        for (OnVerificationRequested onVerificationRequested :
                                                mOnVerificationRequested) {
                                            onVerificationRequested.onVerificationRequestFailed(
                                                    code);
                                        }
                                    }
                                    mVerificationRequestInProgress.set(false);
                                }

                                @Override
                                public void onResponse(
                                        @NonNull Call call, @NonNull Response response) {
                                    final var code = response.code();
                                    if (code == 200) {
                                        createAccountAndWait(phoneNumber, 0L);
                                    } else if (code == 429) {
                                        createAccountAndWait(phoneNumber, retryAfter(response));
                                    } else {
                                        synchronized (mOnVerificationRequested) {
                                            for (OnVerificationRequested onVerificationRequested :
                                                    mOnVerificationRequested) {
                                                onVerificationRequested.onVerificationRequestFailed(
                                                        code);
                                            }
                                        }
                                    }
                                    mVerificationRequestInProgress.set(false);
                                }
                            });
        }
    }

    public void signalAccountStateChange() {
        if (awaitingAccountStateChange != null && awaitingAccountStateChange.getCount() > 0) {
            Log.d(Config.LOGTAG, "signaled state change");
            awaitingAccountStateChange.countDown();
        }
    }

    private void createAccountAndWait(
            final Phonenumber.PhoneNumber phoneNumber, final long timestamp) {
        String local = PhoneNumberUtilWrapper.normalize(service, phoneNumber);
        Log.d(
                Config.LOGTAG,
                "requesting verification for "
                        + PhoneNumberUtilWrapper.normalize(service, phoneNumber));
        Jid jid = Jid.of(local, Config.QUICKSY_DOMAIN, null);
        Account account = AccountUtils.getFirst(service);
        if (account == null || !account.getJid().asBareJid().equals(jid.asBareJid())) {
            if (account != null) {
                service.deleteAccount(account);
            }
            account = new Account(jid, CryptoHelper.createPassword(new SecureRandom()));
            account.setOption(Account.OPTION_DISABLED, true);
            account.setOption(Account.OPTION_MAGIC_CREATE, true);
            account.setOption(Account.OPTION_UNVERIFIED, true);
            service.createAccount(account);
        }
        synchronized (mOnVerificationRequested) {
            for (OnVerificationRequested onVerificationRequested : mOnVerificationRequested) {
                if (timestamp <= 0) {
                    onVerificationRequested.onVerificationRequested();
                } else {
                    onVerificationRequested.onVerificationRequestedRetryAt(timestamp);
                }
            }
        }
    }

    public void verify(final Account account, final String pin) {
        if (mVerificationInProgress.compareAndSet(false, true)) {
            final var okHttp = HttpConnectionManager.okHttpClient(service);
            final RequestBody body =
                    RequestBody.create(
                            account.getPassword(), MediaType.parse("text/plain; charset=utf-8"));

            final var request =
                    new Request.Builder()
                            .url(BASE_URL.newBuilder().addPathSegment("password").build())
                            .addHeader(
                                    "Authorization",
                                    Plain.getAuthorization(account.getUsername(), pin))
                            .addHeader("Installation-Id", getInstallationId())
                            .addHeader("Accept-Language", Locale.getDefault().getLanguage())
                            .post(body)
                            .build();
            okHttp.newCall(request)
                    .enqueue(
                            new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    final int code = getApiErrorCode(e);
                                    synchronized (mOnVerification) {
                                        for (OnVerification onVerification : mOnVerification) {
                                            onVerification.onVerificationFailed(code);
                                        }
                                    }
                                    mVerificationInProgress.set(false);
                                }

                                @Override
                                public void onResponse(
                                        @NonNull Call call, @NonNull Response response) {
                                    final var code = response.code();
                                    if (code == 200 || code == 201) {
                                        account.setOption(Account.OPTION_UNVERIFIED, false);
                                        account.setOption(Account.OPTION_DISABLED, false);
                                        awaitingAccountStateChange = new CountDownLatch(1);
                                        service.updateAccount(account);
                                        try {
                                            if (!awaitingAccountStateChange.await(
                                                    5, TimeUnit.SECONDS)) {
                                                Log.d(
                                                        Config.LOGTAG,
                                                        account.getJid().asBareJid()
                                                                + ": timer expired while waiting"
                                                                + " for account to connect");
                                            }
                                        } catch (InterruptedException e) {
                                            Log.d(
                                                    Config.LOGTAG,
                                                    account.getJid().asBareJid()
                                                            + ":interrupted while waiting for"
                                                            + " account to connect");
                                        }
                                        synchronized (mOnVerification) {
                                            for (OnVerification onVerification : mOnVerification) {
                                                onVerification.onVerificationSucceeded();
                                            }
                                        }
                                    } else if (code == 429) {
                                        final long retryAfter = retryAfter(response);
                                        synchronized (mOnVerification) {
                                            for (OnVerification onVerification : mOnVerification) {
                                                onVerification.onVerificationRetryAt(retryAfter);
                                            }
                                        }
                                    } else {
                                        synchronized (mOnVerification) {
                                            for (OnVerification onVerification : mOnVerification) {
                                                onVerification.onVerificationFailed(code);
                                            }
                                        }
                                    }
                                    mVerificationInProgress.set(false);
                                }
                            });
        }
    }

    private String getInstallationId() {
        final var appSettings = service.getAppSettings();
        final long installationId = appSettings.getInstallationId();
        return AccountUtils.createUuid4(installationId, installationId).toString();
    }

    private int getApiErrorCode(final Exception e) {
        if (new NetworkManager(service).getHint() != NetworkManager.Hint.ACTIVE) {
            return API_ERROR_AIRPLANE_MODE;
        } else if (e instanceof UnknownHostException) {
            return API_ERROR_UNKNOWN_HOST;
        } else if (e instanceof ConnectException) {
            return API_ERROR_CONNECT;
        } else if (e instanceof SSLHandshakeException) {
            return API_ERROR_SSL_HANDSHAKE;
        } else if (e instanceof SSLPeerUnverifiedException || e instanceof CertificateException) {
            return API_ERROR_SSL_CERTIFICATE;
        } else if (e instanceof SSLException || e instanceof GeneralSecurityException) {
            return API_ERROR_SSL_GENERAL;
        } else if (e instanceof SocketTimeoutException) {
            return API_ERROR_TIMEOUT;
        } else {
            Log.d(Config.LOGTAG, e.getClass().getName());
            return API_ERROR_OTHER;
        }
    }

    public boolean isVerifying() {
        return mVerificationInProgress.get();
    }

    public boolean isRequestingVerification() {
        return mVerificationRequestInProgress.get();
    }

    @Override
    public boolean isSynchronizing() {
        return mRunningSyncJobs.get() > 0;
    }

    @Override
    public void considerSync() {
        considerSync(false);
    }

    @Override
    public void considerSyncBackground(final boolean forced) {
        mRunningSyncJobs.incrementAndGet();
        mSerialSingleThreadExecutor.execute(
                () -> {
                    considerSync(forced);
                    if (mRunningSyncJobs.decrementAndGet() == 0) {
                        service.updateRosterUi();
                    }
                });
    }

    @Override
    public void handleSmsReceived(final Intent intent) {
        final Bundle extras = intent.getExtras();
        final String pin = SmsRetrieverWrapper.extractPin(extras);
        if (pin == null) {
            Log.d(Config.LOGTAG, "unable to extract Pin from received SMS");
            return;
        }
        final Account account = AccountUtils.getFirst(service);
        if (account == null) {
            Log.d(Config.LOGTAG, "no account configured to process PIN received by SMS");
            return;
        }
        verify(account, pin);
        synchronized (mOnVerification) {
            for (OnVerification onVerification : mOnVerification) {
                onVerification.startBackgroundVerification(pin);
            }
        }
    }

    private void considerSync(boolean forced) {
        final ImmutableMap<String, PhoneNumberContact> allContacts =
                PhoneNumberContact.load(service);
        for (final Account account : service.getAccounts()) {
            final Map<String, PhoneNumberContact> contacts =
                    filtered(allContacts, account.getJid().getLocal());
            if (contacts.size() < allContacts.size()) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": found own phone number in address book. ignoring...");
            }
            refresh(account, contacts.values());
            if (!considerSync(account, contacts, forced)) {
                account.getXmppConnection().getManager(RosterManager.class).writeToDatabaseAsync();
            }
        }
    }

    @SafeVarargs
    private static <A, B> Map<A, B> filtered(final Map<A, B> input, final A... filters) {
        final HashMap<A, B> result = new HashMap<>(input);
        for (final A filtered : filters) {
            result.remove(filtered);
        }
        return result;
    }

    private void refresh(Account account, Collection<PhoneNumberContact> contacts) {
        for (final var contact :
                account.getRoster().getWithSystemAccounts(PhoneNumberContact.class)) {
            final Uri uri = contact.getSystemAccount();
            if (uri == null) {
                continue;
            }
            final String number = getNumber(contact);
            final PhoneNumberContact phoneNumberContact =
                    PhoneNumberContact.findByUriOrNumber(contacts, uri, number);
            final boolean needsCacheClean;
            if (phoneNumberContact != null) {
                if (!uri.equals(phoneNumberContact.getLookupUri())) {
                    Log.d(
                            Config.LOGTAG,
                            "lookupUri has changed from "
                                    + uri
                                    + " to "
                                    + phoneNumberContact.getLookupUri());
                }
                needsCacheClean = contact.setPhoneContact(phoneNumberContact);
            } else {
                needsCacheClean = contact.unsetPhoneContact(PhoneNumberContact.class);
                Log.d(Config.LOGTAG, uri + " vanished from address book");
            }
            if (needsCacheClean) {
                service.getAvatarService().clear(contact);
            }
        }
    }

    private static String getNumber(final Contact contact) {
        final Jid jid = contact.getAddress();
        if (jid.getLocal() != null && Config.QUICKSY_DOMAIN.equals(jid.getDomain())) {
            return jid.getLocal();
        }
        return null;
    }

    private boolean considerSync(
            final Account account,
            final Map<String, PhoneNumberContact> contacts,
            final boolean forced) {
        final int hash = contacts.keySet().hashCode();
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": consider sync of " + hash);
        if (!mLastSyncAttempt.retry(hash) && !forced) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": do not attempt sync");
            return false;
        }
        mRunningSyncJobs.incrementAndGet();
        final Jid syncServer = Jid.of(API_DOMAIN);
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid() + ": sending phone list to " + syncServer);
        final List<Element> entries = new ArrayList<>();
        for (final PhoneNumberContact c : contacts.values()) {
            entries.add(new Element("entry").setAttribute("number", c.getPhoneNumber()));
        }
        final Iq query = new Iq(Iq.Type.GET);
        query.setTo(syncServer);
        final Element book =
                new Element("phone-book", Namespace.SYNCHRONIZATION).setChildren(entries);
        final String statusQuo =
                Entry.statusQuo(
                        contacts.values(),
                        account.getRoster().getWithSystemAccounts(PhoneNumberContact.class));
        book.setAttribute("ver", statusQuo);
        query.addChild(book);
        mLastSyncAttempt = Attempt.create(hash);
        service.sendIqPacket(
                account,
                query,
                (response) -> {
                    if (response.getType() == Iq.Type.RESULT) {
                        final Element phoneBook =
                                response.findChild("phone-book", Namespace.SYNCHRONIZATION);
                        if (phoneBook != null) {
                            final var remaining =
                                    new ArrayList<>(
                                            account.getRoster()
                                                    .getWithSystemAccounts(
                                                            PhoneNumberContact.class));
                            for (Entry entry : Entry.ofPhoneBook(phoneBook)) {
                                final PhoneNumberContact phoneContact =
                                        contacts.get(entry.getNumber());
                                if (phoneContact == null) {
                                    continue;
                                }
                                for (final Jid jid : entry.getJids()) {
                                    final Contact contact = account.getRoster().getContact(jid);
                                    final boolean needsCacheClean =
                                            contact.setPhoneContact(phoneContact);
                                    if (needsCacheClean) {
                                        service.getAvatarService().clear(contact);
                                    }
                                    remaining.remove(contact);
                                }
                            }
                            for (final Contact contact : remaining) {
                                final boolean needsCacheClean =
                                        contact.unsetPhoneContact(PhoneNumberContact.class);
                                if (needsCacheClean) {
                                    service.getAvatarService().clear(contact);
                                }
                            }
                        } else {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": phone number contact list remains unchanged");
                        }
                    } else if (response.getType() == Iq.Type.TIMEOUT) {
                        mLastSyncAttempt = Attempt.NULL;
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": failed to sync contact list with api server");
                    }
                    mRunningSyncJobs.decrementAndGet();
                    account.getXmppConnection()
                            .getManager(RosterManager.class)
                            .writeToDatabaseAsync();
                    service.updateRosterUi();
                });
        return true;
    }

    public interface OnVerificationRequested {
        void onVerificationRequestFailed(int code);

        void onVerificationRequested();

        void onVerificationRequestedRetryAt(long timestamp);
    }

    public interface OnVerification {
        void onVerificationFailed(int code);

        void onVerificationSucceeded();

        void onVerificationRetryAt(long timestamp);

        void startBackgroundVerification(String pin);
    }

    private record Attempt(long timestamp, int hash) {
        private static final Attempt NULL = new Attempt(0, 0);

        public static Attempt create(int hash) {
            return new Attempt(SystemClock.elapsedRealtime(), hash);
        }

        public boolean retry(int hash) {
            return hash != this.hash
                    || SystemClock.elapsedRealtime() - timestamp
                            >= Config.CONTACT_SYNC_RETRY_INTERVAL;
        }
    }
}
