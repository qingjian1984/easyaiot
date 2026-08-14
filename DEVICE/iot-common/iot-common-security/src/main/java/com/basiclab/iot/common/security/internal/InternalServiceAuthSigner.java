package com.basiclab.iot.common.security.internal;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.basiclab.iot.common.security.internal.InternalServiceAuthHeaders.BODY_SHA256;
import static com.basiclab.iot.common.security.internal.InternalServiceAuthHeaders.KEY_ID;
import static com.basiclab.iot.common.security.internal.InternalServiceAuthHeaders.NONCE;
import static com.basiclab.iot.common.security.internal.InternalServiceAuthHeaders.SERVICE_ID;
import static com.basiclab.iot.common.security.internal.InternalServiceAuthHeaders.SIGNATURE;
import static com.basiclab.iot.common.security.internal.InternalServiceAuthHeaders.TIMESTAMP;

/** 仅供显式绑定到内部 Feign client 的 signer，不注册为全局 Feign 拦截器。 */
public final class InternalServiceAuthSigner {

    private final InternalServiceKeyProvider keyProvider;
    private final String serviceId;
    private final String keyId;
    private final Clock clock;
    private final Supplier<String> nonceSupplier;

    public InternalServiceAuthSigner(InternalServiceKeyProvider keyProvider, String serviceId,
                                     String keyId) {
        this(keyProvider, serviceId, keyId, Clock.systemUTC(),
                () -> randomNonce(new SecureRandom()));
    }

    public InternalServiceAuthSigner(InternalServiceKeyProvider keyProvider, String serviceId,
                                     String keyId, Clock clock, Supplier<String> nonceSupplier) {
        this.keyProvider = keyProvider;
        this.serviceId = serviceId;
        this.keyId = keyId;
        this.clock = clock;
        this.nonceSupplier = nonceSupplier;
    }

    public Map<String, String> sign(String method, String pathWithQuery, byte[] body) {
        Optional<byte[]> key = keyProvider.findKey(serviceId, keyId);
        if (key.isEmpty() || key.get().length < 32) {
            throw new InternalServiceAuthException("SERVICE_AUTH_KEY_UNKNOWN");
        }
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String nonce = nonceSupplier.get();
        String bodyHash = InternalServiceAuthCanonicalizer.sha256Hex(body);
        String canonical = InternalServiceAuthCanonicalizer.canonicalRequest(
                method, pathWithQuery, serviceId, keyId, timestamp, nonce, bodyHash);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(SERVICE_ID, serviceId);
        headers.put(KEY_ID, keyId);
        headers.put(TIMESTAMP, timestamp);
        headers.put(NONCE, nonce);
        headers.put(BODY_SHA256, bodyHash);
        headers.put(SIGNATURE, InternalServiceAuthCanonicalizer.hmacSha256Hex(key.get(), canonical));
        return headers;
    }

    private static String randomNonce(SecureRandom random) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder result = new StringBuilder(32);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
