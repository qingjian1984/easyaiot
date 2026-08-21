package com.basiclab.iot.sink.telemetry.outbox.backfill;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Result of applying one authenticated route-backfill manifest page.
 *
 * <p>The result deliberately contains only stable correlation data.  It does
 * not expose the authorization bytes, signature, envelope bytes, or any
 * other outbox payload.</p>
 */
public sealed interface RouteBackfillApplyResult
        permits RouteBackfillApplyResult.Applied,
        RouteBackfillApplyResult.AlreadyApplied,
        RouteBackfillApplyResult.Rejected,
        RouteBackfillApplyResult.Degraded {

    record Applied(String operationId, String manifestContentSha256,
                   long updatedRowCount, RouteBackfillKey nextInventoryCursor)
        implements RouteBackfillApplyResult {
        public Applied {
            requireCorrelation(operationId, manifestContentSha256, false);
            if (updatedRowCount < 0) {
                throw new IllegalArgumentException("updatedRowCount must be >= 0");
            }
            nextInventoryCursor = copyCursor(nextInventoryCursor);
        }
    }

    record AlreadyApplied(String operationId, String manifestContentSha256,
                          long updatedRowCount, RouteBackfillKey nextInventoryCursor)
        implements RouteBackfillApplyResult {
        public AlreadyApplied {
            requireCorrelation(operationId, manifestContentSha256, false);
            if (updatedRowCount < 0) {
                throw new IllegalArgumentException("updatedRowCount must be >= 0");
            }
            nextInventoryCursor = copyCursor(nextInventoryCursor);
        }
    }

    record Rejected(String operationId, String manifestContentSha256, String code)
        implements RouteBackfillApplyResult {
        public Rejected {
            requireCorrelation(operationId, manifestContentSha256, true);
            if (code == null || !REJECTED_CODES.contains(code)) {
                throw new IllegalArgumentException("unsupported route backfill rejection code");
            }
        }
    }

    record Degraded(String operationId, String manifestContentSha256, String code)
        implements RouteBackfillApplyResult {
        public Degraded {
            requireCorrelation(operationId, manifestContentSha256, false);
            if (code == null || !DEGRADED_CODES.contains(code)) {
                throw new IllegalArgumentException("unsupported route backfill degraded code");
            }
        }
    }

    Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    Set<String> REJECTED_CODES = Set.of(
            "ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED",
            "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED",
            "ROUTE_BACKFILL_AUTHORIZATION_BINDING_MISMATCH",
            "ROUTE_BACKFILL_TARGET_WORKLOAD_MISMATCH",
            "ROUTE_BACKFILL_AUTHORIZATION_KEY_UNKNOWN",
            "ROUTE_BACKFILL_AUTHORIZATION_NOT_YET_VALID",
            "ROUTE_BACKFILL_AUTHORIZATION_EXPIRED",
            "ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID",
            "ROUTE_BACKFILL_OPERATION_COLLISION");
    Set<String> DEGRADED_CODES = Set.of(
            "ROUTE_BACKFILL_CHECKPOINT_CONFLICT",
            "ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT",
            "ROUTE_BACKFILL_ROW_COUNT_MISMATCH");

    private static void requireCorrelation(String operationId, String manifestContentSha256,
                                           boolean allowNull) {
        if (operationId == null) {
            if (!allowNull) {
                throw new IllegalArgumentException("operationId required");
            }
        } else if (!isCanonicalUuid(operationId)) {
            throw new IllegalArgumentException("operationId must be canonical UUID");
        }
        if (manifestContentSha256 == null) {
            if (!allowNull) {
                throw new IllegalArgumentException("manifestContentSha256 required");
            }
        } else if (!SHA256_HEX.matcher(manifestContentSha256).matches()) {
            throw new IllegalArgumentException("manifestContentSha256 must be lowercase SHA-256 hex");
        }
    }

    private static boolean isCanonicalUuid(String value) {
        if (value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static RouteBackfillKey copyCursor(RouteBackfillKey cursor) {
        return cursor == null ? null : new RouteBackfillKey(
                cursor.tenantId(), cursor.siteCode(), cursor.configVersion(),
                cursor.deviceIdentification());
    }
}
