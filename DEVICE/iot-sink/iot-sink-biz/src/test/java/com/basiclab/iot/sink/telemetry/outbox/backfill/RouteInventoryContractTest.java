package com.basiclab.iot.sink.telemetry.outbox.backfill;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteInventoryContractTest {

    @Test
    void keyValidatesRequiredValuesWithoutChangingOriginalStrings() {
        String nfd = "cafe\u0301-meter";
        RouteBackfillKey key = new RouteBackfillKey(" tenant ", "site", 0, nfd);

        assertEquals(" tenant ", key.tenantId());
        assertEquals(nfd, key.deviceIdentification());
        assertEquals(0, key.configVersion());
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillKey(null, "site", 0, "device"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillKey(" ", "site", 0, "device"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillKey("tenant", null, 0, "device"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillKey("tenant", "", 0, "device"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillKey("tenant", "site", -1, "device"));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillKey("tenant", "site", 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillKey("tenant", "site", 0, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteBackfillKey("tenant", "site", 0, "\t"));
    }

    @Test
    void pageCopiesEntriesAndEnforcesStrictKeyOrderAndCursorRelation() {
        RouteInventoryEntry first = new RouteInventoryEntry(key("tenant-a", "site-a", 1, "device-a"), 2);
        RouteInventoryEntry second = new RouteInventoryEntry(key("tenant-a", "site-a", 1, "device-b"), 3);
        List<RouteInventoryEntry> source = new ArrayList<>(List.of(first, second));
        RouteInventoryPage page = new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", source, second.key());

        source.clear();
        assertEquals(2, page.entries().size());
        assertEquals(second.key(), page.nextCursor());
        assertThrows(UnsupportedOperationException.class, () -> page.entries().clear());
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", List.of(second, first), null));
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", List.of(first, first), null));
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", List.of(first), key("tenant-a", "site-a", 1, "device-c")));
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", List.of(), first.key()));
        List<RouteInventoryEntry> entriesWithNull = new ArrayList<>();
        entriesWithNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", null, null));
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", entriesWithNull, null));
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                "1.1", RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION, "other-canonicalization",
                "workload-1", List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION, RouteInventoryPage.CANONICALIZATION_VERSION,
                null, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION, RouteInventoryPage.CANONICALIZATION_VERSION,
                " ", List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryEntry(null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryEntry(first.key(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryEntry(first.key(), -1));
    }

    @Test
    void artifactIsCanonicalDeterministicAndDefensivelyCopied() {
        String nfd = "cafe\u0301-meter";
        RouteInventoryPage page = new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1",
                List.of(new RouteInventoryEntry(key("tenant", "site", 3, nfd), 4)),
                null);

        RouteInventoryArtifact first = new RouteInventoryArtifact(page);
        RouteInventoryArtifact second = new RouteInventoryArtifact(page);
        byte[] original = first.canonicalBytes();
        assertArrayEquals(original, second.canonicalBytes());
        assertEquals(first.contentSha256(), second.contentSha256());
        assertFalse(new String(original, StandardCharsets.UTF_8).startsWith("\uFEFF"));
        assertFalse(new String(original, StandardCharsets.UTF_8).contains("\n"));
        assertTrue(new String(original, StandardCharsets.UTF_8).contains(nfd));
        assertFalse(new String(original, StandardCharsets.UTF_8).contains("messageId"));

        original[0] = (byte) (original[0] ^ 0x01);
        assertArrayEquals(first.canonicalBytes(), second.canonicalBytes());
        assertThrows(UnsupportedOperationException.class, () -> page.entries().add(
                new RouteInventoryEntry(key("tenant", "site", 4, "other"), 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryArtifact(null, first.canonicalBytes(), first.contentSha256()));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryArtifact(page, null, first.contentSha256()));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryArtifact(page, new byte[0], first.contentSha256()));
        byte[] mismatchedBytes = first.canonicalBytes();
        mismatchedBytes[mismatchedBytes.length - 1] ^= 0x01;
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryArtifact(page, mismatchedBytes, first.contentSha256()));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryArtifact(page, first.canonicalBytes(), "0".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryArtifact(page, first.canonicalBytes(), "A" + "0".repeat(63)));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteInventoryArtifact(page, first.canonicalBytes(), "g".repeat(64)));

        RouteInventoryPage nfcPage = new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1",
                List.of(new RouteInventoryEntry(key("tenant", "site", 3, "caf\u00e9-meter"), 4)),
                null);
        assertNotEquals(first.contentSha256(), new RouteInventoryArtifact(nfcPage).contentSha256());
    }

    @Test
    void pageRejectsMoreThanMaximumEntries() {
        List<RouteInventoryEntry> entries = new ArrayList<>();
        for (int i = 0; i <= RouteInventoryPage.MAX_ENTRIES; i++) {
            entries.add(new RouteInventoryEntry(key("tenant", "site", 0,
                    String.format("device-%03d", i)), 1));
        }
        RouteInventoryPage maximumPage = new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", entries.subList(0, RouteInventoryPage.MAX_ENTRIES), null);
        assertEquals(RouteInventoryPage.MAX_ENTRIES, maximumPage.entries().size());
        assertThrows(IllegalArgumentException.class, () -> new RouteInventoryPage(
                RouteInventoryPage.SCHEMA_VERSION,
                RouteInventoryPage.CANONICALIZATION_VERSION,
                "workload-1", entries, null));
    }

    private static RouteBackfillKey key(String tenant, String site, long config, String device) {
        return new RouteBackfillKey(tenant, site, config, device);
    }
}
