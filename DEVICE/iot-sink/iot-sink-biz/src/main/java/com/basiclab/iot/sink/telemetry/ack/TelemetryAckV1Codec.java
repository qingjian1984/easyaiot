package com.basiclab.iot.sink.telemetry.ack;

import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict JSON codec for the seven-field successful ACK V1 contract.
 *
 * <p>Jackson's duplicate-field detection is enabled at the parser factory.
 * The codec accepts unknown optional members in a 1.x payload, but legacy
 * {@code resultCode/errorCode/observedAt} members are explicitly rejected so
 * an old wire message cannot silently enter the V1 state machine.
 */
public final class TelemetryAckV1Codec {

    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "schemaVersion", "messageId", "requestId", "status", "code", "reasonCode", "persistedAt");
    private static final Set<String> LEGACY_FIELDS = Set.of("resultCode", "errorCode", "observedAt");
    private static final Pattern UTC_MILLIS = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z");

    private final ObjectMapper mapper;

    public TelemetryAckV1Codec() {
        mapper = new ObjectMapper();
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    /** Decode a UTF-8 ACK V1 payload without exposing its contents in errors. */
    public TelemetryAckV1 decode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw failure("ACK_PAYLOAD_EMPTY");
        }
        String json = decodeUtf8(payload);
        if (!json.isEmpty() && json.charAt(0) == '\ufeff') {
            throw failure("ACK_UTF8_BOM_FORBIDDEN");
        }

        JsonNode root;
        try (JsonParser parser = mapper.getFactory().createParser(json)) {
            root = mapper.readTree(parser);
            if (root == null || parser.nextToken() != null) {
                throw failure("ACK_JSON_MALFORMED");
            }
        } catch (TelemetryAckCodecException e) {
            throw e;
        } catch (Exception e) {
            // Includes malformed JSON and duplicate JSON object keys.  Do not
            // propagate Jackson's payload-bearing parser message.
            throw failure("ACK_JSON_MALFORMED", e);
        }

        if (!root.isObject()) {
            throw failure("ACK_ROOT_NOT_OBJECT");
        }
        for (String legacyField : LEGACY_FIELDS) {
            if (root.has(legacyField)) {
                throw failure("ACK_LEGACY_WIRE_UNSUPPORTED");
            }
        }
        for (String field : REQUIRED_FIELDS) {
            JsonNode value = root.get(field);
            if (value == null || value.isNull()) {
                throw failure("ACK_REQUIRED_FIELD_MISSING");
            }
        }

        String schemaVersion = requiredText(root, "schemaVersion");
        // LC03 freezes the wire schema to 1.0.  Unknown members remain
        // forward-compatible within that major schema, but a different
        // major/minor contract cannot be guessed.
        if (!TelemetryAckV1.SCHEMA_VERSION.equals(schemaVersion)) {
            throw failure("ACK_SCHEMA_UNSUPPORTED");
        }

        String messageId = requiredText(root, "messageId");
        if (!TelemetryAckV1.isWireMessageId(messageId)) {
            throw failure("ACK_MESSAGE_ID_INVALID");
        }
        String requestId = requiredText(root, "requestId");
        if (!TelemetryAckV1.isCanonicalRequestId(requestId)) {
            throw failure("ACK_REQUEST_ID_INVALID");
        }

        String statusName = requiredText(root, "status");
        TelemetryAckStatus status;
        try {
            status = TelemetryAckStatus.valueOf(statusName);
        } catch (IllegalArgumentException e) {
            throw failure("ACK_STATUS_INVALID");
        }
        if (!status.isSuccess()) {
            throw failure("ACK_STATUS_UNSUPPORTED");
        }

        JsonNode codeNode = root.get("code");
        if (!codeNode.isIntegralNumber() || !codeNode.canConvertToInt()) {
            throw failure("ACK_CODE_TYPE_INVALID");
        }
        int code = codeNode.intValue();
        String reasonCode = requiredText(root, "reasonCode");

        String persistedAtText = requiredText(root, "persistedAt");
        if (!UTC_MILLIS.matcher(persistedAtText).matches()) {
            throw failure("ACK_PERSISTED_AT_INVALID");
        }
        long persistedAtMs;
        try {
            Instant parsed = Instant.parse(persistedAtText);
            // The regex above fixes UTC and millisecond precision.  The
            // round-trip additionally rejects impossible calendar values.
            if (!formatPersistedAt(parsed.toEpochMilli()).equals(persistedAtText)) {
                throw failure("ACK_PERSISTED_AT_INVALID");
            }
            persistedAtMs = parsed.toEpochMilli();
        } catch (DateTimeParseException e) {
            throw failure("ACK_PERSISTED_AT_INVALID");
        }

        try {
            return new TelemetryAckV1(schemaVersion, messageId, requestId,
                    status, code, reasonCode, persistedAtMs);
        } catch (IllegalArgumentException e) {
            throw failure(contractErrorCode(e), e);
        }
    }

    public TelemetryAckV1 decode(String payload) {
        if (payload == null) {
            throw failure("ACK_PAYLOAD_EMPTY");
        }
        return decode(payload.getBytes(StandardCharsets.UTF_8));
    }

    /** Encode exactly the seven V1 fields in the frozen producer order. */
    public byte[] encode(TelemetryAckV1 ack) {
        if (ack == null) {
            throw failure("ACK_VALUE_MISSING");
        }
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("schemaVersion", ack.schemaVersion());
            node.put("messageId", ack.messageId());
            node.put("requestId", ack.requestId());
            node.put("status", ack.status().name());
            node.put("code", ack.code());
            node.put("reasonCode", ack.reasonCode());
            node.put("persistedAt", formatPersistedAt(ack.persistedAtMs()));
            return mapper.writeValueAsBytes(node);
        } catch (TelemetryAckCodecException e) {
            throw e;
        } catch (Exception e) {
            throw failure("ACK_ENCODE_FAILED", e);
        }
    }

    public String encodeToString(TelemetryAckV1 ack) {
        return new String(encode(ack), StandardCharsets.UTF_8);
    }

    /** Convert a decoded V1 ACK into the shared writer command. */
    public AckCommand decodeCommand(byte[] payload, TelemetryRoute route, long observedAtMs) {
        return new AckCommand(decode(payload), route, observedAtMs);
    }

    public static String formatPersistedAt(long persistedAtMs) {
        String formatted = Instant.ofEpochMilli(persistedAtMs).toString();
        if (formatted.endsWith("Z") && formatted.indexOf('.') < 0) {
            return formatted.substring(0, formatted.length() - 1) + ".000Z";
        }
        int dot = formatted.indexOf('.');
        if (dot < 0) {
            return formatted;
        }
        int fractionEnd = formatted.length() - 1;
        String fraction = formatted.substring(dot + 1, fractionEnd);
        if (fraction.length() == 1) {
            fraction += "00";
        } else if (fraction.length() == 2) {
            fraction += "0";
        } else if (fraction.length() > 3) {
            fraction = fraction.substring(0, 3);
        }
        return formatted.substring(0, dot + 1) + fraction + "Z";
    }

    private static String requiredText(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || value.isNull() || !value.isTextual() || value.textValue().isBlank()) {
            throw failure("ACK_FIELD_TYPE_INVALID");
        }
        return value.textValue();
    }

    private static String decodeUtf8(byte[] payload) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload))
                    .toString();
        } catch (CharacterCodingException e) {
            throw failure("ACK_UTF8_INVALID", e);
        }
    }

    private static String contractErrorCode(IllegalArgumentException e) {
        String code = e.getMessage();
        return code != null && code.startsWith("ACK_") ? code : "ACK_CONTRACT_INVALID";
    }

    private static TelemetryAckCodecException failure(String code) {
        return new TelemetryAckCodecException(code);
    }

    private static TelemetryAckCodecException failure(String code, Throwable cause) {
        return new TelemetryAckCodecException(code, cause);
    }
}
