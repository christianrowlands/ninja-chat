package eu.siacs.conversations.http;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import de.gultsch.common.TrustManagers;
import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.TLSSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpConnectionManager extends AbstractConnectionManager {

    private final List<HttpDownloadConnection> downloadConnections = new ArrayList<>();
    private final List<HttpUploadConnection> uploadConnections = new ArrayList<>();

    public static final Executor EXECUTOR = Executors.newFixedThreadPool(4);

    private static final OkHttpClient OK_HTTP_CLIENT;

    static {
        OK_HTTP_CLIENT =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                chain -> {
                                    final Request original = chain.request();
                                    final Request modified =
                                            original.newBuilder()
                                                    .header("User-Agent", getUserAgent())
                                                    .build();
                                    return chain.proceed(modified);
                                })
                        .build();
    }

    public static String getUserAgent() {
        return String.format("%s/%s", BuildConfig.APP_NAME, BuildConfig.VERSION_NAME);
    }

    public HttpConnectionManager(XmppConnectionService service) {
        super(service);
    }

    public static Proxy getProxy() {
        final InetAddress localhost;
        try {
            localhost = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(localhost, 9050));
        } else {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(localhost, 8118));
        }
    }

    public void createNewDownloadConnection(Message message) {
        this.createNewDownloadConnection(message, false);
    }

    public void createNewDownloadConnection(final Message message, boolean interactive) {
        synchronized (this.downloadConnections) {
            for (HttpDownloadConnection connection : this.downloadConnections) {
                if (connection.getMessage() == message) {
                    Log.d(
                            Config.LOGTAG,
                            message.getConversation().getAccount().getJid().asBareJid()
                                    + ": download already in progress");
                    return;
                }
            }
            final HttpDownloadConnection connection = new HttpDownloadConnection(message, this);
            connection.init(interactive);
            this.downloadConnections.add(connection);
        }
    }

    public void createNewUploadConnection(final Message message, boolean delay) {
        synchronized (this.uploadConnections) {
            for (HttpUploadConnection connection : this.uploadConnections) {
                if (connection.getMessage() == message) {
                    Log.d(
                            Config.LOGTAG,
                            message.getConversation().getAccount().getJid().asBareJid()
                                    + ": upload already in progress");
                    return;
                }
            }
            HttpUploadConnection connection = new HttpUploadConnection(message, this);
            connection.init(delay);
            this.uploadConnections.add(connection);
        }
    }

    void finishConnection(HttpDownloadConnection connection) {
        synchronized (this.downloadConnections) {
            this.downloadConnections.remove(connection);
        }
    }

    void finishUploadConnection(HttpUploadConnection httpUploadConnection) {
        synchronized (this.uploadConnections) {
            this.uploadConnections.remove(httpUploadConnection);
        }
    }

    OkHttpClient buildHttpClient(final HttpUrl url, final Account account, boolean interactive) {
        return buildHttpClient(url, account, 30, interactive);
    }

    public OkHttpClient buildHttpClient(
            final HttpUrl url, final Account account, int readTimeout, boolean interactive) {
        final String slotHostname = url.host();
        final boolean onionSlot = slotHostname.endsWith(".onion");
        final OkHttpClient.Builder builder = OK_HTTP_CLIENT.newBuilder();
        builder.writeTimeout(30, TimeUnit.SECONDS);
        builder.readTimeout(readTimeout, TimeUnit.SECONDS);
        setupTrustManager(builder, interactive);
        if (mXmppConnectionService.useTorToConnect() || account.isOnion() || onionSlot) {
            builder.proxy(HttpConnectionManager.getProxy()).build();
        }
        return builder.build();
    }

    public ListenableFuture<Set<String>> checkAvailability(
            final Account account, final Collection<String> urls) {
        final var futures = Collections2.transform(urls, url -> checkAvailability(account, url));
        final var availableUrlsFuture = Futures.successfulAsList(futures);
        return Futures.transform(
                availableUrlsFuture,
                available -> ImmutableSet.copyOf(Collections2.filter(available, Objects::nonNull)),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<String> checkAvailability(final Account account, final String url) {
        final SettableFuture<String> future = SettableFuture.create();
        final var httpUrl = AesGcmURL.of(url);
        final var okHttp =
                buildHttpClient(httpUrl, account, false)
                        .newBuilder()
                        .callTimeout(Duration.ofSeconds(5))
                        .build();
        final var request =
                new Request.Builder()
                        .head()
                        .url(URL.stripFragment(httpUrl))
                        .addHeader("Accept-Encoding", "identity")
                        .build();
        okHttp.newCall(request)
                .enqueue(
                        new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                future.setException(e);
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) {
                                if (response.isSuccessful()) {
                                    // TODO check 'Sunset' HTTP response Header (rfc8594) to see if
                                    // it is at least ~24 hours in the future
                                    future.set(url);
                                } else {
                                    future.setException(
                                            new IllegalStateException(
                                                    String.format(
                                                            Locale.US,
                                                            "HTTP response code is %d",
                                                            response.code())));
                                }
                            }
                        });
        return future;
    }

    private void setupTrustManager(final OkHttpClient.Builder builder, final boolean interactive) {
        final X509TrustManager trustManager;
        if (interactive) {
            trustManager = mXmppConnectionService.getMemorizingTrustManager().getInteractive();
        } else {
            trustManager = mXmppConnectionService.getMemorizingTrustManager().getNonInteractive();
        }
        try {
            final SSLSocketFactory sf =
                    new TLSSocketFactory(
                            new X509TrustManager[] {trustManager}, mXmppConnectionService);
            builder.sslSocketFactory(sf, trustManager);
        } catch (final KeyManagementException | NoSuchAlgorithmException ignored) {
        }
    }

    public static InputStream open(final String url, final boolean tor) throws IOException {
        return open(HttpUrl.get(url), tor);
    }

    public static InputStream open(final HttpUrl httpUrl, final boolean tor) throws IOException {
        final OkHttpClient.Builder builder = OK_HTTP_CLIENT.newBuilder();
        if (tor) {
            builder.proxy(HttpConnectionManager.getProxy()).build();
        }
        final OkHttpClient client = builder.build();
        final Request request = new Request.Builder().get().url(httpUrl).build();
        final ResponseBody body = client.newCall(request).execute().body();
        if (body == null) {
            throw new IOException("No response body found");
        }
        return body.byteStream();
    }

    public static OkHttpClient okHttpClient(final Context context) {
        final OkHttpClient.Builder builder = HttpConnectionManager.OK_HTTP_CLIENT.newBuilder();
        try {
            final X509TrustManager trustManager = TrustManagers.createForAndroidVersion(context);
            final SSLSocketFactory socketFactory =
                    new TLSSocketFactory(new X509TrustManager[] {trustManager}, context);
            builder.sslSocketFactory(socketFactory, trustManager);
        } catch (final IOException
                | KeyManagementException
                | NoSuchAlgorithmException
                | KeyStoreException
                | CertificateException e) {
            Log.d(Config.LOGTAG, "not reconfiguring service to work with bundled LetsEncrypt");
            throw new RuntimeException(e);
        }
        return builder.build();
    }
}
