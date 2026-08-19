package com.basiclab.iot.sink.telemetry.store.tdengine;

import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.WriteItemResult;
import com.basiclab.iot.sink.telemetry.store.WriteStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDengine batch contract tests deliberately do not open a network connection.
 * Valid samples are exercised only through the local batch guard; invalid
 * deterministic values are rejected before adapter initialization.
 */
class TDengineTelemetryStoreBatchContractTest {

    private final TDengineTelemetryStore store = new TDengineTelemetryStore(
            "unreachable-contract-host", 6041, "contract-user", "contract-password");

    @Test
    void nullAndEmptyBatchHaveExplicitContracts() {
        assertThrows(IllegalArgumentException.class, () -> store.appendBatch(null));
        assertTrue(store.appendBatch(List.of()).items().isEmpty());
    }

    @Test
    void hardCapPreservesOrderAndOnlyIdentifiableItemsGetTooLarge() {
        List<TelemetrySample> input = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            input.add(sample("td-cap-" + i, "{\"value\":\"1.0\"}"));
        }
        input.add(null);

        var result = store.appendBatch(input);
        assertEquals(501, result.items().size());
        assertEquals("td-cap-0", result.items().get(0).messageId());
        assertEquals(WriteStatus.FINAL_FAILED, result.items().get(0).status());
        assertEquals("STORE_BATCH_TOO_LARGE", result.items().get(0).errorCode());
        assertEquals(WriteStatus.FINAL_FAILED, result.items().get(500).status());
        assertEquals("STORE_SAMPLE_INVALID", result.items().get(500).errorCode());
        assertNull(result.items().get(500).messageId());
    }

    @Test
    void deterministicInvalidValueIsFinalWithoutNetworkAccess() {
        WriteItemResult result = store.appendBatch(List.of(
                sample("td-invalid-value", "{\"value\":\"not-a-decimal\"}"))).items().get(0);
        assertEquals(WriteStatus.FINAL_FAILED, result.status());
        assertEquals("STORE_VALUE_INVALID", result.errorCode());
    }

    @Test
    void deterministicClassificationCoversDuplicateCollisionAndCorruptStateWithoutNetwork() throws Exception {
        TelemetrySample sample = sample("td-classify", "{\"value\":\"1.0\"}");
        assertEquals(WriteStatus.DUPLICATE,
                classify(sample, List.of("a".repeat(64))).status());
        WriteItemResult collision = classify(sample, List.of("b".repeat(64)));
        assertEquals(WriteStatus.FINAL_FAILED, collision.status());
        assertEquals("MESSAGE_ID_COLLISION", collision.errorCode());
        WriteItemResult corrupt = classify(sample, List.of("a".repeat(64), "b".repeat(64)));
        assertEquals(WriteStatus.FINAL_FAILED, corrupt.status());
        assertEquals("STORE_STATE_CORRUPT", corrupt.errorCode());
    }

    private static WriteItemResult classify(TelemetrySample sample, List<String> hashes) throws Exception {
        Method method = TDengineTelemetryStore.class.getDeclaredMethod(
                "classifyExisting", TelemetrySample.class, List.class);
        method.setAccessible(true);
        return (WriteItemResult) method.invoke(null, sample, hashes);
    }

    private static TelemetrySample sample(String messageId, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new TelemetrySample(messageId, "request-" + messageId, "100", "site",
                "device", "property", bytes, "a".repeat(64), 1L, 1L, "test", 1L);
    }
}
