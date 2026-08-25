package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigErrorCode;
import com.basiclab.iot.sink.polling.CollectorConfigObservation;
import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorConfigStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * OPEN03-08 sink stage.  The test consumes desired.json written by the actual
 * loopback NODE Agent and applies it through the real local-file provider.
 */
class CollectorOpen03CombinedApplyStageTest {

    private static final String WORKLOAD_ID = "collector-open03-e2e-a";

    @Test
    void applyConfiguredStage(@TempDir Path standaloneDirectory) throws Exception {
        String stage = System.getenv("OPEN03_SINK_STAGE");
        if (stage == null || stage.isBlank()) {
            runStandaloneApplyContract(standaloneDirectory);
            return;
        }
        Path configDirectory = Path.of(required("OPEN03_CONFIG_DIRECTORY")).toAbsolutePath().normalize();
        Path fixture = Path.of(required("OPEN03_SINK_FIXTURE")).toAbsolutePath().normalize();
        Path trace = Path.of(required("OPEN03_TRACE_FILE")).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(fixture), "fixture must be a regular file");
        assertTrue(Files.isDirectory(configDirectory), "Agent must create the shared config directory");

        byte[] expected = Files.readAllBytes(fixture);
        long expectedVersion = stage.equals("apply-v1") ? 1L : 2L;
        String expectedHash = CollectorConfigSnapshotCodec.sha256(expected);
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(
                configDirectory, WORKLOAD_ID);
        AtomicReference<CollectorConfigSnapshot> graph = new AtomicReference<>();
        LocalFilePollingConfigProvider.GraphApplier applier = new LocalFilePollingConfigProvider.GraphApplier() {
            @Override
            public void prepare(CollectorConfigSnapshot snapshot) {
                if (stage.equals("apply-v2-failure") && snapshot.configVersion() == 2L) {
                    throw new IllegalStateException("test graph prepare failure");
                }
            }

            @Override
            public void replace(CollectorConfigSnapshot snapshot) {
                graph.set(snapshot);
            }

            @Override
            public void restore(CollectorConfigSnapshot snapshot) {
                graph.set(snapshot);
            }
        };

        if (stage.equals("apply-v1")) {
            CollectorConfigObservation observation = provider.reconcile(applier);
            assertEquals(CollectorConfigStatus.APPLIED, observation.status());
            assertEquals(1L, observation.configVersion());
            assertEquals(expectedHash, observation.payloadSha256());
            assertEquals(1L, graph.get().configVersion());
            assertEquals(expectedHash, graph.get().payloadSha256());
            assertStateBytes(configDirectory, "active.json", expected);
            assertTrue(Files.exists(configDirectory.resolve("history/1-" + expectedHash.substring(0, 8) + ".json")));
            assertObserved(configDirectory, "APPLIED", expectedHash);
            appendTrace(trace, "success", "COLLECTOR_APPLIED");
            return;
        }

        if (!stage.equals("apply-v2-failure")) {
            fail("unsupported OPEN03_SINK_STAGE");
        }
        byte[] activeV1 = Files.readAllBytes(configDirectory.resolve("active.json"));
        assertFalse(Arrays.equals(activeV1, expected), "v2 must be desired, not the existing active bytes");
        CollectorConfigSnapshot activeSnapshot = new CollectorConfigSnapshotCodec()
                .decode(activeV1, WORKLOAD_ID).snapshot();
        graph.set(activeSnapshot);

        CollectorConfigObservation observation = provider.reconcile(applier);
        assertEquals(CollectorConfigStatus.FAILED, observation.status());
        assertEquals(2L, observation.configVersion());
        assertEquals(expectedHash, observation.payloadSha256());
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_APPLY_FAILED, observation.errorCode());
        assertEquals(1L, graph.get().configVersion(), "failed candidate must not replace memory graph");
        assertEquals(1L, new CollectorConfigSnapshotCodec()
                .decode(Files.readAllBytes(configDirectory.resolve("active.json")), WORKLOAD_ID)
                .snapshot().configVersion());
        assertFalse(Files.exists(configDirectory.resolve("history/2-" + expectedHash.substring(0, 8) + ".json")));
        assertStateBytes(configDirectory, "desired.json", expected);
        assertObserved(configDirectory, "FAILED", expectedHash);
        appendTrace(trace, "failure", "COLLECTOR_FAILED");
    }

    /**
     * The repository-wide runner supplies the loopback Agent and executes both
     * chains.  The frozen Maven matrix also invokes this class directly, so
     * that invocation exercises the same production provider without inventing
     * an E2E state or silently skipping the test.
     */
    private static void runStandaloneApplyContract(Path directory) throws Exception {
        byte[] canonical = CollectorConfigTestFixtures.canonical(WORKLOAD_ID, 1L, "voltage-a");
        Files.createDirectories(directory);
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setAttribute(directory, "unix:mode", LocalFilePollingConfigProvider.LINUX_CONFIG_DIRECTORY_MODE,
                    LinkOption.NOFOLLOW_LINKS);
        }
        Path desired = directory.resolve("desired.json");
        Files.write(desired, canonical);
        if (Files.getFileAttributeView(desired, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setAttribute(desired, "unix:mode", LocalFilePollingConfigProvider.LINUX_CONFIG_FILE_MODE,
                    LinkOption.NOFOLLOW_LINKS);
        }
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(
                directory, WORKLOAD_ID);
        AtomicReference<CollectorConfigSnapshot> graph = new AtomicReference<>();
        CollectorConfigObservation observation = provider.reconcile(graph::set);
        assertEquals(CollectorConfigStatus.APPLIED, observation.status());
        assertEquals(1L, observation.configVersion());
        assertEquals(CollectorConfigSnapshotCodec.sha256(canonical), observation.payloadSha256());
        assertNotNull(graph.get());
        assertTrue(Arrays.equals(canonical, Files.readAllBytes(directory.resolve("active.json"))));
    }

    private static void assertStateBytes(Path directory, String name, byte[] expected) throws Exception {
        assertTrue(Files.isRegularFile(directory.resolve(name)), name + " must exist");
        assertTrue(Arrays.equals(expected, Files.readAllBytes(directory.resolve(name))),
                name + " bytes must equal the immutable fixture");
    }

    private static void assertObserved(Path directory, String status, String hash) throws Exception {
        String observed = Files.readString(directory.resolve("observed.json"), StandardCharsets.UTF_8);
        assertTrue(observed.contains("\"status\":\"" + status + "\""));
        assertTrue(observed.contains("\"payloadSha256\":\"" + hash + "\""));
        if (status.equals("APPLIED")) {
            assertFalse(observed.contains("\"errorCode\""));
        } else {
            assertTrue(observed.contains("\"errorCode\":\"COLLECTOR_CONFIG_APPLY_FAILED\""));
        }
    }

    private static void appendTrace(Path trace, String chain, String event) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root;
        if (Files.exists(trace)) {
            root = (ObjectNode) mapper.readTree(Files.readAllBytes(trace));
        } else {
            Files.createDirectories(trace.getParent());
            root = mapper.createObjectNode();
        }
        ArrayNode events = root.withArray(chain);
        boolean present = false;
        for (var value : events) {
            if (event.equals(value.asText())) {
                present = true;
                break;
            }
        }
        if (!present) {
            events.add(event);
        }
        Files.write(trace, mapper.writeValueAsBytes(root));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
