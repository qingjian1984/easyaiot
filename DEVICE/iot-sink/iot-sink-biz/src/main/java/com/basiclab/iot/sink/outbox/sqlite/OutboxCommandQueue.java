package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.outbox.OutboxBackpressureException;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * TD-002 §9 有界命令队列（双队列：控制命令优先于数据命令）。
 *
 * <p>控制命令（Claim/ApplyAck/Reclaim）入 controlQueue，数据命令（AppendBatch）入 dataQueue。
 * take() 优先从 controlQueue 取，controlQueue 空时取 dataQueue，确保 ACK/Claim 不被 AppendBatch 饿死。
 */
public final class OutboxCommandQueue {

    private final BlockingQueue<OutboxCommand> controlQueue;
    private final BlockingQueue<OutboxCommand> dataQueue;

    OutboxCommandQueue(int capacity) {
        int half = Math.max(1, capacity / 2);
        this.controlQueue = new ArrayBlockingQueue<>(half);
        this.dataQueue = new ArrayBlockingQueue<>(capacity - half);
    }

    public void offer(OutboxCommand cmd, Duration timeout) {
        BlockingQueue<OutboxCommand> q = isControl(cmd) ? controlQueue : dataQueue;
        try {
            if (!q.offer(cmd, timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new OutboxBackpressureException("outbox queue full after " + timeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxBackpressureException("interrupted while enqueuing");
        }
    }

    OutboxCommand take() throws InterruptedException {
        OutboxCommand cmd = controlQueue.poll();
        if (cmd != null) {
            return cmd;
        }
        return dataQueue.take();
    }

    boolean isEmpty() {
        return controlQueue.isEmpty() && dataQueue.isEmpty();
    }

    int size() {
        return controlQueue.size() + dataQueue.size();
    }

    private static boolean isControl(OutboxCommand cmd) {
        return cmd instanceof OutboxCommand.Claim
                || cmd instanceof OutboxCommand.ApplyAck
                || cmd instanceof OutboxCommand.ReclaimExpiredLeases;
    }
}
