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
}
