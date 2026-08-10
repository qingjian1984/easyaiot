package com.basiclab.iot.device.config;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * 事件管道启动门禁：开关启用时，四类 V1 处理器必须全部装配。
 * 缺失任一处理器即失败关闭，避免合法事件因空注册表被误投 DLQ。
 */
@Component
@ConditionalOnProperty(name = "power.model.events.enabled", havingValue = "true")
public final class PowerModelEventHandlerActivationGuard {

    static final Set<String> REQUIRED_EVENT_TYPES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1,
                    PowerModelEventEnvelope.EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1,
                    PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1,
                    PowerModelEventEnvelope.EVENT_BINDING_ROLLED_BACK_V1)));

    public PowerModelEventHandlerActivationGuard(PowerModelEventHandlerRegistry registry) {
        verify(registry);
    }

    static void verify(PowerModelEventHandlerRegistry registry) {
        Set<String> registered = new TreeSet<String>(registry.registeredEventTypes());
        Set<String> missing = new TreeSet<String>(REQUIRED_EVENT_TYPES);
        missing.removeAll(registered);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "POWER_MODEL_EVENT_HANDLERS_INCOMPLETE: missing=" + missing
                            + ", registered=" + registered);
        }
    }
}
