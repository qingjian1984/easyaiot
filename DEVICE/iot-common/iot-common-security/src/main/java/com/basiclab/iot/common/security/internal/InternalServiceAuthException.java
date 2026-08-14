package com.basiclab.iot.common.security.internal;

/** 不携带密钥、签名或请求体的稳定认证错误。 */
public class InternalServiceAuthException extends RuntimeException {

    private final String code;

    public InternalServiceAuthException(String code) {
        super(code);
        this.code = code;
    }

    public InternalServiceAuthException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
