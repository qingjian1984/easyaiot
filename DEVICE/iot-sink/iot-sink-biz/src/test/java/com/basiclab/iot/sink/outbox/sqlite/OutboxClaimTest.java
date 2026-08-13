package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimedEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-002 §11 claim 合同：PENDING→IN_FLIGHT + attempts+1 + 排序 + 批量上限。
 */
class OutboxClaimTest {

    @TempDir
    Path dir;
    private SqliteTelemetryOutbox outbox;

    @BeforeEach
    void setup() throws Exception {
        Path db = dir.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        outbox = new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), 100);
    }

    @AfterEach
    void teardown() {
        if (outbox != null) outbox.shutdown();
    }

    private TelemetryEnvelope env(String msgId) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION, TelemetryEnvelope.CANONICALIZATION_VERSION,
                msgId, "req-" + msgId, "123", "site-1", "dev-1", "voltage-a", "220.5",
                TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                TelemetryQuality.GOOD, DataPriority.NORMAL_TELEMETRY,
                "2026-08-13T00:00:00Z", "2026-08-13T00:00:00Z", 1, "modbus-rtu", 1);
    }

    @Test
    void claimEmptyReturnsEmpty() {
        ClaimBatchResult r = outbox.claimBatch(100, Duration.ofMinutes(5));
        assertTrue(r instanceof ClaimBatchResult.Empty);
    }

    @Test
    void claimPendingReturnsClaimed() {
        outbox.appendBatch(List.of(env("msg-1"), env("msg-2")), Duration.ofSeconds(5));
        ClaimBatchResult r = outbox.claimBatch(100, Duration.ofMinutes(5));
        assertTrue(r instanceof ClaimBatchResult.Claimed);
        assertEquals(2, r.envelopes().size());
    }

    @Test
    void claimRespectsMaxCount() {
        outbox.appendBatch(List.of(env("msg-1"), env("msg-2"), env("msg-3")), Duration.ofSeconds(5));
        ClaimBatchResult r = outbox.claimBatch(2, Duration.ofMinutes(5));
        assertEquals(2, r.envelopes().size());
    }

    @Test
    void claimedEnvelopeContainsCanonicalBytes() {
        outbox.appendBatch(List.of(env("msg-1")), Duration.ofSeconds(5));
        ClaimBatchResult r = outbox.claimBatch(100, Duration.ofMinutes(5));
        ClaimedEnvelope ce = r.envelopes().get(0);
        assertEquals("msg-1", ce.messageId());
        assertTrue(ce.canonicalBytes().length > 0, "canonical bytes must be present");
        assertEquals("123", ce.tenantId());
        assertEquals("site-1", ce.siteCode());
        assertEquals("dev-1", ce.deviceIdentification());
        assertEquals("voltage-a", ce.propertyCode());
    }

    @Test
    void secondClaimAfterFirstReturnsEmpty() {
        outbox.appendBatch(List.of(env("msg-1")), Duration.ofSeconds(5));
        outbox.claimBatch(100, Duration.ofMinutes(5));
        ClaimBatchResult r2 = outbox.claimBatch(100, Duration.ofMinutes(5));
        assertTrue(r2 instanceof ClaimBatchResult.Empty, "all PENDING claimed → second claim empty");
    }
}
