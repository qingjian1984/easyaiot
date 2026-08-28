package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LC03-04A §16.3 center-side restart scenarios (E2E-03/04/05).
 *
 * <p>Real production classes over an in-process MQTT server and a real
 * SQLite outbox file; durable fakes keep Inbox/dispatch facts across the
 * simulated center restarts (close + rebuild, state preserved).
 */
@Timeout(60)
class TelemetryAckRestartReconciliationTest {

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
    void e2e03CenterRestartBetweenCommitAndPublishScannerRecovers() throws Exception {
        // 1. Collector appends + publishes upstream; center ingests and commits.
        fixture.appendEnvelope(fixture.manifest.acceptedEnvelope);
        InboxReceiveResult.Item item = fixture.centerReceive(fixture.manifest.acceptedEnvelope);
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, item.status());

        // 2. Center "restarts" before any ACK publish: build the stack but drop the
        //    very first publish attempt (DROP_BEFORE_PUBLISH simulates the kill point).
        CenterTelemetryAckService first =
                fixture.startCenter();
        fixture.publisher.fault = Lc03AckE2eFixture.Fault.DROP_BEFORE_PUBLISH;
        first.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.requestId,
                TelemetryAckStatus.ACCEPTED_DURABLE, item.persistedAtMs());
        assertTrue(fixture.publisher.awaitFault(5), "fault barrier must be reached");

        // Nothing published yet; scanner must be the recovery path.
        assertTrue(fixture.publisher.publishedAcks().isEmpty());
        assertNull(fixture.dispatch.state(fixture.manifest.messageId).sentAtMs);

        // 3. Rebuild center (restart) and run the startup scan once.
        CenterTelemetryAckService second =
                fixture.startCenter();
        TelemetryAckReconciliationTask scanner =
                fixture.startScanner(second);
        try {
            scanner.scanOnce();
        } finally {
            fixture.stopScanner();
        }

        // 4. ACK was recovered: attempts increased, sent marked, collector SQLite ACKED.
        Lc03AckE2eFixture.DurableFakeAckDispatchPort.State state =
                fixture.dispatch.state(fixture.manifest.messageId);
        assertNotNull(state);
        assertTrue(state.attempts >= 2, "scanner retry must increase attempts");
        assertNotNull(state.sentAtMs, "scanner must mark sent after successful publish");
        fixture.awaitSqliteStatus(fixture.manifest.messageId, "ACKED");
        assertEquals(1, fixture.inbox.size());
        assertEquals(1, fixture.store.size());
    }

    @Test
    void e2e04CenterRestartAfterPublishBeforeMarkDuplicateAckAbsorbed() throws Exception {
        fixture.appendEnvelope(fixture.manifest.acceptedEnvelope);
        InboxReceiveResult.Item item = fixture.centerReceive(fixture.manifest.acceptedEnvelope);
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, item.status());

        // Publish succeeds, then the "process dies" before markSent: barrier fails the
        // publish() return so markSent never runs, but the payload reached the broker.
        CenterTelemetryAckService service =
                fixture.startCenter();
        fixture.publisher.fault = Lc03AckE2eFixture.Fault.PUBLISH_THEN_FAIL_BEFORE_MARK;
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.requestId,
                TelemetryAckStatus.ACCEPTED_DURABLE, item.persistedAtMs());
        assertTrue(fixture.publisher.awaitFault(5));

        // Collector already consumed the delivered ACK.
        fixture.awaitSqliteStatus(fixture.manifest.messageId, "ACKED");

        // sent stays NULL -> rebuilt scanner re-publishes; duplicate ACK is absorbed.
        assertNull(fixture.dispatch.state(fixture.manifest.messageId).sentAtMs);
        TelemetryAckReconciliationTask scanner =
                fixture.startScanner(service);
        try {
            scanner.scanOnce();
        } finally {
            fixture.stopScanner();
        }
        assertNotNull(fixture.dispatch.state(fixture.manifest.messageId).sentAtMs);
        fixture.awaitSqliteStatus(fixture.manifest.messageId, "ACKED");
        assertEquals(1, fixture.inbox.size(), "duplicate ACK must not add a logical row");
        assertEquals(1, fixture.store.size());
    }

    @Test
    void e2e05CollectorRestartAfterUpstreamPublishRecoversExactSubscription() throws Exception {
        // 1. Collector publishes upstream then "crashes" before the ACK is applied.
        fixture.appendEnvelope(fixture.manifest.acceptedEnvelope);
        fixture.collectorPublishUpstream(fixture.manifest.acceptedEnvelope);
        InboxReceiveResult.Item item = fixture.centerReceive(fixture.manifest.acceptedEnvelope);
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, item.status());

        // Hold the very first center ACK so nothing is applied before the restart.
        CenterTelemetryAckService service =
                fixture.startCenter();
        fixture.publisher.fault = Lc03AckE2eFixture.Fault.DROP_BEFORE_PUBLISH;
        service.sendImmediateAck(Long.parseLong(fixture.manifest.tenantId),
                fixture.manifest.messageId, fixture.manifest.requestId,
                TelemetryAckStatus.ACCEPTED_DURABLE, item.persistedAtMs());
        assertTrue(fixture.publisher.awaitFault(5));

        // 2. Collector restarts on the SAME SQLite file: unacknowledged route must be
        //    re-subscribed exactly before dispatch resumes.
        fixture.stopCollector();
        fixture.startCollector();

        // 3. Scanner on the (still running) center republishes; SQLite reaches ACKED.
        TelemetryAckReconciliationTask scanner =
                fixture.startScanner(service);
        try {
            scanner.scanOnce();
        } finally {
            fixture.stopScanner();
        }
        fixture.awaitSqliteStatus(fixture.manifest.messageId, "ACKED");
        assertEquals(1, fixture.inbox.size());
    }
}
