package com.basiclab.iot.sink.telemetry.store;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;

import java.util.Objects;

/**
 * Closed store projection sample.  It is deliberately an API value object so
 * the projector and either backend share the same batch contract without a
 * dependency on a business module.
 */
public record TelemetrySample(
        String messageId,
        String requestId,
        String tenantId,
        String siteCode,
        String deviceIdentification,
        String propertyCode,
        byte[] canonicalBytes,
        String contentSha256,
        long collectedAtMs,
        long sequence,
        String source,
        long configVersion
) {
    public TelemetrySample {
        canonicalBytes = canonicalBytes == null ? null : canonicalBytes.clone();
    }

    @Override
    public byte[] canonicalBytes() {
        return canonicalBytes == null ? null : canonicalBytes.clone();
    }

    /** Explicit bridge from the Inbox API object; no business-layer dependency. */
    public static TelemetrySample fromInboxEnvelope(InboxEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        return new TelemetrySample(
                envelope.messageId(), envelope.requestId(), envelope.tenantId(),
                envelope.siteCode(), envelope.deviceIdentification(), envelope.propertyCode(),
                envelope.canonicalBytes(), envelope.contentSha256(), envelope.collectedAtMs(),
                envelope.sequence(), envelope.source(), envelope.configVersion());
    }

    /** Structural validation used by the port before a backend is touched. */
    public boolean isValid() {
        return nonBlank(messageId) && nonBlank(requestId) && nonBlank(tenantId)
                && nonBlank(siteCode) && nonBlank(deviceIdentification) && nonBlank(propertyCode)
                && canonicalBytes != null && canonicalBytes.length > 0
                && nonBlank(contentSha256) && contentSha256.matches("[0-9a-f]{64}")
                && collectedAtMs >= 0 && sequence >= 0 && nonBlank(source) && configVersion >= 0;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
