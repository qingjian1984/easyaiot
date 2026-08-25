package com.basiclab.iot.device.alarm.infrastructure.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/** 告警 Outbox 的纯重试、退避和错误摘要策略。 */
public final class AlarmOutboxRetryPolicy {

    public enum FailureDecision {
        RETRY,
        DEAD_LETTER
    }

    private static final String GENERIC_ERROR_CODE = "ALARM_OUTBOX_TRANSPORT_ERROR";
    private static final String REDACTED = "redacted";
    private static final String UNSPECIFIED = "unspecified";
    private static final int ERROR_SUMMARY_MAX_LENGTH = 256;
    private static final Pattern NON_CODE = Pattern.compile("[^A-Z0-9_]");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(?:password|passwd|secret|token|authorization|api[-_]?key)\\s*[:=]\\s*[^\\s,;]+" );
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?|mqtts?|mqtt|tcp|udp)://[^\\s]+" );
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)\\+?\\d[\\d() .-]{7,}\\d(?!\\d)" );

    private AlarmOutboxRetryPolicy() {
    }

    /** final 错误或达到最大尝试次数时进入 DEAD_LETTER。 */
    public static FailureDecision afterFailure(boolean retryable, int attempts,
                                               int maxRetries) {
        if (attempts < 1 || maxRetries < 0) {
            throw new IllegalArgumentException(
                    "ALARM_OUTBOX_RETRY_POLICY_INVALID: attempt budget is invalid");
        }
        if (!retryable || attempts >= maxRetries) {
            return FailureDecision.DEAD_LETTER;
        }
        return FailureDecision.RETRY;
    }

    /** 有界指数退避：第 1 次失败为 base，之后每次翻倍并封顶 cap。 */
    public static Duration exponentialDelay(int attempts, Duration base, Duration cap) {
        validateDurations(base, cap);
        if (attempts < 1) {
            throw new IllegalArgumentException(
                    "ALARM_OUTBOX_RETRY_POLICY_INVALID: attempts < 1");
        }
        Duration delay = base;
        for (int i = 1; i < attempts && delay.compareTo(cap) < 0; i++) {
            try {
                delay = delay.multipliedBy(2L);
            } catch (ArithmeticException overflow) {
                return cap;
            }
            if (delay.compareTo(cap) > 0) {
                return cap;
            }
        }
        return delay.compareTo(cap) > 0 ? cap : delay;
    }

    /** 把指数退避和非负抖动合并，并强制不超过 cap。 */
    public static Instant nextAttemptAt(Instant now, int attempts, Duration base,
                                        Duration cap, Duration jitter) {
        if (now == null) {
            throw new NullPointerException("now");
        }
        Duration exponential = exponentialDelay(attempts, base, cap);
        Duration remaining = cap.minus(exponential);
        Duration safeJitter = jitter == null || jitter.isNegative()
                ? Duration.ZERO
                : (jitter.compareTo(remaining) > 0 ? remaining : jitter);
        return now.plus(exponential).plus(safeJitter);
    }

    /** 将外部错误码收敛为不含自由文本的稳定 ASCII 码。 */
    public static String stableErrorCode(String errorCode) {
        String normalized = errorCode == null
                ? ""
                : errorCode.trim().toUpperCase(Locale.ROOT);
        normalized = NON_CODE.matcher(normalized).replaceAll("_");
        if (normalized.isEmpty() || !normalized.matches("^[A-Z][A-Z0-9_]*$")) {
            return GENERIC_ERROR_CODE;
        }
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return normalized;
    }

    /**
     * 错误摘要只保留短文本，并删除 URL、凭据、手机号和疑似正文。
     * 不接受 claimed entry，因此不会把 payload 作为上下文带入摘要。
     */
    public static String sanitizeErrorSummary(String errorSummary) {
        if (errorSummary == null || errorSummary.trim().isEmpty()) {
            return UNSPECIFIED;
        }
        String value = errorSummary.replaceAll("[\\r\\n\\t]+", " ").trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("payload") || lower.contains("credential")
                || lower.contains("request body") || looksLikeJson(value)) {
            return REDACTED;
        }
        value = CREDENTIAL.matcher(value).replaceAll("[CREDENTIAL_REDACTED]");
        value = URL.matcher(value).replaceAll("[URL_REDACTED]");
        value = PHONE.matcher(value).replaceAll("[PHONE_REDACTED]");
        value = value.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            return REDACTED;
        }
        return value.length() <= ERROR_SUMMARY_MAX_LENGTH
                ? value
                : value.substring(0, ERROR_SUMMARY_MAX_LENGTH);
    }

    private static boolean looksLikeJson(String value) {
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))
                || trimmed.contains("\"payload\"");
    }

    private static void validateDurations(Duration base, Duration cap) {
        if (base == null || cap == null || base.isZero() || base.isNegative()
                || cap.compareTo(base) < 0) {
            throw new IllegalArgumentException(
                    "ALARM_OUTBOX_RETRY_POLICY_INVALID: base/cap must be positive and ordered");
        }
    }
}
