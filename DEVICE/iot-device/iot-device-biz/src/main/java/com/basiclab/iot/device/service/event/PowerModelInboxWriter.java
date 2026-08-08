package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * ADR-014 §决策 MUST + P-07：消费者 Inbox 写入编排。
 * 消费循环对本类返回 {@link Action#PROCESS} 的事件执行处理，随后 markProcessed
 * 成功才提交 offset（手动 offset 在 Inbox 写成功后提交）；
 * DUPLICATE/QUARANTINED/IGNORED 不产生业务副作用，可直接提交 offset（隔离/DLQ 由本类落库）。
 * 同 ID 异 hash 隔离并 critical；未知主版本隔离/DLQ 并告警，不阻塞其他事件。
 * 日志只携带 eventId/tenantId 与 payload_hash，绝不记录 payload 正文。Java 8 兼容。
 */
public class PowerModelInboxWriter {

    private static final Logger log = LoggerFactory.getLogger(PowerModelInboxWriter.class);

    /** 消费循环应对该事件采取的动作。 */
    public enum Action {
        /** 执行处理，成功后 markProcessed 再提交 offset。 */
        PROCESS,
        /** 幂等重复：不重复执行，可提交 offset。 */
        DUPLICATE,
        /** 已隔离（新隔离或维持隔离）：不执行，可提交 offset；告警由本类触发。 */
        QUARANTINED,
        /** 首插争抢落败：重读裁决后另行处理（本轮不执行、不提交 offset）。 */
        LOST_CONTENTION
    }

    /** 摄入结果。 */
    public static final class IngestResult {
        private final Action action;
        private final boolean critical;

        private IngestResult(Action action, boolean critical) {
            this.action = action;
            this.critical = critical;
        }

        public Action action() {
            return action;
        }

        /** true=触发 critical 告警（同 ID 异 hash / 未知主版本新隔离）。 */
        public boolean isCritical() {
            return critical;
        }
    }

    private final PowerModelInboxRepository repository;
    private final Set<Integer> supportedMajors;

    /**
     * @param supportedMajors 消费者支持的主版本集合（M1 为 {1}；双发窗口为 {1,2}）
     */
    public PowerModelInboxWriter(PowerModelInboxRepository repository, Set<Integer> supportedMajors) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (supportedMajors == null || supportedMajors.isEmpty()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_RETRY_POLICY_INVALID: supportedMajors 不得为空");
        }
        this.supportedMajors = supportedMajors;
    }

    /**
     * 摄入一条事件（幂等）。
     *
     * @param envelope         已校验的 Envelope（eventType 后缀与 schemaVersion 已一致）
     * @param canonicalPayload 规范序列化正文（用于 payload_hash）
     * @param now              当前时间（注入以便确定性测试；仅用于日志/回写）
     */
    public IngestResult ingest(PowerModelEventEnvelope envelope, String canonicalPayload, Instant now) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(canonicalPayload, "canonicalPayload");
        Objects.requireNonNull(now, "now");
        String eventId = envelope.eventId();
        String payloadHash = PowerModelEventEnvelope.payloadHash(canonicalPayload);
        long tenantId = Long.parseLong(envelope.tenantId());

        InboxArbiter.Decision decision = InboxArbiter.decide(
                repository.findByEventId(eventId), envelope.schemaVersion(), payloadHash, supportedMajors);
        switch (decision.outcome()) {
            case PROCEED:
                if (!repository.insertReceived(eventId, tenantId, envelope.eventType(), payloadHash)) {
                    // 首插争抢落败：交由下一轮重读裁决，避免双处理。
                    return new IngestResult(Action.LOST_CONTENTION, false);
                }
                return new IngestResult(Action.PROCESS, false);
            case DUPLICATE:
                log.info("power-model event duplicate eventId={} tenantId={}", eventId, tenantId);
                return new IngestResult(Action.DUPLICATE, false);
            case RETRYABLE:
                return new IngestResult(Action.PROCESS, false);
            case QUARANTINE_HASH_CONFLICT:
                repository.upsertQuarantined(eventId, tenantId, envelope.eventType(), payloadHash,
                        "MODEL_EVENT_HASH_CONFLICT", "same eventId different payload_hash");
                log.error("power-model event quarantined critical eventId={} tenantId={} reason=hash-conflict",
                        eventId, tenantId);
                return new IngestResult(Action.QUARANTINED, true);
            case REJECT_UNKNOWN_MAJOR_VERSION:
                repository.upsertQuarantined(eventId, tenantId, envelope.eventType(), payloadHash,
                        "MODEL_EVENT_UNKNOWN_MAJOR_VERSION", "schemaVersion=" + envelope.schemaVersion());
                log.error("power-model event quarantined critical eventId={} tenantId={} reason=unknown-major schemaVersion={}",
                        eventId, tenantId, envelope.schemaVersion());
                return new IngestResult(Action.QUARANTINED, true);
            case AWAITING_DISPOSITION:
            default:
                return new IngestResult(Action.QUARANTINED, false);
        }
    }

    /** 处理完成回写；成功后消费循环方可提交 offset。 */
    public void markProcessed(String eventId, Instant processedAt) {
        repository.markProcessed(eventId, processedAt);
    }
}
