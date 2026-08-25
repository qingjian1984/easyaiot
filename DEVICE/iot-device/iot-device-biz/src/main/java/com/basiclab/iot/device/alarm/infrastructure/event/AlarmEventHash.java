package com.basiclab.iot.device.alarm.infrastructure.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 告警 Envelope 的本地规范字节与摘要合同。
 *
 * <p>规范字节定义为严格解析后的 JSON 树，以字典序排序对象键、保留数组
 * 顺序、UTF-8 无空白序列化。该摘要覆盖整个 Envelope；来源 payload 中的
 * {@code payloadHash} 只是证据摘要，不会被当作 {@code envelopeHash}。</p>
 */
public final class AlarmEventHash {

    private static final Pattern HASH_PATTERN =
            Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

    private AlarmEventHash() {
    }

    /** 将规范 JSON 文本转换为本地规范 UTF-8 字节。 */
    public static byte[] canonicalBytes(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            throw new IllegalArgumentException("ALARM_EVENT_HASH_INVALID: Envelope 为空");
        }
        try {
            return canonicalBytes(CANONICAL_MAPPER.readTree(rawJson));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "ALARM_EVENT_HASH_INVALID: Envelope 不是合法 JSON", error);
        }
    }

    /** 将 JSON 树按同一合同序列化为规范 UTF-8 字节。 */
    public static byte[] canonicalBytes(JsonNode envelope) {
        if (envelope == null || !envelope.isObject()) {
            throw new IllegalArgumentException(
                    "ALARM_EVENT_HASH_INVALID: Envelope 必须为 JSON 对象");
        }
        try {
            return CANONICAL_MAPPER.writeValueAsBytes(canonicalize(envelope));
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "ALARM_EVENT_HASH_INVALID: Envelope 无法规范序列化", error);
        }
    }

    /** 从原始 Envelope JSON 计算摘要。 */
    public static String envelopeHash(String rawJson) {
        return sha256(canonicalBytes(rawJson));
    }

    /** 从 JSON 树计算摘要。 */
    public static String envelopeHash(JsonNode envelope) {
        return sha256(canonicalBytes(envelope));
    }

    /** 对已确定的规范字节计算格式化 SHA-256。 */
    public static String sha256(byte[] canonicalBytes) {
        if (canonicalBytes == null) {
            throw new IllegalArgumentException("ALARM_EVENT_HASH_INVALID: 规范字节为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalBytes);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JVM 不支持 SHA-256", error);
        }
    }

    /** 只接受本地计算结果格式，不接受大写或缺失前缀。 */
    public static boolean isValid(String value) {
        return value != null && HASH_PATTERN.matcher(value).matches();
    }

    /** 供需要显式传输字节的适配器使用；不改变摘要合同。 */
    public static byte[] utf8(String value) {
        return value == null ? null : value.getBytes(StandardCharsets.UTF_8);
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = CANONICAL_MAPPER.createObjectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
            node.fields().forEachRemaining(fields::add);
            fields.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, JsonNode> field : fields) {
                sorted.set(field.getKey(), canonicalize(field.getValue()));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = CANONICAL_MAPPER.createArrayNode();
            for (JsonNode child : node) {
                array.add(canonicalize(child));
            }
            return array;
        }
        return node;
    }
}
