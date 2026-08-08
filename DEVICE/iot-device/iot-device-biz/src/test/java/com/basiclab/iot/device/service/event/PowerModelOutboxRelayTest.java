package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ADR-014 §验证 OUT-001～004：发布器编排合同（fake 仓储/transport，不触碰 DB/Kafka）。
 * claim 透传租约参数；成功→PUBLISHED 回写；retryable→退避 RETRY 回写；
 * final→即时 DEAD_LETTER；重试超限→DEAD_LETTER。
 */
class PowerModelOutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(60);

    @Test
    void successMarksPublished() {
        FakeRepository repository = new FakeRepository();
        repository.due.add(entry("e1", 0));
        FakeTransport transport = new FakeTransport();
        relay(repository, transport).relayOnce(NOW);

        assertEquals("power-model-release-v1", transport.topic);
        assertEquals("1:power_model_template:1001", transport.key);
        assertEquals(Arrays.asList("pub:e1"), repository.writes);
    }

    @Test
    void claimPassesLeaseAndBatchParameters() {
        FakeRepository repository = new FakeRepository();
        relay(repository, new FakeTransport()).relayOnce(NOW);

        assertEquals(NOW, repository.claimNow);
        assertEquals("pmoutbox-test", repository.claimOwner);
        assertEquals(LEASE, repository.claimLease);
        assertEquals(100, repository.claimBatch);
    }

    @Test
    void retryableFailureSchedulesBackoff() {
        FakeRepository repository = new FakeRepository();
        repository.due.add(entry("e1", 0));
        FakeTransport transport = new FakeTransport();
        transport.next = PowerModelEventTransport.TransportResult.failure(true, "MODEL_EVENT_SEND_RETRYABLE", "x");
        relay(repository, transport).relayOnce(NOW);

        // 第 1 次失败：attempts=1 → 退避 base 1s。
        assertEquals(Arrays.asList("retry:e1:1:" + NOW.plusSeconds(1)), repository.writes);
    }

    @Test
    void retryableBackoffDoublesWithRetryCount() {
        FakeRepository repository = new FakeRepository();
        repository.due.add(entry("e1", 3));
        FakeTransport transport = new FakeTransport();
        transport.next = PowerModelEventTransport.TransportResult.failure(true, "MODEL_EVENT_SEND_RETRYABLE", "x");
        relay(repository, transport).relayOnce(NOW);

        // retryCount=3 → attempts=4 → 退避 8s。
        assertEquals(Arrays.asList("retry:e1:4:" + NOW.plusSeconds(8)), repository.writes);
    }

    @Test
    void finalFailureGoesDeadLetterImmediately() {
        FakeRepository repository = new FakeRepository();
        repository.due.add(entry("e1", 0));
        FakeTransport transport = new FakeTransport();
        transport.next = PowerModelEventTransport.TransportResult.failure(false, "MODEL_EVENT_SEND_FINAL", "x");
        relay(repository, transport).relayOnce(NOW);

        assertEquals(Arrays.asList("dead:e1:MODEL_EVENT_SEND_FINAL"), repository.writes);
    }

    @Test
    void exhaustionGoesDeadLetter() {
        FakeRepository repository = new FakeRepository();
        repository.due.add(entry("e1", 4));
        FakeTransport transport = new FakeTransport();
        transport.next = PowerModelEventTransport.TransportResult.failure(true, "MODEL_EVENT_SEND_RETRYABLE", "x");
        relay(repository, transport).relayOnce(NOW);

        // maxRetries=5：attempts=5 达到上限 → DEAD_LETTER。
        assertEquals(Arrays.asList("dead:e1:MODEL_EVENT_SEND_RETRYABLE"), repository.writes);
    }

    @Test
    void nothingDueRelaysZero() {
        FakeRepository repository = new FakeRepository();
        int claimed = relay(repository, new FakeTransport()).relayOnce(NOW);
        assertEquals(0, claimed);
        assertEquals(Collections.emptyList(), repository.writes);
    }

    @Test
    void eachClaimedEntryIsDeliveredIndependently() {
        FakeRepository repository = new FakeRepository();
        repository.due.add(entry("e1", 0));
        repository.due.add(entry("e2", 0));
        FakeTransport transport = new FakeTransport();
        // 第一条成功、第二条 final 失败。
        transport.queue = new ArrayList<PowerModelEventTransport.TransportResult>(Arrays.asList(
                PowerModelEventTransport.TransportResult.success(),
                PowerModelEventTransport.TransportResult.failure(false, "MODEL_EVENT_SEND_FINAL", "x")));
        int claimed = relay(repository, transport).relayOnce(NOW);

        assertEquals(2, claimed);
        assertEquals(Arrays.asList("pub:e1", "dead:e2:MODEL_EVENT_SEND_FINAL"), repository.writes);
    }

    @Test
    void deliverRecordsMetrics() {
        RecordingEventMetrics metrics = new RecordingEventMetrics();
        FakeRepository repository = new FakeRepository();
        repository.due.add(entry("e1", 0));
        repository.due.add(entry("e2", 0));
        repository.due.add(entry("e3", 4));
        FakeTransport transport = new FakeTransport();
        transport.queue = new ArrayList<PowerModelEventTransport.TransportResult>(Arrays.asList(
                PowerModelEventTransport.TransportResult.success(),
                PowerModelEventTransport.TransportResult.failure(true, "MODEL_EVENT_SEND_RETRYABLE", "x"),
                PowerModelEventTransport.TransportResult.failure(false, "MODEL_EVENT_SEND_FINAL", "x")));
        new PowerModelOutboxRelay(repository, transport, metrics,
                "power-model-release-v1", "pmoutbox-test", LEASE, 100,
                Duration.ofSeconds(1), Duration.ofSeconds(16)).relayOnce(NOW);

        assertEquals(1, metrics.published("published"));
        assertEquals(1, metrics.published("retry_scheduled"));
        assertEquals(1, metrics.published("dead_letter"), "attempts=5 达到 maxRetries → dead_letter");
        assertEquals(3, metrics.deliveryDurations.size(), "每次 send 都记录投递耗时");
        assertEquals(0, metrics.quarantined);
    }

    private static PowerModelOutboxRelay relay(FakeRepository repository, FakeTransport transport) {
        return new PowerModelOutboxRelay(repository, transport, new RecordingEventMetrics(),
                "power-model-release-v1", "pmoutbox-test", LEASE, 100,
                Duration.ofSeconds(1), Duration.ofSeconds(16));
    }

    private static ClaimedOutboxEntry entry(String eventId, int retryCount) {
        return new ClaimedOutboxEntry(eventId, 1L, "power_model_template", "1001",
                "POWER_MODEL_TEMPLATE_PUBLISHED_V1", 1, "{\"k\":1}", retryCount, 5);
    }

    private static final class FakeRepository implements PowerModelOutboxRepository {
        final List<ClaimedOutboxEntry> due = new ArrayList<ClaimedOutboxEntry>();
        final List<String> writes = new ArrayList<String>();
        Instant claimNow;
        String claimOwner;
        Duration claimLease;
        int claimBatch;

        @Override
        public void insertPending(OutboxEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ClaimedOutboxEntry> claimDue(Instant now, String leaseOwner,
                                                 Duration leaseDuration, int batchSize) {
            this.claimNow = now;
            this.claimOwner = leaseOwner;
            this.claimLease = leaseDuration;
            this.claimBatch = batchSize;
            List<ClaimedOutboxEntry> result = new ArrayList<ClaimedOutboxEntry>(due);
            due.clear();
            return result;
        }

        @Override
        public void markPublished(String eventId, Instant publishedAt) {
            writes.add("pub:" + eventId);
            assertEquals(NOW, publishedAt);
        }

        @Override
        public void markRetry(String eventId, int retryCount, Instant nextAttemptAt,
                              String errorCode, String errorDigest) {
            writes.add("retry:" + eventId + ":" + retryCount + ":" + nextAttemptAt);
        }

        @Override
        public void markDeadLetter(String eventId, String errorCode, String errorDigest) {
            writes.add("dead:" + eventId + ":" + errorCode);
        }

        @Override
        public long countByStatus(String status) {
            return 0L;
        }
    }

    private static final class FakeTransport implements PowerModelEventTransport {
        TransportResult next = TransportResult.success();
        List<TransportResult> queue;
        String topic;
        String key;

        @Override
        public TransportResult send(String topic, String key, String payload) {
            this.topic = topic;
            this.key = key;
            if (queue != null && !queue.isEmpty()) {
                return queue.remove(0);
            }
            return next;
        }
    }
}
