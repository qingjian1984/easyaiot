package com.basiclab.iot.sink.telemetry.envelope;

import java.math.BigDecimal;

/**
 * ECMAScript Number::toString 等价格式化（RFC 8785 数值序列化要求）。
 * 移植自 iot-device JcsNumberFormatter（TD-005 §6），保持逐位一致。
 */
final class EnvelopeJcsNumberFormatter {

    private EnvelopeJcsNumberFormatter() {
    }

    static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("ENVELOPE_JCS_NON_FINITE_NUMBER: RFC 8785 不允许非有限数值");
        }
        if (value == 0.0d) {
            return "0";
        }
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
