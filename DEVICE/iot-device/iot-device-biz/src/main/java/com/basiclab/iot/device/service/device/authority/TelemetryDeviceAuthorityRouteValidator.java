package com.basiclab.iot.device.service.device.authority;

/** Local copy of the immutable MQTT identity guard for the device API. */
final class TelemetryDeviceAuthorityRouteValidator {

    static final int MAX_PRODUCT_CODE_POINTS = 128;
    static final int MAX_DEVICE_CODE_POINTS = 256;

    private TelemetryDeviceAuthorityRouteValidator() {
    }

    static boolean isValid(String value, int maxCodePoints) {
        if (value == null || value.isEmpty() || value.isBlank()) {
            return false;
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount < 1 || codePointCount > maxCodePoints) {
            return false;
        }
        for (int index = 0; index < value.length();) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
            int codePoint = value.codePointAt(index);
            if (codePoint == '/' || codePoint == '+' || codePoint == '#'
                    || codePoint <= 0x1F || (codePoint >= 0x7F && codePoint <= 0x9F)) {
                return false;
            }
            index += Character.charCount(codePoint);
        }
        return true;
    }
}
