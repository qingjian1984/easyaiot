package com.basiclab.iot.sink.telemetry.outbox.backfill;

import java.util.List;

/** All-or-nothing result of center-side route identity resolution. */
public sealed interface RouteBackfillResolutionResult
        permits RouteBackfillResolutionResult.Resolved, RouteBackfillResolutionResult.Rejected {

    record Resolved(RouteBackfillManifestArtifact artifact)
            implements RouteBackfillResolutionResult {
        public Resolved {
            if (artifact == null) {
                throw new IllegalArgumentException("artifact required");
            }
        }
    }

    record Rejected(String sourceInventorySha256, String workloadId,
                    List<RouteBackfillIssue> issues)
            implements RouteBackfillResolutionResult {
        public Rejected {
            if (sourceInventorySha256 == null || sourceInventorySha256.isBlank()) {
                throw new IllegalArgumentException("sourceInventorySha256 required");
            }
            if (workloadId == null || workloadId.isBlank()) {
                throw new IllegalArgumentException("workloadId required");
            }
            if (issues == null || issues.isEmpty()) {
                throw new IllegalArgumentException("issues required");
            }
            if (issues.stream().anyMatch(issue -> issue == null)) {
                throw new IllegalArgumentException("issues must not contain null");
            }
            issues = List.copyOf(issues);
        }
    }
}
