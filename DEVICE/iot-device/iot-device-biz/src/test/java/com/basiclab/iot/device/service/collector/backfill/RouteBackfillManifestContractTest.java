package com.basiclab.iot.device.service.collector.backfill;

import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillIssue;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestEntry;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillResolutionResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteBackfillManifestContractTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void manifestAndArtifactAreDeterministicAndDefensivelyCopied() {
        RouteBackfillManifestEntry entry = entry("device-a", "product-a", 17L);
        List<RouteBackfillManifestEntry> source = new ArrayList<>(List.of(entry));
        RouteBackfillKey cursor = entry.key();
        RouteBackfillManifest manifest = new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                HASH, "workload-a", source, cursor);
        RouteBackfillManifestArtifact first = new RouteBackfillManifestArtifact(manifest);
        RouteBackfillManifestArtifact second = new RouteBackfillManifestArtifact(manifest);

        source.clear();
        assertEquals(1, manifest.entries().size());
        assertArrayEquals(first.canonicalBytes(), second.canonicalBytes());
        assertEquals(first.contentSha256(), second.contentSha256());
        assertEquals(first.contentSha256(), new RouteBackfillManifestArtifact(manifest).contentSha256());
        byte[] bytes = first.canonicalBytes();
        bytes[0] ^= 1;
        assertArrayEquals(first.canonicalBytes(), second.canonicalBytes());
        assertTrue(new String(first.canonicalBytes(), StandardCharsets.UTF_8)
                .contains("sourceInventorySha256"));
        assertNotEquals(first.contentSha256(), "0".repeat(64));
    }

    @Test
    void emptyManifestIsValidButCursorIsNot() {
        RouteBackfillManifest empty = new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                HASH, "workload-a", List.of(), null);
        assertTrue(empty.entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                HASH, "workload-a", List.of(), key("device-a")));
    }

    @Test
    void contractsRejectUnknownCodesInvalidHashesAndMismatchedWorkloads() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillIssue(key("device-a"), "NOT_A_STABLE_CODE"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillManifestEntry(key("device-a"), 1,
                        "product-a", "workload-a", 1, "B".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillManifestEntry(key("device-a"), 1,
                        " ", "workload-a", 1, HASH));
        RouteBackfillManifestEntry wrongWorkload = entry("device-a", "product-a", 1);
        assertThrows(IllegalArgumentException.class, () -> new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                HASH, "workload-b", List.of(wrongWorkload), null));
    }

    @Test
    void artifactRejectsModifiedBytesAndHash() {
        RouteBackfillManifest manifest = new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                HASH, "workload-a", List.of(), null);
        RouteBackfillManifestArtifact artifact = new RouteBackfillManifestArtifact(manifest);
        byte[] modified = artifact.canonicalBytes();
        modified[modified.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> new RouteBackfillManifestArtifact(
                manifest, modified, artifact.contentSha256()));
        assertThrows(IllegalArgumentException.class, () -> new RouteBackfillManifestArtifact(
                manifest, artifact.canonicalBytes(), "0".repeat(64)));
    }

    @Test
    void rejectedResultCopiesIssuesAndCarriesNoManifest() {
        List<RouteBackfillIssue> source = new ArrayList<>(List.of(
                new RouteBackfillIssue(key("device-a"), "ROUTE_BACKFILL_RELEASE_NOT_UNIQUE")));
        RouteBackfillResolutionResult.Rejected rejected = new RouteBackfillResolutionResult.Rejected(
                HASH, "workload-a", source);
        source.clear();
        assertEquals(1, rejected.issues().size());
        assertThrows(UnsupportedOperationException.class, () -> rejected.issues().clear());
        assertThrows(IllegalArgumentException.class, () -> new RouteBackfillResolutionResult.Rejected(
                HASH, "workload-a", List.of()));
    }

    private static RouteBackfillManifestEntry entry(String device, String product, long release) {
        return new RouteBackfillManifestEntry(key(device), 2, product, "workload-a", release, HASH);
    }

    private static RouteBackfillKey key(String device) {
        return new RouteBackfillKey("1", "site-a", 1, device);
    }
}
