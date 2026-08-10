package com.basiclab.iot.device.service.power;

import com.basiclab.iot.device.PowerObjectQueryApi;
import com.basiclab.iot.device.controller.power.PowerObjectQueryController;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotReqDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-004 §14.1 provider/consumer 静态契约，防路径和 tenant 入参漂移。 */
class PowerObjectQueryApiContractTest {

    @Test
    void feignAndProviderShareFrozenInternalPath() throws Exception {
        Method method = PowerObjectQueryApi.class.getMethod(
                "queryCollectorSnapshots", PowerCollectorObjectSnapshotReqDTO.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertEquals("/internal-api/device/power-object-snapshots/collector", mapping.value()[0]);
        assertTrue(PowerObjectQueryApi.class.isAssignableFrom(PowerObjectQueryController.class));
    }

    @Test
    void requestContractCannotCarryTenantId() {
        assertFalse(Arrays.stream(PowerCollectorObjectSnapshotReqDTO.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch("tenantId"::equals));
    }
}
