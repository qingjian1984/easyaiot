package com.basiclab.iot.device.service.event;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * TD-005 migration §4.6 / ADR-014：Outbox 入列服务（业务侧入口）。
 * MUST 语义的结构化强制：
 * - {@code Propagation.MANDATORY}：只能在活动业务事务内调用，
 *   保证业务事实、领域审计与 Outbox 同事务提交，事务提交前不存在可投递行；
 * - capability fail-closed：manifest 未启用 {@code power.device.model}（如 mini 档）
 *   时在守卫处拒绝发布，不产生 Outbox 待投递残留（ADR-014 §档位行为）。
 */
@Service
public class PowerModelOutboxService {

    /** 电力模型能力编码（与 capability manifest 一致）。 */
    public static final String CAPABILITY_CODE = "power.device.model";

    private final PowerModelOutboxRepository repository;
    private final CapabilityService capabilityService;

    public PowerModelOutboxService(PowerModelOutboxRepository repository,
                                   CapabilityService capabilityService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.capabilityService = Objects.requireNonNull(capabilityService, "capabilityService");
    }

    /**
     * 业务事务内入列一条待投递事件。
     *
     * @throws ServiceException capability 未启用（fail-closed）或无活动事务
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void enqueue(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!capabilityService.isEnabled(CAPABILITY_CODE)) {
            throw new ServiceException(
                    "MODEL_CAPABILITY_DISABLED: capability " + CAPABILITY_CODE
                            + " 未启用，fail-closed 拒绝事件入列");
        }
        repository.insertPending(entry);
    }
}
