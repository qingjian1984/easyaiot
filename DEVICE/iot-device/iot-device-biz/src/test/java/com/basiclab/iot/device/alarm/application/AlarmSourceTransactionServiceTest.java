package com.basiclab.iot.device.alarm.application;

import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.ActionEntry;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.AlarmSnapshot;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.InboxEntry;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.InboxStatus;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.OutboxEntry;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.SourceMapping;
import com.basiclab.iot.device.alarm.contract.AlarmSeverity;
import com.basiclab.iot.device.alarm.contract.AlarmStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlarmSourceTransactionServiceTest {

    private static final String H1 = "sha256:" + "1".repeat(64);
    private static final String H2 = "sha256:" + "2".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-25T04:00:00Z");

    @Test
    void directInvocationWithoutSpringTransactionFailsClosed() {
        Fixture f = fixture();
        assertThrows(IllegalStateException.class, () -> f.target.process(command(
                "00000000-0000-0000-0000-000000000001", AlarmSourceCommand.Action.RAISED, H1)));
        assertTrue(f.store.inbox.isEmpty());
    }

    @Test
    void firstRaiseCommitsInboxMappingRecordActionAndOutboxAtomically() {
        Fixture f = fixture();
        AlarmSourceResult result = f.proxy.process(command(
                "00000000-0000-0000-0000-000000000001", AlarmSourceCommand.Action.RAISED, H1));

        assertEquals(AlarmSourceResult.Outcome.PROCESSED, result.outcome());
        assertEquals(InboxStatus.PROCESSED, f.store.inbox.values().iterator().next().status());
        assertEquals(1, f.store.alarms.size());
        assertEquals(1, f.store.mappings.size());
        assertEquals(1, f.store.actions.size());
        assertEquals(1, f.store.outbox.size());
        assertEquals("device.alarm.created.v1", f.store.outbox.get(0).eventType());
    }

    @Test
    void anyFailureRollsBackAllFacts() {
        Fixture f = fixture();
        f.store.failOnEnqueue = true;

        assertThrows(IllegalStateException.class, () -> f.proxy.process(command(
                "00000000-0000-0000-0000-000000000001", AlarmSourceCommand.Action.RAISED, H1)));

        assertTrue(f.store.inbox.isEmpty());
        assertTrue(f.store.alarms.isEmpty());
        assertTrue(f.store.mappings.isEmpty());
        assertTrue(f.store.actions.isEmpty());
        assertTrue(f.store.outbox.isEmpty());
    }

    @Test
    void sameMessageAndHashIsDuplicateWithoutSecondDomainMutation() {
        Fixture f = fixture();
        AlarmSourceCommand first = command("00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1);
        f.proxy.process(first);
        AlarmSourceResult replay = f.proxy.process(first);

        assertEquals(AlarmSourceResult.Outcome.DUPLICATE, replay.outcome());
        assertEquals(1, f.store.actions.size());
        assertEquals(1, f.store.outbox.size());
        assertEquals(1, f.store.alarms.values().iterator().next().occurrenceCount());
    }

    @Test
    void sameHashReceivedAfterCrashIsRetriedInsteadOfAcknowledgedAsDuplicate() {
        Fixture f = fixture();
        AlarmSourceCommand command = command("00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1);
        f.store.inbox.put(command.messageId(), new InboxEntry(H1, InboxStatus.RECEIVED));

        AlarmSourceResult result = f.proxy.process(command);

        assertEquals(AlarmSourceResult.Outcome.PROCESSED, result.outcome());
        assertEquals(InboxStatus.PROCESSED, f.store.inbox.get(command.messageId()).status());
        assertEquals(1, f.store.alarms.size());
        assertEquals(1, f.store.outbox.size());
    }

    @Test
    void repeatedRaiseForSameCycleKeepsOneAlarmAndRecordsOccurrence() {
        Fixture f = fixture();
        f.proxy.process(command("00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1));
        f.proxy.process(command("00000000-0000-0000-0000-000000000002",
                AlarmSourceCommand.Action.RAISED, H2));

        assertEquals(1, f.store.alarms.size());
        AlarmSnapshot alarm = f.store.alarms.values().iterator().next();
        assertEquals(2, alarm.occurrenceCount());
        assertEquals(1, alarm.rowVersion());
        assertEquals(2, f.store.actions.size());
        assertEquals("device.alarm.occurrence-recorded.v1", f.store.outbox.get(1).eventType());
    }

    @Test
    void firstRaiseLosingCycleUniqueRaceReusesWinnerAlarmId() {
        Fixture f = fixture();
        f.store.simulateCycleRace = true;

        AlarmSourceResult result = f.proxy.process(command(
                "00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1));

        assertEquals(AlarmSourceResult.Outcome.PROCESSED, result.outcome());
        assertEquals(999L, result.alarmId());
        assertEquals(1, f.store.alarms.size());
        assertEquals(999L, f.store.mappings.values().iterator().next().alarmId());
        assertEquals(2, f.store.alarms.get(999L).occurrenceCount());
    }

    @Test
    void sameMessageDifferentHashIsQuarantinedAndAlarmDoesNotChange() {
        Fixture f = fixture();
        String messageId = "00000000-0000-0000-0000-000000000001";
        f.proxy.process(command(messageId, AlarmSourceCommand.Action.RAISED, H1));
        AlarmSnapshot before = f.store.alarms.values().iterator().next();

        AlarmSourceResult conflict = f.proxy.process(command(messageId,
                AlarmSourceCommand.Action.RAISED, H2));

        assertEquals(AlarmSourceResult.Outcome.QUARANTINED, conflict.outcome());
        assertEquals("ALARM_SOURCE_HASH_CONFLICT", conflict.errorCode());
        assertEquals(InboxStatus.QUARANTINED, f.store.inbox.get(messageId).status());
        assertEquals(before.rowVersion(), f.store.alarms.get(before.id()).rowVersion());
        assertEquals(1, f.store.actions.size());
        assertEquals(1, f.store.outbox.size());
    }

    @Test
    void recoveryUsesTenantSiteAndExpectedVersionCasThenDuplicateRecoveryIsNoOp() {
        Fixture f = fixture();
        f.proxy.process(command("00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1));
        AlarmSourceResult recovered = f.proxy.process(command(
                "00000000-0000-0000-0000-000000000002",
                AlarmSourceCommand.Action.RECOVERED, H2));
        AlarmSourceResult repeated = f.proxy.process(command(
                "00000000-0000-0000-0000-000000000003",
                AlarmSourceCommand.Action.RECOVERED, "sha256:" + "3".repeat(64)));

        assertEquals(AlarmSourceResult.Outcome.PROCESSED, recovered.outcome());
        assertEquals(AlarmSourceResult.Outcome.PROCESSED, repeated.outcome());
        AlarmSnapshot alarm = f.store.alarms.values().iterator().next();
        assertEquals(AlarmStatus.RECOVERED, alarm.status());
        assertEquals(1, alarm.rowVersion());
        assertEquals(2, f.store.actions.size());
        assertEquals(2, f.store.outbox.size());
    }

    @Test
    void cycleHashCollisionComparesFullIdentityAndQuarantinesWithoutAlarmMutation() {
        Fixture f = fixture();
        f.proxy.process(command("00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1));
        AlarmSnapshot before = f.store.alarms.values().iterator().next();

        AlarmSourceResult collision = f.proxy.process(command(
                "00000000-0000-0000-0000-000000000002",
                AlarmSourceCommand.Action.RAISED, H2, "source-2", "cycle-2",
                "different-full-cycle-identity"));

        assertEquals(AlarmSourceResult.Outcome.QUARANTINED, collision.outcome());
        assertEquals("ALARM_CYCLE_HASH_COLLISION", collision.errorCode());
        assertEquals(before.rowVersion(), f.store.alarms.get(before.id()).rowVersion());
        assertEquals(1, f.store.mappings.size());
        assertEquals(1, f.store.actions.size());
        assertEquals(1, f.store.outbox.size());
    }

    @Test
    void recoveredCycleCannotBeRaisedAgain() {
        Fixture f = fixture();
        f.proxy.process(command("00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1));
        f.proxy.process(command("00000000-0000-0000-0000-000000000002",
                AlarmSourceCommand.Action.RECOVERED, H2));

        AlarmSourceResult conflict = f.proxy.process(command(
                "00000000-0000-0000-0000-000000000003",
                AlarmSourceCommand.Action.RAISED, "sha256:" + "3".repeat(64)));

        assertEquals(AlarmSourceResult.Outcome.QUARANTINED, conflict.outcome());
        assertEquals("ALARM_SOURCE_CYCLE_CONFLICT", conflict.errorCode());
        assertEquals(AlarmStatus.RECOVERED, f.store.alarms.values().iterator().next().status());
        assertEquals(2, f.store.actions.size());
        assertEquals(2, f.store.outbox.size());
    }

    @Test
    void expectedVersionCasConflictRollsBackIncomingInbox() {
        Fixture f = fixture();
        f.proxy.process(command("00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1));
        f.store.failCas = true;

        assertThrows(IllegalStateException.class, () -> f.proxy.process(command(
                "00000000-0000-0000-0000-000000000002",
                AlarmSourceCommand.Action.RAISED, H2)));

        assertFalse(f.store.inbox.containsKey("00000000-0000-0000-0000-000000000002"));
        assertEquals(1, f.store.actions.size());
        assertEquals(1, f.store.outbox.size());
    }

    @Test
    void crossSiteMappingFailsClosedAndRollsBackIncomingInbox() {
        Fixture f = fixture();
        f.proxy.process(command("00000000-0000-0000-0000-000000000001",
                AlarmSourceCommand.Action.RAISED, H1));
        AlarmSnapshot original = f.store.alarms.values().iterator().next();
        f.store.alarms.put(original.id(), new AlarmSnapshot(original.id(), original.tenantId(), 21,
                original.sourceType(), original.sourceId(), original.cycleKey(),
                original.cycleIdentity(), original.cycleIdentityHash(), original.sourceObjectId(),
                original.deviceIdentification(), original.propertyCode(), original.ruleId(),
                original.ruleVersionId(), original.ruleVersion(), original.severity(), original.status(),
                original.rowVersion(), original.occurrenceCount(), original.escalationLevel(),
                original.firstOccurredAt(), original.lastOccurredAt(), original.recoveredAt(),
                original.sourceTimezone(), original.sourceOffset(), original.actorId(), original.createdAt()));

        assertThrows(IllegalStateException.class, () -> f.proxy.process(command(
                "00000000-0000-0000-0000-000000000002",
                AlarmSourceCommand.Action.RECOVERED, H2)));
        assertFalse(f.store.inbox.containsKey("00000000-0000-0000-0000-000000000002"));
    }

    private static Fixture fixture() {
        FakeStore store = new FakeStore();
        SequenceIds ids = new SequenceIds();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AlarmOutboxEventFactory events = new AlarmOutboxEventFactory(mapper, ids, 5);
        AlarmSourceTransactionService target = new AlarmSourceTransactionService(store, ids, events);
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new FakeTransactionManager(),
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        return new Fixture(store, target, (AlarmSourceTransactionService) factory.getProxy());
    }

    private static AlarmSourceCommand command(String messageId, AlarmSourceCommand.Action action,
                                              String envelopeHash) {
        return command(messageId, action, envelopeHash, "source-1", "cycle-1",
                "10|20|DEVICE_EVENT|source-1|cycle-1");
    }

    private static AlarmSourceCommand command(String messageId, AlarmSourceCommand.Action action,
                                              String envelopeHash, String sourceId,
                                              String cycleKey, String cycleIdentity) {
        return new AlarmSourceCommand(messageId, 10, 20, "DEVICE_EVENT", action,
                sourceId, cycleKey, cycleIdentity,
                H1, "object-1", "device-1", null, null, null, null,
                AlarmSeverity.NORMAL, NOW, NOW.plusSeconds(1), H2, envelopeHash,
                "{\"safe\":true}", "Asia/Shanghai", "+08:00",
                "corr-1", "trace-1", "source-service");
    }

    private record Fixture(FakeStore store, AlarmSourceTransactionService target,
                           AlarmSourceTransactionService proxy) { }

    private static final class SequenceIds implements AlarmIdGenerator {
        private final AtomicLong values = new AtomicLong(100);
        @Override public long nextLongId() { return values.incrementAndGet(); }
        @Override public String nextEventId() {
            return new UUID(0, values.incrementAndGet()).toString();
        }
    }

    private static final class FakeTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }

    private static final class FakeStore implements AlarmSourcePersistencePort {
        private Map<String, InboxEntry> inbox = new LinkedHashMap<>();
        private Map<String, SourceMapping> mappings = new LinkedHashMap<>();
        private Map<Long, AlarmSnapshot> alarms = new LinkedHashMap<>();
        private List<ActionEntry> actions = new ArrayList<>();
        private List<OutboxEntry> outbox = new ArrayList<>();
        private boolean snapshotRegistered;
        private boolean failOnEnqueue;
        private boolean failCas;
        private boolean simulateCycleRace;

        @Override public InboxEntry findInbox(String messageId) { return inbox.get(messageId); }
        @Override public boolean insertInboxReceived(long id, AlarmSourceCommand c) {
            beforeMutation();
            if (inbox.containsKey(c.messageId())) return false;
            inbox.put(c.messageId(), new InboxEntry(c.envelopeHash(), InboxStatus.RECEIVED));
            return true;
        }
        @Override public void markInboxProcessed(String messageId, Instant at) {
            beforeMutation();
            InboxEntry current = inbox.get(messageId);
            if (current == null || current.status() != InboxStatus.RECEIVED) throw new IllegalStateException();
            inbox.put(messageId, new InboxEntry(current.envelopeHash(), InboxStatus.PROCESSED));
        }
        @Override public void quarantineInbox(long id, AlarmSourceCommand c, String code,
                                              String summary, Instant at) {
            beforeMutation();
            InboxEntry current = inbox.get(c.messageId());
            String retained = current == null ? c.envelopeHash() : current.envelopeHash();
            inbox.put(c.messageId(), new InboxEntry(retained, InboxStatus.QUARANTINED));
        }
        @Override public SourceMapping findMapping(long tenantId, String sourceType,
                                                   String sourceId, String cycleKey) {
            return mappings.get(mapKey(tenantId, sourceType, sourceId, cycleKey));
        }
        @Override public boolean insertMappingIfAbsent(long id, long tenantId, long alarmId,
                                                       String sourceType, String sourceId,
                                                       String cycleKey, String sourcePayloadHash,
                                                       Instant createdAt) {
            beforeMutation();
            String key = mapKey(tenantId, sourceType, sourceId, cycleKey);
            if (mappings.containsKey(key)) return false;
            mappings.put(key, new SourceMapping(alarmId, sourcePayloadHash));
            return true;
        }
        @Override public AlarmSnapshot findAlarm(long tenantId, long siteId, long alarmId) {
            AlarmSnapshot a = alarms.get(alarmId);
            return a != null && a.tenantId() == tenantId && a.siteId() == siteId ? a : null;
        }
        @Override public AlarmSnapshot findAlarmByCycleHash(long tenantId, String hash) {
            return alarms.values().stream().filter(a -> a.tenantId() == tenantId
                    && hash.equals(a.cycleIdentityHash())).findFirst().orElse(null);
        }
        @Override public boolean insertAlarmIfAbsent(AlarmSnapshot alarm) {
            beforeMutation();
            if (findAlarmByCycleHash(alarm.tenantId(), alarm.cycleIdentityHash()) != null) return false;
            if (simulateCycleRace) {
                simulateCycleRace = false;
                alarms.put(999L, withId(alarm, 999L));
                return false;
            }
            alarms.put(alarm.id(), alarm);
            return true;
        }
        @Override public boolean recordRaisedCas(long tenantId, long siteId, long alarmId,
                                                 long version, Instant occurredAt,
                                                 Instant recordedAt, String actorId) {
            AlarmSnapshot a = findAlarm(tenantId, siteId, alarmId);
            if (failCas || a == null || a.rowVersion() != version || a.status() == AlarmStatus.RECOVERED) return false;
            beforeMutation();
            alarms.put(alarmId, copy(a, a.status(), version + 1, a.occurrenceCount() + 1, null));
            return true;
        }
        @Override public boolean recordRecoveredCas(long tenantId, long siteId, long alarmId,
                                                    long version, Instant recoveredAt,
                                                    Instant recordedAt, String actorId) {
            AlarmSnapshot a = findAlarm(tenantId, siteId, alarmId);
            if (failCas || a == null || a.rowVersion() != version || a.status() == AlarmStatus.RECOVERED) return false;
            beforeMutation();
            alarms.put(alarmId, copy(a, AlarmStatus.RECOVERED, version + 1,
                    a.occurrenceCount(), recoveredAt));
            return true;
        }
        @Override public void appendAction(ActionEntry action) { beforeMutation(); actions.add(action); }
        @Override public void enqueue(OutboxEntry event) {
            beforeMutation(); outbox.add(event);
            if (failOnEnqueue) throw new IllegalStateException("fake outbox failure");
        }

        private void beforeMutation() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            if (snapshotRegistered) return;
            snapshotRegistered = true;
            Map<String, InboxEntry> oldInbox = new LinkedHashMap<>(inbox);
            Map<String, SourceMapping> oldMappings = new LinkedHashMap<>(mappings);
            Map<Long, AlarmSnapshot> oldAlarms = new LinkedHashMap<>(alarms);
            List<ActionEntry> oldActions = new ArrayList<>(actions);
            List<OutboxEntry> oldOutbox = new ArrayList<>(outbox);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        inbox = oldInbox; mappings = oldMappings; alarms = oldAlarms;
                        actions = oldActions; outbox = oldOutbox;
                    }
                    snapshotRegistered = false;
                }
            });
        }

        private static String mapKey(long tenantId, String type, String source, String cycle) {
            return tenantId + "|" + type + "|" + source + "|" + cycle;
        }
        private static AlarmSnapshot copy(AlarmSnapshot a, AlarmStatus status, long version,
                                          long occurrences, Instant recoveredAt) {
            return new AlarmSnapshot(a.id(), a.tenantId(), a.siteId(), a.sourceType(), a.sourceId(),
                    a.cycleKey(), a.cycleIdentity(), a.cycleIdentityHash(), a.sourceObjectId(),
                    a.deviceIdentification(), a.propertyCode(), a.ruleId(), a.ruleVersionId(),
                    a.ruleVersion(), a.severity(), status, version, occurrences,
                    a.escalationLevel(), a.firstOccurredAt(), a.lastOccurredAt(), recoveredAt,
                    a.sourceTimezone(), a.sourceOffset(), a.actorId(), a.createdAt());
        }
        private static AlarmSnapshot withId(AlarmSnapshot a, long id) {
            return new AlarmSnapshot(id, a.tenantId(), a.siteId(), a.sourceType(), a.sourceId(),
                    a.cycleKey(), a.cycleIdentity(), a.cycleIdentityHash(), a.sourceObjectId(),
                    a.deviceIdentification(), a.propertyCode(), a.ruleId(), a.ruleVersionId(),
                    a.ruleVersion(), a.severity(), a.status(), a.rowVersion(), a.occurrenceCount(),
                    a.escalationLevel(), a.firstOccurredAt(), a.lastOccurredAt(), a.recoveredAt(),
                    a.sourceTimezone(), a.sourceOffset(), a.actorId(), a.createdAt());
        }
    }
}
