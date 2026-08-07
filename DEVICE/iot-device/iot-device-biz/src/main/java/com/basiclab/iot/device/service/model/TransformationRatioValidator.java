package com.basiclab.iot.device.service.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * TD-005 §5.3：互感器变比语义（CT/PT）。
 * 全部输入、除法、乘法使用 BigDecimal 十进制；显式 ratio 必须与
 * 6 位 HALF_UP 计算值一致；归一化中间值不截断，最终按目标属性 precision 舍入。
 * Java 8 兼容。
 */
public final class TransformationRatioValidator {

    /** transformation-ratio.precision=6（字典 normalizationRules 冻结值）。 */
    private static final int RATIO_PRECISION = 6;

    /**
     * 校验显式 ratio 与服务端计算值一致，返回 6 位 HALF_UP 计算 ratio。
     * 额定二次值必须大于 0；不一致绝不静默采用任一方。
     */
    public BigDecimal requireConsistentRatio(BigDecimal ratedPrimary, BigDecimal ratedSecondary,
                                             BigDecimal explicitRatio) {
        Objects.requireNonNull(ratedPrimary, "ratedPrimary");
        Objects.requireNonNull(ratedSecondary, "ratedSecondary");
        Objects.requireNonNull(explicitRatio, "explicitRatio");
        requirePositiveSecondary(ratedSecondary);
        BigDecimal computed = ratedPrimary.divide(ratedSecondary, RATIO_PRECISION, RoundingMode.HALF_UP);
        if (computed.compareTo(explicitRatio) != 0) {
            throw new IllegalArgumentException(
                    "MODEL_TRANSFORMATION_RATIO_MISMATCH: 显式 ratio " + explicitRatio
                            + " 与服务端计算值 " + computed + " 不一致");
        }
        return computed;
    }

    /**
     * 遥测归一化：rawSecondaryValue × ratio 中间值保持任意十进制精度，
     * 仅在输出时按目标属性 precision 做 HALF_UP；目标无可判定 precision 必须失败。
     */
    public BigDecimal normalizeTelemetry(BigDecimal rawSecondaryValue, BigDecimal ratedPrimary,
                                         BigDecimal ratedSecondary, Integer targetPrecision) {
        Objects.requireNonNull(rawSecondaryValue, "rawSecondaryValue");
        Objects.requireNonNull(ratedPrimary, "ratedPrimary");
        Objects.requireNonNull(ratedSecondary, "ratedSecondary");
        requirePositiveSecondary(ratedSecondary);
        if (targetPrecision == null || targetPrecision < 0) {
            throw new IllegalArgumentException(
                    "MODEL_PROPERTY_PRECISION_REQUIRED: 目标数值属性缺少可判定的 precision，禁止猜测默认值");
        }
        BigDecimal ratio = ratedPrimary.divide(ratedSecondary, MathContext.DECIMAL128);
        return rawSecondaryValue.multiply(ratio).setScale(targetPrecision, RoundingMode.HALF_UP);
    }

    private static void requirePositiveSecondary(BigDecimal ratedSecondary) {
        if (ratedSecondary.signum() <= 0) {
            throw new IllegalArgumentException(
                    "MODEL_PRECONDITION_FAILED: 额定二次值必须大于 0，实际 " + ratedSecondary);
        }
    }
}
