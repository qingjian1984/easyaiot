package com.basiclab.iot.sink.telemetry.envelope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * TD-002 §6 / TD-003 §6 Envelope V1 canonical codec。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #canonicalize}：record → Jackson JsonNode → JCS canonical → UTF-8 bytes + SHA-256</li>
 *   <li>{@link #generateMessageId}：UUID v4 小写 36 字符（RFC 4122）</li>
 * </ul>
 *
 * <p>落库/发送/重试复用 {@link CanonicalEnvelope#canonicalBytes()}，不重序列化（TD-002 §6 不变量）。
 * 超过 {@link TelemetryEnvelope#MAX_ENVELOPE_BYTES} 抛 {@link EnvelopeCanonicalizationException}。
 */
public final class EnvelopeCanonicalCodec {

    private final ObjectMapper mapper;
    private final EnvelopeJcsCanonicalizer jcs;

    public EnvelopeCanonicalCodec(ObjectMapper mapper) {
        this.mapper = mapper;
        this.jcs = new EnvelopeJcsCanonicalizer();
    }

    public EnvelopeCanonicalCodec() {
        this(new ObjectMapper());
    }

    /** canonicalize envelope → UTF-8 bytes + content_sha256。 */
    public CanonicalEnvelope canonicalize(TelemetryEnvelope envelope) {
        JsonNode node = mapper.valueToTree(envelope);
        String canonical = jcs.canonicalize(node);
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > TelemetryEnvelope.MAX_ENVELOPE_BYTES) {
            throw new EnvelopeCanonicalizationException(
                    "envelope exceeds " + TelemetryEnvelope.MAX_ENVELOPE_BYTES + " bytes: " + bytes.length);
        }
        return new CanonicalEnvelope(bytes, lowercaseHex(sha256(bytes)));
    }

    /** UUID v4 小写 36 字符（RFC 4122，TD-002 §6 messageId 格式）。 */
    public static String generateMessageId() {
        return UUID.randomUUID().toString();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK missing SHA-256", e);
        }
    }

    private static String lowercaseHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /** canonicalize 结果：canonical UTF-8 bytes + content_sha256（lowercase hex）。 */
    public static final class CanonicalEnvelope {
        private final byte[] canonicalBytes;
        private final String contentSha256;

        public CanonicalEnvelope(byte[] canonicalBytes, String contentSha256) {
            this.canonicalBytes = canonicalBytes.clone();
            this.contentSha256 = contentSha256;
        }

        public byte[] canonicalBytes() {
            return canonicalBytes.clone();
        }

        public String contentSha256() {
            return contentSha256;
        }
    }
}
