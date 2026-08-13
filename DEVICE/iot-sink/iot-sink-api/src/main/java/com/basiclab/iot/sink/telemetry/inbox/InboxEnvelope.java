package com.basiclab.iot.sink.telemetry.inbox;

/**
 * TD-003 §6 Inbox 存储的 envelope 载荷（从 MQTT 上行消息解析）。
 * canonical bytes 原字节落库，不重新序列化。
 */
public record InboxEnvelope(
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
    public InboxEnvelope {
        canonicalBytes = canonicalBytes.clone();
    }

    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }
}
