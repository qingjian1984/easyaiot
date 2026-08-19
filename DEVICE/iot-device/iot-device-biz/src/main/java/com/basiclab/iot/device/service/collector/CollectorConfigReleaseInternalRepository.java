package com.basiclab.iot.device.service.collector;

import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedStatus;

import java.util.List;
import java.util.Optional;

/** collector release 内部读取与 observed CAS 的持久化端口。 */
public interface CollectorConfigReleaseInternalRepository {

    List<ReleaseRecord> findPending(int limit);

    Optional<ReleaseRecord> findById(long releaseId);

    ObservedCasResult observe(ObservedRecord observed);

    record ReleaseRecord(long releaseId,
                         long tenantId,
                         long nodeId,
                         String workloadId,
                         long configVersion,
                         String schemaVersion,
                         String canonicalizationVersion,
                         String payloadCanonical,
                         String payloadSha256,
                         long canonicalLengthBytes,
                         String publishedAt) {
    }

    record ObservedRecord(long releaseId,
                          long tenantId,
                          long nodeId,
                          String workloadId,
                          long configVersion,
                          String payloadSha256,
                          CollectorConfigReleaseObservedStatus status,
                          String errorCode,
                          String errorDetailSanitized) {
    }

    enum Outcome {
        APPLIED,
        FAILED,
        AGENT_ACCEPTED,
        IDEMPOTENT,
        STALE,
        MISMATCH
    }

    record ObservedCasResult(Outcome outcome) {
    }
}
