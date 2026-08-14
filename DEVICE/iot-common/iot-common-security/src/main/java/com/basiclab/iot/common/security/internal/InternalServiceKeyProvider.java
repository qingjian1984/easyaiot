package com.basiclab.iot.common.security.internal;

import java.util.Optional;

/** 服务身份密钥 Provider；调用方不能绕过该抽象读取明文。 */
@FunctionalInterface
public interface InternalServiceKeyProvider {

    Optional<byte[]> findKey(String serviceId, String keyId);
}
