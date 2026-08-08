package com.basiclab.iot.device.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.Objects;

/**
 * ADR-014：Outbox 发布器调度驱动。选型：Spring Scheduling
 * （2026-08-08 owner 部署评审裁定；Quartz/iot-common-job 会引入新依赖与
 * QRTZ_* 表，对单功能轮询属过度设计，且多实例并发安全已由认领 SQL 的
 * FOR UPDATE SKIP LOCKED + 租约承担，调度层无需集群协调）。
 * fixedDelay 轮询驱动 {@link PowerModelOutboxRelay#relayOnce}；单轮异常只记录
 * 异常类型摘要（绝不含 payload），不向外抛出，保证后续轮询存活。
 * Bean 仅在 {@code power.model.events.enabled=true} 时装配（mini 不调度）。
 * Java 8 兼容。
 */
public class PowerModelOutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(PowerModelOutboxRelayScheduler.class);

    private final PowerModelOutboxRelay relay;
    private final Clock clock;

    public PowerModelOutboxRelayScheduler(PowerModelOutboxRelay relay, Clock clock) {
        this.relay = Objects.requireNonNull(relay, "relay");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(initialDelayString = "${power.model.events.relay.initial-delay-ms:5000}",
            fixedDelayString = "${power.model.events.relay.poll-interval-ms:1000}")
    public void poll() {
        try {
            int claimed = relay.relayOnce(clock.instant());
            if (claimed > 0) {
                log.info("[poll][发布器轮询完成 claimed={}]", claimed);
            }
        } catch (Exception ex) {
            // 不抛出：fixedDelay 轮询必须存活；摘要只带异常类型，绝不含 payload
            log.error("[poll][发布器轮询失败 type={}]", ex.getClass().getSimpleName());
        }
    }
}
