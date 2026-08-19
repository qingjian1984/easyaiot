package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigErrorCode;
import com.basiclab.iot.sink.polling.CollectorConfigStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFilePollingConfigProviderTest {
    private static final String WORKLOAD = "collector-site-1001-a";

    @Test
    void firstApplyPreservesOriginalBytesAndWritesClosedObserved(@TempDir Path directory) throws Exception {
        LocalFilePollingConfigProvider provider = providerWithDesired(directory, 1, "voltage");
        AtomicBoolean graphBuilt = new AtomicBoolean();
        var result = provider.reconcile(snapshot -> graphBuilt.set(true));
        assertEquals(CollectorConfigStatus.APPLIED, result.status());
        assertTrue(graphBuilt.get());
        assertTrue(Files.exists(directory.resolve("active.json")));
        assertTrue(Files.exists(directory.resolve("history/1-" + result.payloadSha256().substring(0, 8) + ".json")));
        String observed = Files.readString(directory.resolve("observed.json"));
        assertTrue(observed.contains("\"status\":\"APPLIED\""));
        assertFalse(observed.contains("errorDetail"));
        assertEquals(result.payloadSha256(), LocalFilePollingConfigProviderTest.sha256(Files.readAllBytes(directory.resolve("active.json"))));
    }

    @Test
    void noConfigWaitsAndGraphFailureDoesNotPublishActive(@TempDir Path directory) throws Exception {
        prepareDirectory(directory);
        LocalFilePollingConfigProvider empty = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        var waiting = empty.reconcile(snapshot -> { });
        assertEquals(CollectorConfigStatus.WAITING_CONFIG, waiting.status());
        assertFalse(Files.exists(directory.resolve("active.json")));
        String waitingObserved = Files.readString(directory.resolve("observed.json"));
        assertTrue(waitingObserved.contains("\"status\":\"WAITING_CONFIG\""));
        assertFalse(waitingObserved.contains("configVersion"));
        assertFalse(waitingObserved.contains("payloadSha256"));

        LocalFilePollingConfigProvider failing = providerWithDesired(directory.resolve("second"), 2, "current");
        var result = failing.reconcile(snapshot -> { throw new IllegalStateException("injected graph failure"); });
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_APPLY_FAILED,
                result.errorCode());
        assertFalse(Files.exists(directory.resolve("second/active.json")));
        String failedObserved = Files.readString(directory.resolve("second/observed.json"));
        assertTrue(failedObserved.contains("\"status\":\"FAILED\""));
        assertTrue(failedObserved.contains("\"errorCode\":\"COLLECTOR_CONFIG_APPLY_FAILED\""));
        assertFalse(failedObserved.contains("errorDetail"));
    }

    @Test
    void restartRebuildsFromActiveAndHistoryTempsAreIgnored(@TempDir Path directory) throws Exception {
        LocalFilePollingConfigProvider first = providerWithDesired(directory, 3, "current");
        var applied = first.reconcile(snapshot -> { });
        Path history = directory.resolve("history");
        Files.writeString(history.resolve(".3-" + "a".repeat(8) + ".json." + "b".repeat(32) + ".tmp"),
                "partial");
        Files.writeString(history.resolve(".3-" + "a".repeat(8) + ".json." + "c".repeat(32) + ".restore"),
                "partial");
        LocalFilePollingConfigProvider restarted = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        assertEquals(applied.configVersion(), restarted.current().orElseThrow().configVersion());
        assertEquals(CollectorConfigStatus.APPLIED, restarted.reconcile(snapshot -> { }).status());
    }

    @Test
    void sameVersionDifferentHashIsStateCorrupt(@TempDir Path directory) throws Exception {
        byte[] first = CollectorConfigTestFixtures.canonical(WORKLOAD, 4, "a");
        byte[] second = CollectorConfigTestFixtures.canonical(WORKLOAD, 4, "b");
        prepareDirectory(directory);
        Files.write(directory.resolve("active.json"), first);
        Files.write(directory.resolve("desired.json"), second);
        setFileMode(directory.resolve("active.json"));
        setFileMode(directory.resolve("desired.json"));
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        var result = provider.reconcile(snapshot -> { });
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_STATE_CORRUPT, result.errorCode());
    }

    @Test
    void activeHistoryAndObservedWriteFailuresRemainClosed(@TempDir Path directory) throws Exception {
        prepareDirectory(directory);
        CollectorConfigSnapshotCodec.DecodedSnapshot decoded = decoded(5, "voltage");

        Files.writeString(directory.resolve("history"), "not-a-directory");
        setFileMode(directory.resolve("history"));
        LocalFilePollingConfigProvider historyFailure = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        CollectorConfigStateException historyError = assertThrows(CollectorConfigStateException.class,
                () -> historyFailure.persistActiveAndHistory(decoded));
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, historyError.errorCode());

        Files.delete(directory.resolve("history"));
        Files.createDirectory(directory.resolve("active.json"));
        LocalFilePollingConfigProvider activeFailure = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        CollectorConfigStateException activeError = assertThrows(CollectorConfigStateException.class,
                () -> activeFailure.persistActiveAndHistory(decoded));
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, activeError.errorCode());
        Files.delete(directory.resolve("active.json"));

        LocalFilePollingConfigProvider observedFailure = providerWithDesired(directory, 6, "current");
        var result = observedFailure.reconcile(snapshot -> {
            try {
                Files.createDirectory(directory.resolve("observed.json"));
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_WRITE_FAILED, result.errorCode());
        assertFalse(Files.exists(directory.resolve("active.json")));
    }

    @Test
    void activePersistsBeforeRestartAndRebuildsWithoutDesired(@TempDir Path directory) throws Exception {
        prepareDirectory(directory);
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        provider.persistActiveAndHistory(decoded(7, "current"));

        LocalFilePollingConfigProvider restarted = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        assertEquals(7, restarted.current().orElseThrow().configVersion());
        assertEquals(CollectorConfigStatus.APPLIED, restarted.reconcile(snapshot -> { }).status());
    }

    @Test
    void startupBuildsKnownActiveBeforeRejectingMalformedDesired(@TempDir Path directory) throws Exception {
        prepareDirectory(directory);
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        provider.persistActiveAndHistory(decoded(9, "current"));
        Path desired = directory.resolve("desired.json");
        Files.writeString(desired, "not-json");
        setFileMode(desired);

        AtomicBoolean activeWasBuilt = new AtomicBoolean();
        var result = provider.reconcile(snapshot -> {
            assertEquals(9, snapshot.configVersion());
            activeWasBuilt.set(true);
        });
        assertTrue(activeWasBuilt.get());
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_JSON_INVALID, result.errorCode());
        assertEquals(9, provider.current().orElseThrow().configVersion());
    }

    @Test
    void candidateGraphIsPreparedBeforePersistenceAndCommittedAfterActive(@TempDir Path directory) throws Exception {
        LocalFilePollingConfigProvider provider = providerWithDesired(directory, 10, "current");
        List<String> order = new ArrayList<>();
        var result = provider.reconcile(new LocalFilePollingConfigProvider.GraphApplier() {
            @Override
            public void prepare(com.basiclab.iot.sink.polling.CollectorConfigSnapshot snapshot) {
                assertFalse(Files.exists(directory.resolve("active.json")));
                assertFalse(Files.exists(directory.resolve("history/10-"
                        + CollectorConfigSnapshotCodec.sha256(CollectorConfigTestFixtures.canonical(
                        WORKLOAD, 10, "current")).substring(0, 8) + ".json")));
                order.add("prepare");
            }

            @Override
            public void replace(com.basiclab.iot.sink.polling.CollectorConfigSnapshot snapshot) {
                assertTrue(Files.exists(directory.resolve("active.json")));
                assertTrue(Files.exists(directory.resolve("history/10-"
                        + snapshot.payloadSha256().substring(0, 8) + ".json")));
                order.add("replace");
            }

            @Override
            public void restore(com.basiclab.iot.sink.polling.CollectorConfigSnapshot snapshot) {
            }
        });
        assertEquals(CollectorConfigStatus.APPLIED, result.status());
        assertEquals(List.of("prepare", "replace"), order);
    }

    @Test
    void graphPrepareFailureLeavesOldActiveHistoryAndGraphUntouched(@TempDir Path directory) throws Exception {
        prepareDirectory(directory);
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        byte[] oldBytes = CollectorConfigTestFixtures.canonical(WORKLOAD, 11, "old");
        provider.persistActiveAndHistory(new CollectorConfigSnapshotCodec().decode(oldBytes, WORKLOAD));
        Path desired = directory.resolve("desired.json");
        byte[] candidateBytes = CollectorConfigTestFixtures.canonical(WORKLOAD, 12, "new");
        Files.write(desired, candidateBytes);
        setFileMode(desired);

        AtomicBoolean activePrepared = new AtomicBoolean();
        var result = provider.reconcile(new LocalFilePollingConfigProvider.GraphApplier() {
            @Override
            public void prepare(com.basiclab.iot.sink.polling.CollectorConfigSnapshot snapshot) {
                if (activePrepared.getAndSet(true)) {
                    throw new IllegalStateException("candidate graph build failure");
                }
            }

            @Override
            public void replace(com.basiclab.iot.sink.polling.CollectorConfigSnapshot snapshot) {
                assertEquals(11, snapshot.configVersion());
            }

            @Override
            public void restore(com.basiclab.iot.sink.polling.CollectorConfigSnapshot snapshot) {
                throw new AssertionError("restore must not run before persistence");
            }
        });
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_APPLY_FAILED, result.errorCode());
        assertEquals(java.util.Arrays.toString(oldBytes), java.util.Arrays.toString(Files.readAllBytes(
                directory.resolve("active.json"))));
        assertFalse(Files.exists(directory.resolve("history/12-"
                + CollectorConfigSnapshotCodec.sha256(candidateBytes).substring(0, 8) + ".json")));
    }

    @Test
    void lockAndStatePathsRejectSymlinkOrNonRegularEntries(@TempDir Path directory) throws Exception {
        prepareDirectory(directory);
        Path lock = directory.resolve(LocalFilePollingConfigProvider.LOCK_FILE);
        Path target = directory.resolve("lock-target");
        Files.writeString(target, "lock");
        try {
            Files.createSymbolicLink(lock, target.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException denied) {
            Files.createDirectory(lock);
        }
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        CollectorConfigStateException error = assertThrows(CollectorConfigStateException.class,
                () -> provider.reconcile(snapshot -> { }));
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, error.errorCode());
    }

    @Test
    void existingPermissionDriftIsRejectedAndPortableWindowsPathRemainsCovered(@TempDir Path directory)
            throws Exception {
        prepareDirectory(directory);
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) == null) {
            assertEquals(02770, LocalFilePollingConfigProvider.LINUX_CONFIG_DIRECTORY_MODE);
            assertEquals(0660, LocalFilePollingConfigProvider.LINUX_CONFIG_FILE_MODE);
            return;
        }
        Files.setAttribute(directory, "unix:mode", 0770, LinkOption.NOFOLLOW_LINKS);
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(directory, WORKLOAD);
        CollectorConfigStateException directoryError = assertThrows(CollectorConfigStateException.class,
                () -> provider.current());
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, directoryError.errorCode());

        prepareDirectory(directory);
        Path desired = directory.resolve("desired.json");
        Files.write(desired, CollectorConfigTestFixtures.canonical(WORKLOAD, 8, "voltage"));
        Files.setAttribute(desired, "unix:mode", 0640, LinkOption.NOFOLLOW_LINKS);
        CollectorConfigStateException fileError = assertThrows(CollectorConfigStateException.class,
                () -> provider.candidate(8));
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, fileError.errorCode());
    }

    @Test
    void relativeConfigPathIsRejectedBeforeStateCreation() {
        CollectorConfigStateException error = assertThrows(CollectorConfigStateException.class,
                () -> new LocalFilePollingConfigProvider(Path.of("relative-config"), WORKLOAD));
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, error.errorCode());
    }

    @Test
    void parentSymlinkIsRejectedBeforeStateAccess(@TempDir Path directory) throws Exception {
        Path real = directory.resolve("real");
        prepareDirectory(real);
        Path redirected = directory.resolve("redirected");
        boolean linked = false;
        try {
            Files.createSymbolicLink(redirected, real.getFileName());
            linked = true;
        } catch (UnsupportedOperationException | java.io.IOException denied) {
            Files.writeString(redirected, "not a directory");
        }
        Path config = redirected.resolve("config");
        if (linked) {
            CollectorConfigStateException error = assertThrows(CollectorConfigStateException.class,
                    () -> new LocalFilePollingConfigProvider(config.toAbsolutePath(), WORKLOAD));
            assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_PERMISSION_INVALID, error.errorCode());
        } else {
            assertTrue(Files.isRegularFile(redirected));
        }
    }

    private static CollectorConfigSnapshotCodec.DecodedSnapshot decoded(long version, String property) {
        byte[] bytes = CollectorConfigTestFixtures.canonical(WORKLOAD, version, property);
        return new CollectorConfigSnapshotCodec().decode(bytes, WORKLOAD);
    }

    private static LocalFilePollingConfigProvider providerWithDesired(Path directory, long version,
                                                                       String property) throws Exception {
        prepareDirectory(directory);
        Path desired = directory.resolve("desired.json");
        Files.write(desired, CollectorConfigTestFixtures.canonical(WORKLOAD, version, property));
        setFileMode(desired);
        return new LocalFilePollingConfigProvider(directory.toAbsolutePath(), WORKLOAD);
    }

    private static void prepareDirectory(Path directory) throws Exception {
        Files.createDirectories(directory);
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setAttribute(directory, "unix:mode", LocalFilePollingConfigProvider.LINUX_CONFIG_DIRECTORY_MODE,
                    LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static void setFileMode(Path file) throws Exception {
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setAttribute(file, "unix:mode", LocalFilePollingConfigProvider.LINUX_CONFIG_FILE_MODE,
                    LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return CollectorConfigSnapshotCodec.sha256(value);
    }
}
