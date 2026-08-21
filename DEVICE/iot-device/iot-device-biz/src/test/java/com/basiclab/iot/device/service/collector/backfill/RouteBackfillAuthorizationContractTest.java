package com.basiclab.iot.device.service.collector.backfill;

import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorization;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorizationArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestArtifact;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteBackfillAuthorizationContractTest {

    private static final String HASH = "a".repeat(64);
    private static final String OPERATION_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void signedArtifactCopiesCanonicalBytesAndRejectsWrongDigest() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        RouteBackfillAuthorizationArtifact artifact = new RouteBackfillAuthorizationSigner()
                .authorize(manifest(), "key-1", OPERATION_ID,
                        Instant.ofEpochSecond(1_700_000_000L),
                        Instant.ofEpochSecond(1_700_000_600L), pair.getPrivate());

        byte[] first = artifact.canonicalBytes();
        byte[] original = first.clone();
        first[0] ^= 1;
        assertArrayEquals(original, artifact.canonicalBytes());
        assertEquals(RouteBackfillAuthorization.SIGNATURE_ALGORITHM,
                artifact.authorization().signatureAlgorithm());
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillAuthorizationArtifact(artifact.authorization(),
                        artifact.canonicalBytes(), "0".repeat(64), artifact.signatureBase64()));
    }

    @Test
    void authorizationContractKeepsExactUnicodeAndEpochValues() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String nfd = "cafe\u0301-workload";
        RouteBackfillManifest source = new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                HASH, nfd, List.of(), null);
        RouteBackfillAuthorizationArtifact artifact = new RouteBackfillAuthorizationSigner()
                .authorize(new RouteBackfillManifestArtifact(source), "key_1", OPERATION_ID,
                        Instant.ofEpochSecond(1_700_000_000L),
                        Instant.ofEpochSecond(1_700_086_400L), pair.getPrivate());

        assertEquals(nfd, artifact.authorization().workloadId());
        assertEquals(1_700_000_000L, artifact.authorization().issuedAtEpochSeconds());
        assertEquals(1_700_086_400L, artifact.authorization().expiresAtEpochSeconds());
        assertEquals(64, java.util.Base64.getDecoder()
                .decode(artifact.signatureBase64()).length);
    }

    private static RouteBackfillManifestArtifact manifest() {
        RouteBackfillManifest manifest = new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                HASH, "workload-a", List.of(), (RouteBackfillKey) null);
        return new RouteBackfillManifestArtifact(manifest);
    }
}
