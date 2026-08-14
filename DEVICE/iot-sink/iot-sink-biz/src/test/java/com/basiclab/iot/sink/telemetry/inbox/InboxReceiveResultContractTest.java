package com.basiclab.iot.sink.telemetry.inbox;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M1-LC-01 pure contract tests; no PostgreSQL or MQTT required. */
class InboxReceiveResultContractTest {

    @Test
    void emptyBatchIsValid() {
        InboxReceiveResult.Batch batch = new InboxReceiveResult.Batch(List.of());
        assertTrue(batch.items().isEmpty());
    }

    @Test
    void nullBatchItemsFailFast() {
        assertThrows(NullPointerException.class, () -> new InboxReceiveResult.Batch(null));
    }

    @Test
    void batchDefensivelyCopiesAndRejectsMutation() {
        InboxReceiveResult.Item item = new InboxReceiveResult.Item(0, "m", "r",
                InboxReceiveResult.Status.ACCEPTED_DURABLE, 1L);
        ArrayList<InboxReceiveResult.Item> source = new ArrayList<>(List.of(item));
        InboxReceiveResult.Batch batch = new InboxReceiveResult.Batch(source);
        source.clear();
        assertEquals(List.of(item), batch.items());
        assertThrows(UnsupportedOperationException.class, () -> batch.items().clear());
    }

    @Test
    void itemEnforcesStatusSpecificPersistenceTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new InboxReceiveResult.Item(0, "m", "r",
                        InboxReceiveResult.Status.MESSAGE_ID_COLLISION, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new InboxReceiveResult.Item(0, "m", "r",
                        InboxReceiveResult.Status.DUPLICATE, null));
        assertNull(new InboxReceiveResult.Item(0, "m", "r",
                InboxReceiveResult.Status.MESSAGE_ID_COLLISION, null).persistedAtMs());
    }

    @Test
    void batchRequiresContiguousInputIndexes() {
        InboxReceiveResult.Item item = new InboxReceiveResult.Item(1, "m", "r",
                InboxReceiveResult.Status.ACCEPTED_DURABLE, 1L);
        assertThrows(IllegalArgumentException.class,
                () -> new InboxReceiveResult.Batch(List.of(item)));
    }
}
