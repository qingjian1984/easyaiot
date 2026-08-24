package com.basiclab.iot.device.alarm.domain;

import com.basiclab.iot.device.alarm.contract.AlarmSeverity;
import com.basiclab.iot.device.alarm.contract.AlarmStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-006 冻结状态转换矩阵。 */
class AlarmStateMachineTest {

    private static final OffsetDateTime IGNORE_UNTIL =
            OffsetDateTime.parse("2026-08-24T09:00:00+08:00");

    @Test
    void sourceRaisedCreatesActiveOccurrenceWhenAlarmDoesNotExist() {
        AlarmTransitionResult result = transition(null, AlarmAction.SOURCE_RAISED);

        assertTrue(result.allowed());
        assertNull(result.previousStatus());
        assertEquals(AlarmStatus.ACTIVE, result.targetStatus());
        assertTrue(result.stateChanged());
        assertTrue(result.occurrenceRecorded());
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class,
            names = {"ACTIVE", "ACKNOWLEDGED", "PROCESSING", "IGNORED"})
    void sourceRaisedKeepsEveryActiveCycleState(AlarmStatus status) {
        AlarmTransitionContext.Builder builder = AlarmTransitionContext.builder()
                .currentStatus(status)
                .cycleKey("cycle-1");
        if (status == AlarmStatus.IGNORED) {
            builder.ignoredFromStatus(AlarmStatus.ACTIVE);
        }
        AlarmTransitionResult result = AlarmStateMachine.transition(builder.build(),
                AlarmAction.SOURCE_RAISED);

        assertTrue(result.allowed());
        assertEquals(status, result.targetStatus());
        assertFalse(result.stateChanged());
        assertTrue(result.occurrenceRecorded());
    }

    @Test
    void normalLifecycleRequiresConfirmationAndRecoveryBeforeClose() {
        AlarmTransitionResult active = transition(null, AlarmAction.SOURCE_RAISED);
        AlarmTransitionResult acknowledged = transition(active.targetStatus(), AlarmAction.ACK);
        AlarmTransitionResult processing = transition(acknowledged.targetStatus(),
                AlarmAction.START_PROCESSING);
        AlarmTransitionResult recovered = transition(processing.targetStatus(),
                AlarmAction.SOURCE_RECOVERED);
        AlarmTransitionResult closed = transition(recovered.targetStatus(), AlarmAction.CLOSE);

        assertEquals(AlarmStatus.ACTIVE, active.targetStatus());
        assertEquals(AlarmStatus.ACKNOWLEDGED, acknowledged.targetStatus());
        assertEquals(AlarmStatus.PROCESSING, processing.targetStatus());
        assertEquals(AlarmStatus.RECOVERED, recovered.targetStatus());
        assertEquals(AlarmStatus.CLOSED, closed.targetStatus());
        assertTrue(closed.stateChanged());
    }

    @Test
    void invalidDirectProcessingAndCloseReturnStableCode() {
        assertEquals(AlarmErrorCode.ALARM_INVALID_TRANSITION.code(),
                transition(AlarmStatus.ACTIVE, AlarmAction.START_PROCESSING).errorCode());
        assertEquals(AlarmErrorCode.ALARM_INVALID_TRANSITION.code(),
                transition(AlarmStatus.ACTIVE, AlarmAction.CLOSE).errorCode());
        assertEquals(AlarmErrorCode.ALARM_INVALID_TRANSITION.code(),
                transition(AlarmStatus.PROCESSING, AlarmAction.IGNORE).errorCode());
    }

    @Test
    void ignoreRequiresOriginalStateReasonAndDeadline() {
        AlarmTransitionResult missingFacts = AlarmStateMachine.transition(
                AlarmTransitionContext.builder().currentStatus(AlarmStatus.ACTIVE).build(),
                AlarmAction.IGNORE);
        assertEquals(AlarmErrorCode.ALARM_IGNORED_FROM_STATUS_REQUIRED.code(),
                missingFacts.errorCode());

        AlarmTransitionResult missingReason = AlarmStateMachine.transition(
                AlarmTransitionContext.builder().currentStatus(AlarmStatus.ACKNOWLEDGED)
                        .ignoredFromStatus(AlarmStatus.ACKNOWLEDGED)
                        .ignoredUntil(IGNORE_UNTIL)
                        .build(), AlarmAction.IGNORE);
        assertEquals(AlarmErrorCode.ALARM_IGNORE_ARGUMENT_REQUIRED.code(),
                missingReason.errorCode());

        AlarmTransitionResult ignored = AlarmStateMachine.transition(
                AlarmTransitionContext.builder().currentStatus(AlarmStatus.ACKNOWLEDGED)
                        .ignoredFromStatus(AlarmStatus.ACKNOWLEDGED)
                        .reason("planned maintenance")
                        .ignoredUntil(IGNORE_UNTIL)
                        .build(), AlarmAction.IGNORE);
        assertTrue(ignored.allowed());
        assertEquals(AlarmStatus.IGNORED, ignored.targetStatus());
        assertEquals(AlarmStatus.ACKNOWLEDGED, ignored.ignoredFromStatus());
    }

    @Test
    void emergencyIgnoreIsForbidden() {
        AlarmTransitionResult result = AlarmStateMachine.transition(
                AlarmTransitionContext.builder().currentStatus(AlarmStatus.ACTIVE)
                        .severity(AlarmSeverity.EMERGENCY)
                        .ignoredFromStatus(AlarmStatus.ACTIVE)
                        .reason("reason")
                        .ignoredUntil(IGNORE_UNTIL)
                        .build(), AlarmAction.IGNORE);
        assertEquals(AlarmErrorCode.ALARM_EMERGENCY_IGNORE_FORBIDDEN.code(), result.errorCode());
        assertEquals(AlarmStatus.ACTIVE, result.targetStatus());
    }

    @Test
    void ignoreCanBeAcknowledgedOrRestoredOnlyToOriginalActiveState() {
        AlarmTransitionContext ignored = AlarmTransitionContext.builder()
                .currentStatus(AlarmStatus.IGNORED)
                .ignoredFromStatus(AlarmStatus.ACTIVE)
                .build();
        AlarmTransitionResult acknowledged = AlarmStateMachine.transition(ignored, AlarmAction.ACK);
        AlarmTransitionResult unignored = AlarmStateMachine.transition(ignored, AlarmAction.UNIGNORE);

        assertEquals(AlarmStatus.ACKNOWLEDGED, acknowledged.targetStatus());
        assertNull(acknowledged.ignoredFromStatus());
        assertEquals(AlarmStatus.ACTIVE, unignored.targetStatus());
        assertNull(unignored.ignoredFromStatus());
    }

    @Test
    void ignoreExpiryUsesSameRestoreRuleAndRecoveryClearsIgnoreFacts() {
        AlarmTransitionContext ignored = AlarmTransitionContext.builder()
                .currentStatus(AlarmStatus.IGNORED)
                .ignoredFromStatus(AlarmStatus.ACKNOWLEDGED)
                .build();

        AlarmTransitionResult expired = AlarmStateMachine.transition(ignored,
                AlarmAction.IGNORE_EXPIRED);
        AlarmTransitionResult recovered = AlarmStateMachine.transition(ignored,
                AlarmAction.SOURCE_RECOVERED);

        assertEquals(AlarmStatus.ACKNOWLEDGED, expired.targetStatus());
        assertNull(expired.ignoredFromStatus());
        assertEquals(AlarmStatus.RECOVERED, recovered.targetStatus());
        assertNull(recovered.ignoredFromStatus());
    }

    @Test
    void recoveredCannotReopenSameCycleAndDuplicateRecoveryIsNoOp() {
        AlarmTransitionContext recovered = AlarmTransitionContext.builder()
                .currentStatus(AlarmStatus.RECOVERED)
                .cycleKey("cycle-1")
                .build();
        AlarmTransitionResult raise = AlarmStateMachine.transition(recovered,
                AlarmAction.SOURCE_RAISED);
        AlarmTransitionResult repeatRecover = AlarmStateMachine.transition(recovered,
                AlarmAction.SOURCE_RECOVERED);

        assertEquals(AlarmErrorCode.ALARM_SOURCE_CYCLE_CONFLICT.code(), raise.errorCode());
        assertEquals(AlarmStatus.RECOVERED, repeatRecover.targetStatus());
        assertTrue(repeatRecover.allowed());
        assertTrue(repeatRecover.noOp());
    }

    @Test
    void falseAlarmProposalRequiresPendingReviewAndIndependentReviewer() {
        AlarmTransitionContext active = AlarmTransitionContext.builder()
                .currentStatus(AlarmStatus.ACTIVE)
                .falseAlarmProposerId("alice")
                .build();
        AlarmTransitionResult proposed = AlarmStateMachine.transition(active,
                AlarmAction.PROPOSE_FALSE_ALARM);
        assertTrue(proposed.allowed());
        assertTrue(proposed.falseAlarmReviewPending());
        assertEquals(AlarmStatus.ACTIVE, proposed.targetStatus());

        AlarmTransitionContext pendingSamePerson = active.toBuilder()
                .pendingFalseAlarmReview(true)
                .falseAlarmReviewerId("alice")
                .build();
        AlarmTransitionResult conflict = AlarmStateMachine.transition(pendingSamePerson,
                AlarmAction.APPROVE_FALSE_ALARM);
        assertEquals(AlarmErrorCode.ALARM_REVIEWER_CONFLICT.code(), conflict.errorCode());

        AlarmTransitionResult cannotOverrideSamePerson = AlarmStateMachine.transition(
                pendingSamePerson.toBuilder().reviewerIndependent(true).build(),
                AlarmAction.APPROVE_FALSE_ALARM);
        assertEquals(AlarmErrorCode.ALARM_REVIEWER_CONFLICT.code(),
                cannotOverrideSamePerson.errorCode());

        AlarmTransitionContext pendingIndependent = pendingSamePerson.toBuilder()
                .falseAlarmReviewerId("bob")
                .reviewerIndependent(true)
                .build();
        AlarmTransitionResult approved = AlarmStateMachine.transition(pendingIndependent,
                AlarmAction.APPROVE_FALSE_ALARM);
        assertEquals(AlarmStatus.FALSE_ALARM, approved.targetStatus());
        assertTrue(approved.stateChanged());
        assertFalse(approved.falseAlarmReviewPending());
    }

    @Test
    void independentReviewerCanRejectWithoutChangingMainState() {
        AlarmTransitionContext pending = AlarmTransitionContext.builder()
                .currentStatus(AlarmStatus.PROCESSING)
                .pendingFalseAlarmReview(true)
                .falseAlarmProposerId("alice")
                .falseAlarmReviewerId("bob")
                .reviewerIndependent(true)
                .build();

        AlarmTransitionResult rejected = AlarmStateMachine.transition(pending,
                AlarmAction.REJECT_FALSE_ALARM);

        assertTrue(rejected.allowed());
        assertEquals(AlarmStatus.PROCESSING, rejected.targetStatus());
        assertFalse(rejected.stateChanged());
        assertFalse(rejected.falseAlarmReviewPending());
    }

    @Test
    void duplicateFalseAlarmProposalAndMissingPendingReviewAreStable() {
        AlarmTransitionContext pending = AlarmTransitionContext.builder()
                .currentStatus(AlarmStatus.ACKNOWLEDGED)
                .pendingFalseAlarmReview(true)
                .build();
        AlarmTransitionResult duplicate = AlarmStateMachine.transition(pending,
                AlarmAction.PROPOSE_FALSE_ALARM);
        AlarmTransitionResult missing = AlarmStateMachine.transition(
                AlarmTransitionContext.forStatus(AlarmStatus.ACKNOWLEDGED),
                AlarmAction.APPROVE_FALSE_ALARM);

        assertEquals(AlarmErrorCode.ALARM_FALSE_ALARM_REVIEW_PENDING.code(), duplicate.errorCode());
        assertEquals(AlarmErrorCode.ALARM_FALSE_ALARM_REVIEW_REQUIRED.code(), missing.errorCode());
    }

    @Test
    void escalationDoesNotChangeMainStatusAndIgnoredCannotEscalate() {
        AlarmTransitionResult escalated = AlarmStateMachine.transition(
                AlarmTransitionContext.builder().currentStatus(AlarmStatus.PROCESSING)
                        .escalationLevel(2).build(), AlarmAction.ESCALATE);
        AlarmTransitionResult ignored = transition(AlarmStatus.IGNORED, AlarmAction.ESCALATE);

        assertEquals(AlarmStatus.PROCESSING, escalated.targetStatus());
        assertFalse(escalated.stateChanged());
        assertEquals(2, escalated.previousEscalationLevel());
        assertEquals(3, escalated.targetEscalationLevel());
        assertEquals(AlarmErrorCode.ALARM_INVALID_TRANSITION.code(), ignored.errorCode());
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"CLOSED", "FALSE_ALARM"})
    void terminalStatesRejectEveryAction(AlarmStatus terminal) {
        for (AlarmAction action : AlarmAction.values()) {
            AlarmTransitionResult result = transition(terminal, action);
            assertEquals(AlarmErrorCode.ALARM_INVALID_TRANSITION.code(), result.errorCode(),
                    terminal + " must reject " + action);
            assertEquals(terminal, result.targetStatus());
        }
    }

    @Test
    void nullActionDoesNotLeakNullPointerException() {
        AlarmTransitionResult result = AlarmStateMachine.transition(
                AlarmTransitionContext.forStatus(AlarmStatus.ACTIVE), null);
        assertEquals(AlarmErrorCode.ALARM_INVALID_TRANSITION.code(), result.errorCode());
    }

    private static AlarmTransitionResult transition(AlarmStatus status, AlarmAction action) {
        return AlarmStateMachine.transition(AlarmTransitionContext.forStatus(status), action);
    }
}
