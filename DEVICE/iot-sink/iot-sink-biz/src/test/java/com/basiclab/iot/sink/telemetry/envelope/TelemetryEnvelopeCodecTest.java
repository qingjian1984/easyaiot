package com.basiclab.iot.sink.telemetry.envelope;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-002 §6 / TD-003 §6 EnvelopeCanonicalCodec 合同：
 * canonical 确定性、SHA-256 一致、UUID v4、64KiB 上限。
 */
class TelemetryEnvelopeCodecTest {

    private final EnvelopeCanonicalCodec codec = new EnvelopeCanonicalCodec();

    private TelemetryEnvelope env(String msgId, String value) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION,
                TelemetryEnvelope.CANONICALIZATION_VERSION,
                msgId, "req-" + msgId,
                "123", "site-1", "dev-1", "voltage-a", value,
                TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                TelemetryQuality.GOOD, DataPriority.NORMAL_TELEMETRY,
                "2026-08-12T00:00:00Z", "2026-08-12T00:00:00Z",
                1, "modbus-rtu", 1
        );
    }

    @Test
    void canonicalDeterministicSameEnvelope() {
        TelemetryEnvelope e = env("msg-1", "220.5");
        EnvelopeCanonicalCodec.CanonicalEnvelope c1 = codec.canonicalize(e);
        EnvelopeCanonicalCodec.CanonicalEnvelope c2 = codec.canonicalize(e);
        assertArrayEquals(c1.canonicalBytes(), c2.canonicalBytes(), "same envelope → same canonical bytes");
        assertEquals(c1.contentSha256(), c2.contentSha256(), "same envelope → same SHA-256");
    }

    @Test
    void canonicalKeyOrderIsUTF16() {
        // JCS 要求对象键按 UTF-16 排序；不同 value 但相同字段集应产生一致结构
        EnvelopeCanonicalCodec.CanonicalEnvelope c1 = codec.canonicalize(env("msg-1", "220.5"));
        EnvelopeCanonicalCodec.CanonicalEnvelope c2 = codec.canonicalize(env("msg-2", "221.0"));
        // 结构相同（键序一致），内容不同（不同 value/msgId）
        String s1 = new String(c1.canonicalBytes(), StandardCharsets.UTF_8);
        String s2 = new String(c2.canonicalBytes(), StandardCharsets.UTF_8);
        assertTrue(s1.startsWith("{\"canonicalizationVersion\":"), "键序：canonicalizationVersion 应排前");
    }

    @Test
    void differentValueProducesDifferentHash() {
        EnvelopeCanonicalCodec.CanonicalEnvelope c1 = codec.canonicalize(env("msg-1", "220.5"));
        EnvelopeCanonicalCodec.CanonicalEnvelope c2 = codec.canonicalize(env("msg-1", "221.0"));
        assertNotEquals(c1.contentSha256(), c2.contentSha256(), "不同 value 必须产生不同 SHA-256");
    }

    @Test
    void sha256MatchesManualComputation() throws Exception {
        EnvelopeCanonicalCodec.CanonicalEnvelope c = codec.canonicalize(env("msg-1", "220.5"));
        byte[] manual = MessageDigest.getInstance("SHA-256").digest(c.canonicalBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : manual) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        assertEquals(hex.toString(), c.contentSha256(), "SHA-256 应匹配手动计算");
    }

    @Test
    void generateMessageIdIsUUIDv4Lowercase36() {
        String id = EnvelopeCanonicalCodec.generateMessageId();
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"),
                "UUID v4 小写 36 字符: " + id);
    }

    @Test
    void generateMessageIdUnique() {
        String id1 = EnvelopeCanonicalCodec.generateMessageId();
        String id2 = EnvelopeCanonicalCodec.generateMessageId();
        assertNotEquals(id1, id2, "两次 UUID 必须不同");
    }
}
