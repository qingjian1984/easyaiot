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

    /**
     * 查找 {@code serviceId:keyId} 的 secret 引用。
     *
     * <p>兼容 Spring Boot relaxed binding 对 Map 键内冒号的剥离：
     * yaml 中 {@code "iot-sink:authority-v1"} 绑定后实际键为
     * {@code iot-sinkauthority-v1}（冒号被移除），因此先按原组合键查找、
     * miss 后回退去冒号键，两种 yaml 写法均可用。</p>
     */
    @Override
    public Optional<byte[]> findKey(String serviceId, String keyId) {
        String combined = serviceId + ":" + keyId;
        String reference = keyReferences.get(combined);
        if (reference == null || reference.trim().isEmpty()) {
            reference = keyReferences.get(combined.replace(":", ""));
        }
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
