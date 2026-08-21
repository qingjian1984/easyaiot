package com.basiclab.iot.device.service.collector.backfill;

import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorization;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorizationArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestArtifact;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteBackfillAuthorizationSignerTest {

    private static final String HASH = "b".repeat(64);
    private static final String OPERATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final Instant ISSUED_AT = Instant.ofEpochSecond(1_700_000_000L);
    private static final Instant EXPIRES_AT = Instant.ofEpochSecond(1_700_000_600L);

    @Test
    void sameInputProducesDeterministicDomainSeparatedEd25519Artifact() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        RouteBackfillAuthorizationSigner signer = new RouteBackfillAuthorizationSigner();
        RouteBackfillManifestArtifact manifest = manifest();

        RouteBackfillAuthorizationArtifact first = signer.authorize(
                manifest, "center.key-1", OPERATION_ID, ISSUED_AT, EXPIRES_AT,
                pair.getPrivate());
        RouteBackfillAuthorizationArtifact second = signer.authorize(
                manifest, "center.key-1", OPERATION_ID, ISSUED_AT, EXPIRES_AT,
                pair.getPrivate());

        assertArrayEquals(first.canonicalBytes(), second.canonicalBytes());
        assertEquals(first.contentSha256(), second.contentSha256());
        assertEquals(first.signatureBase64(), second.signatureBase64());
        assertEquals(manifest.contentSha256(), first.authorization().manifestContentSha256());
        assertEquals(manifest.manifest().sourceInventorySha256(),
                first.authorization().sourceInventorySha256());
        assertEquals(manifest.manifest().workloadId(), first.authorization().workloadId());

        byte[] domain = RouteBackfillAuthorizationSigner.DOMAIN_SEPARATOR
                .getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[domain.length + first.canonicalBytes().length];
        System.arraycopy(domain, 0, input, 0, domain.length);
        System.arraycopy(first.canonicalBytes(), 0, input, domain.length,
                first.canonicalBytes().length);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pair.getPublic());
        verifier.update(input);
        assertTrue(verifier.verify(Base64.getDecoder().decode(first.signatureBase64())));
    }

    @Test
    void signerRejectsInvalidWindowIdentityAndPrivateKey() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        RouteBackfillAuthorizationSigner signer = new RouteBackfillAuthorizationSigner();
        assertThrows(IllegalArgumentException.class, () -> signer.authorize(
                manifest(), "bad key", OPERATION_ID, ISSUED_AT, EXPIRES_AT, pair.getPrivate()));
        assertThrows(IllegalArgumentException.class, () -> signer.authorize(
                manifest(), "key-1", OPERATION_ID.toUpperCase(), ISSUED_AT, EXPIRES_AT,
                pair.getPrivate()));
        assertThrows(IllegalArgumentException.class, () -> signer.authorize(
                manifest(), "key-1", OPERATION_ID, ISSUED_AT,
                ISSUED_AT.plusSeconds(86_401), pair.getPrivate()));
        assertThrows(IllegalArgumentException.class, () -> signer.authorize(
                manifest(), "key-1", OPERATION_ID, ISSUED_AT, EXPIRES_AT,
                new FakePrivateKey()));
    }

    @Test
    void signerRechecksManifestArtifactInsteadOfTrustingItsFields() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        RouteBackfillManifestArtifact valid = manifest();
        byte[] changed = valid.canonicalBytes();
        changed[changed.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> new RouteBackfillManifestArtifact(
                valid.manifest(), changed, valid.contentSha256()));
        RouteBackfillAuthorizationArtifact signed = new RouteBackfillAuthorizationSigner()
                .authorize(valid, "key-1", OPERATION_ID, ISSUED_AT, EXPIRES_AT,
                        pair.getPrivate());
        assertEquals(valid.contentSha256(), signed.authorization().manifestContentSha256());
        // The valid artifact remains usable after callers mutate their copy.
        assertEquals(valid.contentSha256(), manifest().contentSha256());
    }

    private static RouteBackfillManifestArtifact manifest() {
        return new RouteBackfillManifestArtifact(new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                HASH, "workload-a", List.of(), null));
    }

    private static final class FakePrivateKey implements PrivateKey {
        @Override
        public String getAlgorithm() {
            return "RSA";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    }
}
