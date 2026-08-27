package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.outbox.sqlite.SqliteOutboxMigration;
import com.basiclab.iot.sink.outbox.sqlite.SqliteTelemetryOutbox;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1Codec;
import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LC03-02 §4.3 inbound association contract: exact topic parse, strict ACK V1
 * decode, requestId/route match, and the PENDING/IN_FLIGHT/ACKED/DEAD_LETTER
 * idempotence matrix — exercised end to end against a real in-process MQTT
 * server and a real SQLite outbox.
 */
@Timeout(60)
class CollectorMqttAckSubscriberV1Test {

    private static final String PRODUCT = "power-meter";
    private static final String DEVICE = "meter-01";
    private static final String ACK_TOPIC =
            "/iot/power-meter/meter-01/property/downstream/report/ack";
    private static final String MESSAGE_36 = "2ca80f25-4b6c-443f-a114-1b3df0a8cdf9";
    private static final TelemetryAckV1Codec CODEC = new TelemetryAckV1Codec();

    private static Vertx vertx;

    @TempDir
    Path dir;
    private MqttServer server;
    private MqttClient client;
    private SqliteTelemetryOutbox outbox;
    private Path dbPath;
    private int port;
    private io.vertx.mqtt.MqttEndpoint endpoint;

    @BeforeAll
    static void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @BeforeEach
    void setup() throws Exception {
        dbPath = dir.resolve("outbox.db");
        SqliteOutboxMigration.migrate(dbPath);
        outbox = new SqliteTelemetryOutbox(dbPath, new EnvelopeCanonicalCodec(), 100);

        CompletableFuture<Void> endpointReady = new CompletableFuture<>();
        server = MqttServer.create(vertx, new MqttServerOptions().setPort(0));
        server.endpointHandler(ep -> {
            endpoint = ep;
            ep.accept(false);
            ep.subscribeHandler(subscribe -> {
                String filter = subscribe.topicSubscriptions().get(0).topicName();
                if (filter.equals(ACK_TOPIC)) {
                    ep.subscribeAcknowledge(subscribe.messageId(),
                            List.of(MqttSubAckReasonCode.GRANTED_QOS1), new MqttProperties());
                } else {
                    ep.subscribeAcknowledge(subscribe.messageId(),
                            List.of(MqttSubAckReasonCode.NOT_AUTHORIZED), new MqttProperties());
                }
            });
            ep.publishHandler(message -> ep.publishAcknowledge(message.messageId()));
            endpointReady.complete(null);
        });
        CompletableFuture<Integer> listening = new CompletableFuture<>();
        server.listen(ar -> listening.complete(ar.result().actualPort()));
        port = listening.get(5, TimeUnit.SECONDS);

        client = MqttClient.create(vertx, new MqttClientOptions()
                .setClientId("lc03-02-subscriber-test")
                .setCleanSession(true));
        client.connect(port, "localhost").toCompletionStage().toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        new CollectorMqttAckSubscriber(outbox, client).start();
        CompletableFuture<Void> subscribed = new CompletableFuture<>();
        client.subscribeCompletionHandler(ack -> subscribed.complete(null));
        client.subscribe(ACK_TOPIC, 1).toCompletionStage().toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        subscribed.get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void teardown() throws Exception {
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
        if (outbox != null) {
            outbox.shutdown();
        }
    }

    @Test
    void acceptedDurableAckTransitionsInFlightToAckedOverRealMqtt() throws Exception {
        String requestId = appendAndClaimOne();

        publishAck(ack(TelemetryAckStatus.ACCEPTED_DURABLE, 0, "OK", requestId));

        awaitStatus(MESSAGE_36, "ACKED");
    }

    @Test
    void duplicateAckIsIdempotentOnAlreadyAckedRow() throws Exception {
        String requestId = appendAndClaimOne();
        publishAck(ack(TelemetryAckStatus.ACCEPTED_DURABLE, 0, "OK", requestId));
        awaitStatus(MESSAGE_36, "ACKED");

        publishAck(ack(TelemetryAckStatus.DUPLICATE, 1001, "DUPLICATE", requestId));

        awaitStatus(MESSAGE_36, "ACKED");
        assertEquals(0, gapCount(), "duplicate ACK on ACKED row must not create a gap");
    }

    @Test
    void pendingRowIsAckedBySuccessAckWithoutClaim() throws Exception {
        outbox.appendBatch(batch(env(MESSAGE_36)), Duration.ofSeconds(5));

        publishAck(ack(TelemetryAckStatus.DUPLICATE, 1001, "DUPLICATE",
                requestIdOfRow(MESSAGE_36)));

        awaitStatus(MESSAGE_36, "ACKED");
    }

    @Test
    void requestIdMismatchLeavesStateUntouched() throws Exception {
        appendAndClaimOne();

        publishAck(ack(TelemetryAckStatus.ACCEPTED_DURABLE, 0, "OK",
                "00000000-0000-4000-8000-000000000000"));

        Thread.sleep(400);
        assertEquals("IN_FLIGHT", statusOf(MESSAGE_36));
        assertEquals(0, gapCount());
    }

    @Test
    void wrongRouteTopicLeavesStateUntouched() throws Exception {
        appendAndClaimOne();
        String requestId = requestIdOfRow(MESSAGE_36);

        // Same payload, but published on a different device's ACK topic.
        byte[] payload = CODEC.encode(ack(TelemetryAckStatus.ACCEPTED_DURABLE, 0, "OK", requestId));
        endpoint.publish("/iot/power-meter/meter-99/property/downstream/report/ack",
                Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false);

        Thread.sleep(400);
        assertEquals("IN_FLIGHT", statusOf(MESSAGE_36));
    }

    @Test
    void legacyFourFieldWireIsNeverApplied() throws Exception {
        appendAndClaimOne();

        String legacy = "{\"messageId\":\"" + MESSAGE_36 + "\",\"resultCode\":\"ACCEPTED_DURABLE\","
                + "\"errorCode\":\"OK\",\"observedAt\":1691234567890}";
        endpoint.publish(ACK_TOPIC, Buffer.buffer(legacy), MqttQoS.AT_LEAST_ONCE, false, false);

        Thread.sleep(400);
        assertEquals("IN_FLIGHT", statusOf(MESSAGE_36));
    }

    @Test
    void malformedPayloadOnCanonicalTopicIsDropped() throws Exception {
        appendAndClaimOne();

        endpoint.publish(ACK_TOPIC, Buffer.buffer("not-json"),
                MqttQoS.AT_LEAST_ONCE, false, false);

        Thread.sleep(400);
        assertEquals("IN_FLIGHT", statusOf(MESSAGE_36));
    }

    @Test
    void deadLetterRowIsNotResurrectedBySuccessAck() throws Exception {
        appendAndClaimOne();
        String requestId = requestIdOfRow(MESSAGE_36);
        // Force DEAD_LETTER via the legacy in-memory seam (REJECTED_FINAL).
        outbox.applyAck(new com.basiclab.iot.sink.telemetry.outbox.AckCommand(
                MESSAGE_36, com.basiclab.iot.sink.telemetry.outbox.AckResultCode.REJECTED_FINAL,
                "FORCE", System.currentTimeMillis()));
        awaitStatus(MESSAGE_36, "DEAD_LETTER");

        publishAck(ack(TelemetryAckStatus.ACCEPTED_DURABLE, 0, "OK", requestId));

        Thread.sleep(400);
        assertEquals("DEAD_LETTER", statusOf(MESSAGE_36));
    }

    @Test
    void unknownMessageIdNeverCreatesARow() throws Exception {
        publishAck(ack(TelemetryAckStatus.ACCEPTED_DURABLE, 0, "OK",
                "a9afddc7-02ee-4df3-905b-ec3e4107f25d"));

        Thread.sleep(400);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM telemetry_outbox")) {
            assertEquals(0, rs.next() ? rs.getInt(1) : -1);
        }
    }

    // ==================== helpers ====================

    private String appendAndClaimOne() {
        outbox.appendBatch(batch(env(MESSAGE_36)), Duration.ofSeconds(5));
        outbox.claimBatch(100, Duration.ofMinutes(5));
        return requestIdOfRow(MESSAGE_36);
    }

    private String requestIdOfRow(String messageId) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT request_id FROM telemetry_outbox WHERE message_id = '" + messageId + "'")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void publishAck(TelemetryAckV1 ack) {
        endpoint.publish(ACK_TOPIC, Buffer.buffer(CODEC.encode(ack)),
                MqttQoS.AT_LEAST_ONCE, false, false);
    }

    private static TelemetryAckV1 ack(TelemetryAckStatus status, int code, String reason,
                                      String requestId) {
        return new TelemetryAckV1("1.0", MESSAGE_36, requestId, status, code, reason,
                1_753_850_400_123L);
    }

    private void awaitStatus(String messageId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        String status = statusOf(messageId);
        while (!expected.equals(status) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            status = statusOf(messageId);
        }
        assertEquals(expected, status);
    }

    private String statusOf(String messageId) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT status FROM telemetry_outbox WHERE message_id = '" + messageId + "'")) {
            return rs.next() ? rs.getString(1) : "NOT_FOUND";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int gapCount() {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM telemetry_gap")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static TelemetryEnvelope env(String msgId) {
        // Production PollingResultMapper generates a canonical 36-char UUID
        // for requestId; the ACK V1 contract requires exactly that shape.
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION, TelemetryEnvelope.CANONICALIZATION_VERSION,
                msgId, java.util.UUID.randomUUID().toString(), "123", "site-1", DEVICE,
                "voltage-a", "220.5",
                TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                TelemetryQuality.GOOD, DataPriority.NORMAL_TELEMETRY,
                "2026-08-13T00:00:00Z", "2026-08-13T00:00:00Z", 1, "modbus-rtu", 1);
    }

    private static TelemetryOutboxBatch batch(TelemetryEnvelope... envelopes) {
        return new TelemetryOutboxBatch(PRODUCT, List.of(envelopes));
    }
}
