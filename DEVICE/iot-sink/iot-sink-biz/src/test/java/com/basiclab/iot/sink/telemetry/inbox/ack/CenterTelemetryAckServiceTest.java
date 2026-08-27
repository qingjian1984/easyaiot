package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LC03-03 §5.3 即时 ACK 服务直接合同：只对成功状态发送、行事实不匹配
 * 零 publish、publish 失败保持 sent NULL 交扫描器、成功后条件 markSent。
 */
class CenterTelemetryAckServiceTest {

    private static final long TENANT = 42L;
    private static final String MESSAGE_ID = "2ca80f25-4b6c-443f-a114-1b3df0a8cdf9";
    private static final String REQUEST_ID = "a9afddc7-02ee-4df3-905b-ec3e4107f25d";
    private static final TelemetryRoute ROUTE = new TelemetryRoute("power-meter", "meter-01");

    @Test
    void collisionAndNullStatusNeverPublish() {
        RecordingPublisher publisher = new RecordingPublisher(true);
        FakeDispatch dispatch = new FakeDispatch();
        CenterTelemetryAckService service = new CenterTelemetryAckService(dispatch, publisher);

        service.sendImmediateAck(TENANT, MESSAGE_ID, REQUEST_ID,
                TelemetryAckStatus.ACCEPTED_DURABLE, 1000L) ;
        // reset for the actual assertions below
        publisher.published.clear();

        service.sendImmediateAck(TENANT, MESSAGE_ID, REQUEST_ID, null, 1000L);
        assertEquals(List.of(), publisher.published);

        // Non-success status enum value is impossible for TelemetryAckStatus.isSuccess
        // (only ACCEPTED_DURABLE / DUPLICATE); null covers the guard branch.
        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void rowNotSendableFailsClosedWithoutPublish() {
        RecordingPublisher publisher = new RecordingPublisher(true);
        FakeDispatch dispatch = new FakeDispatch();
        dispatch.row = null; // repository fail-closed: missing/unsendable row
        CenterTelemetryAckService service = new CenterTelemetryAckService(dispatch, publisher);

        service.sendImmediateAck(TENANT, MESSAGE_ID, REQUEST_ID,
                TelemetryAckStatus.ACCEPTED_DURABLE, 1000L);

        assertEquals(List.of(), publisher.published);
        assertNull(dispatch.markedSent);
    }

    @Test
    void requestIdOrPersistedAtMismatchFailsClosed() {
        RecordingPublisher publisher = new RecordingPublisher(true);
        FakeDispatch dispatch = new FakeDispatch();
        CenterTelemetryAckService service = new CenterTelemetryAckService(dispatch, publisher);

        service.sendImmediateAck(TENANT, MESSAGE_ID, "00000000-0000-4000-8000-000000000000",
                TelemetryAckStatus.ACCEPTED_DURABLE, 1000L);
        service.sendImmediateAck(TENANT, MESSAGE_ID, REQUEST_ID,
                TelemetryAckStatus.DUPLICATE, 999L);

        assertEquals(List.of(), publisher.published);
        assertNull(dispatch.markedSent);
    }

    @Test
    void successPublishMarksSentWithOriginalPersistedAt() {
        RecordingPublisher publisher = new RecordingPublisher(true);
        FakeDispatch dispatch = new FakeDispatch();
        CenterTelemetryAckService service = new CenterTelemetryAckService(dispatch, publisher);

        service.sendImmediateAck(TENANT, MESSAGE_ID, REQUEST_ID,
                TelemetryAckStatus.ACCEPTED_DURABLE, 1000L);

        assertEquals(1, publisher.published.size());
        TelemetryAckV1 ack = publisher.published.get(0);
        assertEquals(MESSAGE_ID, ack.messageId());
        assertEquals(REQUEST_ID, ack.requestId());
        assertEquals(TelemetryAckStatus.ACCEPTED_DURABLE, ack.status());
        assertEquals(0, ack.code());
        assertEquals("OK", ack.reasonCode());
        assertEquals(1000L, ack.persistedAtMs());
        assertEquals("/iot/power-meter/meter-01/property/downstream/report/ack",
                publisher.topics.get(0));
        assertEquals(MESSAGE_ID, dispatch.markedSent);
    }

    @Test
    void duplicatePublishKeepsFirstReceivedAtAndCode1001() {
        RecordingPublisher publisher = new RecordingPublisher(true);
        FakeDispatch dispatch = new FakeDispatch();
        CenterTelemetryAckService service = new CenterTelemetryAckService(dispatch, publisher);

        service.sendImmediateAck(TENANT, MESSAGE_ID, REQUEST_ID,
                TelemetryAckStatus.DUPLICATE, 1000L);

        TelemetryAckV1 ack = publisher.published.get(0);
        assertEquals(TelemetryAckStatus.DUPLICATE, ack.status());
        assertEquals(1001, ack.code());
        assertEquals("DUPLICATE", ack.reasonCode());
        assertEquals(1000L, ack.persistedAtMs(), "persistedAt must be the row's first received_at_ms");
    }

    @Test
    void publishFailureKeepsSentNullForScanner() {
        RecordingPublisher publisher = new RecordingPublisher(false);
        FakeDispatch dispatch = new FakeDispatch();
        CenterTelemetryAckService service = new CenterTelemetryAckService(dispatch, publisher);

        service.sendImmediateAck(TENANT, MESSAGE_ID, REQUEST_ID,
                TelemetryAckStatus.ACCEPTED_DURABLE, 1000L);

        assertEquals(1, publisher.published.size());
        assertNull(dispatch.markedSent);
    }

    @Test
    void publishThrowIsAbsorbedAndKeepsSentNull() {
        RecordingPublisher publisher = new RecordingPublisher(true);
        publisher.throwOnPublish = true;
        FakeDispatch dispatch = new FakeDispatch();
        CenterTelemetryAckService service = new CenterTelemetryAckService(dispatch, publisher);

        service.sendImmediateAck(TENANT, MESSAGE_ID, REQUEST_ID,
                TelemetryAckStatus.ACCEPTED_DURABLE, 1000L);

        assertNull(dispatch.markedSent);
    }

    private static final class FakeDispatch implements TelemetryAckDispatchPort {
        TelemetryAckDeliveryRow row = new TelemetryAckDeliveryRow(
                TENANT, MESSAGE_ID, REQUEST_ID, ROUTE, 1000L, null, 0);
        String markedSent;

        @Override
        public List<TelemetryAckDeliveryRow> claimPending(int limit) {
            return List.of();
        }

        @Override
        public TelemetryAckDeliveryRow loadForImmediateAck(long tenantId, String messageId) {
            return row;
        }

        @Override
        public boolean markSent(long tenantId, String messageId, long sentAtMs) {
            markedSent = messageId;
            return true;
        }
    }

    private static final class RecordingPublisher implements CenterTelemetryAckPublisherPort {
        final List<TelemetryAckV1> published = new ArrayList<>();
        final List<String> topics = new ArrayList<>();
        boolean throwOnPublish;
        private final boolean delivered;

        RecordingPublisher(boolean delivered) {
            this.delivered = delivered;
        }

        @Override
        public boolean publish(TelemetryAckV1 ack, String ackTopic) {
            published.add(ack);
            topics.add(ackTopic);
            if (throwOnPublish) {
                throw new IllegalStateException("simulated broker sync failure");
            }
            return delivered;
        }
    }
}
