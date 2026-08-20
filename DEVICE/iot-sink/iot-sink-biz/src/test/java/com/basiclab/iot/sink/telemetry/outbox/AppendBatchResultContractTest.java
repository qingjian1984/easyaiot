package com.basiclab.iot.sink.telemetry.outbox;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppendBatchResultContractTest {

    @Test
    void successAndCollisionExposeDisjointImmutableResultGroups() {
        AppendBatchResult.Success success = new AppendBatchResult.Success(
                List.of("stored-1"), List.of("duplicate-1"));
        assertEquals(List.of("stored-1"), success.storedMessageIds());
        assertEquals(List.of("duplicate-1"), success.duplicateMessageIds());
        assertTrue(success.collisionMessageIds().isEmpty());

        List<String> source = new ArrayList<>(List.of("collision-1", "collision-2"));
        AppendBatchResult.Collision collision = new AppendBatchResult.Collision(source);
        source.clear();

        assertTrue(collision.storedMessageIds().isEmpty());
        assertTrue(collision.duplicateMessageIds().isEmpty());
        assertEquals(List.of("collision-1", "collision-2"), collision.collisionMessageIds());
        assertThrows(UnsupportedOperationException.class,
                () -> collision.collisionMessageIds().add("collision-3"));
        assertThrows(UnsupportedOperationException.class,
                () -> collision.storedMessageIds().add("stored-2"));
        assertThrows(UnsupportedOperationException.class,
                () -> collision.duplicateMessageIds().add("duplicate-2"));
    }

    @Test
    void collisionRejectsNullEmptyAndBlankIds() {
        assertThrows(IllegalArgumentException.class, () -> new AppendBatchResult.Collision(null));
        assertThrows(IllegalArgumentException.class, () -> new AppendBatchResult.Collision(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AppendBatchResult.Collision(Arrays.asList("collision-1", null)));
        assertThrows(IllegalArgumentException.class,
                () -> new AppendBatchResult.Collision(List.of("collision-1", " \t")));
    }
}
