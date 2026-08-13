package com.basiclab.iot.sink.telemetry.store;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TelemetryValueCodec} 单测：验证 TD-003 §6「无效质量可省略 value，禁止补 0」契约。
 *
 * <p>核心断言：value 缺失 / 显式 null / 非法 / JSON 损坏 → 一律返回 {@code null}（绝不 {@code BigDecimal.ZERO}）；
 * 有效 decimal-string（含 JSON 数字字面量）→ 解析为 {@code BigDecimal}（不经 Double，保精度）。
 */
class TelemetryValueCodecTest {

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parseDecimalValue_returnsBigDecimal_whenValuePresent() {
        BigDecimal v = TelemetryValueCodec.parseDecimalValue(bytes("{\"value\":\"225.500000\"}"));
        assertEquals(0, v.compareTo(new BigDecimal("225.5")));
    }

    @Test
    void parseDecimalValue_returnsNull_whenValueMissing() {
        // TD-003 §6: 无效质量可省略 value → null（禁止补 0）
        BigDecimal v = TelemetryValueCodec.parseDecimalValue(
                bytes("{\"messageId\":\"m1\",\"tenantId\":\"t1\"}"));
        assertNull(v, "missing value must return null, not ZERO (TD-003 §6 禁止补0)");
    }

    @Test
    void parseDecimalValue_returnsNull_whenValueExplicitNull() {
        BigDecimal v = TelemetryValueCodec.parseDecimalValue(bytes("{\"value\":null}"));
        assertNull(v);
    }

    @Test
    void parseDecimalValue_returnsNull_whenValueIllegal() {
        BigDecimal v = TelemetryValueCodec.parseDecimalValue(bytes("{\"value\":\"abc\"}"));
        assertNull(v, "illegal value must return null, not ZERO");
    }

    @Test
    void parseDecimalValue_returnsNull_whenJsonBroken() {
        BigDecimal v = TelemetryValueCodec.parseDecimalValue(bytes("not-json"));
        assertNull(v);
    }

    @Test
    void parseDecimalValue_acceptsNumericJsonValue() {
        // JSON 数字字面量（未加引号）也应被容忍解析
        BigDecimal v = TelemetryValueCodec.parseDecimalValue(bytes("{\"value\":220.5}"));
        assertEquals(0, v.compareTo(new BigDecimal("220.5")));
    }
}
