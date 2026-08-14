package com.basiclab.iot.common.security.internal;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/** Redis SET NX nonce store；Redis 故障直接抛出，由 verifier fail-closed。 */
public final class RedisInternalServiceAuthNonceStore implements InternalServiceAuthNonceStore {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisInternalServiceAuthNonceStore(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean claim(String serviceId, String nonce, long ttlSeconds) {
        try {
            String key = keyPrefix + serviceId + ":" + nonce;
            Boolean accepted = redisTemplate.opsForValue().setIfAbsent(
                    key, "1", ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(accepted);
        } catch (RuntimeException e) {
            throw new InternalServiceAuthException("SERVICE_AUTH_NONCE_STORE_UNAVAILABLE", e);
        }
    }
}
