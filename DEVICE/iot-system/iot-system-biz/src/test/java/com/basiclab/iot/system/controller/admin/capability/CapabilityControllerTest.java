package com.basiclab.iot.system.controller.admin.capability;

import com.basiclab.iot.common.capability.ManifestCapabilityService;
import com.basiclab.iot.common.domain.CommonResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CapabilityControllerTest {

    @Test
    void miniResponseMustBeReadOnlyAndFailClosed() {
        CapabilityController controller = new CapabilityController(
                ManifestCapabilityService.disabled("mini"));

        CommonResult<?> result = controller.getCapabilities();
        assertEquals(0, result.getCode());
        assertEquals("mini", controller.getCapabilities().getData().getProfile());
        assertFalse(controller.getCapabilities().getData().getCapabilities()
                .containsKey("power.device.model"));
    }
}
