package com.basiclab.iot.common.security.internal;

import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/** 通过 secret 引用读取密钥；配置中保存引用名，不保存明文 key。 */
public final class EnvironmentInternalServiceKeyProvider implements InternalServiceKeyProvider {

    private final Environment environment;
    private final Map<String, String> keyReferences;

    public EnvironmentInternalServiceKeyProvider(Environment environment,
                                                  Map<String, String> keyReferences) {
        this.environment = environment;
        this.keyReferences = keyReferences;
    }

    @Override
    public Optional<byte[]> findKey(String serviceId, String keyId) {
        String reference = keyReferences.get(serviceId + ":" + keyId);
        if (reference == null || reference.trim().isEmpty()) {
            return Optional.empty();
        }
        String secret = environment.getProperty(reference.trim());
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            return Optional.empty();
        }
        return Optional.of(secret.getBytes(StandardCharsets.UTF_8));
    }
}
