package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.outbox.dispatch.CollectorAckSubscriptionCoordinator;
import com.basiclab.iot.sink.outbox.dispatch.CollectorMqttAckSubscriber;
import com.basiclab.iot.sink.outbox.sqlite.SqliteOutboxMigration;
import com.basiclab.iot.sink.outbox.sqlite.SqliteTelemetryOutbox;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1Codec;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRouteSetProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * LC03-04A §16.2 deterministic combined-E2E fixture.
 *
 * <p>Real components: in-process Vert.x MQTT server, real SQLite outbox file,
 * real {@link CollectorMqttAckSubscriber}/{@link CollectorAckSubscriptionCoordinator},
 * real {@link CenterTelemetryAckService}/{@link TelemetryAckReconciliationTask} and
 * the production ACK V1 codec/parser. Durable fakes carry the center-side
 * Inbox/dispatch/store facts; rebuilds (restarts) never clear them.
 */
final class Lc03AckE2eFixture implements AutoCloseable {

    /** Single-shot fault barriers allowed by §16.2. */
    enum Fault { NONE, DROP_BEFORE_PUBLISH, PUBLISH_THEN_FAIL_BEFORE_MARK }

    static final class Manifest {
        final String tenantId;
        final String product;
        final String device;
        final String requestId;
        final String messageId;
        final String collisionRequestId;
        final String upstreamTopic;
        final String ackTopic;
        final long firstPersistedAtMs;
        final TelemetryEnvelope acceptedEnvelope;
        final TelemetryEnvelope collisionEnvelope;

        private Manifest(String tenantId, String product, String device, String requestId,
                         String messageId, String collisionRequestId, String upstreamTopic,
                         String ackTopic, long firstPersistedAtMs,
                         TelemetryEnvelope acceptedEnvelope, TelemetryEnvelope collisionEnvelope) {
            this.tenantId = tenantId;
            this.product = product;
            this.device = device;
            this.requestId = requestId;
            this.messageId = messageId;
            this.collisionRequestId = collisionRequestId;
            this.upstreamTopic = upstreamTopic;
            this.ackTopic = ackTopic;
            this.firstPersistedAtMs = firstPersistedAtMs;
            this.acceptedEnvelope = acceptedEnvelope;
            this.collisionEnvelope = collisionEnvelope;
        }
    }

    /** Durable fake Inbox: INSERTED / DUPLICATE / COLLISION by messageId + canonical hash. */
    static final class DurableFakeInbox {
        static final class Row {
            final String messageId;
            final String requestId;
            final String contentSha256;
            final String product;
            final String device;
            final long receivedAtMs;
            Row(String messageId, String requestId, String contentSha256,
                String product, String device, long receivedAtMs) {
                this.messageId = messageId;
                this.requestId = requestId;
                this.contentSha256 = contentSha256;
                this.product = product;
                this.device = device;
                this.receivedAtMs = receivedAtMs;
            }
        }

        private final Map<String, Row> rows = new HashMap<>();
        private long clock;

        DurableFakeInbox(long startClockMs) {
            this.clock = startClockMs;
        }

        InboxReceiveResult.Item receive(InboxEnvelope envelope) {
            Row existing = rows.get(envelope.messageId());
            if (existing == null) {
                Row row = new Row(envelope.messageId(), envelope.requestId(),
                        envelope.contentSha256(), envelope.productIdentification(),
                        envelope.deviceIdentification(), clock);
                rows.put(row.messageId, row);
                return new InboxReceiveResult.Item(0, envelope.messageId(),
                        envelope.requestId(), InboxReceiveResult.Status.ACCEPTED_DURABLE, clock);
            }
            if (existing.contentSha256.equals(envelope.contentSha256())) {
                return new InboxReceiveResult.Item(0, envelope.messageId(),
                        envelope.requestId(), InboxReceiveResult.Status.DUPLICATE,
                        existing.receivedAtMs);
            }
            return new InboxReceiveResult.Item(0, envelope.messageId(),
                    envelope.requestId(), InboxReceiveResult.Status.MESSAGE_ID_COLLISION, null);
        }

        Row row(String messageId) {
            return rows.get(messageId);
        }

        int size() {
            return rows.size();
        }
    }

    /** Durable fake dispatch port: persisted attempts/sent state across center restarts. */
    static final class DurableFakeAckDispatchPort implements TelemetryAckDispatchPort {
        static final class State {
            int attempts;
            Long sentAtMs;
        }

        private final DurableFakeInbox inbox;
        private final Map<String, State> states = new HashMap<>();

        DurableFakeAckDispatchPort(DurableFakeInbox inbox) {
            this.inbox = inbox;
        }

        @Override
        public List<TelemetryAckDeliveryRow> claimPending(int limit) {
            List<TelemetryAckDeliveryRow> pending = new ArrayList<>();
            inbox.rows.values().stream()
                    .sorted((a, b) -> Long.compare(a.receivedAtMs, b.receivedAtMs))
                    .forEach(row -> {
                        if (pending.size() >= limit) {
                            return;
                        }
                        State state = states.computeIfAbsent(row.messageId, k -> new State());
                        if (state.sentAtMs != null) {
                            return;
                        }
                        state.attempts += 1;
                        pending.add(new TelemetryAckDeliveryRow(Long.parseLong(row.requestId
                                .isEmpty() ? "0" : tenantOf()), row.messageId, row.requestId,
                                new TelemetryRoute(row.product, row.device),
                                row.receivedAtMs, null, state.attempts));
                    });
            return pending;
        }

        private String tenantOf() {
            return "1";
        }

        @Override
        public TelemetryAckDeliveryRow loadForImmediateAck(long tenantId, String messageId) {
            DurableFakeInbox.Row row = inbox.row(messageId);
            if (row == null || row.product == null || row.product.isBlank()
                    || row.device == null || row.device.isBlank()) {
                return null;
            }
            State state = states.computeIfAbsent(messageId, k -> new State());
            state.attempts += 1;
            return new TelemetryAckDeliveryRow(tenantId, row.messageId, row.requestId,
                    new TelemetryRoute(row.product, row.device), row.receivedAtMs,
                    state.sentAtMs, state.attempts);
        }

        @Override
        public boolean markSent(long tenantId, String messageId, long sentAtMs) {
            State state = states.get(messageId);
            if (state == null || state.sentAtMs != null) {
                return false;
            }
            state.sentAtMs = sentAtMs;
            return true;
        }

        State state(String messageId) {
            return states.get(messageId);
        }
    }

    /** Idempotent fake store: one logical sample per messageId. */
    static final class IdempotentFakeStore {
        private final Map<String, String> samples = new HashMap<>();

        void write(InboxEnvelope envelope) {
            samples.putIfAbsent(envelope.messageId(), envelope.contentSha256());
        }

        int size() {
            return samples.size();
        }
    }

    /** Deterministic authority: every fixture route resolves to tenant 1. */
    static final class DeterministicAuthority {
        String resolveTenant(TelemetryRoute route) {
            return "1";
        }
    }

    /** Dedicated warm-up probe identity; never referenced by scenario assertions. */
    static final String PROBE_MESSAGE_ID = "0f9e8d7c-6b5a-4c3d-2e1f-0a1b2c3d4e5f";
    static final String PROBE_REQUEST_ID = "1a2b3c4d-0000-4000-8000-0000000000ff";

    private TelemetryEnvelope probeEnvelope() {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION,
                TelemetryEnvelope.CANONICALIZATION_VERSION,
                PROBE_MESSAGE_ID, PROBE_REQUEST_ID,
                manifest.tenantId, "site-lc03-probe",
                manifest.device, "voltage-probe",
                "1.0", TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality.GOOD,
                com.basiclab.iot.sink.telemetry.envelope.DataPriority.NORMAL_TELEMETRY,
                "2026-07-30T16:00:00Z", "2026-07-30T16:00:00Z",
                1, "modbus-rtu", 1);
    }

    /** §16.2 fault-injecting publisher with two single-shot barriers. */
    static final class FaultInjectingAckPublisher implements CenterTelemetryAckPublisherPort {
        private final List<TelemetryAckV1> published = new CopyOnWriteArrayList<>();
        final List<String> topics = new CopyOnWorkAround<>();
        private final java.util.function.Supplier<io.vertx.mqtt.MqttEndpoint> collectorEndpoint;
        private final io.vertx.core.Vertx vertx;
        volatile Fault fault = Fault.NONE;
        final CountDownLatch faultReached = new CountDownLatch(1);

        FaultInjectingAckPublisher(io.vertx.core.Vertx vertx,
                java.util.function.Supplier<io.vertx.mqtt.MqttEndpoint> collectorEndpoint) {
            this.vertx = vertx;
            this.collectorEndpoint = collectorEndpoint;
        }

        @Override
        public boolean publish(TelemetryAckV1 ack, String topic) {
            if (fault == Fault.DROP_BEFORE_PUBLISH) {
                fault = Fault.NONE;
                faultReached.countDown();
                return false;
            }
            // Server-side publish: the payload is delivered to the collector's
            // exact subscription through the same in-process broker session.
            // The write is hopped onto the endpoint's Vert.x context so the
            // MQTT write never races the event loop from the caller thread.
            byte[] payload = new TelemetryAckV1Codec().encode(ack);
            io.vertx.mqtt.MqttEndpoint endpoint = collectorEndpoint.get();
            if (endpoint == null) {
                return false;
            }
            try {
                CompletableFuture<Void> sent = new CompletableFuture<>();
                sendOnContext(endpoint, topic, payload, sent);
                sent.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                return false;
            }
            published.add(ack);
            topics.add(topic);
            if (fault == Fault.PUBLISH_THEN_FAIL_BEFORE_MARK) {
                fault = Fault.NONE;
                faultReached.countDown();
                return false;
            }
            return true;
        }

        private void sendOnContext(io.vertx.mqtt.MqttEndpoint endpoint, String topic,
                                   byte[] payload, CompletableFuture<Void> sent) {
            vertx.runOnContext(v -> {
                try {
                    endpoint.publish(topic, Buffer.buffer(payload),
                            MqttQoS.AT_LEAST_ONCE, false, false);
                    sent.complete(null);
                } catch (RuntimeException e) {
                    sent.completeExceptionally(e);
                }
            });
        }

        List<TelemetryAckV1> publishedAcks() {
            return List.copyOf(published);
        }

        boolean awaitFault(long seconds) throws InterruptedException {
            return faultReached.await(seconds, TimeUnit.SECONDS);
        }
    }

    private static final class CopyOnWorkAround<E> extends CopyOnWriteArrayList<E> {
    }

    // ==================== fixture wiring ====================

    final Vertx vertx = Vertx.vertx();
    final MqttServer server;
    final int serverPort;
    final Path sqliteFile;
    final SqliteTelemetryOutbox outbox;
    final EnvelopeCanonicalCodec envelopeCodec = new EnvelopeCanonicalCodec();
    final DurableFakeInbox inbox;
    final DurableFakeAckDispatchPort dispatch;
    final IdempotentFakeStore store = new IdempotentFakeStore();
    final DeterministicAuthority authority = new DeterministicAuthority();
    final Manifest manifest;
    FaultInjectingAckPublisher publisher;

    private MqttClient collectorClient;
    private volatile io.vertx.mqtt.MqttEndpoint collectorEndpoint;
    private CollectorMqttAckSubscriber collectorSubscriber;
    private CollectorAckSubscriptionCoordinator coordinator;
    private TelemetryAckReconciliationTask scanner;

    Lc03AckE2eFixture(Path tempDir) throws Exception {
        this.manifest = loadManifest();
        // Real in-process MQTT server accepting the fixture route subscriptions.
        CompletableFuture<Integer> listening = new CompletableFuture<>();
        server = MqttServer.create(vertx, new MqttServerOptions().setPort(0));
        server.endpointHandler(endpoint -> {
            endpoint.accept(false);
            endpoint.subscribeHandler(subscribe -> endpoint.subscribeAcknowledge(
                    subscribe.messageId(),
                    List.of(MqttSubAckReasonCode.GRANTED_QOS1), new MqttProperties()));
            endpoint.unsubscribeHandler(unsubscribe -> endpoint.unsubscribeAcknowledge(
                    unsubscribe.messageId(),
                    List.of(io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode.SUCCESS),
                    new MqttProperties()));
            endpoint.publishHandler(message -> endpoint.publishAcknowledge(message.messageId()));
            // Latest connected client session is the live collector; the fault
            // publisher pushes ACKs server-side through this endpoint.
            collectorEndpoint = endpoint;
            endpoint.closeHandler(ignored -> {
                if (collectorEndpoint == endpoint) {
                    collectorEndpoint = null;
                }
            });
        });
        server.listen(ar -> listening.complete(ar.result().actualPort()));
        serverPort = listening.get(5, TimeUnit.SECONDS);

        // Real SQLite outbox file surviving collector restarts.
        sqliteFile = tempDir.resolve("lc03-04-outbox.db");
        SqliteOutboxMigration.migrate(sqliteFile);
        outbox = new SqliteTelemetryOutbox(sqliteFile, envelopeCodec, 100);

        inbox = new DurableFakeInbox(manifest.firstPersistedAtMs);
        dispatch = new DurableFakeAckDispatchPort(inbox);
    }

    /** Center-side ingress: guard facts then durable Inbox + store write. */
    InboxReceiveResult.Item centerReceive(TelemetryEnvelope envelope) {
        EnvelopeCanonicalCodec.CanonicalEnvelope canonical = envelopeCodec.canonicalize(envelope);
        InboxEnvelope inboxEnvelope = new InboxEnvelope(envelope.messageId(),
                envelope.requestId(), envelope.tenantId(),
                manifest.product, envelope.siteCode(), envelope.deviceIdentification(),
                envelope.propertyCode(), canonical.canonicalBytes(),
                canonical.contentSha256(), System.currentTimeMillis(), envelope.sequence(),
                envelope.source(), envelope.configVersion());
        InboxReceiveResult.Item item = inbox.receive(inboxEnvelope);
        if (item.status() != InboxReceiveResult.Status.MESSAGE_ID_COLLISION) {
            store.write(inboxEnvelope);
        }
        return item;
    }

    /** Build (or rebuild after restart) the center ACK stack; durable fakes persist. */
    CenterTelemetryAckService startCenter() {
        publisher = new FaultInjectingAckPublisher(vertx, () -> collectorEndpoint);
        return new CenterTelemetryAckService(dispatch, publisher);
    }

    TelemetryAckReconciliationTask startScanner(CenterTelemetryAckService service) {
        scanner = new TelemetryAckReconciliationTask(dispatch, service, 3600_000L, 1000);
        return scanner;
    }

    void stopScanner() {
        if (scanner != null) {
            scanner.close();
            scanner = null;
        }
    }

    /** Build (or rebuild after restart) the collector stack on the same SQLite file. */
    void startCollector() throws Exception {
        collectorClient = MqttClient.create(vertx, new MqttClientOptions()
                .setClientId("lc03-04-collector-" + System.nanoTime())
                .setCleanSession(true));
        CompletableFuture<Void> connected = new CompletableFuture<>();
        collectorClient.connect(serverPort, "localhost",
                ar -> connected.complete(null));
        connected.get(5, TimeUnit.SECONDS);

        TelemetryRouteSetProvider provider = new TelemetryRouteSetProvider() {
            @Override
            public List<TelemetryRoute> currentRoutes() {
                List<TelemetryRoute> routes = new ArrayList<>(outbox.listUnfinishedRoutes());
                routes.add(new TelemetryRoute(manifest.product, manifest.device));
                return List.copyOf(routes);
            }
        };
        coordinator = new CollectorAckSubscriptionCoordinator(collectorClient, provider);
        collectorSubscriber = new CollectorMqttAckSubscriber(outbox, collectorClient);
        collectorSubscriber.start();
        coordinator.recover();
        // Warm up the full first-publish path (event loop + Netty channel
        // write + collector inbound decode) before any timed scenario: in
        // slower containerized environments the first server-side publish
        // stalls the event loop for seconds, which would otherwise consume
        // the §16.2 5s eventually budget of the FIRST test only. The probe
        // uses a dedicated throwaway subscription and unknown messageId, so
        // the collector fail-closes it (no SQLite state change) exactly like
        // the §16.3 negative matrix path.
        warmUpFirstPublishPath();
    }

    private void warmUpFirstPublishPath() throws Exception {
        String probeTopic = "/iot/" + manifest.product + "/" + manifest.device
                + "/property/downstream/report/ack";
        CompletableFuture<Void> probeReceived = new CompletableFuture<>();
        collectorClient.publishHandler(message -> {
            if (!probeReceived.isDone()) {
                probeReceived.complete(null);
            }
        });
        io.vertx.mqtt.MqttEndpoint endpoint = collectorEndpoint;
        if (endpoint == null) {
            return;
        }
        byte[] probe = new TelemetryAckV1Codec().encode(new TelemetryAckV1(
                TelemetryAckV1.SCHEMA_VERSION,
                "0f9e8d7c-6b5a-4c3d-2e1f-0a1b2c3d4e5f",
                "0f9e8d7c-6b5a-4c3d-2e1f-0a1b2c3d4e5f",
                TelemetryAckStatus.DUPLICATE,
                1001, "DUPLICATE", manifest.firstPersistedAtMs));
        // Unknown-messageId semantics per §16.3 negative matrix: the SQLite
        // writer refuses to create a row for it, so this is side-effect free.
        CompletableFuture<Void> sent = new CompletableFuture<>();
        vertx.runOnContext(v -> {
            try {
                endpoint.publish(probeTopic, Buffer.buffer(probe),
                        MqttQoS.AT_LEAST_ONCE, false, false);
                sent.complete(null);
            } catch (RuntimeException e) {
                sent.completeExceptionally(e);
            }
        });
        try {
            sent.get(10, TimeUnit.SECONDS);
            probeReceived.get(10, TimeUnit.SECONDS);
            // Also warm the SQLite writer thread + first-append path with a
            // probe envelope on a dedicated messageId; it stays PENDING and
            // is never referenced by any scenario assertion.
            appendEnvelope(probeEnvelope());
            awaitSqliteStatus(PROBE_MESSAGE_ID, "PENDING");
        } finally {
            // Restore the real collector inbound handler wiring: the
            // subscriber re-registers its handler on the shared client.
            collectorSubscriber.close();
            collectorSubscriber = new CollectorMqttAckSubscriber(outbox, collectorClient);
            collectorSubscriber.start();
        }
    }

    void stopCollector() {
        if (collectorSubscriber != null) {
            collectorSubscriber.close();
            collectorSubscriber = null;
        }
        if (coordinator != null) {
            coordinator.close();
            coordinator = null;
        }
        if (collectorClient != null && collectorClient.isConnected()) {
            try {
                collectorClient.disconnect().toCompletionStage().toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
            } catch (Exception ignore) {
                // best-effort
            }
            collectorClient = null;
        }
    }

    MqttClient collectorClientForCenterPublishing() {
        return collectorClient;
    }

    /** Publish one upstream envelope over real MQTT to trigger center ingest. */
    void collectorPublishUpstream(TelemetryEnvelope envelope) {
        EnvelopeCanonicalCodec.CanonicalEnvelope canonical = envelopeCodec.canonicalize(envelope);
        collectorClient.publish(manifest.upstreamTopic,
                Buffer.buffer(canonical.canonicalBytes()),
                MqttQoS.AT_LEAST_ONCE, false, false);
    }

    String sqliteStatus(String messageId) throws Exception {
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + sqliteFile.toAbsolutePath());
             java.sql.Statement s = c.createStatement();
             java.sql.ResultSet rs = s.executeQuery(
                     "SELECT status FROM telemetry_outbox WHERE message_id = '"
                             + messageId.replace("'", "''") + "'")) {
            return rs.next() ? rs.getString(1) : "NOT_FOUND";
        }
    }

    void awaitSqliteStatus(String messageId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        String status = sqliteStatus(messageId);
        while (!expected.equals(status) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            status = sqliteStatus(messageId);
        }
        if (!expected.equals(status)) {
            throw new AssertionError("SQLite status expected " + expected
                    + " but was " + status + " for " + messageId);
        }
    }

    void appendEnvelope(TelemetryEnvelope envelope) {
        outbox.appendBatch(new com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch(
                manifest.product, List.of(envelope)), Duration.ofSeconds(5));
    }

    private static Manifest loadManifest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ManifestJson m = mapper.readValue(resource("lc03-04/fixture-manifest.json"),
                ManifestJson.class);
        TelemetryEnvelope accepted = mapper.readValue(
                resource("lc03-04/accepted-envelope-v1.json"), TelemetryEnvelope.class);
        TelemetryEnvelope collision = mapper.readValue(
                resource("lc03-04/collision-envelope-v1.json"), TelemetryEnvelope.class);
        return new Manifest(m.tenantId, m.productIdentification, m.deviceIdentification,
                m.requestId, m.messageId, m.collisionRequestId, m.upstreamTopic, m.ackTopic,
                m.firstPersistedAtMs, accepted, collision);
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = Lc03AckE2eFixture.class.getClassLoader()
                .getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("fixture resource missing: " + name);
            }
            return in.readAllBytes();
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ManifestJson {
        public String tenantId;
        public String productIdentification;
        public String deviceIdentification;
        public String requestId;
        public String messageId;
        public String collisionRequestId;
        public String upstreamTopic;
        public String ackTopic;
        public long firstPersistedAtMs;
    }

    @Override
    public void close() {
        stopScanner();
        stopCollector();
        try {
            outbox.shutdown();
        } catch (Exception ignore) {
            // best-effort
        }
        server.close();
        vertx.close();
    }
}
