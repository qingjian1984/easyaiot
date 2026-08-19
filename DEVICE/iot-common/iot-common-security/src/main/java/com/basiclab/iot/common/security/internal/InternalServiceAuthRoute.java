package com.basiclab.iot.common.security.internal;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 服务身份的最小 method/path allowlist。
 *
 * <p>除静态路径外，只允许一个命名段 {@code {name}} 模板。模板不是
 * wildcard：它不能跨越 {@code /}、匹配空段或吞掉 query；这样可以让详情/回报
 * 路径复用同一份 allowlist，同时保持 ADR-018 的最小权限边界。</p>
 */
public record InternalServiceAuthRoute(String method, String path) {

    private static final Pattern TEMPLATE_SEGMENT = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_]*}");

    public InternalServiceAuthRoute {
        method = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        path = path == null ? "" : path.trim();
        if (method.isEmpty() || path.isEmpty() || !path.startsWith("/")) {
            throw new IllegalArgumentException("internal auth route must contain method and absolute path");
        }
        validatePath(path);
    }

    public boolean matches(String actualMethod, String actualPath) {
        if (!method.equalsIgnoreCase(actualMethod) || actualPath == null) {
            return false;
        }
        String[] expectedSegments = segments(path);
        boolean template = expectedSegments != null && hasTemplate(expectedSegments);
        // Keep the historical exact-match semantics for static routes.
        if (!template) {
            return path.equals(actualPath);
        }

        if (!actualPath.startsWith("/") || actualPath.indexOf('?') >= 0
                || actualPath.indexOf('#') >= 0) {
            return false;
        }
        String[] actualSegments = segments(actualPath);
        if (expectedSegments == null || actualSegments == null
                || expectedSegments.length != actualSegments.length) {
            return false;
        }
        for (int i = 0; i < expectedSegments.length; i++) {
            if (isTemplateSegment(expectedSegments[i])) {
                if (isUnsafeTemplateValue(actualSegments[i])) {
                    return false;
                }
            } else if (!expectedSegments[i].equals(actualSegments[i])) {
                return false;
            }
        }
        return true;
    }

    private static void validatePath(String routePath) {
        String[] routeSegments = segments(routePath);
        if (routeSegments == null) {
            throw new IllegalArgumentException("internal auth route must be an absolute path");
        }
        int templateCount = 0;
        for (String segment : routeSegments) {
            if (isTemplateSegment(segment)) {
                templateCount++;
            } else if (segment.contains("{") || segment.contains("}")) {
                throw new IllegalArgumentException("unsupported internal auth route template");
            }
        }
        if (templateCount > 1) {
            throw new IllegalArgumentException("internal auth route supports one template segment only");
        }
        if (templateCount == 1 && (routePath.indexOf('?') >= 0 || routePath.indexOf('#') >= 0
                || routePath.indexOf('*') >= 0
                || (routePath.length() > 1 && routePath.contains("//")))) {
            throw new IllegalArgumentException("templated internal auth route must be path-only");
        }
        if (templateCount == 1) {
            for (String segment : routeSegments) {
                if (segment.isEmpty()) {
                    throw new IllegalArgumentException(
                            "templated internal auth route must not contain empty path segments");
                }
            }
        }
    }

    private static boolean hasTemplate(String[] segments) {
        for (String segment : segments) {
            if (isTemplateSegment(segment)) return true;
        }
        return false;
    }

    private static boolean isTemplateSegment(String segment) {
        return segment != null && TEMPLATE_SEGMENT.matcher(segment).matches();
    }

    private static String[] segments(String value) {
        if (value == null || !value.startsWith("/")) {
            return null;
        }
        return value.substring(1).split("/", -1);
    }

    private static boolean isUnsafeTemplateValue(String value) {
        if (value == null || value.isEmpty() || value.indexOf('/') >= 0
                || value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            return true;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        // Reject encoded separators/delimiters too; route matching must not depend on
        // whether an upstream servlet has decoded the path before this check.
        return lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%3f") || lower.contains("%23");
    }
}
