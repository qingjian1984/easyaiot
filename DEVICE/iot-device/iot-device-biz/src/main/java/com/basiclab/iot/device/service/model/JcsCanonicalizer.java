package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * TD-005 §6：JCS（RFC 8785，`jcs-rfc8785-v1`）canonical 与内容哈希。
 * 应用层一次生成 canonical 字节并复用于入库、哈希、导出和发布包；
 * 禁止从 PostgreSQL jsonb 重新序列化后比较哈希。
 * 对象键按 UTF-16 代码单元排序（Java String.compareTo 与 ECMAScript 同序）。
 * 保持 Java 8 兼容（模块默认编译级别 1.8）。
 */
public final class JcsCanonicalizer {

    public String canonicalize(JsonNode value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    /** contentHash = "sha256:" + lowercaseHex(SHA-256(UTF-8(contentCanonical))) */
    public String contentHash(JsonNode value) {
        byte[] canonical = canonicalize(value).getBytes(StandardCharsets.UTF_8);
        return "sha256:" + lowercaseHex(sha256(canonical));
    }

    private void write(JsonNode value, StringBuilder out) {
        if (value == null || value.isNull()) {
            out.append("null");
        } else if (value.isBoolean()) {
            out.append(value.booleanValue() ? "true" : "false");
        } else if (value.isTextual()) {
            writeString(value.textValue(), out);
        } else if (value.isIntegralNumber()) {
            out.append(value.bigIntegerValue().toString());
        } else if (value.isNumber()) {
            out.append(JcsNumberFormatter.format(value.doubleValue()));
        } else if (value.isArray()) {
            out.append('[');
            for (int i = 0; i < value.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(value.get(i), out);
            }
            out.append(']');
        } else if (value.isObject()) {
            List<Map.Entry<String, JsonNode>> members = new ArrayList<>();
            value.fields().forEachRemaining(members::add);
            members.sort(Comparator.comparing(Map.Entry::getKey));
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> member : members) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(member.getKey(), out);
                out.append(':');
                write(member.getValue(), out);
            }
            out.append('}');
        } else {
            throw new IllegalArgumentException("MODEL_JCS_UNSUPPORTED_NODE: 不支持的 JSON 节点类型 " + value.getNodeType());
        }
    }

    /** JSON.stringify 等价的字符串转义：控制字符以外的字符（含非 ASCII）原样输出。 */
    private void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256", e);
        }
    }

    private static String lowercaseHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
