package com.basiclab.iot.common.security.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 验签所需的原始请求视图，body 必须是收到的原始 bytes。 */
public final class InternalServiceAuthRequest {

    private final String method;
    private final String pathWithQuery;
    private final byte[] body;
    private final Map<String, String> headers;

    public InternalServiceAuthRequest(String method, String pathWithQuery, byte[] body,
                                      Map<String, String> headers) {
        this.method = method == null ? "" : method;
        this.pathWithQuery = pathWithQuery == null ? "" : pathWithQuery;
        this.body = body == null ? new byte[0] : body.clone();
        Map<String, String> copy = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((key, value) -> copy.put(key, value == null ? "" : value));
        }
        this.headers = Collections.unmodifiableMap(copy);
    }

    public String getMethod() {
        return method;
    }

    public String getPathWithQuery() {
        return pathWithQuery;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public String header(String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return "";
    }
}
