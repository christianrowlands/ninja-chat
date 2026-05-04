package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.time.Time;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;

public class EntityTimeManager extends AbstractManager {

    private static final Duration DURATION_RECENT_MESSAGE = Duration.ofMinutes(12);

    private final CacheLoader<Jid, ListenableFuture<EntityTime>> entityTimeLoader =
            new CacheLoader<>() {

                @Override
                public ListenableFuture<EntityTime> load(final Jid address) {
                    return Futures.transform(
                            zonedDateTime(address),
                            zonedDateTime ->
                                    new OffsetEntityTime(
                                            Objects.requireNonNull(zonedDateTime).getOffset()),
                            MoreExecutors.directExecutor());
                }
            };

    private final LoadingCache<Jid, ListenableFuture<EntityTime>> entityTimeCache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(Duration.ofHours(3))
                    .build(this.entityTimeLoader);
    private final XmppConnectionService service;
    private final AppSettings appSettings;

    public EntityTimeManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.appSettings = new AppSettings(context.getApplicationContext());
        this.service = service;
    }

    public void request(final Iq request) {
        if (this.appSettings.isUseTor() || getAccount().isOnion()) {
            this.connection.sendErrorFor(request, new Condition.Forbidden());
            return;
        }
        final var from = request.getFrom();
        if (from == null) {
            this.connection.sendErrorFor(request, new Condition.NotAcceptable());
            return;
        }
        final var contact = getManager(RosterManager.class).getContactFromContactList(from);
        if (contact != null && contact.showInContactList() && this.appSettings.isEntityTime()) {
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid().asBareJid()
                            + ": responding to entity time request from "
                            + request.getFrom());
            this.connection.sendResultFor(request, new Time(ZonedDateTime.now()));
        } else {
            this.connection.sendErrorFor(request, new Condition.Forbidden());
        }
    }

    public ListenableFuture<ZonedDateTime> zonedDateTime(final Jid address) {
        Log.d(Config.LOGTAG, "requesting entity time: " + address);
        final var iqFuture = this.connection.sendIqPacket(new Iq(Iq.Type.GET, address, new Time()));
        final var zonedEntityTimeFuture =
                Futures.transform(
                        iqFuture,
                        iq -> {
                            final var time =
                                    Objects.requireNonNull(iq).getOnlyExtension(Time.class);
                            if (time == null) {
                                throw new IllegalArgumentException(
                                        "No valid time extension in response");
                            }
                            final var zonedDateTime = time.asZonedDateTime();
                            if (zonedDateTime == null) {
                                throw new IllegalArgumentException(
                                        "No valid zoned date time in response");
                            }
                            return zonedDateTime;
                        },
                        MoreExecutors.directExecutor());
        Futures.addCallback(
                zonedEntityTimeFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final ZonedDateTime result) {
                        service.updateConversationUi();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not request entity time from" + address, t);
                    }
                },
                MoreExecutors.directExecutor());
        return zonedEntityTimeFuture;
    }

    private ListenableFuture<List<EntityTime>> getEntityTimes(final Jid address) {
        final var presences = getManager(PresenceManager.class).getPresences(address);
        final Collection<ListenableFuture<EntityTime>> futures =
                Collections2.transform(
                        presences,
                        p -> {
                            final var fullAddress = Objects.requireNonNull(p).getFrom();
                            final var infoQuery = getManager(DiscoManager.class).get(fullAddress);
                            if (infoQuery == null || !infoQuery.hasFeature(Namespace.TIME)) {
                                return Futures.immediateFuture(new NoEntityTime());
                            }
                            final var entityTimeFuture = entityTimeCache.getUnchecked(fullAddress);
                            final var entityTimeTimeoutFuture =
                                    Futures.withTimeout(
                                            entityTimeFuture,
                                            Duration.ofSeconds(10L),
                                            FUTURE_TIMEOUT_EXECUTOR);
                            return Futures.catching(
                                    entityTimeTimeoutFuture,
                                    Exception.class,
                                    ex -> new InvalidEntityTime(),
                                    MoreExecutors.directExecutor());
                        });
        return Futures.allAsList(futures);
    }

    public ListenableFuture<ZonedDateTime> getZonedDateTime(final Jid address) {
        if (!this.appSettings.isEntityTime()) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Requesting entity time is disabled"));
        }
        final var contact = getManager(RosterManager.class).getContactFromContactList(address);
        if (contact == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Requesting entity time is limited to contacts"));
        }
        return Futures.transform(
                getEntityTimes(address),
                entityTimes -> {
                    if (entityTimes == null) {
                        throw new IllegalStateException("No entity times available");
                    }
                    final var unknownAsNulls =
                            Collections2.transform(
                                    entityTimes,
                                    entityTime -> {
                                        if (entityTime
                                                instanceof
                                                OffsetEntityTime(ZoneOffset zoneOffset)) {
                                            return zoneOffset;
                                        } else {
                                            return null;
                                        }
                                    });
                    final Set<ZoneOffset> zoneOffsets =
                            ImmutableSet.copyOf(
                                    Collections2.filter(unknownAsNulls, Objects::nonNull));
                    if (zoneOffsets.size() == 1) {
                        return Instant.now().atZone(Iterables.getOnlyElement(zoneOffsets));
                    } else {
                        throw new IllegalStateException("Ambiguous time zones");
                    }
                },
                MoreExecutors.directExecutor());
    }

    public static boolean isNightTime(final ZonedDateTime zonedDateTime) {
        final var localTime = zonedDateTime.toLocalTime();
        return localTime.isAfter(LocalTime.of(22, 0)) || localTime.isBefore(LocalTime.of(8, 0));
    }

    public static boolean isDifferentTimeZone(final ZonedDateTime zonedDateTime) {
        final var local = ZonedDateTime.now();
        return !local.getOffset().equals(zonedDateTime.getOffset());
    }

    public static boolean noRecentMessages(final Conversation conversation) {
        final var duration = Duration.between(conversation.getLastReceived(), Instant.now());
        return DURATION_RECENT_MESSAGE.compareTo(duration) < 0;
    }

    public sealed interface EntityTime {}

    public record NoEntityTime() implements EntityTime {}

    public record OffsetEntityTime(ZoneOffset zoneOffset) implements EntityTime {}

    public record InvalidEntityTime() implements EntityTime {}
}
