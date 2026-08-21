package com.basiclab.iot.sink.telemetry.outbox.backfill;

/**
 * Collector-side request containing the manifest and its independent source
 * authorization.  The verifier treats both artifacts as untrusted input.
 */
public record RouteBackfillApplyRequest(
        RouteBackfillManifestArtifact manifestArtifact,
        RouteBackfillAuthorizationArtifact authorizationArtifact
) {
}
