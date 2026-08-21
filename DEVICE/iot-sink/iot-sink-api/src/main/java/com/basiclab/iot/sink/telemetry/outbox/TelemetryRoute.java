package com.basiclab.iot.sink.telemetry.outbox;

import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;

/**
 * Immutable product/device route identity for reliable telemetry.
 *
 * <p>The values are deliberately preserved exactly as supplied.  Validation
 * is limited to the constraints required for one exact MQTT topic level; it
 * never trims, normalizes, folds case, or derives an identity from another
 * field.
 */
public record TelemetryRoute(
        String productIdentification,
        String deviceIdentification
) implements Comparable<TelemetryRoute> {

    public static final int MAX_PRODUCT_IDENTIFICATION_CODE_POINTS = 128;
    public static final int MAX_DEVICE_IDENTIFICATION_CODE_POINTS = 256;

    public TelemetryRoute {
        validateProductIdentification(productIdentification);
        validateDeviceIdentification(deviceIdentification);
    }

    /** Public guard used by the center parser before constructing a route. */
    public static void validateProductIdentification(String value) {
        validateIdentity("productIdentification", value,
                MAX_PRODUCT_IDENTIFICATION_CODE_POINTS);
    }

    /** Public guard used by the center parser before constructing a route. */
    public static void validateDeviceIdentification(String value) {
        validateIdentity("deviceIdentification", value,
                MAX_DEVICE_IDENTIFICATION_CODE_POINTS);
    }

    /** Build the one canonical reliable telemetry publish topic. */
    public String upstreamTopic() {
        return IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT
                .buildTopic(productIdentification, deviceIdentification);
    }

    /** Build the one canonical telemetry ACK topic for the later ACK stage. */
    public String ackTopic() {
        return IotDeviceTopicEnum.PROPERTY_DOWNSTREAM_REPORT_ACK
                .buildTopic(productIdentification, deviceIdentification);
    }

    @Override
    public int compareTo(TelemetryRoute other) {
        int productComparison = productIdentification.compareTo(other.productIdentification);
        return productComparison != 0
                ? productComparison
                : deviceIdentification.compareTo(other.deviceIdentification);
    }

    private static void validateIdentity(String name, String value, int maxCodePoints) {
        if (value == null || value.isEmpty() || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount < 1 || codePointCount > maxCodePoints) {
            throw new IllegalArgumentException(name + " code point length must be in [1, "
                    + maxCodePoints + "]: " + codePointCount);
        }

        for (int index = 0; index < value.length();) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(name + " contains an unpaired surrogate");
                }
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(name + " contains an unpaired surrogate");
            }

            int codePoint = value.codePointAt(index);
            if (codePoint == '/' || codePoint == '+' || codePoint == '#'
                    || isC0OrC1Control(codePoint)) {
                throw new IllegalArgumentException(name + " contains an invalid MQTT topic-level character");
            }
            index += Character.charCount(codePoint);
        }
    }

    private static boolean isC0OrC1Control(int codePoint) {
        return codePoint <= 0x1F || (codePoint >= 0x7F && codePoint <= 0x9F);
    }
}
