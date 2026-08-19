package com.basiclab.iot.node.service.collector.config;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * collector 派发 job 的单实例入口。
 *
 * <p>调度器由安装侧在 capability/审批完成后通过条件配置显式装配；服务内部的
 * AtomicBoolean 仍保证同一 JVM 内单飞。</p>
 */
public final class CollectorConfigDispatchJob {

    public static final int DEFAULT_BATCH_LIMIT = 100;

    private final CollectorConfigDispatchService service;
    private final int batchLimit;

    public CollectorConfigDispatchJob(CollectorConfigDispatchService service, int batchLimit) {
        if (service == null || batchLimit < CollectorConfigDispatchService.MIN_PENDING_LIMIT
                || batchLimit > CollectorConfigDispatchService.MAX_PENDING_LIMIT) {
            throw new IllegalArgumentException("collector dispatch job configuration is invalid");
        }
        this.service = service;
        this.batchLimit = batchLimit;
    }

    public CollectorConfigDispatchJob(CollectorConfigDispatchService service) {
        this(service, DEFAULT_BATCH_LIMIT);
    }

    public CollectorConfigDispatchBatchResult runOnce() {
        return service.dispatchPending(batchLimit);
    }

    @Scheduled(fixedDelayString = "${easyaiot.collector.config-dispatch.fixed-delay-ms:30000}")
    public void dispatchScheduled() {
        service.dispatchPending(batchLimit);
    }
}
