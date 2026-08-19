package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.OutboxBackpressureException;
import com.basiclab.iot.sink.telemetry.outbox.OutboxUnavailableException;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.WriteBatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorCrossTdContractTest {

    @Test
    void outboxAppendSignatureIsNotTheTd003StoreSignature() throws Exception {
        Method outboxAppend = TelemetryOutboxPort.class.getMethod(
                "appendBatch", List.class, Duration.class);
        assertEquals(AppendBatchResult.class, outboxAppend.getReturnType());
        Type listType = outboxAppend.getGenericParameterTypes()[0];
        assertTrue(listType instanceof ParameterizedType);
        assertEquals(List.class, ((ParameterizedType) listType).getRawType());
        assertEquals(TelemetryEnvelope.class,
                ((ParameterizedType) listType).getActualTypeArguments()[0]);
        assertEquals(Duration.class, outboxAppend.getParameterTypes()[1]);

        Method storeAppend = TelemetryStorePort.class.getMethod("appendBatch", List.class);
        assertEquals(WriteBatchResult.class, storeAppend.getReturnType());
        Type storeListType = storeAppend.getGenericParameterTypes()[0];
        assertTrue(storeListType instanceof ParameterizedType);
        assertEquals(List.class, ((ParameterizedType) storeListType).getRawType());
        assertEquals(TelemetrySample.class,
                ((ParameterizedType) storeListType).getActualTypeArguments()[0]);
        assertEquals(1, storeAppend.getParameterCount());
        assertEquals(2, outboxAppend.getParameterCount());
        assertFalse(outboxAppend.equals(storeAppend),
                "TD-002 and TD-003 appendBatch methods must remain distinct contracts");
    }

    @Test
    void committedBatchCanBeReopenedClaimedAndRetried(@TempDir Path directory) throws Exception {
        Path db = directory.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        SqliteTelemetryOutbox first = new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), 8);
        try {
            AppendBatchResult stored = first.appendBatch(List.of(envelope("cross-1", "220.5")),
                    Duration.ofSeconds(5));
            assertEquals(List.of("cross-1"), stored.storedMessageIds());
        } finally {
            first.shutdown();
        }

        SqliteTelemetryOutbox reopened = new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), 8);
        try {
            ClaimBatchResult claimed = reopened.claimBatch(10, Duration.ofMinutes(5));
            assertEquals(List.of("cross-1"), claimed.envelopes().stream()
                    .map(value -> value.messageId()).toList());
            AppendBatchResult duplicate = reopened.appendBatch(
                    List.of(envelope("cross-1", "220.5")), Duration.ofSeconds(5));
            assertEquals(List.of("cross-1"), duplicate.duplicateMessageIds());
            assertTrue(duplicate.storedMessageIds().isEmpty());
        } finally {
            reopened.shutdown();
        }
    }

    @Test
    void sameIdDifferentHashIsCollisionAndDoesNotPartiallyCommit(@TempDir Path directory) throws Exception {
        Path db = directory.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        SqliteTelemetryOutbox outbox = new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), 8);
        try {
            outbox.appendBatch(List.of(envelope("cross-2", "220.5")), Duration.ofSeconds(5));
            AppendBatchResult collision = outbox.appendBatch(List.of(
                    envelope("cross-2", "221.0"), envelope("cross-3", "222.0")),
                    Duration.ofSeconds(5));
            assertTrue(collision.storedMessageIds().isEmpty());
            assertTrue(collision.duplicateMessageIds().isEmpty());
            assertEquals(1L, rowCount(db));
        } finally {
            outbox.shutdown();
        }
    }

    @Test
    void queueTimeoutIsBackpressureAndUnavailableDatabaseIsStable(@TempDir Path directory) throws Exception {
        OutboxCommandQueue queue = new OutboxCommandQueue(2);
        queue.offer(new OutboxCommand.AppendBatch(List.of(), new CompletableFuture<>()), Duration.ZERO);
        assertThrows(OutboxBackpressureException.class,
                () -> queue.offer(new OutboxCommand.AppendBatch(List.of(), new CompletableFuture<>()),
                        Duration.ZERO));

        Path missingParentDb = directory.resolve("missing").resolve("outbox.db");
        SqliteTelemetryOutbox unavailable = new SqliteTelemetryOutbox(
                missingParentDb, new EnvelopeCanonicalCodec(), 8);
        try {
            OutboxUnavailableException error = assertThrows(OutboxUnavailableException.class,
                    () -> unavailable.appendBatch(List.of(envelope("cross-4", "220.5")),
                            Duration.ofSeconds(1)));
            assertInstanceOf(OutboxUnavailableException.class, error);
        } finally {
            unavailable.shutdown();
        }
    }

    @Test
    void collectorAndOutboxMountsAreSeparateAndWriterDoesNotUseLegacyBus() throws Exception {
        Path collector = Path.of("/var/lib/easyaiot/config");
        Path outbox = Path.of("/var/lib/easyaiot/outbox");
        assertFalse(collector.equals(outbox));
        assertFalse(isNested(collector, outbox));
        assertFalse(isNested(outbox, collector));
        String source = Files.readString(Path.of("src/main/java/com/basiclab/iot/sink/protocol/polling/CollectorTelemetryWriter.java"));
        assertFalse(source.contains("IotDeviceMessageService"));
        assertFalse(source.contains("IotMessageBus"));
    }

    private static boolean isNested(Path parent, Path child) {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        return !normalizedParent.equals(normalizedChild) && normalizedChild.startsWith(normalizedParent);
    }

    private static long rowCount(Path db) throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM telemetry_outbox")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static TelemetryEnvelope envelope(String messageId, String value) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION, TelemetryEnvelope.CANONICALIZATION_VERSION,
                messageId, "req-" + messageId, "123", "site-1", "dev-1", "voltage-a", value,
                TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING, TelemetryQuality.GOOD,
                DataPriority.NORMAL_TELEMETRY, "2026-08-17T00:00:00Z", "2026-08-17T00:00:00Z",
                1, "modbus-rtu", 1);
    }
}
