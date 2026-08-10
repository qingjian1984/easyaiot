package com.basiclab.iot.device.config;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry.PowerModelEventHandler;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerModelEventHandlerActivationGuardTest {

    private static final PowerModelEventHandler NOOP = (envelope, dataJson) -> { };

    @Test
    void completeV1HandlerSetIsAllowed() {
        assertDoesNotThrow(() -> PowerModelEventHandlerActivationGuard.verify(
                registryWithAllRequiredHandlers()));
    }

    @Test
    void emptyRegistryFailsClosed() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> PowerModelEventHandlerActivationGuard.verify(new PowerModelEventHandlerRegistry(
                        Collections.<String, PowerModelEventHandler>emptyMap())));

        assertTrue(failure.getMessage().startsWith("POWER_MODEL_EVENT_HANDLERS_INCOMPLETE"));
        assertTrue(failure.getMessage().contains(PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1));
    }

    @Test
    void partialRegistryReportsMissingHandlerDeterministically() {
        Map<String, PowerModelEventHandler> handlers = allRequiredHandlers();
        handlers.remove(PowerModelEventEnvelope.EVENT_BINDING_ROLLED_BACK_V1);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> PowerModelEventHandlerActivationGuard.verify(
                        new PowerModelEventHandlerRegistry(handlers)));

        assertTrue(failure.getMessage().contains("missing=["
                + PowerModelEventEnvelope.EVENT_BINDING_ROLLED_BACK_V1 + "]"));
    }

    @Test
    void additionalHandlerDoesNotBlockForwardCompatibleDeployment() {
        Map<String, PowerModelEventHandler> handlers = allRequiredHandlers();
        handlers.put("POWER_MODEL_FUTURE_EVENT_V1", NOOP);

        assertDoesNotThrow(() -> PowerModelEventHandlerActivationGuard.verify(
                new PowerModelEventHandlerRegistry(handlers)));
    }

    private static PowerModelEventHandlerRegistry registryWithAllRequiredHandlers() {
        return new PowerModelEventHandlerRegistry(allRequiredHandlers());
    }

    private static Map<String, PowerModelEventHandler> allRequiredHandlers() {
        Map<String, PowerModelEventHandler> handlers =
                new LinkedHashMap<String, PowerModelEventHandler>();
        for (String eventType : PowerModelEventHandlerActivationGuard.REQUIRED_EVENT_TYPES) {
            handlers.put(eventType, NOOP);
        }
        return handlers;
    }
}
