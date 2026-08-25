package com.basiclab.iot.device.alarm.relay;

import com.basiclab.iot.device.alarm.infrastructure.event.AlarmOutboxClaimedEntry;
import com.basiclab.iot.device.alarm.infrastructure.event.AlarmOutboxJitterSource;
import com.basiclab.iot.device.alarm.infrastructure.event.AlarmOutboxRelay;
import com.basiclab.iot.device.alarm.infrastructure.event.AlarmOutboxRepository;
import com.basiclab.iot.device.alarm.infrastructure.event.AlarmOutboxTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P02-M2-02B 纯合同：仅使用 fake repository/transport，不连接数据库、Kafka 或 MQTT。
 */
class AlarmOutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);

    @Test
    void twoClaimsAreMutuallyExclusive() {
        FakeRepository repository = new FakeRepository(entry("e1", 0, 5));

        List<AlarmOutboxClaimedEntry> first = repository.claimDue(
                NOW, "relay-a", LEASE, 10);
        List<AlarmOutboxClaimedEntry> second = repository.claimDue(
                NOW, "relay-b", LEASE, 10);

        assertEquals(1, first.size());
        assertTrue(second.isEmpty(), "未过期租约不得被第二副本认领");
        assertEquals("relay-a", repository.state("e1").leaseOwner);
    }

    @Test
    void unexpiredLeaseCannotBeClaimedButExpiredLeaseRecovers() {
        FakeRepository repository = new FakeRepository(entry("e1", 0, 5));
        repository.claimDue(NOW, "relay-a", LEASE, 10);

        assertTrue(repository.claimDue(NOW.plusSeconds(29), "relay-b", LEASE, 10).isEmpty());
        List<AlarmOutboxClaimedEntry> recovered = repository.claimDue(
                NOW.plusSeconds(30), "relay-b", LEASE, 10);

        assertEquals(1, recovered.size());
        assertEquals("relay-b", repository.state("e1").leaseOwner);
        assertEquals(NOW.plusSeconds(60), repository.state("e1").leaseUntil);
    }

    @Test
    void brokerAcknowledgementMarksPublished() {
        FakeRepository repository = new FakeRepository(entry("e1", 0, 5));
        FakeTransport transport = new FakeTransport(AlarmOutboxTransport.TransportResult.acknowledged());
        AlarmOutboxRelay relay = relay(repository, transport, AlarmOutboxJitterSource.none());

        assertEquals(1, relay.relayOnce());
        assertEquals(1, transport.sent.size());
        assertEquals(FakeRepository.Status.PUBLISHED, repository.state("e1").status);
        assertEquals(NOW, repository.state("e1").publishedAt);
        assertTrue(repository.state("e1").leaseOwner == null);
    }

    @Test
    void retryableFailureUsesBoundedExponentialBackoffAndJitter() {
        FakeRepository repository = new FakeRepository(entry("e1", 0, 5));
        FakeTransport transport = new FakeTransport(AlarmOutboxTransport.TransportResult.retryable(
                "BROKER_TIMEOUT", "temporary broker delay"));
        AlarmOutboxJitterSource jitter = (eventId, attempt, exponential) -> Duration.ofMillis(250);
        AlarmOutboxRelay relay = relay(repository, transport, jitter);

        relay.relayOnce();

        FakeRepository.State state = repository.state("e1");
        assertEquals(FakeRepository.Status.PENDING, state.status);
        assertEquals(1, state.retryCount);
        assertEquals(NOW.plusMillis(1250), state.nextAttemptAt);
        assertEquals("BROKER_TIMEOUT", state.errorCode);
        assertEquals("temporary broker delay", state.errorSummary);
        assertTrue(state.nextAttemptAt.isBefore(NOW.plusSeconds(16)));
    }

    @Test
    void retryBackoffNeverExceedsCap() {
        FakeRepository repository = new FakeRepository(entry("e1", 3, 5));
        FakeTransport transport = new FakeTransport(AlarmOutboxTransport.TransportResult.retryable(
                "BROKER_TIMEOUT", "temporary"));
        AlarmOutboxRelay relay = relay(repository, transport,
                (eventId, attempt, exponential) -> Duration.ofHours(1));

        relay.relayOnce();

        assertEquals(NOW.plusSeconds(16), repository.state("e1").nextAttemptAt);
    }

    @Test
    void finalFailureGoesToDeadLetter() {
        FakeRepository repository = new FakeRepository(entry("e1", 0, 5));
        FakeTransport transport = new FakeTransport(AlarmOutboxTransport.TransportResult.finalFailure(
                "SCHEMA_REJECTED", "event rejected"));

        relay(repository, transport, AlarmOutboxJitterSource.none()).relayOnce();

        FakeRepository.State state = repository.state("e1");
        assertEquals(FakeRepository.Status.DEAD_LETTER, state.status);
        assertEquals(NOW, state.deadLetteredAt);
        assertEquals("SCHEMA_REJECTED", state.errorCode);
    }

    @Test
    void retryableFailureAtAttemptLimitGoesToDeadLetter() {
        FakeRepository repository = new FakeRepository(entry("e1", 4, 5));
        FakeTransport transport = new FakeTransport(AlarmOutboxTransport.TransportResult.retryable(
                "BROKER_TIMEOUT", "temporary"));

        relay(repository, transport, AlarmOutboxJitterSource.none()).relayOnce();

        assertEquals(FakeRepository.Status.DEAD_LETTER, repository.state("e1").status);
        assertEquals("BROKER_TIMEOUT", repository.state("e1").errorCode);
    }

    @Test
    void transportExceptionDoesNotMarkPublished() {
        FakeRepository repository = new FakeRepository(entry("e1", 0, 5));
        FakeTransport transport = new FakeTransport(null);
        transport.failure = new IllegalStateException(
                "password=top-secret phone=13800138000 https://private.example/payload");

        relay(repository, transport, AlarmOutboxJitterSource.none()).relayOnce();

        FakeRepository.State state = repository.state("e1");
        assertFalse(state.status == FakeRepository.Status.PUBLISHED);
        assertEquals(FakeRepository.Status.PENDING, state.status);
        assertEquals("ALARM_OUTBOX_TRANSPORT_EXCEPTION", state.errorCode);
        assertEquals("transport exception", state.errorSummary);
    }

    @Test
    void errorSummaryIsRedactedBeforePersistence() {
        FakeRepository repository = new FakeRepository(entry("e1", 0, 5));
        FakeTransport transport = new FakeTransport(AlarmOutboxTransport.TransportResult.retryable(
                "BROKER_TIMEOUT", "password=top-secret phone=13800138000 "
                        + "https://private.example/a payload={\"alarmId\":1}"));

        relay(repository, transport, AlarmOutboxJitterSource.none()).relayOnce();

        String summary = repository.state("e1").errorSummary;
        assertNotNull(summary);
        assertFalse(summary.contains("top-secret"));
        assertFalse(summary.contains("13800138000"));
        assertFalse(summary.contains("private.example"));
        assertFalse(summary.contains("alarmId"));
        assertEquals("redacted", summary);
    }

    private static AlarmOutboxRelay relay(FakeRepository repository,
                                          FakeTransport transport,
                                          AlarmOutboxJitterSource jitter) {
        return new AlarmOutboxRelay(repository, transport, "relay-test", LEASE, 10,
                Duration.ofSeconds(1), Duration.ofSeconds(16), () -> NOW, jitter);
    }

    private static AlarmOutboxClaimedEntry entry(String eventId, int retryCount,
                                                 int maxRetries) {
        return new AlarmOutboxClaimedEntry(eventId, "1", "1001",
                "device.alarm.created.v1", "1.0", "1:1001",
                "sha256:" + "a".repeat(64), "{\"alarmId\":\"1001\"}",
                "{}", retryCount, maxRetries);
    }

    private static final class FakeTransport implements AlarmOutboxTransport {
        private final TransportResult result;
        private final List<AlarmOutboxClaimedEntry> sent = new ArrayList<>();
        private RuntimeException failure;

        private FakeTransport(TransportResult result) {
            this.result = result;
        }

        @Override
        public TransportResult send(AlarmOutboxClaimedEntry entry) {
            sent.add(entry);
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class FakeRepository implements AlarmOutboxRepository {
        enum Status { PENDING, PUBLISHING, PUBLISHED, DEAD_LETTER }

        private final Map<String, State> states = new LinkedHashMap<>();

        private FakeRepository(AlarmOutboxClaimedEntry... entries) {
            for (AlarmOutboxClaimedEntry entry : entries) {
                states.put(entry.eventId(), new State(entry));
            }
        }

        @Override
        public synchronized List<AlarmOutboxClaimedEntry> claimDue(Instant now,
                                                                     String leaseOwner,
                                                                     Duration leaseDuration,
                                                                     int batchSize) {
            List<State> ordered = new ArrayList<>(states.values());
            ordered.sort(Comparator.comparing(state -> state.entry.eventId()));
            List<AlarmOutboxClaimedEntry> result = new ArrayList<>();
            for (State state : ordered) {
                if (result.size() >= batchSize || !claimable(state, now)) {
                    continue;
                }
                state.status = Status.PUBLISHING;
                state.leaseOwner = leaseOwner;
                state.leaseUntil = now.plus(leaseDuration);
                result.add(state.entry);
            }
            return result;
        }

        @Override
        public synchronized void markPublished(String eventId, String leaseOwner,
                                                Instant publishedAt) {
            State state = ownedPublishing(eventId, leaseOwner);
            state.status = Status.PUBLISHED;
            state.publishedAt = publishedAt;
            clearLease(state);
        }

        @Override
        public synchronized void markRetry(String eventId, String leaseOwner,
                                           int retryCount, Instant failedAt,
                                           Instant nextAttemptAt,
                                           String errorCode, String errorSummary) {
            State state = ownedPublishing(eventId, leaseOwner);
            state.status = Status.PENDING;
            state.entry = state.entry.withRetryCount(retryCount);
            state.retryCount = retryCount;
            state.nextAttemptAt = nextAttemptAt;
            state.errorCode = errorCode;
            state.errorSummary = errorSummary;
            clearLease(state);
        }

        @Override
        public synchronized void markDeadLetter(String eventId, String leaseOwner,
                                                 Instant deadLetteredAt, String errorCode,
                                                 String errorSummary) {
            State state = ownedPublishing(eventId, leaseOwner);
            state.status = Status.DEAD_LETTER;
            state.deadLetteredAt = deadLetteredAt;
            state.errorCode = errorCode;
            state.errorSummary = errorSummary;
            clearLease(state);
        }

        private boolean claimable(State state, Instant now) {
            if (state.status == Status.PENDING) {
                return !state.nextAttemptAt.isAfter(now);
            }
            return state.status == Status.PUBLISHING
                    && (state.leaseUntil == null || !state.leaseUntil.isAfter(now));
        }

        private State ownedPublishing(String eventId, String leaseOwner) {
            State state = state(eventId);
            if (state.status != Status.PUBLISHING || !leaseOwner.equals(state.leaseOwner)) {
                throw new IllegalStateException("stale lease callback");
            }
            return state;
        }

        private void clearLease(State state) {
            state.leaseOwner = null;
            state.leaseUntil = null;
        }

        private synchronized State state(String eventId) {
            State state = states.get(eventId);
            if (state == null) {
                throw new AssertionError("missing state " + eventId);
            }
            return state;
        }

        private static final class State {
            private AlarmOutboxClaimedEntry entry;
            private Status status = Status.PENDING;
            private int retryCount;
            private Instant nextAttemptAt = NOW;
            private String leaseOwner;
            private Instant leaseUntil;
            private Instant publishedAt;
            private Instant deadLetteredAt;
            private String errorCode;
            private String errorSummary;

            private State(AlarmOutboxClaimedEntry entry) {
                this.entry = entry;
                this.retryCount = entry.retryCount();
            }
        }
    }
}
