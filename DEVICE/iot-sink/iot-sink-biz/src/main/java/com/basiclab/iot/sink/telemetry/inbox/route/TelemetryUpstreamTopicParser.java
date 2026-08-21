package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;

import java.util.Objects;

/**
 * Pure parser for the single canonical property-upstream topic.  The level
 * structure and shared subscription filter are derived from the enum template;
 * no parallel Topic spelling is permitted here.
 */
public final class TelemetryUpstreamTopicParser {

    private static final String PRODUCT_PLACEHOLDER = "${productIdentification}";
    private static final String DEVICE_PLACEHOLDER = "${deviceIdentification}";

    private final String[] templateLevels;
    private final int productLevel;
    private final int deviceLevel;
    private final String sharedSubscriptionFilter;

    public TelemetryUpstreamTopicParser() {
        String template = IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT.getTopicTemplate();
        this.templateLevels = split(template);
        this.productLevel = indexOf(PRODUCT_PLACEHOLDER);
        this.deviceLevel = indexOf(DEVICE_PLACEHOLDER);
        if (productLevel < 0 || deviceLevel < 0 || productLevel == deviceLevel) {
            throw new IllegalStateException("canonical property topic template is incomplete");
        }
        this.sharedSubscriptionFilter = deriveSharedSubscriptionFilter();
    }

    public static String sharedSubscriptionFilter() {
        return new TelemetryUpstreamTopicParser().sharedSubscriptionFilter;
    }

    public String sharedSubscriptionFilterValue() {
        return sharedSubscriptionFilter;
    }

    public Result parse(String topic) {
        if (topic == null) {
            return rejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_MALFORMED);
        }
        String[] levels = split(topic);
        if (levels.length != templateLevels.length) {
            return rejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_MALFORMED);
        }
        for (int index = 0; index < templateLevels.length; index++) {
            if (index != productLevel && index != deviceLevel
                    && !templateLevels[index].equals(levels[index])) {
                return rejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_MALFORMED);
            }
        }

        try {
            TelemetryRoute.validateProductIdentification(levels[productLevel]);
        } catch (IllegalArgumentException exception) {
            return rejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_PRODUCT_INVALID);
        }
        try {
            TelemetryRoute.validateDeviceIdentification(levels[deviceLevel]);
        } catch (IllegalArgumentException exception) {
            return rejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_DEVICE_INVALID);
        }

        try {
            TelemetryRoute route = new TelemetryRoute(levels[productLevel], levels[deviceLevel]);
            if (!route.upstreamTopic().equals(topic)) {
                return rejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_MALFORMED);
            }
            return new Parsed(route);
        } catch (IllegalArgumentException exception) {
            return rejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_MALFORMED);
        }
    }

    private String deriveSharedSubscriptionFilter() {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < templateLevels.length; index++) {
            if (index > 0) {
                result.append('/');
            }
            if (index == productLevel || index == deviceLevel) {
                result.append('+');
            } else {
                result.append(templateLevels[index]);
            }
        }
        return result.toString();
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
        return Objects.requireNonNull(value, "topic template").split("/", -1);
    }

    private static Rejected rejected(TelemetryIngressRejectionCode code) {
        return new Rejected(TelemetryIngressRejection.of(code));
    }

    public sealed interface Result permits Parsed, Rejected {
    }

    public record Parsed(TelemetryRoute route) implements Result {
        public Parsed {
            Objects.requireNonNull(route, "route");
        }
    }

    public record Rejected(TelemetryIngressRejection rejection) implements Result {
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }
}
