package com.basiclab.iot.sink.telemetry.outbox.backfill;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stable, non-sensitive result of collector-side authorization verification.
 * A rejection carries only correlation-safe identifiers and a stable code;
 * payload, signature, key and canonical bytes never cross this boundary.
 */
public sealed interface RouteBackfillVerificationResult
        permits RouteBackfillVerificationResult.Verified,
        RouteBackfillVerificationResult.Rejected {

        record Verified(RouteBackfillApplyRequest request)
            implements RouteBackfillVerificationResult {
        public Verified {
            if (request == null) {
                throw new IllegalArgumentException("request required");
            }
        }
    }

    record Rejected(String operationId, String manifestContentSha256, String code)
            implements RouteBackfillVerificationResult {
        private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

        public static final Set<String> CODES = Set.of(
                "ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED",
                "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED",
                "ROUTE_BACKFILL_AUTHORIZATION_BINDING_MISMATCH",
                "ROUTE_BACKFILL_TARGET_WORKLOAD_MISMATCH",
                "ROUTE_BACKFILL_AUTHORIZATION_KEY_UNKNOWN",
                "ROUTE_BACKFILL_AUTHORIZATION_NOT_YET_VALID",
                "ROUTE_BACKFILL_AUTHORIZATION_EXPIRED",
                "ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID");

        public Rejected {
            if (operationId != null && !isCanonicalUuid(operationId)) {
                throw new IllegalArgumentException("operationId must be canonical UUID or null");
            }
            if (manifestContentSha256 != null
                    && !SHA256_HEX.matcher(manifestContentSha256).matches()) {
                throw new IllegalArgumentException(
                        "manifestContentSha256 must be lowercase SHA-256 hex or null");
            }
            if (code == null || !CODES.contains(code)) {
                throw new IllegalArgumentException("unsupported route backfill verification code");
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
    }
}
