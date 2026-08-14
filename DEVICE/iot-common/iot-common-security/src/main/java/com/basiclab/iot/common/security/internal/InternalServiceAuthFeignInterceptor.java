package com.basiclab.iot.common.security.internal;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import java.util.Map;

/** 必须按 client 专属 configuration 显式挂载，禁止作为全局 Feign interceptor。 */
public final class InternalServiceAuthFeignInterceptor implements RequestInterceptor {

    private final InternalServiceAuthSigner signer;

    public InternalServiceAuthFeignInterceptor(InternalServiceAuthSigner signer) {
        this.signer = signer;
    }

    @Override
    public void apply(RequestTemplate template) {
        byte[] body = template.body() == null ? new byte[0] : template.body();
        Map<String, String> headers = signer.sign(template.method(), template.url(), body);
        headers.forEach(template::header);
    }
}
