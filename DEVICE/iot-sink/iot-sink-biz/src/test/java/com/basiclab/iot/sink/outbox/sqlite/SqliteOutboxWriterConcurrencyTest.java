package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-002 §8 单 writer 串行化 + 并发安全合同。
 */
class SqliteOutboxWriterConcurrencyTest {

    @TempDir
    Path dir;
    private SqliteTelemetryOutbox outbox;

    @BeforeEach
    void setup() throws Exception {
        Path db = dir.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        outbox = new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), 1024);
    }

    @AfterEach
    void teardown() {
        if (outbox != null) {
            outbox.shutdown();
        }
    }

    private TelemetryEnvelope env(String msgId) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION,
                TelemetryEnvelope.CANONICALIZATION_VERSION,
                msgId, "req-" + msgId,
                "123", "site-1", "dev-1", "voltage-a", "220.5",
                TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                TelemetryQuality.GOOD, DataPriority.NORMAL_TELEMETRY,
                "2026-08-12T00:00:00Z", "2026-08-12T00:00:00Z",
                1, "modbus-rtu", 1
        );
    }

    @Test
    void concurrentAppendBatchAllStoredNoCollision() throws Exception {
        int threads = 10;
        int perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        ConcurrentHashMap<String, Integer> results = new ConcurrentHashMap<>();

        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            pool.submit(() -> {
                try {
                    List<TelemetryEnvelope> batch = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        batch.add(env("msg-" + threadIdx + "-" + i));
                    }
                    AppendBatchResult r = outbox.appendBatch(new TelemetryOutboxBatch("power-meter", batch),
                            Duration.ofSeconds(10));
                    results.merge("stored", r.storedMessageIds().size(), Integer::sum);
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "all threads should complete");
        pool.shutdown();

        assertEquals(threads * perThread, results.get("stored"),
                threads + " threads × " + perThread + " = " + (threads * perThread) + " all STORED (unique messageIds)");
    }

    @RepeatedTest(3)
    void interleavedAppendBatchAndDuplicate() {
        String msgId = "dup-" + UUID.randomUUID();
        outbox.appendBatch(new TelemetryOutboxBatch("power-meter", List.of(env(msgId))), Duration.ofSeconds(5));
        AppendBatchResult r = outbox.appendBatch(new TelemetryOutboxBatch("power-meter", List.of(env(msgId))),
                Duration.ofSeconds(5));
        assertEquals(1, r.duplicateMessageIds().size(), "repeat same messageId → DUPLICATE");
    }
}
