package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LC03-04A §16.3 happy paths and the negative matrix (E2E-01/02 + negative).
 *
 * <p>Real production classes end to end over an in-process MQTT server and a
 * real SQLite outbox file; durable fakes provide only the center-side facts.
 */
@Timeout(60)
class TelemetryAckCombinedE2ETest {

    @TempDir
    Path dir;
    private Lc03AckE2eFixture fixture;

    @BeforeEach
    void start() throws Exception {
        fixture = new Lc03AckE2eFixture(dir);
        fixture.startCollector();
    }

    @AfterEach
    void stop() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void e2e01InsertedImmediateAckReachesCollector() throws Exception {
        fixture.appendEnvelope(fixture.manifest.acceptedEnvelope);
        InboxReceiveResult.Item item = fixture.centerReceive(fixture.manifest.acceptedEnvelope);
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, item.status());

        CenterTelemetryAckService service =
                fixture.startCenter();
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.requestId,
                TelemetryAckStatus.ACCEPTED_DURABLE, item.persistedAtMs());

        // SQLite transitions to ACKED via the real MQTT round trip.
        fixture.awaitSqliteStatus(fixture.manifest.messageId, "ACKED");

        TelemetryAckV1 published = fixture.publisher.publishedAcks().get(0);
        assertEquals(fixture.manifest.messageId, published.messageId());
        assertEquals(fixture.manifest.requestId, published.requestId());
        assertEquals(TelemetryAckStatus.ACCEPTED_DURABLE, published.status());
        assertEquals(0, published.code());
        assertEquals("OK", published.reasonCode());
        assertEquals(item.persistedAtMs().longValue(), published.persistedAtMs());
        assertEquals(fixture.manifest.ackTopic, fixture.publisher.topics.get(0));
        assertEquals(1, fixture.inbox.size());
        assertEquals(1, fixture.store.size());
    }

    @Test
    void e2e02LostAckThenResendGetsDuplicateAndFinalAcked() throws Exception {
        // First delivery attempt is dropped (ACK lost before collector applies it).
        fixture.appendEnvelope(fixture.manifest.acceptedEnvelope);
        InboxReceiveResult.Item first = fixture.centerReceive(fixture.manifest.acceptedEnvelope);
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, first.status());

        CenterTelemetryAckService service =
                fixture.startCenter();
        fixture.publisher.fault = Lc03AckE2eFixture.Fault.DROP_BEFORE_PUBLISH;
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.requestId,
                TelemetryAckStatus.ACCEPTED_DURABLE, first.persistedAtMs());
        assertTrue(fixture.publisher.awaitFault(5));
        assertEquals("PENDING", fixture.sqliteStatus(fixture.manifest.messageId));

        // Collector lease/reclaim resends the SAME envelope; center answers DUPLICATE
        // and the DUPLICATE ACK finally transitions SQLite.
        InboxReceiveResult.Item second = fixture.centerReceive(fixture.manifest.acceptedEnvelope);
        assertEquals(InboxReceiveResult.Status.DUPLICATE, second.status());
        assertEquals(first.persistedAtMs(), second.persistedAtMs(),
                "persistedAt must stay the first receive time");
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.requestId,
                TelemetryAckStatus.DUPLICATE, second.persistedAtMs());

        fixture.awaitSqliteStatus(fixture.manifest.messageId, "ACKED");
        TelemetryAckV1 last = fixture.publisher.publishedAcks()
                .get(fixture.publisher.publishedAcks().size() - 1);
        assertEquals(TelemetryAckStatus.DUPLICATE, last.status());
        assertEquals(1001, last.code());
        assertEquals("DUPLICATE", last.reasonCode());
        assertEquals(first.persistedAtMs().longValue(), last.persistedAtMs());
        assertEquals(1, fixture.inbox.size(), "no second logical Inbox row");
        assertEquals(1, fixture.store.size(), "no second logical sample");
    }

    @Test
    void negativeCollisionMismatchedFactsAndInboxFailureProduceZeroAck() throws Exception {
        CenterTelemetryAckService service =
                fixture.startCenter();

        // 1) collision: same messageId, different canonical content.
        fixture.appendEnvelope(fixture.manifest.acceptedEnvelope);
        InboxReceiveResult.Item accepted = fixture.centerReceive(fixture.manifest.acceptedEnvelope);
        fixture.appendEnvelope(fixture.manifest.collisionEnvelope);
        InboxReceiveResult.Item collision = fixture.centerReceive(fixture.manifest.collisionEnvelope);
        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION, collision.status());
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.collisionRequestId,
                TelemetryAckStatus.ACCEPTED_DURABLE, accepted.persistedAtMs());

        // 2) mismatched requestId: persisted row keeps the original requestId.
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, "00000000-0000-4000-8000-0000000000ff",
                TelemetryAckStatus.ACCEPTED_DURABLE, accepted.persistedAtMs());

        // 3) mismatched persistedAt.
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.requestId,
                TelemetryAckStatus.ACCEPTED_DURABLE, accepted.persistedAtMs() + 1);

        // 4) non-success status never reaches the publisher.
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.requestId,
                null, accepted.persistedAtMs());

        // 5) unknown messageId / missing row.
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                "0f9e8d7c-6b5a-4c3d-2e1f-0a1b2c3d4e5f",
                "0f9e8d7c-6b5a-4c3d-2e1f-0a1b2c3d4e5f",
                TelemetryAckStatus.ACCEPTED_DURABLE, accepted.persistedAtMs());

        // Zero successful ACK, zero sent mark, zero SQLite progress beyond PENDING.
        assertTrue(fixture.publisher.publishedAcks().isEmpty(),
                "no negative path may produce a successful ACK publish");
        assertNull(fixture.dispatch.state(fixture.manifest.messageId).sentAtMs);
        assertEquals("PENDING", fixture.sqliteStatus(fixture.manifest.messageId));
        assertEquals(1, fixture.inbox.size(), "collision keeps exactly one logical row");
    }
}
