package com.basiclab.iot.device.service.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * TD-005 §7/§10.2：模板版本生命周期状态机。
 * 只允许本 TD 定义的前向转换；PUBLISHED 起内容不可修改；
 * RETIRE 需要结构化 migrationNotice、已发布替代版本与影响确认。
 * 纯领域逻辑，持久化接线待 ADR-013 Accepted 后接入。Java 8 兼容。
 */
public final class TemplateLifecycle {

    public enum Lifecycle {
        DRAFT, PUBLISHED, DEPRECATED, RETIRED
    }

    /** 草稿管理态（不扩展版本生命周期）。 */
    public enum DraftState {
        ACTIVE, ABANDONED
    }

    /** 草稿无活动清理阈值（§7：连续 90 天无活动标记 ABANDONED）。 */
    private static final Duration DRAFT_INACTIVITY_LIMIT = Duration.ofDays(90);

    private TemplateLifecycle() {
    }

    /** §7 状态图合法转换；其余一律拒绝。 */
    public static Lifecycle requireTransition(Lifecycle from, Lifecycle to) {
        boolean legal = (from == Lifecycle.DRAFT && to == Lifecycle.PUBLISHED)
                || (from == Lifecycle.PUBLISHED && to == Lifecycle.DEPRECATED)
                || (from == Lifecycle.DEPRECATED && to == Lifecycle.RETIRED)
                || (from == Lifecycle.PUBLISHED && to == Lifecycle.DRAFT)
                || (from == Lifecycle.DEPRECATED && to == Lifecycle.DRAFT);
        if (!legal) {
            throw new IllegalArgumentException(
                    "MODEL_PRECONDITION_FAILED: 非法生命周期转换 " + from + " -> " + to);
        }
        return to;
    }

    /** §7：RETIRED 需要结构化 migrationNotice、替代版本已发布且影响报告已确认。 */
    public static void requireRetirable(MigrationNotice notice, boolean alternativePublished,
                                        boolean impactConfirmed) {
        if (notice == null || !notice.isComplete()) {
            throw new IllegalArgumentException(
                    "MODEL_RETIRE_PRECONDITION_FAILED: migrationNotice 不完整（需 alternativeTemplateCode/"
                            + "alternativeVersion/migrationSteps/compatibleUntil）");
        }
        if (!alternativePublished) {
            throw new IllegalArgumentException(
                    "MODEL_RETIRE_PRECONDITION_FAILED: 替代版本必须已发布且可读取");
        }
        if (!impactConfirmed) {
            throw new IllegalArgumentException(
                    "MODEL_RETIRE_PRECONDITION_FAILED: 影响报告未确认");
        }
    }

    /** §7：DEPRECATED 禁止新产品绑定；只有 PUBLISHED 可绑定。 */
    public static void requireBindable(Lifecycle lifecycle) {
        if (lifecycle == Lifecycle.DEPRECATED) {
            throw new IllegalArgumentException(
                    "MODEL_DEPRECATED_NEW_BINDING_DENIED: DEPRECATED 模板禁止新产品绑定");
        }
        if (lifecycle != Lifecycle.PUBLISHED) {
            throw new IllegalArgumentException(
                    "MODEL_PRECONDITION_FAILED: 只有 PUBLISHED 模板可绑定，当前 " + lifecycle);
        }
    }

    /** §7：PUBLISHED 起内容、版本、基线和哈希不可修改，只允许生命周期元数据变化。 */
    public static void requireContentMutable(Lifecycle lifecycle) {
        if (lifecycle != Lifecycle.DRAFT) {
            throw new IllegalArgumentException(
                    "MODEL_TEMPLATE_PUBLISHED_IMMUTABLE: " + lifecycle + " 内容不可修改");
        }
    }

    /** §7：连续 90 天无活动由清理任务标记 ABANDONED。 */
    public static DraftState draftStateAfterCleanup(Instant lastActivityAt, Instant now) {
        Objects.requireNonNull(lastActivityAt, "lastActivityAt");
        Objects.requireNonNull(now, "now");
        return Duration.between(lastActivityAt, now).compareTo(DRAFT_INACTIVITY_LIMIT) > 0
                ? DraftState.ABANDONED : DraftState.ACTIVE;
    }

    /** §7：ABANDONED 草稿不可编辑，需要继续时克隆新的 ACTIVE 草稿。 */
    public static void requireDraftEditable(DraftState draftState) {
        if (draftState != DraftState.ACTIVE) {
            throw new IllegalArgumentException(
                    "MODEL_PRECONDITION_FAILED: ABANDONED 草稿不可编辑，须克隆新草稿");
        }
    }

    /** §7：RETIRED 的结构化迁移说明。 */
    public static final class MigrationNotice {
        private final String alternativeTemplateCode;
        private final String alternativeVersion;
        private final List<String> migrationSteps;
        private final Instant compatibleUntil;

        public MigrationNotice(String alternativeTemplateCode, String alternativeVersion,
                               List<String> migrationSteps, Instant compatibleUntil) {
            this.alternativeTemplateCode = alternativeTemplateCode;
            this.alternativeVersion = alternativeVersion;
            this.migrationSteps = migrationSteps;
            this.compatibleUntil = compatibleUntil;
        }

        boolean isComplete() {
            return alternativeTemplateCode != null && !alternativeTemplateCode.isEmpty()
                    && alternativeVersion != null && !alternativeVersion.isEmpty()
                    && migrationSteps != null && !migrationSteps.isEmpty()
                    && compatibleUntil != null;
        }

        public String alternativeTemplateCode() {
            return alternativeTemplateCode;
        }

        public String alternativeVersion() {
            return alternativeVersion;
        }

        public List<String> migrationSteps() {
            return migrationSteps;
        }

        public Instant compatibleUntil() {
            return compatibleUntil;
        }
    }
}
