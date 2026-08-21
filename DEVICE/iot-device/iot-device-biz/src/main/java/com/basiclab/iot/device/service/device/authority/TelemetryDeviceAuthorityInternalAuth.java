package com.basiclab.iot.device.service.device.authority;

import com.basiclab.iot.common.security.internal.InternalServiceAuthHeaders;
import com.basiclab.iot.common.security.internal.InternalServiceAuthRequest;
import com.basiclab.iot.common.security.internal.InternalServiceAuthRoute;
import com.basiclab.iot.common.security.internal.InternalServiceAuthVerifier;
import com.basiclab.iot.device.TelemetryDeviceAuthorityInternalApi;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Binds the authority endpoint to the iot-sink ADR-018 service identity. */
public final class TelemetryDeviceAuthorityInternalAuth {

    public static final String AUTHORIZED_SERVICE_ID = "iot-sink";

    private final InternalServiceAuthVerifier verifier;

    public TelemetryDeviceAuthorityInternalAuth(InternalServiceAuthVerifier verifier) {
        this.verifier = verifier;
    }

    public static List<InternalServiceAuthRoute> requiredRoutes() {
        return Collections.singletonList(new InternalServiceAuthRoute(
                "GET", TelemetryDeviceAuthorityInternalApi.RESOLVE_PATH));
    }

    public InternalServiceAuthVerifier.VerificationResult verify(HttpServletRequest request) {
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
                        new byte[0], headers));
        if (!AUTHORIZED_SERVICE_ID.equals(result.serviceId())) {
            throw new com.basiclab.iot.common.security.internal.InternalServiceAuthException(
                    "SERVICE_AUTH_UNKNOWN_CALLER");
        }
        return result;
    }
}
