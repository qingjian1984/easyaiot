package com.basiclab.iot.sink.outbox.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxFileLockTest {

    @Test
    void sameJvmOverlapIsStableAndReleasesChannel(@TempDir Path directory) throws Exception {
        Path lockPath = directory.resolve("collector-outbox.lock");
        try (OutboxFileLock first = new OutboxFileLock(lockPath)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new OutboxFileLock(lockPath));
            assertTrue(failure.getMessage().startsWith("OUTBOX_ALREADY_OWNED"));
        }

        assertDoesNotThrow(() -> {
            try (OutboxFileLock reopened = new OutboxFileLock(lockPath)) {
                // The first failed overlap must not leak the channel.
            }
        });
    }
}
