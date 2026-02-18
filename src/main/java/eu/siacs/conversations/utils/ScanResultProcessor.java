package eu.siacs.conversations.utils;

import android.content.Context;
import android.util.Log;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import de.gultsch.common.MiniUri;
import de.gultsch.common.Patterns;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.http.HttpConnectionManager;
import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ScanResultProcessor {

    private static final Pattern LINK_HEADER_PATTERN = Pattern.compile("<(.*?)>");
    private static final Pattern V_CARD_XMPP_PATTERN = Pattern.compile("\nIMPP([^:]*):(xmpp:.+)\n");
    private static final String VCARD_BEGIN = "BEGIN:VCARD";
    private static final String VCARD_END = "END:VCARD";

    private final Context context;

    public ScanResultProcessor(final Context context) {
        this.context = context.getApplicationContext();
    }

    public ListenableFuture<MiniUri> process(final @Nullable String input) {
        if (Strings.isNullOrEmpty(input)) {
            return Futures.immediateFailedFuture(new IllegalArgumentException("QR code was empty"));
        }
        final var lines = Splitter.on('\n').trimResults().omitEmptyStrings().splitToList(input);
        if (lines.size() == 1) {
            final var line = Iterables.getOnlyElement(lines);
            if (Patterns.URI_GENERIC.matcher(line).matches()) {
                try {
                    return process(MiniUri.asMiniUri(line));
                } catch (final IllegalArgumentException e) {
                    return Futures.immediateFailedFuture(e);
                }
            }
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException("QR code is not a URI"));
        } else if (Objects.equal(VCARD_BEGIN, Iterables.getFirst(lines, null))
                && Objects.equal(VCARD_END, Iterables.getLast(lines))) {
            final Matcher matcher = V_CARD_XMPP_PATTERN.matcher(input);
            if (matcher.find()) {
                try {
                    return process(MiniUri.asMiniUri(matcher.group(2)));
                } catch (final IllegalArgumentException e) {
                    return Futures.immediateFailedFuture(e);
                }
            } else {
                return Futures.immediateFailedFuture(
                        new IllegalArgumentException("VCard contains no XMPP uri"));
            }
        }
        return Futures.immediateFailedFuture(new IllegalArgumentException("Unrecognized content"));
    }

    private ListenableFuture<MiniUri> process(@NonNull MiniUri uri) {
        if (uri instanceof MiniUri.Xmpp xmpp) {
            if (xmpp.isAddress()) {
                return Futures.immediateFuture(uri);
            }
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException("xmpp uri has no address"));
        } else if (uri instanceof MiniUri.Http http) {
            if (http.transform() instanceof MiniUri.Xmpp xmpp) {
                if (xmpp.isAddress()) {
                    return Futures.immediateFuture(xmpp);
                }
                return Futures.immediateFailedFuture(
                        new IllegalArgumentException("xmpp uri has no address"));
            }
            if (new AppSettings(context).isUseTor()) {
                return Futures.immediateFuture(http);
            }
            if (eligibleForLinkHeaderDiscovery(http.asHttpUrl())) {
                final var linkHeaderFuture = fetchLinkHeader(http.asHttpUrl());
                return Futures.catching(
                        linkHeaderFuture,
                        Exception.class,
                        ex -> {
                            Log.d(Config.LOGTAG, "error looking up link header", ex);
                            return uri;
                        },
                        MoreExecutors.directExecutor());
            } else {
                return Futures.immediateFuture(http);
            }
        } else {
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException(
                            String.format("Unsupported URI scheme " + uri.getScheme())));
        }
    }

    private boolean eligibleForLinkHeaderDiscovery(final HttpUrl url) {
        // we want to rule out IP addresses and local domains from the start; we do a DNS double
        // check later
        // some invite pages encode information in the fragment; we can't use those since it won't
        // have any Link headers
        return url.isHttps() && url.topPrivateDomain() != null && url.fragment() == null;
    }

    private ListenableFuture<MiniUri> fetchLinkHeader(final HttpUrl url) {
        final SettableFuture<MiniUri> future = SettableFuture.create();
        Log.d(Config.LOGTAG, "checking for link header on " + url);
        final var okHttp =
                HttpConnectionManager.okHttpClient(context)
                        .newBuilder()
                        .dns(
                                hostname -> {
                                    final var addresses = Dns.SYSTEM.lookup(hostname);
                                    for (final var address : addresses) {
                                        if (address.isLoopbackAddress()
                                                || address.isSiteLocalAddress()
                                                || address.isLinkLocalAddress()) {
                                            throw new UnknownHostException(
                                                    "Not performing look ups on local addresses");
                                        }
                                    }
                                    return addresses;
                                })
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .callTimeout(Duration.ofSeconds(3))
                        .build();
        final var call = okHttp.newCall(new Request.Builder().url(url).head().build());
        call.enqueue(
                new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        future.setException(e);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        if (response.isSuccessful()) {
                            final var link = response.header("Link");
                            final var uri =
                                    Strings.isNullOrEmpty(link) ? null : processLinkHeader(link);
                            if (uri != null) {
                                future.set(uri);
                            } else {
                                future.setException(
                                        new IllegalStateException(
                                                "No link header found in response"));
                            }
                        } else {
                            future.setException(
                                    new IllegalStateException("HTTP call was unsuccessful"));
                        }
                    }
                });
        return future;
    }

    private static MiniUri.Xmpp processLinkHeader(final String header) {
        final Matcher matcher = LINK_HEADER_PATTERN.matcher(header);
        if (matcher.find()) {
            final var group = matcher.group();
            final var link = group.substring(1, group.length() - 1);
            try {
                final var miniUri = MiniUri.tryParse(link);
                if (miniUri instanceof MiniUri.Xmpp xmpp && xmpp.isAddress()) {
                    return xmpp;
                }
            } catch (final IllegalArgumentException e) {
                Log.d(Config.LOGTAG, "found invalid uri in link header", e);
            }
        }
        return null;
    }
}
