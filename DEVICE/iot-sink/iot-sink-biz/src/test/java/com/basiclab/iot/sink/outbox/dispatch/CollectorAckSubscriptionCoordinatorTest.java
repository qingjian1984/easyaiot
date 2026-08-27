package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRouteSetProvider;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LC03-02 §4.1/§4.2 exact ACK subscription coordinator contract, exercised
 * against a real in-process vertx-mqtt server on an ephemeral port.
 */
@Timeout(30)
class CollectorAckSubscriptionCoordinatorTest {

    private static final String ACK_TOPIC_SUFFIX = "/property/downstream/report/ack";

    private static Vertx vertx;
    private MqttServer server;
    private MqttClient client;
    private int port;

    private final List<String> subscribedTopics = new CopyOnWriteArrayList<>();
    private final List<String> unsubscribedTopics = new CopyOnWriteArrayList<>();
    private final AtomicReference<Consumer<String>> subscribeDecision = new AtomicReference<>(topic -> { });

    @BeforeAll
    static void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @BeforeEach
    void startBrokerAndClient() throws Exception {
        Promise<MqttServer> started = Promise.promise();
        server = MqttServer.create(vertx, new MqttServerOptions().setPort(0));
        server.endpointHandler(endpoint -> {
            endpoint.accept(false);
            endpoint.subscribeHandler(subscribe -> {
                String filter = subscribe.topicSubscriptions().get(0).topicName();
                subscribedTopics.add(filter);
                try {
                    subscribeDecision.get().accept(filter);
                } catch (RuntimeException deny) {
                    endpoint.subscribeAcknowledge(subscribe.messageId(),
                            List.of(MqttSubAckReasonCode.UNSPECIFIED_ERROR), new MqttProperties());
                    return;
                }
                endpoint.subscribeAcknowledge(subscribe.messageId(),
                        List.of(MqttSubAckReasonCode.GRANTED_QOS1), new MqttProperties());
            });
            endpoint.unsubscribeHandler(unsubscribe -> {
                unsubscribedTopics.add(unsubscribe.topics().get(0));
                endpoint.unsubscribeAcknowledge(unsubscribe.messageId(),
                        List.of(MqttUnsubAckReasonCode.SUCCESS), new MqttProperties());
            });
        });
        server.listen(started);
        port = started.future().toCompletionStage().toCompletableFuture()
                .get(5, TimeUnit.SECONDS).actualPort();

        client = MqttClient.create(vertx, new MqttClientOptions()
                .setClientId("lc03-02-coordinator-test")
                .setCleanSession(true));
        client.connect(port, "localhost").toCompletionStage().toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void cleanup() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
        } catch (Exception ignore) {
            // Best-effort teardown.
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void recoverySubscribesTheExactUnionAndNothingBroad() {
        MutableProvider provider = new MutableProvider(List.of(
                route("power-meter", "meter-01"),
                route("power-meter", "meter-02")));
        CollectorAckSubscriptionCoordinator coordinator =
                new CollectorAckSubscriptionCoordinator(client, provider);

        Set<TelemetryRoute> active = coordinator.recover();

        assertEquals(Set.of(route("power-meter", "meter-01"), route("power-meter", "meter-02")), active);
        assertEquals(List.of(
                "/iot/power-meter/meter-01" + ACK_TOPIC_SUFFIX,
                "/iot/power-meter/meter-02" + ACK_TOPIC_SUFFIX), subscribedTopics);
        assertTrue(subscribedTopics.stream().noneMatch(topic ->
                topic.contains("#") || topic.contains("+") || topic.contains("$")));
        assertTrue(coordinator.isReady());
    }

    @Test
    void emptyUnionNeverSubscribesAnythingBroad() {
        CollectorAckSubscriptionCoordinator coordinator =
                new CollectorAckSubscriptionCoordinator(client, new MutableProvider(List.of()));

        Set<TelemetryRoute> active = coordinator.recover();

        assertTrue(active.isEmpty());
        assertTrue(subscribedTopics.isEmpty());
        assertTrue(coordinator.isReady());
    }

    @Test
    void additionsSwapOnlyAfterEverySuback() {
        MutableProvider provider = new MutableProvider(List.of(route("p-a", "d-a")));
        CollectorAckSubscriptionCoordinator coordinator =
                new CollectorAckSubscriptionCoordinator(client, provider);
        assertEquals(Set.of(route("p-a", "d-a")), coordinator.recover());

        provider.set(List.of(route("p-a", "d-a"), route("p-b", "d-b")));
        subscribeDecision.set(topic -> {
            if (topic.contains("p-b")) {
                throw new RuntimeException("simulated broker failure");
            }
        });
        Set<TelemetryRoute> active = coordinator.refresh();

        assertEquals(Set.of(route("p-a", "d-a")), active);
        assertFalse(coordinator.isReady());
        assertTrue(coordinator.isRouteActive(route("p-a", "d-a")));
        assertFalse(coordinator.isRouteActive(route("p-b", "d-b")));
    }

    @Test
    void removalsAreUnsubscribedOnlyAfterAdditionsSwap() {
        MutableProvider provider = new MutableProvider(List.of(route("p-a", "d-a"), route("p-b", "d-b")));
        CollectorAckSubscriptionCoordinator coordinator =
                new CollectorAckSubscriptionCoordinator(client, provider);
        coordinator.recover();
        assertEquals(2, subscribedTopics.size());

        provider.set(List.of(route("p-b", "d-b")));
        Set<TelemetryRoute> active = coordinator.refresh();

        assertEquals(Set.of(route("p-b", "d-b")), active);
        assertEquals(List.of("/iot/p-a/d-a" + ACK_TOPIC_SUFFIX), unsubscribedTopics);
    }

    @Test
    void unfinishedOutboxRouteKeepsItsSubscriptionAfterUnbind() {
        MutableProvider provider = new MutableProvider(List.of(route("p-a", "d-a")));
        CollectorAckSubscriptionCoordinator coordinator =
                new CollectorAckSubscriptionCoordinator(client, provider);
        coordinator.recover();

        // The provider union keeps the route because the outbox still has
        // PENDING/IN_FLIGHT rows for it; nothing may be unsubscribed.
        provider.set(List.of(route("p-a", "d-a")));
        Set<TelemetryRoute> active = coordinator.refresh();
        assertEquals(Set.of(route("p-a", "d-a")), active);
        assertTrue(unsubscribedTopics.isEmpty());
    }

    @Test
    void runWhenReadyFiresAfterFullSubackAndNotBefore() {
        List<String> fired = new CopyOnWriteArrayList<>();
        MutableProvider provider = new MutableProvider(List.of(route("p-a", "d-a")));
        CollectorAckSubscriptionCoordinator coordinator =
                new CollectorAckSubscriptionCoordinator(client, provider);
        coordinator.runWhenReady(() -> fired.add("dispatcher"));
        assertTrue(fired.isEmpty(), "dispatcher must not start before initial SUBACK");

        coordinator.recover();
        assertEquals(List.of("dispatcher"), fired);
    }

    private static TelemetryRoute route(String product, String device) {
        return new TelemetryRoute(product, device);
    }

    private static final class MutableProvider implements TelemetryRouteSetProvider {
        private volatile List<TelemetryRoute> routes;

        MutableProvider(List<TelemetryRoute> routes) {
            this.routes = List.copyOf(routes);
        }

        void set(List<TelemetryRoute> routes) {
            this.routes = List.copyOf(routes);
        }

        @Override
        public List<TelemetryRoute> currentRoutes() {
            return List.copyOf(new java.util.TreeSet<>(routes));
        }
    }
}
