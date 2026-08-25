package com.basiclab.iot.device.alarm.infrastructure.event;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Inbox 的纯内存裁决合同。
 *
 * <p>本类不访问数据库、不依赖 Spring，也不执行 ACK。调用方必须先把
 * 裁决持久化到同一事务，再根据结果决定传输层行为。</p>
 */
public final class AlarmInboxArbiter {

    public static final int CURRENT_MAJOR = 1;
    private static final Pattern HASH_PATTERN =
            Pattern.compile("^sha256:[0-9a-f]{64}$");

    private AlarmInboxArbiter() {
    }

    /** 与 alarm_source_inbox 状态 CHECK 对齐。 */
    public enum Status {
        RECEIVED, PROCESSED, QUARANTINED
    }

    /** B0 冻结的 Inbox 裁决结果。 */
    public enum Decision {
        PROCESS,
        DUPLICATE,
        QUARANTINE_HASH_CONFLICT,
        REJECT_UNKNOWN_MAJOR,
        REJECT_FINAL
    }

    /** 既有 Inbox 行的最小只读视图。 */
    public record Existing(String envelopeHash, Status status) {
        public Existing {
            requireHash(envelopeHash);
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * 按固定优先级裁决 incoming 消息。
     *
     * <p>未知主版本优先于其他消息内容判断；final-invalid 优先于既有行；
     * 同 ID 异 hash 永不覆盖既有 hash。RECEIVED 表示上次事务未完成，必须
     * 允许同 hash 重试；只有 PROCESSED 才是 DUPLICATE，QUARANTINED 保持
     * 终态并返回 REJECT_FINAL。</p>
     */
    public static Decision decide(Existing existing, int incomingMajor,
                                  String envelopeHash, boolean finalInvalid) {
        requireHash(envelopeHash);
        if (incomingMajor != CURRENT_MAJOR) {
            return Decision.REJECT_UNKNOWN_MAJOR;
        }
        if (finalInvalid) {
            return Decision.REJECT_FINAL;
        }
        if (existing == null) {
            return Decision.PROCESS;
        }
        if (!existing.envelopeHash().equals(envelopeHash)) {
            return Decision.QUARANTINE_HASH_CONFLICT;
        }
        return switch (existing.status()) {
            case RECEIVED -> Decision.PROCESS;
            case PROCESSED -> Decision.DUPLICATE;
            case QUARANTINED -> Decision.REJECT_FINAL;
        };
    }

    private static void requireHash(String value) {
        if (value == null || !HASH_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "ALARM_EVENT_HASH_INVALID: envelopeHash 必须匹配 sha256:<64 位小写 hex>");
        }
    }
}
