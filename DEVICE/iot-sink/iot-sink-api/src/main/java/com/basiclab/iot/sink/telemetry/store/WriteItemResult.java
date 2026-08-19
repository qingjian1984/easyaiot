package com.basiclab.iot.sink.telemetry.store;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Closed, stable per-input result. */
public record WriteItemResult(String messageId, WriteStatus status, String errorCode) {
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Set<String> STABLE_ERRORS = Set.of(
            "STORE_SAMPLE_INVALID", "STORE_VALUE_INVALID", "STORE_UNAVAILABLE",
            "STORE_STATE_CORRUPT", "MESSAGE_ID_COLLISION", "STORE_BATCH_TOO_LARGE",
            "STORE_CONTRACT_INVALID");

    public WriteItemResult {
        Objects.requireNonNull(status, "status");
        if (status == WriteStatus.STORED || status == WriteStatus.DUPLICATE) {
            if (messageId == null || messageId.isBlank()) {
                throw new IllegalArgumentException("successful result requires messageId");
            }
            if (errorCode != null) {
                throw new IllegalArgumentException("successful result cannot carry errorCode");
            }
        } else if (errorCode == null || !ERROR_CODE.matcher(errorCode).matches()
                || !STABLE_ERRORS.contains(errorCode)) {
            throw new IllegalArgumentException("failure result must carry a stable errorCode");
        } else if ((messageId == null || messageId.isBlank())
                && !"STORE_SAMPLE_INVALID".equals(errorCode)) {
            throw new IllegalArgumentException("failure identity is required for this error");
        }
    }

    public static WriteItemResult stored(String messageId) {
        return new WriteItemResult(messageId, WriteStatus.STORED, null);
    }

    public static WriteItemResult duplicate(String messageId) {
        return new WriteItemResult(messageId, WriteStatus.DUPLICATE, null);
    }

    public static WriteItemResult retryable(String messageId, String errorCode) {
        return new WriteItemResult(messageId, WriteStatus.RETRYABLE_FAILED, errorCode);
    }

    public static WriteItemResult finalFailed(String messageId, String errorCode) {
        return new WriteItemResult(messageId, WriteStatus.FINAL_FAILED, errorCode);
    }
}
