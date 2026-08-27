package com.basiclab.iot.sink.telemetry.ack;

import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AckResultCode;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AckCommandContractTest {

    private static final String MESSAGE = "2ca80f25-4b6c-443f-a114-1b3df0a8cdf9";
    private static final String REQUEST = "a9afddc7-02ee-4df3-905b-ec3e4107f25d";
    private static final TelemetryRoute ROUTE = new TelemetryRoute("power-meter", "meter-01");

    @Test
    void commandCarriesTheV1FieldsAndLocalRouteFacts() {
        AckCommand command = new AckCommand("1.0", MESSAGE, REQUEST, ROUTE,
                TelemetryAckStatus.ACCEPTED_DURABLE, 0, "OK", 123L, 456L);
        assertEquals("1.0", command.schemaVersion());
        assertEquals(MESSAGE, command.messageId());
        assertEquals(REQUEST, command.requestId());
        assertEquals(ROUTE, command.route());
        assertEquals(TelemetryAckStatus.ACCEPTED_DURABLE, command.status());
        assertEquals(0, command.code());
        assertEquals("OK", command.reasonCode());
        assertEquals(123L, command.persistedAtMs());
        assertEquals(456L, command.observedAtMs());
        assertEquals(AckResultCode.ACCEPTED_DURABLE, command.resultCode());
        assertEquals("OK", command.errorCode());
        assertEquals(456L, command.observedAt());
    }

    @Test
    void commandCanBeCreatedFromDecodedV1() {
        TelemetryAckV1 ack = new TelemetryAckV1("1.0", MESSAGE, REQUEST,
                TelemetryAckStatus.DUPLICATE, 1001, "DUPLICATE", 789L);
        AckCommand command = new AckCommand(ack, ROUTE, 999L);
        assertEquals(ack.messageId(), command.messageId());
        assertEquals(ack.requestId(), command.requestId());
        assertEquals(ack.persistedAtMs(), command.persistedAtMs());
        assertEquals(TelemetryAckStatus.DUPLICATE, command.status());
        assertEquals(999L, command.observedAtMs());
    }

    @Test
    void v1CommandRejectsMissingRoute() {
        TelemetryAckV1 ack = new TelemetryAckV1("1.0", MESSAGE, REQUEST,
                TelemetryAckStatus.ACCEPTED_DURABLE, 0, "OK", 789L);
        assertThrows(IllegalArgumentException.class, () -> new AckCommand(ack, null, 999L));
    }

    @Test
    void successTriplesAreValidatedWhenRouteIsPresent() {
        assertThrows(IllegalArgumentException.class, () -> new AckCommand(
                "1.0", MESSAGE, REQUEST, ROUTE, TelemetryAckStatus.ACCEPTED_DURABLE,
                1001, "DUPLICATE", 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new AckCommand(
                "1.0", MESSAGE, REQUEST, ROUTE, TelemetryAckStatus.DUPLICATE,
                0, "OK", 0L, 0L));
    }

    @Test
    @SuppressWarnings("deprecation")
    void oldConstructorIsOnlyAnInMemoryCompatibilityView() {
        AckCommand command = new AckCommand("legacy-message", AckResultCode.REJECTED_RETRYABLE,
                "CENTER_BUSY", 321L);
        assertEquals("legacy-message", command.messageId());
        assertEquals(TelemetryAckStatus.REJECTED_RETRYABLE, command.status());
        assertEquals(AckResultCode.REJECTED_RETRYABLE, command.resultCode());
        assertEquals("CENTER_BUSY", command.errorCode());
        assertEquals(321L, command.observedAt());
    }
}
