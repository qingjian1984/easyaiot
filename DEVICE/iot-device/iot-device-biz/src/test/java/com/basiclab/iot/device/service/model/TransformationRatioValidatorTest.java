package com.basiclab.iot.device.service.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-005 §5.3：互感器变比语义——十进制计算、二次值必须大于 0、
 * 显式 ratio 必须与 6 位 HALF_UP 计算值一致、归一化中间值不截断。
 */
class TransformationRatioValidatorTest {

    private final TransformationRatioValidator validator = new TransformationRatioValidator();

    @Test
    void consistentRatioIsAcceptedAndReturned() {
        BigDecimal ratio = validator.requireConsistentRatio(
                new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("20.000000"));
        assertEquals(0, new BigDecimal("20.000000").compareTo(ratio));
    }

    @Test
    void inconsistentRatioIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.requireConsistentRatio(
                        new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("19.999999")));
        assertTrue(error.getMessage().startsWith("MODEL_TRANSFORMATION_RATIO_MISMATCH"));
    }

    @Test
    void divisionRoundsHalfUpToSixDecimalsBeforeComparison() {
        // 100 / 3 = 33.3333333... → 33.333333（6 位 HALF_UP）
        BigDecimal ratio = validator.requireConsistentRatio(
                new BigDecimal("100"), new BigDecimal("3"), new BigDecimal("33.333333"));
        assertEquals(0, new BigDecimal("33.333333").compareTo(ratio));
        // 第 7 位 ≥5 进位：10 / 6 = 1.6666666... → 1.666667
        BigDecimal rounded = validator.requireConsistentRatio(
                new BigDecimal("10"), new BigDecimal("6"), new BigDecimal("1.666667"));
        assertEquals(0, new BigDecimal("1.666667").compareTo(rounded));
    }

    @Test
    void nonPositiveSecondaryIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.requireConsistentRatio(
                        new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("1")));
        assertTrue(error.getMessage().startsWith("MODEL_PRECONDITION_FAILED"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.requireConsistentRatio(
                        new BigDecimal("100"), new BigDecimal("-5"), new BigDecimal("1")));
    }

    @Test
    void telemetryNormalizationKeepsFullPrecisionUntilTargetRounding() {
        // 中间值不截断：0.0001 × 33.333333... 仅在输出按目标 precision=3 舍入
        BigDecimal normalized = validator.normalizeTelemetry(
                new BigDecimal("0.0001"), new BigDecimal("100"), new BigDecimal("3"), 3);
        assertEquals(0, new BigDecimal("0.003").compareTo(normalized),
                "raw × ratio 中间值保持任意精度，最终按目标 precision=3 HALF_UP");
    }

    @Test
    void telemetryNormalizationFailsWithoutDecidableTargetPrecision() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.normalizeTelemetry(
                        new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("5"), null));
        assertTrue(error.getMessage().startsWith("MODEL_PROPERTY_PRECISION_REQUIRED"),
                "目标属性无可判定 precision 必须校验失败而不是猜测默认值");
    }
}
