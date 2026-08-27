package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LC03-03 §5.4 重启扫描直接合同：启动即扫描、批量上限、
 * (received_at_ms,id) 顺序由 SQL 保证、补发统一 DUPLICATE、
 * 单行失败不中断整批、任务异常不外抛、参数校验 fail-fast。
 */
class TelemetryAckReconciliationTaskTest {

    private static final TelemetryRoute ROUTE_A =
            new TelemetryRoute("power-meter", "meter-a");
    private static final TelemetryRoute ROUTE_B =
            new TelemetryRoute("power-meter", "meter-b");

    @Test
    void startRunsImmediateScanThenPeriodic() throws Exception {
        RecordingDispatch dispatch = new RecordingDispatch();
        dispatch.rows = List.of(row("2ca80f25-4b6c-443f-a114-1b3df0a8cdf9", ROUTE_A, 1000L));
        TelemetryAckReconciliationTask task = new TelemetryAckReconciliationTask(
                dispatch, new NoopService(), 3600_000L, 1000);
        task.start();
        // 启动即扫描：scheduleWithFixedDelay(0, ...) 的首轮同步前可能需极短等待。
        long deadline = System.currentTimeMillis() + 5_000;
        while (dispatch.claimCount.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, dispatch.claimCount.get());
        task.close();
    }

    @Test
    void scanPublishesEachClaimedRowAsDuplicate() {
        RecordingDispatch dispatch = new RecordingDispatch();
        dispatch.rows = List.of(
                row("2ca80f25-4b6c-443f-a114-1b3df0a8cdf9", ROUTE_A, 1000L),
                row("0f9e8d7c-6b5a-4c3d-2e1f-0a1b2c3d4e5f", ROUTE_B, 2000L));
        RecordingService service = new RecordingService();
        TelemetryAckReconciliationTask task = new TelemetryAckReconciliationTask(
                dispatch, service, 3600_000L, 1000);

        task.scanOnce();

        assertEquals(2, service.publishedStatuses.size());
        assertTrue(service.publishedStatuses.stream()
                .allMatch(status -> status == TelemetryAckStatus.DUPLICATE));
        assertEquals(1000, dispatch.lastClaimLimit);
    }

    @Test
    void claimFailureIsAbsorbedAndTaskStaysAlive() {
        ThrowingDispatch dispatch = new ThrowingDispatch();
        RecordingService service = new RecordingService();
        TelemetryAckReconciliationTask task = new TelemetryAckReconciliationTask(
                dispatch, service, 3600_000L, 1000);

        task.scanOnce(); // must not throw

        assertEquals(List.of(), service.publishedStatuses);
    }

    @Test
    void oneRowPublishFailureDoesNotStopTheRestOfBatch() {
        RecordingDispatch dispatch = new RecordingDispatch();
        dispatch.rows = List.of(
                row("2ca80f25-4b6c-443f-a114-1b3df0a8cdf9", ROUTE_A, 1000L),
                row("0f9e8d7c-6b5a-4c3d-2e1f-0a1b2c3d4e5f", ROUTE_B, 2000L));
        CountingFailOnFirstService service = new CountingFailOnFirstService();
        TelemetryAckReconciliationTask task = new TelemetryAckReconciliationTask(
                dispatch, service, 3600_000L, 1000);

        task.scanOnce();

        assertEquals(2, service.calls.get(), "both rows must be attempted");
    }

    @Test
    void constructorRejectsNonPositiveParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAckReconciliationTask(
                        new RecordingDispatch(), new NoopService(), 0L, 1000));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAckReconciliationTask(
                        new RecordingDispatch(), new NoopService(), 1000L, 0));
    }

    private static TelemetryAckDeliveryRow row(String messageId, TelemetryRoute route,
                                               long receivedAtMs) {
        return new TelemetryAckDeliveryRow(42L, messageId,
                "a9afddc7-02ee-4df3-905b-ec3e4107f25d", route, receivedAtMs, null, 0);
    }

    private static final class RecordingDispatch implements TelemetryAckDispatchPort {
        List<TelemetryAckDeliveryRow> rows = List.of();
        final AtomicInteger claimCount = new AtomicInteger();
        int lastClaimLimit;

        @Override
        public List<TelemetryAckDeliveryRow> claimPending(int limit) {
            claimCount.incrementAndGet();
            lastClaimLimit = limit;
            return rows;
        }

        @Override
        public TelemetryAckDeliveryRow loadForImmediateAck(long tenantId, String messageId) {
            return null;
        }

        @Override
        public boolean markSent(long tenantId, String messageId, long sentAtMs) {
            return true;
        }
    }

    private static final class ThrowingDispatch implements TelemetryAckDispatchPort {
        @Override
        public List<TelemetryAckDeliveryRow> claimPending(int limit) {
            throw new IllegalStateException("simulated database unavailable");
        }

        @Override
        public TelemetryAckDeliveryRow loadForImmediateAck(long tenantId, String messageId) {
            throw new IllegalStateException("not used in this test");
        }

        @Override
        public boolean markSent(long tenantId, String messageId, long sentAtMs) {
            throw new IllegalStateException("not used in this test");
        }
    }

    /** Publishes nothing; only used to isolate dispatch behaviour. */
    private static class NoopService extends CenterTelemetryAckService {
        NoopService() {
            super(null, null);
        }

        @Override
        void publishRow(TelemetryAckDeliveryRow row, TelemetryAckStatus status) {
            // no-op by design
        }
    }

    private static final class RecordingService extends NoopService {
        final List<TelemetryAckStatus> publishedStatuses = new java.util.ArrayList<>();

        @Override
        void publishRow(TelemetryAckDeliveryRow row, TelemetryAckStatus status) {
            publishedStatuses.add(status);
        }
    }

    private static final class CountingFailOnFirstService extends NoopService {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        void publishRow(TelemetryAckDeliveryRow row, TelemetryAckStatus status) {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("simulated first-row failure");
            }
        }
    }
}
