package com.basiclab.iot.sink.outbox.sqlite;

import java.security.PublicKey;
import java.util.Optional;

/**
 * Collector-owned lookup for the public keys trusted by route-backfill
 * verification.  Implementations decide how a key is provisioned; this
 * contract deliberately has no file, environment, database, or rotation
 * policy.
 */
@FunctionalInterface
public interface RouteBackfillVerificationKeyProvider {

    /**
     * Finds the public key identified by the exact, case-sensitive key id.
     * An empty result means that the key is not trusted by this collector.
     */
    Optional<PublicKey> findKey(String keyId);

    /** Alias for callers that name the lookup after the key id. */
    default Optional<PublicKey> findByKeyId(String keyId) {
        return findKey(keyId);
    }
}
