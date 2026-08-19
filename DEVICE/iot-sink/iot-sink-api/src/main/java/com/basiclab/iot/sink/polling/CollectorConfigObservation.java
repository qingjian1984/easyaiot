package com.basiclab.iot.sink.polling;

import java.time.Instant;

/** Compact observed.json value; deliberately has no free-form error detail. */
public record CollectorConfigObservation(
        String workloadId,
        CollectorConfigStatus status,
        Long configVersion,
        String payloadSha256,
        String observedAt,
        CollectorConfigErrorCode errorCode
) {
    public CollectorConfigObservation {
        if (workloadId == null || workloadId.isBlank()) {
            throw new IllegalArgumentException("workloadId is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (observedAt == null || observedAt.isBlank()) {
            observedAt = Instant.now().toString();
        }
        if (status == CollectorConfigStatus.APPLIED) {
            if (configVersion == null || configVersion < 1 || payloadSha256 == null
                    || !payloadSha256.matches("[0-9a-fA-F]{64}") || errorCode != null) {
                throw new IllegalArgumentException("APPLIED observation must contain version/hash only");
            }
        } else if (status == CollectorConfigStatus.WAITING_CONFIG) {
            if (configVersion != null || payloadSha256 != null || errorCode != null) {
                throw new IllegalArgumentException("WAITING_CONFIG cannot carry config identity");
            }
        } else if (errorCode == null) {
            throw new IllegalArgumentException("FAILED observation requires a stable error code");
        }
    }

    public static CollectorConfigObservation waiting(String workloadId) {
        return new CollectorConfigObservation(workloadId, CollectorConfigStatus.WAITING_CONFIG,
                null, null, Instant.now().toString(), null);
    }

    public static CollectorConfigObservation applied(String workloadId, long version, String sha256) {
        return new CollectorConfigObservation(workloadId, CollectorConfigStatus.APPLIED,
                version, sha256, Instant.now().toString(), null);
    }

    public static CollectorConfigObservation failed(String workloadId, Long candidateVersion,
                                                     String candidateSha256, CollectorConfigErrorCode code) {
        return new CollectorConfigObservation(workloadId, CollectorConfigStatus.FAILED,
                candidateVersion, candidateSha256, Instant.now().toString(), code);
    }
}
