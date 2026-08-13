package com.basiclab.iot.sink.telemetry.outbox;

/**
 * TD-002 §11 claim 选出的 envelope（canonical bytes 原字节，不重新序列化）。
 */
public record ClaimedEnvelope(
        long id,
        String messageId,
        byte[] canonicalBytes,
        String contentSha256,
        String tenantId,
        String siteCode,
        String deviceIdentification,
        String propertyCode,
        String topic
) {
    public ClaimedEnvelope {
        canonicalBytes = canonicalBytes.clone();
    }

    @Override
    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }
}
