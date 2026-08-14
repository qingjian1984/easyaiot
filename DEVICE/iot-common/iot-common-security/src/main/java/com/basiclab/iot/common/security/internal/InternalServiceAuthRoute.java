package com.basiclab.iot.common.security.internal;

import java.util.Locale;

/** 服务身份的最小 method/path allowlist。 */
public record InternalServiceAuthRoute(String method, String path) {

    public InternalServiceAuthRoute {
        method = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        path = path == null ? "" : path.trim();
        if (method.isEmpty() || path.isEmpty() || !path.startsWith("/")) {
            throw new IllegalArgumentException("internal auth route must contain method and absolute path");
        }
    }

    public boolean matches(String actualMethod, String actualPath) {
        return method.equalsIgnoreCase(actualMethod) && path.equals(actualPath);
    }
}
