package com.basiclab.iot.device.controller.power;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** ADR-015：首次发布投影闭环完成前，绑定写 API 必须独立 fail-closed。 */
class PowerModelBindingControllerGateTest {

    @Test
    void bindingApplyApiIsFailClosedByDefault() {
        ConditionalOnProperty condition = PowerModelBindingController.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition);
        assertEquals("easyaiot.power-model", condition.prefix());
        assertEquals("binding-apply-api-enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }
}
