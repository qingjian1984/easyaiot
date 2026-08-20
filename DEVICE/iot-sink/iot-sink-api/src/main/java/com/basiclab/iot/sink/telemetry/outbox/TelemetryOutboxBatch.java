package com.basiclab.iot.sink.telemetry.outbox;

import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;

import java.util.List;

/**
 * Immutable product-routed batch at the reliable outbox write boundary.
 *
 * <p>A poll is scoped to one product and one device.  Product identity is
 * deliberately kept outside the envelope so it can be routed without
 * changing the canonical envelope bytes or hash.
 */
public record TelemetryOutboxBatch(
        String productIdentification,
        List<TelemetryEnvelope> envelopes
) {

    public static final int MAX_PRODUCT_IDENTIFICATION_LENGTH = 128;

    public TelemetryOutboxBatch {
        if (productIdentification == null || productIdentification.isEmpty()
                || productIdentification.isBlank()
                || productIdentification.length() > MAX_PRODUCT_IDENTIFICATION_LENGTH) {
            throw new IllegalArgumentException(
                    "productIdentification must be non-blank and have String.length in [1, "
                            + MAX_PRODUCT_IDENTIFICATION_LENGTH + "]");
        }
        if (envelopes == null || envelopes.isEmpty()) {
            throw new IllegalArgumentException("envelopes must be non-empty");
        }
        if (envelopes.stream().anyMatch(envelope -> envelope == null)) {
            throw new IllegalArgumentException("envelopes must not contain null");
        }

        String batchDeviceIdentification = null;
        for (TelemetryEnvelope envelope : envelopes) {
            String deviceIdentification = envelope.deviceIdentification();
            if (deviceIdentification == null || deviceIdentification.isBlank()
                    || (batchDeviceIdentification != null
                    && !batchDeviceIdentification.equals(deviceIdentification))) {
                throw new IllegalArgumentException(
                        "all envelopes in a batch must belong to one non-blank deviceIdentification");
            }
            batchDeviceIdentification = deviceIdentification;
        }
        envelopes = List.copyOf(envelopes);
    }
}
