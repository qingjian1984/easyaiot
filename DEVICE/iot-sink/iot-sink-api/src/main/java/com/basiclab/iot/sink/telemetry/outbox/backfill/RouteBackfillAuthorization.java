package com.basiclab.iot.sink.telemetry.outbox.backfill;

/**
 * Immutable source authorization for one route backfill manifest.
 *
 * <p>The values are deliberately kept as supplied.  Canonicalization and
 * validation are performed by the signer and verifier at the trust boundary;
 * this record is also usable as the untrusted wire contract.</p>
 */
public record RouteBackfillAuthorization(
        String schemaVersion,
        String canonicalizationVersion,
        String signatureAlgorithm,
        String signatureContext,
        String keyId,
        String operationId,
        long issuedAtEpochSeconds,
        long expiresAtEpochSeconds,
        String manifestContentSha256,
        String sourceInventorySha256,
        String workloadId
) {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String CANONICALIZATION_VERSION = "jcs-rfc8785-v1";
    public static final String SIGNATURE_ALGORITHM = "Ed25519";
    public static final String SIGNATURE_CONTEXT = "easyaiot-route-backfill-authorization-v1";
    public static final long MAX_AUTHORIZATION_WINDOW_SECONDS = 86_400L;
}
