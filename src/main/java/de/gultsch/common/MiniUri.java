package de.gultsch.common;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import eu.siacs.conversations.xmpp.Jid;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;

public class MiniUri {

    public static final String EMPTY_STRING = "";

    private final String raw;
    private final String scheme;
    private final String authority;
    private final String path;
    private final Map<String, Collection<String>> parameter;
    private final String fragment;

    private MiniUri(final String uri) {
        this.raw = uri;
        final var schemeAndRest = Splitter.on(':').limit(2).splitToList(uri);
        if (schemeAndRest.size() < 2) {
            this.scheme = uri;
            this.authority = null;
            this.path = null;
            this.parameter = Collections.emptyMap();
            this.fragment = null;
            return;
        }
        this.scheme = schemeAndRest.get(0);
        final var fragmentAndBefore = Splitter.on('#').limit(2).splitToList(schemeAndRest.get(1));
        final var rest = Strings.nullToEmpty(Iterables.getFirst(fragmentAndBefore, null));
        if (fragmentAndBefore.size() == 2) {
            this.fragment = Iterables.getLast(fragmentAndBefore);
        } else {
            this.fragment = null;
        }
        final var authorityPathAndQuery = Splitter.on('?').limit(2).splitToList(rest);
        final var authorityPath = authorityPathAndQuery.get(0);
        if (authorityPath.length() >= 2 && authorityPath.startsWith("//")) {
            final var authorityPathParts =
                    Splitter.on('/').limit(2).splitToList(authorityPath.substring(2));
            this.authority = authorityPathParts.get(0);
            this.path = authorityPathParts.size() == 2 ? authorityPathParts.get(1) : null;
        } else {
            this.authority = null;
            // TODO path ; style path components from something like geo uri
            this.path = authorityPath;
        }
        if (authorityPathAndQuery.size() == 2) {
            this.parameter = parseParameters(authorityPathAndQuery.get(1), getDelimiter(scheme));
        } else {
            this.parameter = Collections.emptyMap();
        }
    }

    private static char getDelimiter(final String scheme) {
        return switch (scheme) {
            case "xmpp", "geo" -> ';';
            default -> '&';
        };
    }

    private static Map<String, Collection<String>> parseParameters(
            final String query, final char separator) {
        final var builder = new ImmutableMultimap.Builder<String, String>();
        for (final String pair : Splitter.on(separator).omitEmptyStrings().split(query)) {
            final String[] parts = pair.split("=", 2);
            if (parts.length == 0) {
                continue;
            }
            final String key = parts[0].toLowerCase(Locale.US);
            if (parts.length == 2) {
                builder.put(key, urlDecodeOrEmpty(parts[1]));
            } else {
                builder.put(key, EMPTY_STRING);
            }
        }
        return builder.build().asMap();
    }

    public static String urlDecodeOrEmpty(final String input) {
        try {
            return URLDecoder.decode(input, "UTF-8");
        } catch (final UnsupportedEncodingException | IllegalArgumentException e) {
            return EMPTY_STRING;
        }
    }

    public static String urlEncode(final String input) {
        try {
            return URLEncoder.encode(input, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scheme", scheme)
                .add("authority", authority)
                .add("path", path)
                .add("parameter", parameter)
                .add("fragment", fragment)
                .toString();
    }

    public String getScheme() {
        return this.scheme;
    }

    public String getAuthority() {
        return this.authority;
    }

    public String getPath() {
        return Strings.isNullOrEmpty(this.path) || this.authority == null
                ? this.path
                : '/' + this.path;
    }

    public List<String> getPathSegments() {
        return Strings.isNullOrEmpty(this.path)
                ? Collections.emptyList()
                : Splitter.on('/').splitToList(this.path);
    }

    public String getFragment() {
        return this.fragment;
    }

    public String getRaw() {
        return this.raw;
    }

    public Uri asUri() {
        return Uri.parse(this.raw);
    }

    public Map<String, Collection<String>> getParameter() {
        return this.parameter;
    }

    public Map<String, String> getParameterFlat() {
        return Maps.transformValues(
                this.parameter,
                v -> v != null ? Iterables.getFirst(v, EMPTY_STRING) : EMPTY_STRING);
    }

    public String getParameter(final String key) {
        return getParameterFlat().get(key);
    }

    public static MiniUri tryParse(final String uri) {
        if (Strings.isNullOrEmpty(uri)) {
            throw new IllegalArgumentException("URIs can not be empty");
        }
        if (Patterns.URI_GENERIC.matcher(uri).matches()) {
            return asMiniUri(uri);
        }
        throw new IllegalArgumentException("URI does not match generic pattern");
    }

    @Nullable
    public static MiniUri getOrNull(final Uri uri) {
        if (uri == null) {
            return null;
        }
        return getOrNull(uri.toString());
    }

    @Nullable
    public static MiniUri getOrNull(final String uri) {
        if (Strings.isNullOrEmpty(uri)) {
            return null;
        }
        try {
            return tryParse(uri);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public static Xmpp getXmppUriOrNull(final String uri) {
        final var miniUri = getOrNull(uri);
        if (miniUri instanceof Xmpp xmpp) {
            return xmpp;
        } else if (miniUri instanceof Transformable transformable) {
            if (transformable.transform() instanceof Xmpp xmpp) {
                return xmpp;
            }
        }
        return null;
    }

    public static MiniUri tryInternalParse(final String uri) {
        if (Strings.isNullOrEmpty(uri)) {
            throw new IllegalArgumentException("URIs can not be empty");
        }
        if (Patterns.URI_INTERNAL.matcher(uri).matches()) {
            return asMiniUri(uri);
        }
        throw new IllegalArgumentException("URI does not match internal pattern");
    }

    public static MiniUri asMiniUri(final String uri) {
        final var scheme = Iterables.getFirst(Splitter.on(':').limit(2).splitToList(uri), null);
        if (scheme == null) {
            throw new IllegalArgumentException("Empty scheme in URI");
        }
        return switch (scheme) {
            case "tel" -> asMiniUriIfMatch(Patterns.URI_TEL, uri);
            case "http", "https" -> {
                if (Patterns.URI_HTTP.matcher(uri).matches()) {
                    yield new Http(uri);
                }
                throw new IllegalArgumentException("HTTP URI does not match pattern");
            }
            case "geo" -> asMiniUriIfMatch(Patterns.URI_GEO, uri);
            case "xmpp" -> new Xmpp(uri);
            case "taler" -> asMiniUriIfMatch(Patterns.URI_TALER, uri);
            case "imto" -> new Imto(uri);
            case "web+ap" -> {
                if (Patterns.URI_WEB_AP.matcher(uri).matches()) {
                    final var webAp = new MiniUri(uri);
                    // TODO once we have fragment support check that there aren't any
                    if (Objects.nonNull(webAp.getAuthority()) && webAp.getParameter().isEmpty()) {
                        yield webAp;
                    }
                }
                throw new IllegalArgumentException("web+ap URI does not match pattern");
            }
            default -> new MiniUri(uri);
        };
    }

    private static MiniUri asMiniUriIfMatch(final Pattern pattern, final String uri) {
        if (pattern.matcher(uri).matches()) {
            return new MiniUri(uri);
        }
        throw new IllegalArgumentException("URI does not match pattern");
    }

    public abstract static class Transformable extends MiniUri {

        private Transformable(String uri) {
            super(uri);
        }

        public abstract MiniUri transform();
    }

    public static final class Xmpp extends MiniUri {

        private static final Pattern PARAMETER_OMEMO = Pattern.compile("^omemo(-sid-\\d+)?$");

        public static final String ACTION_JOIN = "join";
        public static final String ACTION_MESSAGE = "message";
        public static final String ACTION_REGISTER = "register";
        public static final String ACTION_ROSTER = "roster";
        public static final String PARAMETER_PRE_AUTH = "preauth";
        public static final String PARAMETER_IBR = "ibr";

        private final Jid jid;

        public Xmpp(final String uri) {
            super(uri);
            Preconditions.checkArgument(getScheme().equals("xmpp"), "scheme must be xmpp");
            Preconditions.checkArgument(
                    Objects.isNull(getAuthority()), "authorities are not supported");
            final var path = getPath();
            if (Strings.isNullOrEmpty(path)) {
                if (this.getParameter().isEmpty()) {
                    throw new IllegalArgumentException(
                            "address and parameter should not both be empty");
                }
                this.jid = null;
            } else {
                this.jid = Jid.ofUserInput(path);
            }
        }

        public Xmpp(final Jid jid) {
            this(jid, Collections.emptyMap());
        }

        public Xmpp(final Jid jid, final Map<String, Collection<String>> parameter) {
            this(String.format("%s:%s%s", "xmpp", jid.toString(), asQueryString(parameter)));
        }

        private static String asQueryString(final Map<String, Collection<String>> parameter) {
            if (parameter.isEmpty()) {
                return EMPTY_STRING;
            }
            final var builder = new ImmutableList.Builder<String>();
            for (var entry : parameter.entrySet()) {
                for (final var value : entry.getValue()) {
                    if (EMPTY_STRING.equals(value)) {
                        builder.add(urlEncode(entry.getKey()));
                    } else {
                        builder.add(
                                String.format(
                                        "%s=%s", urlEncode(entry.getKey()), urlEncode(value)));
                    }
                }
            }
            return "?" + Joiner.on(';').join(builder.build());
        }

        public Jid asJid() {
            return this.jid;
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("jid", jid)
                    .add("parameter", getParameter())
                    .toString();
        }

        public boolean isAddress() {
            return this.jid != null;
        }

        public boolean isAction(final String action) {
            final var value = this.getParameter().get(action);
            return value != null
                    && value.size() == 1
                    && EMPTY_STRING.equals(Iterables.getOnlyElement(value));
        }

        public Set<String> getOmemoFingerprints() {
            return ImmutableSet.copyOf(
                    Maps.filterKeys(
                                    this.getParameterFlat(),
                                    k -> k != null && PARAMETER_OMEMO.matcher(k).matches())
                            .values());
        }

        public boolean hasOmemoFingerprints() {
            return !getOmemoFingerprints().isEmpty();
        }

        public String getBody() {
            return getParameterFlat().get("body");
        }

        public String getName() {
            return getParameterFlat().get("name");
        }

        public Http asInvitationUri() {
            return new Http(
                    String.format(
                            "https://%s/#%s",
                            Http.INVITATION_AUTHORITY_DEFAULT,
                            urlEncode(asUri().toString().substring(5))));
        }
    }

    public static final class Http extends Transformable {

        private static final String INVITATION_AUTHORITY_LEGACY = "conversations.im";
        private static final String INVITATION_AUTHORITY_DEFAULT = "xmpp.link";
        private static final Collection<String> INVITATION_AUTHORITIES =
                Arrays.asList(INVITATION_AUTHORITY_DEFAULT, "invite.joinjabber.org");

        private final HttpUrl httpUrl;

        private Http(final String uri) {
            super(uri);
            Preconditions.checkArgument(Arrays.asList("http", "https").contains(getScheme()));
            this.httpUrl = HttpUrl.get(this.getRaw());
        }

        @Override
        public MiniUri transform() {
            if ("http".equals(getScheme())) {
                return this;
            }
            final var pathSegments = getPathSegments();
            final var fragment = getFragment();
            if (getAuthority().equalsIgnoreCase(INVITATION_AUTHORITY_LEGACY)
                    && pathSegments.size() == 2) {
                final var action = Iterables.getFirst(pathSegments, null);
                if (Arrays.asList("i", "j").contains(action)) {
                    final var isJoin = action != null && action.equals("j");
                    final Jid jid;
                    try {
                        jid = Jid.ofUserInput(urlDecodeOrEmpty(Iterables.getLast(pathSegments)));
                    } catch (final IllegalArgumentException e) {
                        return this;
                    }
                    if (isJoin) {
                        return new Xmpp(
                                jid, ImmutableMap.of("join", Collections.singleton(EMPTY_STRING)));
                    } else {
                        return new Xmpp(jid, getParameter());
                    }
                }
            } else if (INVITATION_AUTHORITIES.contains(getAuthority())
                    && pathSegments.isEmpty()
                    && fragment != null) {
                final String uri = urlDecodeOrEmpty(fragment);
                if (Strings.isNullOrEmpty(uri)) {
                    return this;
                }
                try {
                    final var xmpp = new Xmpp("xmpp:" + uri);
                    return xmpp.isAddress() ? xmpp : this;
                } catch (final IllegalArgumentException e) {
                    return this;
                }
            }
            return this;
        }

        public HttpUrl asHttpUrl() {
            return this.httpUrl;
        }
    }

    public static final class Imto extends Transformable {

        private final Jid jid;

        private Imto(final String uri) {
            super(uri);
            Preconditions.checkArgument(getScheme().equals("imto"));
            Preconditions.checkArgument(
                    Arrays.asList("xmpp", "jabber").contains(getAuthority()),
                    "Unsupported imto authority");
            final var pathSegments = getPathSegments();
            Preconditions.checkArgument(
                    pathSegments.size() == 1, "imto uri must have 1 path segment");
            this.jid = Jid.ofUserInput(urlDecodeOrEmpty(Iterables.getOnlyElement(pathSegments)));
        }

        public Xmpp transform() {
            return new Xmpp(jid);
        }
    }
}
