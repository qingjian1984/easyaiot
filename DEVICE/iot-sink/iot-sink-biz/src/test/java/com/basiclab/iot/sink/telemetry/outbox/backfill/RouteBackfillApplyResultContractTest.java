package com.basiclab.iot.sink.telemetry.outbox.backfill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteBackfillApplyResultContractTest {

    private static final String OPERATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String MANIFEST_HASH = "a".repeat(64);

    @Test
    void appliedCopiesCursorAndRejectsNonCanonicalCorrelation() {
        RouteBackfillKey cursor = new RouteBackfillKey("tenant", "site", 7, "device");
        RouteBackfillApplyResult.Applied result = new RouteBackfillApplyResult.Applied(
                OPERATION_ID, MANIFEST_HASH, 2, cursor);

        assertEquals(cursor, result.nextInventoryCursor());
        assertNotSame(cursor, result.nextInventoryCursor());
        assertEquals("device", result.nextInventoryCursor().deviceIdentification());
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillApplyResult.Applied(OPERATION_ID.toUpperCase(), MANIFEST_HASH,
                        1, cursor));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillApplyResult.Applied(OPERATION_ID, "A".repeat(64), 1, cursor));
    }

    @Test
    void rejectedAndDegradedOnlyAcceptFrozenCodes() {
        new RouteBackfillApplyResult.Rejected(null, null,
                "ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED");
        new RouteBackfillApplyResult.Rejected(OPERATION_ID, MANIFEST_HASH,
                "ROUTE_BACKFILL_OPERATION_COLLISION");
        new RouteBackfillApplyResult.Degraded(OPERATION_ID, MANIFEST_HASH,
                "ROUTE_BACKFILL_CHECKPOINT_CONFLICT");

        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillApplyResult.Rejected(OPERATION_ID, MANIFEST_HASH,
                        "ROUTE_BACKFILL_APPLY_FAILED"));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillApplyResult.Degraded(OPERATION_ID, MANIFEST_HASH,
                        "ROUTE_BACKFILL_APPLY_FAILED"));
    }

    @Test
    void correlationIsCanonicalOrNullAndCountsAreNonNegative() {
        new RouteBackfillApplyResult.Rejected(null, null,
                "ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID");
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillApplyResult.Applied(null, MANIFEST_HASH, 1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillApplyResult.AlreadyApplied(OPERATION_ID, null, 1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillApplyResult.Degraded(OPERATION_ID, "A".repeat(64),
                        "ROUTE_BACKFILL_CHECKPOINT_CONFLICT"));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillApplyResult.Applied(OPERATION_ID, MANIFEST_HASH, -1, null));
    }

    @Test
    void everyFrozenResultCodeIsAcceptedOnlyByItsResultKind() {
        String[] verifierCodes = {
            "ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED",
            "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED",
            "ROUTE_BACKFILL_AUTHORIZATION_BINDING_MISMATCH",
            "ROUTE_BACKFILL_TARGET_WORKLOAD_MISMATCH",
            "ROUTE_BACKFILL_AUTHORIZATION_KEY_UNKNOWN",
            "ROUTE_BACKFILL_AUTHORIZATION_NOT_YET_VALID",
            "ROUTE_BACKFILL_AUTHORIZATION_EXPIRED",
            "ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID"
        };
        for (String code : verifierCodes) {
            new RouteBackfillApplyResult.Rejected(null, null, code);
            assertThrows(IllegalArgumentException.class, () ->
                    new RouteBackfillApplyResult.Degraded(OPERATION_ID, MANIFEST_HASH, code));
        }
        new RouteBackfillApplyResult.Rejected(OPERATION_ID, MANIFEST_HASH,
                "ROUTE_BACKFILL_OPERATION_COLLISION");
        String[] degradedCodes = {
            "ROUTE_BACKFILL_CHECKPOINT_CONFLICT",
            "ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT",
            "ROUTE_BACKFILL_ROW_COUNT_MISMATCH"
        };
        for (String code : degradedCodes) {
            new RouteBackfillApplyResult.Degraded(OPERATION_ID, MANIFEST_HASH, code);
            assertThrows(IllegalArgumentException.class, () ->
                    new RouteBackfillApplyResult.Rejected(OPERATION_ID, MANIFEST_HASH, code));
        }
    }
}
