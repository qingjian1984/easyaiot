package com.basiclab.iot.device.service.event;

/**
 * TD-001 §6.2 + §4.1：collector 配置发布单端口。
 * 实现负责：configVersion 单调递增（版本号不倒退，回滚也生成新版本并记录
 * rollbackFromVersion）、canonical/SHA-256 单次生成、静态校验冲突落 FAILED
 * 发布单（不得静默降频）。幂等派生键为
 * (workloadId, templateCode, templateVersion, bindingRevision)。
 * JDBC 实现待 TD-001 DDL 经 ADR-013 runner 增链落库后提供。
 */
public interface CollectorConfigReleasePort {

    /**
     * 当前 desired 是否已是目标再生结果（幂等判定）。
     * templateVersion 为 null（绑定回滚事件不携带目标模板版本）时，
     * 由实现从绑定表的 bindingRevision 解析目标模板版本后判定。
     */
    boolean desiredMatches(String workloadId, String templateCode, String templateVersion,
                           long bindingRevision);

    /**
     * 生成快照再生发布单（新单调递增 configVersion，走 DRAFT→VALIDATED→PUBLISHED 管线）。
     * 静态校验冲突由实现落 FAILED 发布单并抛出
     * {@link IllegalArgumentException}（稳定码，final 分流）；
     * 瞬态错误抛 {@link RuntimeException}（retryable 分流）。
     *
     * @param templateVersion 目标模板版本；绑定回滚事件为 null，由实现按 bindingRevision 解析
     * @param reasonCode      再生原因（BINDING_APPLIED / BINDING_ROLLED_BACK 或事件原因码）
     */
    void createRegenerationDraft(String workloadId, long tenantId, long productId,
                                 String templateCode, String templateVersion, long bindingRevision,
                                 String reasonCode);
}
