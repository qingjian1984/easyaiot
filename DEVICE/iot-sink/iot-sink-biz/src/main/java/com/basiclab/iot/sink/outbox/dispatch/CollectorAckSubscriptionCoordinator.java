package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRouteSetProvider;
import io.vertx.mqtt.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * LC03-02 §4.1/§4.2 exact ACK subscription coordinator.
 *
 * <p>The only accepted subscription set is the union of the applied
 * ConfigSnapshot routes and the PENDING/IN_FLIGHT outbox routes, mapped to
 * {@link TelemetryRoute#ackTopic()}.  Refresh order is frozen: read one
 * immutable snapshot, subscribe all additions first, swap the active set only
 * after every addition received a successful SUBACK, and only then unsubscribe
 * removals.  A failed addition keeps the previous active set and reports
 * {@code ACK_SUBSCRIPTION_NOT_READY}; a failed unsubscribe only delays
 * cleanup.  Wildcards, shared groups and {@code $queue} filters are never
 * produced here — every filter is one exact topic.
 */
public final class CollectorAckSubscriptionCoordinator implements AutoCloseable {

    /** Stable code reported while the exact subscription set is not ready. */
    public static final String NOT_READY_CODE = "ACK_SUBSCRIPTION_NOT_READY";

    private static final Logger log = LoggerFactory.getLogger(CollectorAckSubscriptionCoordinator.class);
    private static final long SUBACK_TIMEOUT_MS = 10_000L;

    private final MqttClient client;
    private final TelemetryRouteSetProvider routeSetProvider;
    private final long subackTimeoutMs;
    private final AtomicReference<Set<TelemetryRoute>> active = new AtomicReference<>(Set.of());
    private final CopyOnWriteArraySet<TelemetryRoute> subscribed = new CopyOnWriteArraySet<>();
    private volatile boolean closed;

    public CollectorAckSubscriptionCoordinator(MqttClient client,
                                               TelemetryRouteSetProvider routeSetProvider) {
        this(client, routeSetProvider, SUBACK_TIMEOUT_MS);
    }

    CollectorAckSubscriptionCoordinator(MqttClient client,
                                        TelemetryRouteSetProvider routeSetProvider,
                                        long subackTimeoutMs) {
        this.client = client;
        this.routeSetProvider = routeSetProvider;
        this.subackTimeoutMs = subackTimeoutMs;
    }

    /**
     * Reconcile the exact subscription set once.  Returns the routes that are
     * safely active after the call; on any addition failure the previously
     * active set is preserved unchanged.
     */
    public synchronized Set<TelemetryRoute> refresh() {
        if (closed) {
            return Set.copyOf(active.get());
        }
        List<TelemetryRoute> desired = List.copyOf(new TreeSet<>(routeSetProvider.currentRoutes()));
        Set<TelemetryRoute> current = active.get();

        List<TelemetryRoute> additions = desired.stream()
                .filter(route -> !current.contains(route))
                .collect(Collectors.toList());
        // A route that still has unfinished outbox rows must keep its
        // subscription even when the applied config dropped it; the provider
        // union already carries those routes, so removals below only contain
        // routes with no unfinished rows left.
        List<TelemetryRoute> removals = current.stream()
                .filter(route -> !desiredSet(desired).contains(route))
                .collect(Collectors.toList());

        if (additions.isEmpty() && removals.isEmpty()) {
            fireReadyIfSatisfied(desired);
            return Set.copyOf(current);
        }

        for (TelemetryRoute route : additions) {
            if (!subscribeExact(route)) {
                log.warn("exact ACK subscription not ready: code={} product={} device={}",
                        NOT_READY_CODE,
                        route.productIdentification(), route.deviceIdentification());
                return Set.copyOf(current);
            }
            subscribed.add(route);
        }

        Set<TelemetryRoute> next = new LinkedHashSet<>(current);
        next.addAll(additions);
        Set<TelemetryRoute> swapped = Collections.unmodifiableSet(next);
        active.set(swapped);
        fireReadyIfSatisfied(desired);

        for (TelemetryRoute route : removals) {
            if (unsubscribeExact(route)) {
                subscribed.remove(route);
                removeFromActive(route);
            } else {
                log.info("ACK unsubscribe deferred for route product={} device={}",
                        route.productIdentification(), route.deviceIdentification());
            }
        }
        return Set.copyOf(active.get());
    }

    private void removeFromActive(TelemetryRoute route) {
        Set<TelemetryRoute> current = active.get();
        if (!current.contains(route)) {
            return;
        }
        Set<TelemetryRoute> next = new LinkedHashSet<>(current);
        next.remove(route);
        active.set(Collections.unmodifiableSet(next));
    }

    /** Routes whose exact ACK SUBACK has succeeded and not been unsubscribed. */
    public Set<TelemetryRoute> activeRoutes() {
        return Set.copyOf(active.get());
    }

    /** Per-route publish gate: true only after that route's exact SUBACK. */
    public boolean isRouteActive(TelemetryRoute route) {
        return active.get().contains(route);
    }

    /**
     * Run the callback once the subscription set has been (or becomes)
     * fully SUBACKed; used to delay dispatcher start until initial recovery
     * succeeds without blocking bean construction.
     */
    public void runWhenReady(Runnable callback) {
        if (isReady()) {
            callback.run();
            return;
        }
        readyCallbacks.add(callback);
    }

    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> readyCallbacks =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** True only when the latest refresh fully subscribed its desired set. */
    public boolean isReady() {
        Set<TelemetryRoute> current = active.get();
        return current.containsAll(new TreeSet<>(routeSetProvider.currentRoutes()));
    }

    /** Initial recovery entrypoint: refresh once and report readiness. */
    public Set<TelemetryRoute> recover() {
        return refresh();
    }

    private boolean subscribeExact(TelemetryRoute route) {
        if (!client.isConnected()) {
            return false;
        }
        try {
            CompletableFuture<Boolean> suback = new CompletableFuture<>();
            pendingSubscribe.set(suback);
            client.subscribeCompletionHandler(message -> {
                CompletableFuture<Boolean> waiter = pendingSubscribe.getAndSet(null);
                if (waiter != null) {
                    // MQTT 3.1.1 failure code is 0x80; any granted level 0-2
                    // means the broker accepted the exact filter.
                    waiter.complete(!message.grantedQoSLevels().isEmpty()
                            && message.grantedQoSLevels().get(0) != 0x80);
                }
            });
            client.subscribe(route.ackTopic(), 1);
            boolean granted = Boolean.TRUE.equals(suback.get(subackTimeoutMs, TimeUnit.MILLISECONDS));
            pendingSubscribe.compareAndSet(suback, null);
            return granted;
        } catch (Exception e) {
            pendingSubscribe.set(null);
            // Only the stable classification is logged; never the topic-level
            // identity of a half-subscribed route set or any payload.
            log.warn("ACK subscribe failed: code={} error={}",
                    NOT_READY_CODE, e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean unsubscribeExact(TelemetryRoute route) {
        if (!client.isConnected()) {
            return false;
        }
        try {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            pendingUnsubscribe.set(done);
            client.unsubscribeCompletionHandler(ignored -> {
                CompletableFuture<Boolean> waiter = pendingUnsubscribe.getAndSet(null);
                if (waiter != null) {
                    waiter.complete(true);
                }
            });
            client.unsubscribe(route.ackTopic());
            boolean finished = Boolean.TRUE.equals(done.get(subackTimeoutMs, TimeUnit.MILLISECONDS));
            pendingUnsubscribe.compareAndSet(done, null);
            return finished;
        } catch (Exception e) {
            pendingUnsubscribe.set(null);
            log.info("ACK unsubscribe failed: error={}", e.getClass().getSimpleName());
            return false;
        }
    }

    private final AtomicReference<CompletableFuture<Boolean>> pendingSubscribe = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Boolean>> pendingUnsubscribe = new AtomicReference<>();

    private static Set<TelemetryRoute> desiredSet(List<TelemetryRoute> desired) {
        return new LinkedHashSet<>(desired);
    }

    private void fireReadyIfSatisfied(List<TelemetryRoute> desired) {
        if (desired.isEmpty() || active.get().containsAll(desired)) {
            for (Runnable callback : readyCallbacks) {
                try {
                    callback.run();
                } catch (RuntimeException e) {
                    log.warn("ready callback failed: error={}", e.getClass().getSimpleName());
                }
            }
            readyCallbacks.clear();
        }
    }

    @Override
    public void close() {
        closed = true;
        List<TelemetryRoute> toRemove = new ArrayList<>(subscribed);
        for (TelemetryRoute route : toRemove) {
            try {
                client.unsubscribe(route.ackTopic());
            } catch (Exception ignore) {
                // Closing is best effort; the broker session drops the rest.
            }
        }
        subscribed.clear();
        active.set(Set.of());
    }
}
