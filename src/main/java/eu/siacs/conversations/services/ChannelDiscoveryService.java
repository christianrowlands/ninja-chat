package eu.siacs.conversations.services;

import static eu.siacs.conversations.utils.Random.SECURE_RANDOM;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.TrustManagers;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Room;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.http.services.MuclumbusService;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.utils.TLSSocketFactory;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;

import im.conversations.android.xmpp.model.stanza.Iq;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class ChannelDiscoveryService {

    private final XmppConnectionService service;

    private MuclumbusService muclumbusService;

    private final Cache<String, List<Room>> cache;

    ChannelDiscoveryService(XmppConnectionService service) {
        this.service = service;
        this.cache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();
    }

    void initializeMuclumbusService() {
        if (Strings.isNullOrEmpty(Config.CHANNEL_DISCOVERY)) {
            this.muclumbusService = null;
            return;
        }
        final OkHttpClient.Builder builder = HttpConnectionManager.OK_HTTP_CLIENT.newBuilder();
        try {
            final X509TrustManager trustManager;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                trustManager = TrustManagers.defaultWithBundledLetsEncrypt(service);
            } else {
                trustManager = TrustManagers.createDefaultTrustManager();
            }
            final SSLSocketFactory socketFactory =
                    new TLSSocketFactory(new X509TrustManager[] {trustManager}, SECURE_RANDOM);
            builder.sslSocketFactory(socketFactory, trustManager);
        } catch (final IOException
                | KeyManagementException
                | NoSuchAlgorithmException
                | KeyStoreException
                | CertificateException e) {
            Log.d(Config.LOGTAG, "not reconfiguring service to work with bundled LetsEncrypt");
            throw new RuntimeException(e);
        }
        if (service.useTorToConnect()) {
            builder.proxy(HttpConnectionManager.getProxy());
        }
        final Retrofit retrofit =
                new Retrofit.Builder()
                        .client(builder.build())
                        .baseUrl(Config.CHANNEL_DISCOVERY)
                        .addConverterFactory(GsonConverterFactory.create())
                        .callbackExecutor(Executors.newSingleThreadExecutor())
                        .build();
        this.muclumbusService = retrofit.create(MuclumbusService.class);
    }

    void cleanCache() {
        cache.invalidateAll();
    }

    void discover(
            @NonNull final String query,
            Method method,
            OnChannelSearchResultsFound onChannelSearchResultsFound) {
        final List<Room> result = cache.getIfPresent(key(method, query));
        if (result != null) {
            onChannelSearchResultsFound.onChannelSearchResultsFound(result);
            return;
        }
        if (method == Method.LOCAL_SERVER) {
            discoverChannelsLocalServers(query, onChannelSearchResultsFound);
        } else {
            if (query.isEmpty()) {
                discoverChannelsJabberNetwork(onChannelSearchResultsFound);
            } else {
                discoverChannelsJabberNetwork(query, onChannelSearchResultsFound);
            }
        }
    }

    private void discoverChannelsJabberNetwork(final OnChannelSearchResultsFound listener) {
        if (muclumbusService == null) {
            listener.onChannelSearchResultsFound(Collections.emptyList());
            return;
        }
        final Call<MuclumbusService.Rooms> call = muclumbusService.getRooms(1);
        call.enqueue(
                new Callback<MuclumbusService.Rooms>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<MuclumbusService.Rooms> call,
                            @NonNull Response<MuclumbusService.Rooms> response) {
                        final MuclumbusService.Rooms body = response.body();
                        if (body == null) {
                            listener.onChannelSearchResultsFound(Collections.emptyList());
                            logError(response);
                            return;
                        }
                        cache.put(key(Method.JABBER_NETWORK, ""), body.items);
                        listener.onChannelSearchResultsFound(body.items);
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<MuclumbusService.Rooms> call,
                            @NonNull Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                "Unable to query muclumbus on " + Config.CHANNEL_DISCOVERY,
                                throwable);
                        listener.onChannelSearchResultsFound(Collections.emptyList());
                    }
                });
    }

    private void discoverChannelsJabberNetwork(
            final String query, final OnChannelSearchResultsFound listener) {
        if (muclumbusService == null) {
            listener.onChannelSearchResultsFound(Collections.emptyList());
            return;
        }
        final MuclumbusService.SearchRequest searchRequest =
                new MuclumbusService.SearchRequest(query);
        final Call<MuclumbusService.SearchResult> searchResultCall =
                muclumbusService.search(searchRequest);
        searchResultCall.enqueue(
                new Callback<MuclumbusService.SearchResult>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<MuclumbusService.SearchResult> call,
                            @NonNull Response<MuclumbusService.SearchResult> response) {
                        final MuclumbusService.SearchResult body = response.body();
                        if (body == null) {
                            listener.onChannelSearchResultsFound(Collections.emptyList());
                            logError(response);
                            return;
                        }
                        cache.put(key(Method.JABBER_NETWORK, query), body.result.items);
                        listener.onChannelSearchResultsFound(body.result.items);
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<MuclumbusService.SearchResult> call,
                            @NonNull Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                "Unable to query muclumbus on " + Config.CHANNEL_DISCOVERY,
                                throwable);
                        listener.onChannelSearchResultsFound(Collections.emptyList());
                    }
                });
    }

    private void discoverChannelsLocalServers(
            final String query, final OnChannelSearchResultsFound listener) {
        final Map<Jid, Account> localMucService = getLocalMucServices();
        Log.d(Config.LOGTAG, "checking with " + localMucService.size() + " muc services");
        if (localMucService.isEmpty()) {
            listener.onChannelSearchResultsFound(Collections.emptyList());
            return;
        }
        if (!query.isEmpty()) {
            final List<Room> cached = cache.getIfPresent(key(Method.LOCAL_SERVER, ""));
            if (cached != null) {
                final List<Room> results = copyMatching(cached, query);
                cache.put(key(Method.LOCAL_SERVER, query), results);
                listener.onChannelSearchResultsFound(results);
            }
        }
        final AtomicInteger queriesInFlight = new AtomicInteger();
        final List<Room> rooms = new ArrayList<>();
        for (final Map.Entry<Jid, Account> entry : localMucService.entrySet()) {
            Iq itemsRequest = service.getIqGenerator().queryDiscoItems(entry.getKey());
            queriesInFlight.incrementAndGet();
            final var account = entry.getValue();
            service.sendIqPacket(
                    account,
                    itemsRequest,
                    (itemsResponse) -> {
                        if (itemsResponse.getType() == Iq.Type.RESULT) {
                            final List<Jid> items = IqParser.items(itemsResponse);
                            for (final Jid item : items) {
                                final Iq infoRequest =
                                        service.getIqGenerator().queryDiscoInfo(item);
                                queriesInFlight.incrementAndGet();
                                service.sendIqPacket(
                                        account,
                                        infoRequest,
                                        infoResponse -> {
                                            if (infoResponse.getType()
                                                    == Iq.Type.RESULT) {
                                                final Room room =
                                                        IqParser.parseRoom(infoResponse);
                                                if (room != null) {
                                                    rooms.add(room);
                                                }
                                                if (queriesInFlight.decrementAndGet() <= 0) {
                                                    finishDiscoSearch(rooms, query, listener);
                                                }
                                            } else {
                                                queriesInFlight.decrementAndGet();
                                            }
                                        });
                            }
                        }
                        if (queriesInFlight.decrementAndGet() <= 0) {
                            finishDiscoSearch(rooms, query, listener);
                        }
                    });
        }
    }

    private void finishDiscoSearch(
            List<Room> rooms, String query, OnChannelSearchResultsFound listener) {
        Collections.sort(rooms);
        cache.put(key(Method.LOCAL_SERVER, ""), rooms);
        if (query.isEmpty()) {
            listener.onChannelSearchResultsFound(rooms);
        } else {
            List<Room> results = copyMatching(rooms, query);
            cache.put(key(Method.LOCAL_SERVER, query), results);
            listener.onChannelSearchResultsFound(rooms);
        }
    }

    private static List<Room> copyMatching(List<Room> haystack, String needle) {
        ArrayList<Room> result = new ArrayList<>();
        for (Room room : haystack) {
            if (room.contains(needle)) {
                result.add(room);
            }
        }
        return result;
    }

    private Map<Jid, Account> getLocalMucServices() {
        final HashMap<Jid, Account> localMucServices = new HashMap<>();
        for (Account account : service.getAccounts()) {
            if (account.isEnabled()) {
                final XmppConnection xmppConnection = account.getXmppConnection();
                if (xmppConnection == null) {
                    continue;
                }
                for (final String mucService : xmppConnection.getMucServers()) {
                    Jid jid = Jid.ofEscaped(mucService);
                    if (!localMucServices.containsKey(jid)) {
                        localMucServices.put(jid, account);
                    }
                }
            }
        }
        return localMucServices;
    }

    private static String key(Method method, String query) {
        return String.format("%s\00%s", method, query);
    }

    private static void logError(final Response response) {
        final ResponseBody errorBody = response.errorBody();
        Log.d(Config.LOGTAG, "code from muclumbus=" + response.code());
        if (errorBody == null) {
            return;
        }
        try {
            Log.d(Config.LOGTAG, "error body=" + errorBody.string());
        } catch (IOException e) {
            // ignored
        }
    }

    public interface OnChannelSearchResultsFound {
        void onChannelSearchResultsFound(List<Room> results);
    }

    public enum Method {
        JABBER_NETWORK,
        LOCAL_SERVER
    }
}
