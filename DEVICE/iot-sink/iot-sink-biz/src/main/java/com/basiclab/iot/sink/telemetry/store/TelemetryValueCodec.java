package com.basiclab.iot.sink.telemetry.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * TelemetryStore 共享 value 解析器（TD-003 §6: value = decimal-string）。
 *
 * <p>从 Envelope V1 canonical bytes 提取 {@code value} 字段为 {@link BigDecimal}。
 * 复用单例 {@link ObjectMapper}（Jackson 线程安全，避免每次 new）。value 缺失 / null / 解析失败
 * → 返回 {@code null}（对齐 TD-003 §6「无效质量可省略 value，禁止补 0」——由各 store 决定写 NULL
 * 或引擎回退）。
 */
public final class TelemetryValueCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TelemetryValueCodec() {
    }

    /**
     * 解析 canonical bytes 的 value 字段。
     *
     * @return value 的 BigDecimal 表示；value 缺失 / null / 解析失败时返回 {@code null}（不补 0）
     */
    public static BigDecimal parseDecimalValue(byte[] canonicalBytes) {
        try {
            JsonNode node = MAPPER.readTree(new String(canonicalBytes, StandardCharsets.UTF_8));
            JsonNode value = node.path("value");
            if (value.isMissingNode() || value.isNull()) {
                return null;
            }
            return new BigDecimal(value.asText());
        } catch (Exception e) {
            return null;
        }
    }

    /** 质量码兜底值（TD-003 §6；旧行与缺省统一 GOOD）。 */
    public static final String QUALITY_GOOD = "GOOD";

    /**
     * TD-003 §6/§14 质量码：从 canonical payload 读取受控枚举文本。
     *
     * <p>Envelope V1 冻结不含强类型 quality 字段，样本级 JSON 由生产者填充；
     * 缺失、非文本或不符合 {@code [A-Z_]{1,32}} 时统一返回 {@code GOOD}。</p>
     */
    public static String parseQuality(byte[] canonicalBytes) {
        try {
            JsonNode node = MAPPER.readTree(new String(canonicalBytes, StandardCharsets.UTF_8));
            JsonNode quality = node.path("quality");
            if (quality.isTextual()) {
                String text = quality.asText().trim().toUpperCase(java.util.Locale.ROOT);
                if (text.matches("[A-Z_]{1,32}")) {
                    return text;
                }
            }
        } catch (Exception e) {
            // malformed payload quality falls back to GOOD; value validity is
            // separately enforced by the stores.
        }
        return QUALITY_GOOD;
    }
}
