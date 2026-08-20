package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-T1 appendBatch 核心合同：STORED / DUPLICATE / COLLISION。
 */
class SqliteOutboxAppendBatchTest {

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
        if (outbox != null) {
            outbox.shutdown();
        }
    }

    private TelemetryEnvelope env(String msgId, String value) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION,
                TelemetryEnvelope.CANONICALIZATION_VERSION,
                msgId, "req-" + msgId,
                "123", "site-1", "dev-1", "voltage-a", value,
                TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                TelemetryQuality.GOOD, DataPriority.NORMAL_TELEMETRY,
                "2026-08-12T00:00:00Z", "2026-08-12T00:00:00Z",
                1, "modbus-rtu", 1
        );
    }

    @Test
    void newEnvelopeStored() {
        AppendBatchResult r = outbox.appendBatch(batch(env("msg-1", "220.5")), Duration.ofSeconds(5));
        assertEquals(1, r.storedMessageIds().size());
        assertTrue(r.storedMessageIds().contains("msg-1"));
        assertEquals(0, r.duplicateMessageIds().size());
    }

    @Test
    void duplicateSameHashSkipped() {
        outbox.appendBatch(batch(env("msg-1", "220.5")), Duration.ofSeconds(5));
        AppendBatchResult r = outbox.appendBatch(batch(env("msg-1", "220.5")), Duration.ofSeconds(5));
        assertEquals(0, r.storedMessageIds().size());
        assertEquals(1, r.duplicateMessageIds().size());
        assertTrue(r.duplicateMessageIds().contains("msg-1"));
    }

    @Test
    void collisionDifferentHashRollsBack() {
        outbox.appendBatch(batch(env("msg-1", "220.5")), Duration.ofSeconds(5));
        AppendBatchResult r = outbox.appendBatch(batch(env("msg-1", "221.0")), Duration.ofSeconds(5));
        assertInstanceOf(AppendBatchResult.Collision.class, r);
        assertEquals(0, r.storedMessageIds().size());
        assertEquals(0, r.duplicateMessageIds().size());
        assertEquals(List.of("msg-1"), r.collisionMessageIds());
    }

    @Test
    void batchMultipleEnvelopesAllStored() {
        AppendBatchResult r = outbox.appendBatch(batch(
                env("msg-1", "220.5"),
                env("msg-2", "221.0"),
                env("msg-3", "222.0")
        ), Duration.ofSeconds(5));
        assertEquals(3, r.storedMessageIds().size());
    }

    @Test
    void emptyBatchIsRejectedAtContractBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> outbox.appendBatch(new TelemetryOutboxBatch("power-meter", List.of()), Duration.ofSeconds(5)));
    }

    private TelemetryOutboxBatch batch(TelemetryEnvelope... envelopes) {
        return new TelemetryOutboxBatch("power-meter", List.of(envelopes));
    }
}
