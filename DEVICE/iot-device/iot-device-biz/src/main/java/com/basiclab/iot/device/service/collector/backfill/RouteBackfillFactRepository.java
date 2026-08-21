package com.basiclab.iot.device.service.collector.backfill;

import java.util.List;

/** Read-only authority queries used by the LC02-04B resolver. */
// The nested facts are intentionally local to this read-only boundary.
public interface RouteBackfillFactRepository {

    List<ReleaseFact> findReleaseFacts(long tenantId, String workloadId,
                                       String siteCode, long configVersion);

    List<ProjectionFact> findProjectionFacts(long tenantId, String workloadId);

    List<ProductFact> findProductFacts(long tenantId, long productId);

    record ReleaseFact(
            long releaseId,
            long tenantId,
            long siteId,
            String siteCode,
            String workloadId,
            long nodeId,
            long configVersion,
            String schemaVersion,
            String canonicalizationVersion,
            String payloadCanonical,
            String payloadProjection,
            String payloadSha256,
            long canonicalLengthBytes,
            String status,
            long productId
    ) {
    }

    record ProjectionFact(
            long tenantId,
            String workloadId,
            long siteId,
            String siteCode,
            long nodeId,
            long productId,
            long configVersion,
            long releaseId,
            String lifecycleStatus
    ) {
    }

    record ProductFact(long tenantId, long productId, String productIdentification) {
    }
}
