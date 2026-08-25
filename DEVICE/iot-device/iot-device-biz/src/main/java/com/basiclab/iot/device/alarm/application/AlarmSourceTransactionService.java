package com.basiclab.iot.device.alarm.application;

import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.ActionEntry;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.AlarmSnapshot;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.InboxEntry;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.SourceMapping;
import com.basiclab.iot.device.alarm.domain.AlarmAction;
import com.basiclab.iot.device.alarm.domain.AlarmStateMachine;
import com.basiclab.iot.device.alarm.domain.AlarmTransitionResult;
import com.basiclab.iot.device.alarm.infrastructure.event.AlarmInboxArbiter;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * 来源告警的唯一事务用例。该类故意不声明 Component：本任务不启用 capability，
 * 后续 transport 装配必须把本类作为独立 Spring 代理 Bean 调用，禁止 self-invocation。
 * REQUIRES_NEW 保证调用方取得结果前本用例事务已经提交或抛错回滚。
 */
public class AlarmSourceTransactionService {

    private static final int CURRENT_MAJOR = 1;

    private final AlarmSourcePersistencePort persistence;
    private final AlarmIdGenerator ids;
    private final AlarmOutboxEventFactory events;

    public AlarmSourceTransactionService(AlarmSourcePersistencePort persistence,
                                         AlarmIdGenerator ids,
                                         AlarmOutboxEventFactory events) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * 仅允许由 Spring 事务代理进入。返回 PROCESSED/DUPLICATE/QUARANTINED 时，
     * 代理已经提交对应 Inbox 裁决，transport 才能据此 ACK。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AlarmSourceResult process(AlarmSourceCommand command) {
        requireActiveTransaction();
        Objects.requireNonNull(command, "command");

        AlarmInboxArbiter.Decision decision = arbitrate(command);
        switch (decision) {
            case DUPLICATE:
                SourceMapping duplicateMapping = mapping(command);
                return AlarmSourceResult.duplicate(
                        duplicateMapping == null ? null : duplicateMapping.alarmId());
            case QUARANTINE_HASH_CONFLICT:
                return quarantine(command, "ALARM_SOURCE_HASH_CONFLICT");
            case REJECT_UNKNOWN_MAJOR:
                return quarantine(command, "ALARM_SOURCE_UNKNOWN_MAJOR");
            case REJECT_FINAL:
                return quarantine(command, "ALARM_SOURCE_CONTRACT_INVALID");
            case PROCESS:
                break;
            default:
                throw new IllegalStateException("ALARM_INBOX_DECISION_UNSUPPORTED");
        }

        return command.action() == AlarmSourceCommand.Action.RAISED
                ? processRaised(command) : processRecovered(command);
    }

    private AlarmInboxArbiter.Decision arbitrate(AlarmSourceCommand command) {
        InboxEntry existing = persistence.findInbox(command.messageId());
        AlarmInboxArbiter.Decision decision = AlarmInboxArbiter.decide(
                toExisting(existing), CURRENT_MAJOR, command.envelopeHash(), false);
        if (decision != AlarmInboxArbiter.Decision.PROCESS || existing != null) {
            return decision;
        }
        if (persistence.insertInboxReceived(ids.nextLongId(), command)) {
            return AlarmInboxArbiter.Decision.PROCESS;
        }
        // 唯一键争抢失败必须重读，不能把另一副本的结果当作本副本成功。
        InboxEntry raced = persistence.findInbox(command.messageId());
        if (raced == null) throw new IllegalStateException("ALARM_INBOX_RACE_LOST_WITHOUT_ROW");
        return AlarmInboxArbiter.decide(
                toExisting(raced), CURRENT_MAJOR, command.envelopeHash(), false);
    }

    private AlarmSourceResult processRaised(AlarmSourceCommand command) {
        SourceMapping sourceMapping = mapping(command);
        AlarmSnapshot alarm;
        boolean created = false;
        AlarmTransitionResult transition = null;

        if (sourceMapping == null) {
            alarm = persistence.findAlarmByCycleHash(command.tenantId(),
                    command.cycleIdentityHash());
            if (alarm == null) {
                AlarmSnapshot candidate = AlarmSnapshot.initial(ids.nextLongId(), command,
                        command.sourceTimezone(), command.sourceOffset());
                created = persistence.insertAlarmIfAbsent(candidate);
                alarm = created ? candidate : persistence.findAlarmByCycleHash(
                        command.tenantId(), command.cycleIdentityHash());
                if (alarm == null) {
                    throw new IllegalStateException("ALARM_CYCLE_RACE_LOST_WITHOUT_ROW");
                }
            }
            if (!sameCycle(command, alarm)) {
                return quarantine(command, "ALARM_CYCLE_HASH_COLLISION");
            }
            if (!created) {
                // 已恢复周期不得因为新来源映射而被静默重开；先裁决再追加映射。
                transition = AlarmStateMachine.transition(
                        alarm.status(), AlarmAction.SOURCE_RAISED);
                if (transition.rejected()) {
                    return quarantine(command, transition.errorCode());
                }
            }

            boolean mapped = persistence.insertMappingIfAbsent(ids.nextLongId(),
                    command.tenantId(), alarm.id(), command.sourceType(), command.sourceId(),
                    command.cycleKey(), command.sourcePayloadHash(), command.recordedAt());
            if (!mapped) {
                SourceMapping winner = mapping(command);
                if (winner == null || winner.alarmId() != alarm.id()) {
                    return quarantine(command, "ALARM_SOURCE_MAPPING_CONFLICT");
                }
            }
        } else {
            alarm = scopedAlarm(command, sourceMapping.alarmId());
        }

        if (created) {
            long sequence = persistence.allocateNextActionSequence(
                    command.tenantId(), command.siteId(), alarm.id());
            persistence.appendAction(action(ids.nextLongId(), alarm, command, sequence,
                    "SOURCE_RAISED", null, alarm.status()));
            persistence.enqueue(events.created(alarm, command));
            persistence.markInboxProcessed(command.messageId(), command.recordedAt());
            return AlarmSourceResult.processed(alarm.id());
        }

        if (transition == null) {
            transition = AlarmStateMachine.transition(alarm.status(), AlarmAction.SOURCE_RAISED);
        }
        if (transition.rejected()) {
            return quarantine(command, transition.errorCode());
        }
        if (!persistence.recordRaisedCas(command.tenantId(), command.siteId(), alarm.id(),
                alarm.rowVersion(), command.occurredAt(), command.recordedAt(), command.actorId())) {
            throw new IllegalStateException("ALARM_RECORD_CAS_CONFLICT");
        }
        long sequence = persistence.allocateNextActionSequence(
                command.tenantId(), command.siteId(), alarm.id());
        persistence.appendAction(action(ids.nextLongId(), alarm, command,
                sequence, "SOURCE_RAISED", alarm.status(), alarm.status()));
        persistence.enqueue(events.occurrence(alarm, command));
        persistence.markInboxProcessed(command.messageId(), command.recordedAt());
        return AlarmSourceResult.processed(alarm.id());
    }

    private AlarmSourceResult processRecovered(AlarmSourceCommand command) {
        SourceMapping sourceMapping = mapping(command);
        if (sourceMapping == null) {
            return quarantine(command, "ALARM_SOURCE_MAPPING_NOT_FOUND");
        }
        AlarmSnapshot alarm = scopedAlarm(command, sourceMapping.alarmId());
        if (!sameCycle(command, alarm)) {
            return quarantine(command, "ALARM_SOURCE_CYCLE_CONFLICT");
        }
        AlarmTransitionResult transition = AlarmStateMachine.transition(
                alarm.status(), AlarmAction.SOURCE_RECOVERED);
        if (transition.rejected()) {
            return quarantine(command, transition.errorCode());
        }
        if (transition.noOp()) {
            persistence.markInboxProcessed(command.messageId(), command.recordedAt());
            return AlarmSourceResult.processed(alarm.id());
        }
        if (!persistence.recordRecoveredCas(command.tenantId(), command.siteId(), alarm.id(),
                alarm.rowVersion(), command.occurredAt(), command.recordedAt(), command.actorId())) {
            throw new IllegalStateException("ALARM_RECORD_CAS_CONFLICT");
        }
        long sequence = persistence.allocateNextActionSequence(
                command.tenantId(), command.siteId(), alarm.id());
        persistence.appendAction(action(ids.nextLongId(), alarm, command,
                sequence, "SOURCE_RECOVERED", alarm.status(),
                transition.targetStatus()));
        persistence.enqueue(events.recovered(alarm, command));
        persistence.markInboxProcessed(command.messageId(), command.recordedAt());
        return AlarmSourceResult.processed(alarm.id());
    }

    private AlarmSnapshot scopedAlarm(AlarmSourceCommand command, long alarmId) {
        AlarmSnapshot alarm = persistence.findAlarm(command.tenantId(), command.siteId(), alarmId);
        if (alarm == null) throw new IllegalStateException("ALARM_SCOPE_MISMATCH");
        return alarm;
    }

    private SourceMapping mapping(AlarmSourceCommand command) {
        return persistence.findMapping(command.tenantId(), command.sourceType(),
                command.sourceId(), command.cycleKey());
    }

    private AlarmSourceResult quarantine(AlarmSourceCommand command, String errorCode) {
        // 摘要只包含稳定码；绝不拼接 payload、异常消息、手机号、凭据或媒体 URL。
        String safeCode = errorCode == null ? "ALARM_SOURCE_REJECTED" : errorCode;
        persistence.quarantineInbox(ids.nextLongId(), command, safeCode,
                safeCode, command.recordedAt());
        return AlarmSourceResult.quarantined(safeCode);
    }

    private static boolean sameCycle(AlarmSourceCommand command, AlarmSnapshot alarm) {
        return command.tenantId() == alarm.tenantId()
                && command.siteId() == alarm.siteId()
                && command.cycleIdentityHash().equals(alarm.cycleIdentityHash())
                && command.cycleIdentity().equals(alarm.cycleIdentity());
    }

    private static ActionEntry action(long id, AlarmSnapshot alarm,
                                      AlarmSourceCommand command, long sequence,
                                      String type, com.basiclab.iot.device.alarm.contract.AlarmStatus from,
                                      com.basiclab.iot.device.alarm.contract.AlarmStatus to) {
        return new ActionEntry(id, command.tenantId(), alarm.id(), sequence, type,
                from, to, command.actorId(), command.messageId(), command.traceId(),
                command.occurredAt(), command.recordedAt());
    }

    private static AlarmInboxArbiter.Existing toExisting(InboxEntry entry) {
        if (entry == null) return null;
        return new AlarmInboxArbiter.Existing(entry.envelopeHash(),
                AlarmInboxArbiter.Status.valueOf(entry.status().name()));
    }

    private static void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("ALARM_TRANSACTION_REQUIRED");
        }
    }
}
