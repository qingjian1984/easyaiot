package com.basiclab.iot.sink.polling;

import java.util.List;

/** Immutable, already validated v1.1 collector configuration snapshot. */
public record CollectorConfigSnapshot(
        String schemaVersion,
        String productIdentification,
        String workloadId,
        String tenantId,
        String siteId,
        String siteCode,
        long configVersion,
        String generatedAt,
        List<CollectorSerialBus> serialBuses,
        String payloadSha256
) {
    public CollectorConfigSnapshot {
        if (!"1.1".equals(schemaVersion) || productIdentification == null || productIdentification.isBlank()
                || workloadId == null || workloadId.isBlank() || tenantId == null || tenantId.isBlank()
                || siteId == null || siteId.isBlank() || siteCode == null || siteCode.isBlank()
                || configVersion < 1 || generatedAt == null || generatedAt.isBlank()
                || serialBuses == null || serialBuses.isEmpty()) {
            throw new IllegalArgumentException("invalid collector config snapshot");
        }
        if (payloadSha256 != null && !payloadSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("payloadSha256 must be sha256");
        }
        serialBuses = List.copyOf(serialBuses);
    }

    public CollectorConfigSnapshot withPayloadSha256(String sha256) {
        return new CollectorConfigSnapshot(schemaVersion, productIdentification, workloadId, tenantId, siteId,
                siteCode, configVersion, generatedAt, serialBuses, sha256);
    }
}
