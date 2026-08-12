package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.outbox.OutboxBackpressureException;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * TD-002 §9 有界命令队列（MPSC 语义，候选 4096 命令/16MiB，待 TD-001 压测冻结）。
 * 队列满且超时 → {@link OutboxBackpressureException}（Poller 保留原 messageId 重试）。
 */
final class OutboxCommandQueue {

    private final ArrayBlockingQueue<OutboxCommand> queue;

    OutboxCommandQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /** 入队（背压超时抛 OutboxBackpressureException）。 */
    void offer(OutboxCommand cmd, Duration timeout) {
        try {
            if (!queue.offer(cmd, timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new OutboxBackpressureException("outbox queue full after " + timeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxBackpressureException("interrupted while enqueuing");
        }
    }

    /** Writer 线程阻塞取出。 */
    OutboxCommand take() throws InterruptedException {
        return queue.take();
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    int size() {
        return queue.size();
    }
}
