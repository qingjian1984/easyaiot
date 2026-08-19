package com.basiclab.iot.device.service.collector;

import com.basiclab.iot.common.security.internal.InternalServiceAuthHeaders;
import com.basiclab.iot.common.security.internal.InternalServiceAuthRequest;
import com.basiclab.iot.common.security.internal.InternalServiceAuthRoute;
import com.basiclab.iot.common.security.internal.InternalServiceAuthVerifier;
import com.basiclab.iot.device.CollectorConfigReleaseInternalApi;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * collector release provider 的 ADR-018 认证适配器。
 *
 * <p>HMAC、body hash、时钟窗和 nonce 仍全部由 common verifier 执行；本类只绑定
 * collector endpoint 的路径视图并额外收紧服务身份为 {@code iot-node}。用户 token、
 * login-user 与租户 Header 不参与身份判断。</p>
 */
public final class CollectorConfigReleaseInternalAuth {

    public static final String AUTHORIZED_SERVICE_ID = "iot-node";

    private final InternalServiceAuthVerifier verifier;

    public CollectorConfigReleaseInternalAuth(InternalServiceAuthVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    /** 供 route 合同测试和部署配置核对使用；实际密钥/nonce 仍来自 ADR-018 配置。 */
    public static List<InternalServiceAuthRoute> requiredRoutes() {
        List<InternalServiceAuthRoute> routes = new ArrayList<>();
        routes.add(new InternalServiceAuthRoute("GET", CollectorConfigReleaseInternalApi.PENDING_PATH));
        routes.add(new InternalServiceAuthRoute("GET", CollectorConfigReleaseInternalApi.DETAIL_PATH));
        routes.add(new InternalServiceAuthRoute("POST", CollectorConfigReleaseInternalApi.OBSERVED_PATH));
        return Collections.unmodifiableList(routes);
    }

    public InternalServiceAuthVerifier.VerificationResult verify(
            HttpServletRequest request, byte[] rawBody) {
        Objects.requireNonNull(request, "request");
        String pathWithQuery = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            pathWithQuery += "?" + request.getQueryString();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(InternalServiceAuthHeaders.SERVICE_ID,
                request.getHeader(InternalServiceAuthHeaders.SERVICE_ID));
        headers.put(InternalServiceAuthHeaders.KEY_ID,
                request.getHeader(InternalServiceAuthHeaders.KEY_ID));
        headers.put(InternalServiceAuthHeaders.TIMESTAMP,
                request.getHeader(InternalServiceAuthHeaders.TIMESTAMP));
        headers.put(InternalServiceAuthHeaders.NONCE,
                request.getHeader(InternalServiceAuthHeaders.NONCE));
        headers.put(InternalServiceAuthHeaders.BODY_SHA256,
                request.getHeader(InternalServiceAuthHeaders.BODY_SHA256));
        headers.put(InternalServiceAuthHeaders.SIGNATURE,
                request.getHeader(InternalServiceAuthHeaders.SIGNATURE));
        InternalServiceAuthVerifier.VerificationResult result = verifier.verify(
                new InternalServiceAuthRequest(request.getMethod(), pathWithQuery,
                        rawBody == null ? new byte[0] : rawBody, headers));
        if (!AUTHORIZED_SERVICE_ID.equals(result.serviceId())) {
            throw new com.basiclab.iot.common.security.internal.InternalServiceAuthException(
                    "SERVICE_AUTH_UNKNOWN_CALLER");
        }
        return result;
    }
}
