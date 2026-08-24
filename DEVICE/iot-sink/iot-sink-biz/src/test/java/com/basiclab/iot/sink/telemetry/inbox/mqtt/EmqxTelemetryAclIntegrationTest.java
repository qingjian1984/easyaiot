package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryUpstreamTopicParser;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** LC02-09-R1 real EMQX 5.8.7 authentication and authorization contract. */
@EnabledIfEnvironmentVariable(named = "LC02_09_EMQX_ENABLED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmqxTelemetryAclIntegrationTest {

    private static final String COLLECTOR_A = "lc02-collector-a";
    private static final String COLLECTOR_B = "lc02-collector-b";
    private static final String CENTER = "lc02-center-inbox";
    private static final String TOPIC_A = "/iot/product-a/device-a/property/upstream/report";
    private static final String TOPIC_B = "/iot/product-b/device-b/property/upstream/report";
    private static final String SHARED_FILTER =
            "$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Duration ABSENCE_WINDOW = Duration.ofMillis(750);

    private Vertx vertx;
    private String host;
    private int port;
    private String passwordA;
    private String passwordB;
    private String passwordCenter;

    @BeforeAll
    void requireExplicitIsolatedFixture() {
        host = requiredEnv("LC02_09_EMQX_HOST");
        port = Integer.parseInt(requiredEnv("LC02_09_EMQX_PORT"));
        passwordA = requiredEnv("LC02_09_COLLECTOR_A_PASSWORD");
        passwordB = requiredEnv("LC02_09_COLLECTOR_B_PASSWORD");
        passwordCenter = requiredEnv("LC02_09_CENTER_PASSWORD");
        assertTrue(port > 0 && port <= 65535, "isolated broker port must be valid");
        vertx = Vertx.vertx();
    }

    @AfterAll
    void closeVertx() throws Exception {
        if (vertx != null) {
            await(vertx.close());
        }
    }

    @Test
    @Order(1)
    void lc02_09_01_rejectsAnonymousUnknownAndWrongPasswords() throws Exception {
        assertConnectRejected(null, null);
        assertConnectRejected("lc02-unknown", "not-a-fixture-secret");
        assertConnectRejected(COLLECTOR_A, "wrong-a");
        assertConnectRejected(COLLECTOR_B, "wrong-b");
        assertConnectRejected(CENTER, "wrong-center");
    }

    @Test
    @Order(2)
    void lc02_09_02_acceptsOnlyAuthenticatedNonSuperuserPrincipals() throws Exception {
        for (Credential credential : credentials()) {
            MqttClient client = connect(credential.username(), credential.password(), "connect-ok");
            close(client);
        }
    }

    @Test
    @Order(3)
    void lc02_09_03_deliversExactQosOneNonRetainedPublishes() throws Exception {
        BlockingQueue<Received> received = new LinkedBlockingQueue<>();
        MqttClient center = connect(CENTER, passwordCenter, "allowed-center");
        center.publishHandler(message -> received.offer(new Received(
                message.topicName(), message.payload().getBytes().clone())));
        subscribeAllowed(center, SHARED_FILTER, 1);

        byte[] payloadA = "遥测-A-原始字节".getBytes(StandardCharsets.UTF_8);
        byte[] payloadB = new byte[]{0x00, 0x01, (byte) 0xFE, 0x7F};
        MqttClient collectorA = connect(COLLECTOR_A, passwordA, "allowed-a");
        MqttClient collectorB = connect(COLLECTOR_B, passwordB, "allowed-b");
        await(collectorA.publish(TOPIC_A, Buffer.buffer(payloadA),
                MqttQoS.AT_LEAST_ONCE, false, false));
        await(collectorB.publish(TOPIC_B, Buffer.buffer(payloadB),
                MqttQoS.AT_LEAST_ONCE, false, false));

        Received first = poll(received);
        Received second = poll(received);
        Map<String, byte[]> byTopic = Map.of(first.topic(), first.payload(),
                second.topic(), second.payload());
        assertArrayEquals(payloadA, byTopic.get(TOPIC_A));
        assertArrayEquals(payloadB, byTopic.get(TOPIC_B));
        assertNoMessage(received);
        close(collectorA, collectorB, center);
    }

    @Test
    @Order(4)
    void lc02_09_04_rejectsCrossDeviceAndSiblingRoutes() throws Exception {
        assertPublishDenied(COLLECTOR_A, passwordA, TOPIC_B, MqttQoS.AT_LEAST_ONCE, false);
        assertPublishDenied(COLLECTOR_A, passwordA,
                "/iot/product-a/device-b/property/upstream/report", MqttQoS.AT_LEAST_ONCE, false);
        assertPublishDenied(COLLECTOR_B, passwordB, TOPIC_A, MqttQoS.AT_LEAST_ONCE, false);
        assertPublishDenied(COLLECTOR_B, passwordB,
                "/iot/product-c/device-b/property/upstream/report", MqttQoS.AT_LEAST_ONCE, false);
    }

    @Test
    @Order(5)
    void lc02_09_05_rejectsLegacyAckSystemWrongQosAndRetainedPublishes() throws Exception {
        assertPublishDenied(COLLECTOR_A, passwordA, "/telemetry/device-a",
                MqttQoS.AT_LEAST_ONCE, false);
        assertPublishDenied(COLLECTOR_A, passwordA,
                "/iot/product-a/device-a/property/downstream/report/ack",
                MqttQoS.AT_LEAST_ONCE, false);
        assertPublishDenied(COLLECTOR_A, passwordA, "$SYS/lc02-probe",
                MqttQoS.AT_LEAST_ONCE, false);
        assertPublishDenied(COLLECTOR_A, passwordA, TOPIC_A, MqttQoS.AT_MOST_ONCE, false);
        assertPublishDenied(COLLECTOR_A, passwordA, TOPIC_A, MqttQoS.EXACTLY_ONCE, false);
        assertPublishDenied(COLLECTOR_A, passwordA, TOPIC_A, MqttQoS.AT_LEAST_ONCE, true);
    }

    @Test
    @Order(6)
    void lc02_09_06_rejectsEveryCollectorSubscription() throws Exception {
        for (String filter : List.of(TOPIC_A,
                TelemetryUpstreamTopicParser.baseSubscriptionFilter(), "#")) {
            assertSubscribeDenied(COLLECTOR_A, passwordA, filter, 1);
        }
        assertSubscribeDenied(COLLECTOR_B, passwordB, TOPIC_B, 1);
    }

    @Test
    @Order(7)
    void lc02_09_07_loadBalancesWithinTheFixedSharedGroupExactlyOnce() throws Exception {
        CountDownLatch messages = new CountDownLatch(8);
        Map<String, Integer> occurrences = new ConcurrentHashMap<>();
        MqttClient centerOne = connect(CENTER, passwordCenter, "shared-one");
        MqttClient centerTwo = connect(CENTER, passwordCenter, "shared-two");
        java.util.function.Consumer<io.vertx.mqtt.messages.MqttPublishMessage> record = message -> {
            String id = message.payload().toString(StandardCharsets.UTF_8);
            occurrences.merge(id, 1, Integer::sum);
            messages.countDown();
        };
        centerOne.publishHandler(record::accept);
        centerTwo.publishHandler(record::accept);
        subscribeAllowed(centerOne, SHARED_FILTER, 1);
        subscribeAllowed(centerTwo, SHARED_FILTER, 1);

        MqttClient collectorA = connect(COLLECTOR_A, passwordA, "shared-a");
        MqttClient collectorB = connect(COLLECTOR_B, passwordB, "shared-b");
        for (int index = 0; index < 8; index++) {
            String id = "shared-message-" + index;
            MqttClient publisher = index % 2 == 0 ? collectorA : collectorB;
            String topic = index % 2 == 0 ? TOPIC_A : TOPIC_B;
            await(publisher.publish(topic, Buffer.buffer(id, StandardCharsets.UTF_8.name()),
                    MqttQoS.AT_LEAST_ONCE, false, false));
        }
        assertTrue(messages.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                "shared group did not receive all messages");
        assertEquals(8, occurrences.size());
        assertTrue(occurrences.values().stream().allMatch(count -> count == 1),
                "a shared message was duplicated");
        close(collectorA, collectorB, centerOne, centerTwo);
    }

    @Test
    @Order(8)
    void lc02_09_08_profilesSharedGroupAndRejectsOtherCenterSubscriptions() throws Exception {
        // EMQX 5.8.7 strips $share/{group}/ before file authorization.  The
        // three QoS-1 filters therefore have the same broker capability
        // profile; Java's direct contract remains the fixed-group guardrail.
        for (String filter : List.of(
                SHARED_FILTER,
                TelemetryUpstreamTopicParser.baseSubscriptionFilter(),
                "$share/lc02-other//iot/+/+/property/upstream/report")) {
            assertSubscribeAllowedThenClose(CENTER, passwordCenter, filter, 1);
        }

        for (SubscriptionAttempt attempt : List.of(
                new SubscriptionAttempt("/iot/#", 1),
                new SubscriptionAttempt("#", 1),
                new SubscriptionAttempt("/telemetry/#", 1),
                new SubscriptionAttempt("/iot/+/+/property/downstream/report/ack", 1),
                new SubscriptionAttempt("$SYS/#", 1),
                new SubscriptionAttempt(SHARED_FILTER, 0),
                new SubscriptionAttempt(SHARED_FILTER, 2))) {
            assertSubscribeDenied(CENTER, passwordCenter, attempt.filter(), attempt.qos());
        }
    }

    @Test
    @Order(9)
    void lc02_09_09_rejectsEveryCenterPublish() throws Exception {
        for (String topic : List.of(TOPIC_A,
                "/iot/product-a/device-a/property/downstream/report/ack", "$SYS/lc02-center")) {
            assertPublishDenied(CENTER, passwordCenter, topic, MqttQoS.AT_LEAST_ONCE, false);
        }
    }

    @Test
    @Order(10)
    void lc02_09_10_hasNoClientIdOrDefaultRuleBypass() throws Exception {
        assertPublishDenied(COLLECTOR_A, passwordA,
                "/iot/unknown/unknown/property/upstream/report", MqttQoS.AT_LEAST_ONCE, false);
        assertPublishDenied(CENTER, passwordCenter, TOPIC_A, MqttQoS.AT_LEAST_ONCE, false);
        MqttClient reconnected = connect(COLLECTOR_A, passwordA, "new-client-id");
        close(reconnected);
    }

    @Test
    @Order(11)
    void lc02_09_11_usesTheFrozenDerivedFilters() {
        assertEquals("/iot/+/+/property/upstream/report",
                TelemetryUpstreamTopicParser.baseSubscriptionFilter());
        assertEquals(SHARED_FILTER, TelemetryUpstreamTopicParser.sharedSubscriptionFilter());
        assertNotEquals(TelemetryUpstreamTopicParser.baseSubscriptionFilter(), SHARED_FILTER);
    }

    @Test
    @Order(12)
    void lc02_09_12_fixtureInputsContainNoRepositoryCredentialFallback() {
        assertFalse(passwordA.isBlank());
        assertFalse(passwordB.isBlank());
        assertFalse(passwordCenter.isBlank());
        assertNotEquals(passwordA, passwordB);
        assertNotEquals(passwordA, passwordCenter);
        assertNotEquals(passwordB, passwordCenter);
    }

    private List<Credential> credentials() {
        return List.of(new Credential(COLLECTOR_A, passwordA),
                new Credential(COLLECTOR_B, passwordB),
                new Credential(CENTER, passwordCenter));
    }

    private void assertConnectRejected(String username, String password) throws Exception {
        MqttClient client = client(username, password, "connect-denied");
        try {
            MqttConnAckMessage acknowledgement = await(client.connect(port, host));
            assertNotEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, acknowledgement.code());
        } catch (Exception expected) {
            // A closed socket is also a deterministic authentication rejection.
        } finally {
            close(client);
        }
    }

    private void assertPublishDenied(String username, String password, String topic,
                                     MqttQoS qos, boolean retained) throws Exception {
        MqttClient client = connect(username, password, "publish-denied");
        CountDownLatch closed = new CountDownLatch(1);
        client.closeHandler(ignored -> closed.countDown());
        try {
            client.publish(topic, Buffer.buffer("acl-denied"), qos, false, retained);
        } catch (RuntimeException ignored) {
            // Client-side write failure after broker close is expected.
        }
        assertTrue(closed.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                "broker did not disconnect a denied publisher");
        close(client);
    }

    private void assertSubscribeDenied(String username, String password, String filter, int qos)
            throws Exception {
        MqttClient client = connect(username, password, "subscribe-denied");
        CountDownLatch closed = new CountDownLatch(1);
        client.closeHandler(ignored -> closed.countDown());
        client.subscribe(filter, qos);
        assertTrue(closed.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                "broker did not disconnect a denied subscriber");
        close(client);
    }

    private void assertSubscribeAllowedThenClose(String username, String password,
                                                 String filter, int qos) throws Exception {
        MqttClient client = connect(username, password, "subscribe-profile");
        try {
            subscribeAllowed(client, filter, qos);
        } finally {
            close(client);
        }
    }

    private void subscribeAllowed(MqttClient client, String filter, int qos) throws Exception {
        BlockingQueue<MqttSubAckMessage> acknowledgements = new LinkedBlockingQueue<>();
        client.subscribeCompletionHandler(acknowledgements::offer);
        await(client.subscribe(filter, qos));
        MqttSubAckMessage acknowledgement = acknowledgements.poll(
                TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(acknowledgement, "broker did not return SUBACK");
        assertEquals(List.of(qos), acknowledgement.grantedQoSLevels());
        assertTrue(client.isConnected());
    }

    private MqttClient connect(String username, String password, String label) throws Exception {
        MqttClient client = client(username, password, label);
        MqttConnAckMessage acknowledgement = await(client.connect(port, host));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, acknowledgement.code());
        assertTrue(client.isConnected());
        return client;
    }

    private MqttClient client(String username, String password, String label) {
        MqttClientOptions options = new MqttClientOptions()
                .setClientId("lc02-09-" + label + "-" + UUID.randomUUID())
                .setCleanSession(true)
                .setKeepAliveInterval(10);
        options.setConnectTimeout((int) TIMEOUT.toMillis());
        if (username != null) {
            options.setUsername(username);
            options.setPassword(password);
        }
        return MqttClient.create(vertx, options);
    }

    private static Received poll(BlockingQueue<Received> messages) throws InterruptedException {
        Received received = messages.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(received, "expected MQTT message was not delivered");
        return received;
    }

    private static void assertNoMessage(BlockingQueue<Received> messages) throws InterruptedException {
        assertEquals(null, messages.poll(ABSENCE_WINDOW.toMillis(), TimeUnit.MILLISECONDS),
                "unexpected additional MQTT message");
    }

    private static <T> T await(Future<T> future) throws Exception {
        CompletableFuture<T> completion = future.toCompletionStage().toCompletableFuture();
        return completion.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static void close(MqttClient... clients) {
        List<Exception> failures = new ArrayList<>();
        for (MqttClient client : clients) {
            if (client != null && client.isConnected()) {
                try {
                    await(client.disconnect());
                } catch (Exception exception) {
                    failures.add(exception);
                }
            }
        }
        if (!failures.isEmpty()) {
            fail("failed to close MQTT client: " + failures.get(0).getClass().getSimpleName());
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        assertNotNull(value, name + " must be provided by the isolated fixture");
        assertFalse(value.isBlank(), name + " must be nonblank");
        return value;
    }

    private record Credential(String username, String password) {
    }

    private record SubscriptionAttempt(String filter, int qos) {
    }

    private record Received(String topic, byte[] payload) {
        private Received {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
