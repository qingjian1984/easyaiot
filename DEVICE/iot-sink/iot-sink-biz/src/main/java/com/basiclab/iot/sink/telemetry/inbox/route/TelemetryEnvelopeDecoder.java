package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/** Existing Envelope V1 field decoder; it does not canonicalize or hash anew. */
final class TelemetryEnvelopeDecoder {

    private final ObjectMapper mapper;

    TelemetryEnvelopeDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    DecodeResult decode(byte[] payload) {
        if (payload == null) {
            return new Invalid();
        }
        try {
            JsonNode root = mapper.readTree(payload);
            if (root == null || !root.isObject()) {
                return new Invalid();
            }
            String messageId = requiredText(root, "messageId", true);
            String requestId = optionalText(root, "requestId");
            String tenantId = requiredText(root, "tenantId", false);
            String siteCode = requiredText(root, "siteCode", true);
            String deviceIdentification = requiredText(root, "deviceIdentification", true);
            String propertyCode = requiredText(root, "propertyCode", true);
            String source = optionalText(root, "source");
            if (messageId == null || tenantId == null || siteCode == null
                    || deviceIdentification == null || propertyCode == null
                    || (root.has("requestId") && root.get("requestId") != null
                    && !root.get("requestId").isNull() && requestId == null)
                    || (root.has("source") && root.get("source") != null
                    && !root.get("source").isNull() && source == null)) {
                return new Invalid();
            }
            // Keep an empty, present tenant for the dedicated tenant-code
            // rejection rather than conflating it with a missing field.
            return buildDecoded(payload, root, messageId, requestId, tenantId,
                    siteCode, deviceIdentification, propertyCode, source);
        } catch (Exception exception) {
            return new Invalid();
        }
    }

    private Decoded buildDecoded(byte[] payload, JsonNode root, String messageId, String requestId,
                                 String tenantId, String siteCode,
                                 String deviceIdentification, String propertyCode,
                                 String source) {
        long collectedAtMs = parseCollectedAt(root.get("collectedAt"));
        long sequence = root.path("sequence").asLong(0);
        long configVersion = root.path("configVersion").asLong(0);
        String effectiveSource = source == null ? "unknown" : source;
        return new Decoded(messageId, requestId, tenantId, siteCode, deviceIdentification,
                propertyCode, payload, sha256(payload), collectedAtMs, sequence,
                effectiveSource, configVersion);
    }

    private static String requiredText(JsonNode root, String field, boolean nonBlank) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.textValue();
        if (nonBlank && (text == null || text.isBlank())) {
            return null;
        }
        return text;
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.textValue() : null;
    }

    private static long parseCollectedAt(JsonNode value) {
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (!value.isTextual()) {
            return 0L;
        }
        try {
            return Instant.parse(value.textValue()).toEpochMilli();
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    private static String sha256(byte[] input) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(Character.forDigit((value >>> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }

    sealed interface DecodeResult permits Decoded, Invalid {
    }

    record Decoded(
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
    ) implements DecodeResult {
        Decoded {
            canonicalBytes = canonicalBytes.clone();
        }

        InboxEnvelope toInboxEnvelope(String productIdentification) {
            return new InboxEnvelope(messageId, requestId, tenantId, productIdentification,
                    siteCode, deviceIdentification, propertyCode, canonicalBytes,
                    contentSha256, collectedAtMs, sequence, source, configVersion);
        }

        @Override
        public byte[] canonicalBytes() {
            return canonicalBytes.clone();
        }
    }

    record Invalid() implements DecodeResult {
    }
}
