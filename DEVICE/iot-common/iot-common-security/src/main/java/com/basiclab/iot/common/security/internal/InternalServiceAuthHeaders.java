package com.basiclab.iot.common.security.internal;

/** ADR-018 内部服务 HMAC 请求头。 */
public final class InternalServiceAuthHeaders {

    public static final String SERVICE_ID = "X-EasyAIoT-Service-Id";
    public static final String KEY_ID = "X-EasyAIoT-Key-Id";
    public static final String TIMESTAMP = "X-EasyAIoT-Timestamp";
    public static final String NONCE = "X-EasyAIoT-Nonce";
    public static final String BODY_SHA256 = "X-EasyAIoT-Body-SHA256";
    public static final String SIGNATURE = "X-EasyAIoT-Signature";

    private InternalServiceAuthHeaders() {
    }
}
