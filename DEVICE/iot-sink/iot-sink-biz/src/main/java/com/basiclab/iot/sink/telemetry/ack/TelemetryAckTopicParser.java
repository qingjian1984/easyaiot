package com.basiclab.iot.sink.telemetry.ack;

import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;

import java.util.Objects;
import java.util.Optional;

/**
 * Parser/builder for the exact product/device ACK Topic.
 *
 * <p>Only the ACK template owned by {@link IotDeviceTopicEnum} is accepted.
 * Wildcards, shared-subscription prefixes, legacy telemetry paths and extra
 * topic levels are rejected before an ACK can reach the outbox writer.
 */
public final class TelemetryAckTopicParser {

    private static final String PRODUCT_PLACEHOLDER = "${productIdentification}";
    private static final String DEVICE_PLACEHOLDER = "${deviceIdentification}";

    private final String[] templateLevels;
    private final int productLevel;
    private final int deviceLevel;

    public TelemetryAckTopicParser() {
        String template = IotDeviceTopicEnum.PROPERTY_DOWNSTREAM_REPORT_ACK.getTopicTemplate();
        this.templateLevels = split(Objects.requireNonNull(template, "ACK topic template"));
        this.productLevel = indexOf(PRODUCT_PLACEHOLDER);
        this.deviceLevel = indexOf(DEVICE_PLACEHOLDER);
        if (productLevel < 0 || deviceLevel < 0 || productLevel == deviceLevel) {
            throw new IllegalStateException("canonical ACK topic template is incomplete");
        }
    }

    public TelemetryRoute parse(String topic) {
        if (topic == null) {
            throw invalidTopic();
        }
        String[] levels = split(topic);
        if (levels.length != templateLevels.length) {
            throw invalidTopic();
        }
        for (int index = 0; index < templateLevels.length; index++) {
            if (index != productLevel && index != deviceLevel
                    && !templateLevels[index].equals(levels[index])) {
                throw invalidTopic();
            }
        }
        try {
            TelemetryRoute route = new TelemetryRoute(levels[productLevel], levels[deviceLevel]);
            if (!route.ackTopic().equals(topic)) {
                throw invalidTopic();
            }
            return route;
        } catch (IllegalArgumentException e) {
            throw invalidTopic();
        }
    }

    public TelemetryRoute parseAckTopic(String topic) {
        return parse(topic);
    }

    public Optional<TelemetryRoute> tryParse(String topic) {
        try {
            return Optional.of(parse(topic));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isCanonical(String topic) {
        return tryParse(topic).isPresent();
    }

    public String build(TelemetryRoute route) {
        if (route == null) {
            throw invalidTopic();
        }
        return route.ackTopic();
    }

    public String buildAckTopic(TelemetryRoute route) {
        return build(route);
    }

    private static IllegalArgumentException invalidTopic() {
        return new IllegalArgumentException("ACK_TOPIC_INVALID");
    }

    private int indexOf(String placeholder) {
        for (int index = 0; index < templateLevels.length; index++) {
            if (placeholder.equals(templateLevels[index])) {
                return index;
            }
        }
        return -1;
    }

    private static String[] split(String value) {
        return value.split("/", -1);
    }
}
