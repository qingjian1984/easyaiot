package com.basiclab.iot.common.security.internal;

/** 成功验签后的 nonce 原子占用存储。 */
@FunctionalInterface
public interface InternalServiceAuthNonceStore {

    boolean claim(String serviceId, String nonce, long ttlSeconds);
}
