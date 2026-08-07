package com.basiclab.iot.device.service.model;

import java.math.BigDecimal;

/**
 * ECMAScript Number::toString 等价格式化（RFC 8785 数值序列化要求）。
 * 依据：Java Double.toString 与 ECMAScript 使用相同的最短往返十进制数字，
 * 差异仅在记号（指数风格/后缀），据此重排即可逐位一致。
 * 注意：本模块默认按 Java 8 编译（基线复跑显式 -Dmaven.compiler.source=17），
 * 新代码必须保持 Java 8 兼容。
 */
final class JcsNumberFormatter {

    private JcsNumberFormatter() {
    }

    static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("MODEL_JCS_NON_FINITE_NUMBER: RFC 8785 不允许非有限数值");
        }
        if (value == 0.0d) {
            return "0";
        }
        // 最短往返十进制 → 去尾零，拆出数字串 s（长度 k）与十进制指数 n：value = s × 10^(n-k)
        BigDecimal decimal = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        String digits = decimal.unscaledValue().abs().toString();
        int k = digits.length();
        int n = k - decimal.scale();

        StringBuilder out = new StringBuilder();
        if (value < 0) {
            out.append('-');
        }
        if (k <= n && n <= 21) {
            out.append(digits);
            appendZeros(out, n - k);
        } else if (0 < n && n <= 21) {
            out.append(digits, 0, n).append('.').append(digits.substring(n));
        } else if (-6 < n && n <= 0) {
            out.append("0.");
            appendZeros(out, -n);
            out.append(digits);
        } else {
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits.substring(1));
            }
            out.append('e').append(n - 1 >= 0 ? '+' : '-').append(Math.abs(n - 1));
        }
        return out.toString();
    }

    private static void appendZeros(StringBuilder out, int count) {
        for (int i = 0; i < count; i++) {
            out.append('0');
        }
    }
}
