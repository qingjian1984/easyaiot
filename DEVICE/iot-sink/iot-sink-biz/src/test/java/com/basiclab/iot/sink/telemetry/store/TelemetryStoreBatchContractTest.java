package com.basiclab.iot.sink.telemetry.store;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryStoreBatchContractTest {

    @Test
    void sampleAndBatchUseDefensiveCopiesAndKeepOrder() {
        byte[] bytes = {1, 2, 3};
        TelemetrySample sample = sample("m-1", bytes);
        bytes[0] = 9;
        assertEquals(1, sample.canonicalBytes()[0]);
        byte[] read = sample.canonicalBytes();
        assertNotSame(read, sample.canonicalBytes());
        read[1] = 8;
        assertEquals(2, sample.canonicalBytes()[1]);

        List<WriteItemResult> mutable = new ArrayList<>(List.of(
                WriteItemResult.stored("m-1"), WriteItemResult.duplicate("m-2")));
        WriteBatchResult result = new WriteBatchResult(mutable);
        mutable.clear();
        assertEquals(List.of("m-1", "m-2"), result.items().stream()
                .map(WriteItemResult::messageId).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> result.items().add(WriteItemResult.stored("m-3")));
    }

    @Test
    void inboxFactoryCopiesAllProjectionFields() {
        InboxEnvelope inbox = new InboxEnvelope(
                "m-1", "r-1", "100", "product", "site", "device", "property",
                new byte[]{7}, "a".repeat(64), 123L, 4L, "modbus", 2L);
        TelemetrySample sample = TelemetrySample.fromInboxEnvelope(inbox);
        assertEquals(inbox.messageId(), sample.messageId());
        assertEquals(inbox.requestId(), sample.requestId());
        assertEquals(inbox.tenantId(), sample.tenantId());
        assertEquals(inbox.contentSha256(), sample.contentSha256());
        assertEquals(inbox.configVersion(), sample.configVersion());
        assertEquals(7, sample.canonicalBytes()[0]);
    }

    @Test
    void compatibilityBridgeCallsBatchOnceAndMapsOnlyMatchingSingleItem() {
        CountingStore store = new CountingStore(WriteBatchResult.of(WriteItemResult.stored("m-1")));
        assertEquals(WriteResult.STORED, store.writeSample(inbox("m-1")));
        assertEquals(1, store.calls);

        store.result = WriteBatchResult.of(WriteItemResult.duplicate("m-1"));
        assertEquals(WriteResult.DUPLICATE, store.writeSample(inbox("m-1")));
        assertEquals(2, store.calls);

        store.result = WriteBatchResult.of(WriteItemResult.retryable("m-1", "STORE_UNAVAILABLE"));
        assertEquals(WriteResult.FAILED, store.writeSample(inbox("m-1")));
        assertEquals(3, store.calls);

        store.result = new WriteBatchResult(List.of(WriteItemResult.stored("other")));
        assertEquals(WriteResult.FAILED, store.writeSample(inbox("m-1")));
        assertEquals(4, store.calls);
    }

    @Test
    void stableResultVocabularyRejectsDetailsAndSuccessErrors() {
        assertNull(WriteItemResult.stored("m").errorCode());
        assertTrue(WriteItemResult.finalFailed("m", "MESSAGE_ID_COLLISION").status()
                == WriteStatus.FINAL_FAILED);
        assertThrows(IllegalArgumentException.class, () -> new WriteBatchResult(null));
        assertThrows(IllegalArgumentException.class,
                () -> new WriteItemResult("", WriteStatus.STORED, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WriteItemResult(null, WriteStatus.RETRYABLE_FAILED, "STORE_UNAVAILABLE"));
        assertThrows(IllegalArgumentException.class,
                () -> new WriteItemResult(null, WriteStatus.FINAL_FAILED, "STORE_BATCH_TOO_LARGE"));
        assertThrows(IllegalArgumentException.class,
                () -> new WriteItemResult("m", WriteStatus.STORED, "STORE_UNAVAILABLE"));
        assertThrows(IllegalArgumentException.class,
                () -> new WriteItemResult("m", WriteStatus.FINAL_FAILED, "SQLSTATE_23505"));
        assertFalse(sample("m", new byte[]{1}).canonicalBytes().length == 0);
    }

    private static TelemetrySample sample(String messageId, byte[] bytes) {
        return new TelemetrySample(messageId, "r-" + messageId, "100", "site", "device", "property",
                bytes, "a".repeat(64), 123L, 1L, "modbus", 1L);
    }

    private static InboxEnvelope inbox(String messageId) {
        return new InboxEnvelope(messageId, "r-" + messageId, "100", "product", "site", "device", "property",
                new byte[]{1}, "a".repeat(64), 123L, 1L, "modbus", 1L);
    }

    private static final class CountingStore implements TelemetryStorePort {
        private WriteBatchResult result;
        private int calls;

        private CountingStore(WriteBatchResult result) {
            this.result = result;
        }

        @Override
        public WriteBatchResult appendBatch(List<TelemetrySample> samples) {
            calls++;
            return result;
        }
    }
}
