package com.basiclab.iot.common.security.internal;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** ADR-018 固定 canonical request 与 SHA-256/HMAC-SHA-256 实现。 */
public final class InternalServiceAuthCanonicalizer {

    private InternalServiceAuthCanonicalizer() {
    }

    public static String canonicalRequest(String method, String pathWithQuery,
                                          String serviceId, String keyId,
                                          String timestamp, String nonce,
                                          String bodySha256) {
        return String.join("\n", method.toUpperCase(Locale.ROOT),
                normalizePathWithSortedQuery(pathWithQuery), serviceId, keyId,
                timestamp, nonce, bodySha256);
    }

    public static String normalizePathWithSortedQuery(String rawPathWithQuery) {
        String raw = rawPathWithQuery == null ? "" : rawPathWithQuery;
        try {
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                URI uri = URI.create(raw);
                raw = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            }
        } catch (IllegalArgumentException ignored) {
            // 由调用方的 HTTP 客户端继续负责 URL 校验；这里保持可复现的原始路径。
        }
        int queryIndex = raw.indexOf('?');
        if (queryIndex < 0 || queryIndex == raw.length() - 1) {
            return queryIndex < 0 ? raw : raw.substring(0, queryIndex);
        }
        String path = raw.substring(0, queryIndex);
        String query = raw.substring(queryIndex + 1);
        List<String> pairs = new ArrayList<>(Arrays.asList(query.split("&", -1)));
        pairs.sort(String::compareTo);
        return path + "?" + pairs.stream().collect(Collectors.joining("&"));
    }

    public static String pathWithoutQuery(String pathWithQuery) {
        String normalized = normalizePathWithSortedQuery(pathWithQuery);
        int queryIndex = normalized.indexOf('?');
        return queryIndex < 0 ? normalized : normalized.substring(0, queryIndex);
    }

    public static String sha256Hex(byte[] body) {
        return hex(digest("SHA-256", body == null ? new byte[0] : body));
    }

    public static String hmacSha256Hex(byte[] secret, String canonicalRequest) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
            return hex(mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static byte[] digest(String algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
