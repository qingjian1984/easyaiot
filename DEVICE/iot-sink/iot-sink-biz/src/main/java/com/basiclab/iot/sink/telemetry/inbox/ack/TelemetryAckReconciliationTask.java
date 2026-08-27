package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * LC03-03 §5.4 重启对账扫描器。
 *
 * <p>启动立即扫描一次，之后每 {@code scanIntervalMs}（默认 10 秒）扫描
 * {@code ack_sent_at_ms IS NULL} 的行，按 {@code (received_at_ms, id)}
 * 升序每批最多 {@code batchSize}（默认 1000）条补发。补发统一使用
 * {@code DUPLICATE} 语义（行已持久即已接收成功）；多实例重复 ACK 由
 * collector 幂等吸收，本任务不持跨 publish 锁。
 */
public final class TelemetryAckReconciliationTask implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TelemetryAckReconciliationTask.class);

    public static final long DEFAULT_SCAN_INTERVAL_MS = 10_000L;
    public static final int DEFAULT_BATCH_SIZE = 1000;

    private final TelemetryAckDispatchPort dispatchPort;
    private final CenterTelemetryAckService ackService;
    private final long scanIntervalMs;
    private final int batchSize;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "telemetry-ack-reconciliation");
                thread.setDaemon(true);
                return thread;
            });
    private volatile boolean started;

    public TelemetryAckReconciliationTask(TelemetryAckDispatchPort dispatchPort,
                                          CenterTelemetryAckService ackService) {
        this(dispatchPort, ackService, DEFAULT_SCAN_INTERVAL_MS, DEFAULT_BATCH_SIZE);
    }

    public TelemetryAckReconciliationTask(TelemetryAckDispatchPort dispatchPort,
                                          CenterTelemetryAckService ackService,
                                          long scanIntervalMs,
                                          int batchSize) {
        if (scanIntervalMs <= 0 || batchSize <= 0) {
            throw new IllegalArgumentException("scanIntervalMs and batchSize must be positive");
        }
        this.dispatchPort = dispatchPort;
        this.ackService = ackService;
        this.scanIntervalMs = scanIntervalMs;
        this.batchSize = batchSize;
    }

    /** 启动即扫描一次，然后按固定周期补发。 */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        scheduler.scheduleWithFixedDelay(this::scanOnce, 0L, scanIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    /** 单轮扫描：领取一批待发行并逐行补发；单行失败不中断整批。 */
    void scanOnce() {
        List<TelemetryAckDeliveryRow> rows;
        try {
            rows = dispatchPort.claimPending(batchSize);
        } catch (RuntimeException e) {
            // 数据库短暂不可用等：保持任务存活，下一轮重试；只记录稳定分类。
            log.warn("ACK reconciliation scan failed: error={}", e.getClass().getSimpleName());
            return;
        }
        int published = 0;
        for (TelemetryAckDeliveryRow row : rows) {
            try {
                ackService.publishRow(row, TelemetryAckStatus.DUPLICATE);
                published += 1;
            } catch (RuntimeException e) {
                // 单行失败只影响该行（保持 sent NULL 下轮重领），不中断整批。
                log.warn("ACK reconciliation row failed: messageId={} error={}",
                        row.messageIdWire(), e.getClass().getSimpleName());
            }
        }
        if (!rows.isEmpty()) {
            log.info("ACK reconciliation batch: claimed={} published={} batchSize={}",
                    rows.size(), published, batchSize);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
