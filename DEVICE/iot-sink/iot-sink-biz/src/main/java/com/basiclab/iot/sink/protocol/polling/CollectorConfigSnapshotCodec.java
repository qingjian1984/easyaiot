package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigErrorCode;
import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorDevice;
import com.basiclab.iot.sink.polling.CollectorPoint;
import com.basiclab.iot.sink.polling.CollectorSerialBus;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Strict v1.1 JSON/JCS/schema/identity validator for local state files. */
public final class CollectorConfigSnapshotCodec {
    public static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;
    public static final long MAX_SAFE_VERSION = 9_007_199_254_740_991L;
    private static final String SCHEMA_VERSION = "1.1";
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "productIdentification", "workloadId",
            "tenantId", "siteId", "siteCode", "configVersion", "generatedAt", "serialBuses");
    private static final Set<String> BUS_FIELDS = Set.of("busId", "serialPort", "baudRate", "dataBits", "stopBits",
            "parity", "transmitDelayMs", "rs485Mode", "devices");
    private static final Set<String> DEVICE_FIELDS = Set.of("deviceId", "deviceIdentification", "unitId",
            "pollIntervalMs", "requestTimeoutMs", "maxRetries", "points");
    private static final Set<String> POINT_FIELDS = Set.of("propertyCode", "function", "address", "quantity",
            "dataType", "byteOrder", "wordOrder", "scale", "offset", "dataPriority", "writable", "pollGroup");
    private static final Set<String> FUNCTIONS = Set.of("COIL", "DISCRETE_INPUT", "HOLDING_REGISTER", "INPUT_REGISTER");
    private static final Set<String> PRIORITIES = Set.of("SAFETY", "ALARM", "METERING_TOTAL", "CONTROL_FEEDBACK",
            "NORMAL_TELEMETRY");
    private static final Set<String> ORDERS = Set.of("BIG_ENDIAN", "LITTLE_ENDIAN");
    private static final Set<String> STOP_BITS = Set.of("1", "1.5", "2");
    private static final Set<String> PARITIES = Set.of("NONE", "EVEN", "ODD");
    private static final String ID_PATTERN = "[0-9]+";
    private static final String DECIMAL_PATTERN = "^[+]?(0|[1-9][0-9]*)([.][0-9]+)?$|^-(0[.]([0-9]*[1-9][0-9]*)|[1-9][0-9]*([.][0-9]+)?)$";

    private final ObjectMapper mapper;
    private final EnvelopeJcsCanonicalizer canonicalizer;

    public CollectorConfigSnapshotCodec() {
        JsonFactory factory = new JsonFactory();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.mapper = new ObjectMapper(factory);
        this.canonicalizer = new EnvelopeJcsCanonicalizer();
    }

    /** Decode only a canonical original-byte payload for the expected workload. */
    public DecodedSnapshot decode(byte[] raw, String expectedWorkloadId) {
        if (raw == null || raw.length == 0) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_NOT_AVAILABLE);
        }
        if (raw.length > MAX_PAYLOAD_BYTES) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_TOO_LARGE);
        }
        if (expectedWorkloadId == null || expectedWorkloadId.isBlank()) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_WORKLOAD_MISMATCH);
        }
        JsonNode root;
        try {
            JsonParser parser = mapper.getFactory().createParser(raw);
            root = mapper.readTree(parser);
            if (root == null || parser.nextToken() != null) {
                throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_JSON_INVALID);
            }
            parser.close();
        } catch (CollectorConfigStateException e) {
            throw e;
        } catch (Exception e) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_JSON_INVALID, e);
        }
        try {
            CollectorConfigSnapshot snapshot = validate(root, expectedWorkloadId);
            byte[] canonical = canonicalizer.canonicalize(root).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(raw, canonical)) {
                throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_CANONICAL_INVALID);
            }
            return new DecodedSnapshot(snapshot, raw.clone(), sha256(raw));
        } catch (CollectorConfigStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID, e);
        }
    }

    public byte[] encodeObservation(ObjectNode observation) {
        try {
            return canonicalizer.canonicalize(observation).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_WRITE_FAILED, e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private CollectorConfigSnapshot validate(JsonNode root, String expectedWorkloadId) {
        object(root, ROOT_FIELDS);
        requiredText(root, "schemaVersion", 1, 3);
        if (!SCHEMA_VERSION.equals(root.get("schemaVersion").textValue())) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        String product = text(root, "productIdentification", 1, 128);
        String workload = text(root, "workloadId", 1, 128);
        if (!expectedWorkloadId.equals(workload)) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_WORKLOAD_MISMATCH);
        }
        String tenant = id(root, "tenantId");
        String siteId = id(root, "siteId");
        String siteCode = text(root, "siteCode", 1, 64);
        long version = integer(root, "configVersion", 1, MAX_SAFE_VERSION);
        String generatedAt = text(root, "generatedAt", 1, 128);
        try {
            try {
                Instant.parse(generatedAt);
            } catch (DateTimeParseException ignored) {
                OffsetDateTime.parse(generatedAt);
            }
        } catch (DateTimeParseException e) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID, e);
        }
        JsonNode buses = root.get("serialBuses");
        if (!buses.isArray() || buses.isEmpty()) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        List<CollectorSerialBus> result = new ArrayList<>();
        Set<String> busIds = new HashSet<>();
        for (JsonNode bus : buses) {
            object(bus, BUS_FIELDS);
            String busId = text(bus, "busId", 1, 256);
            if (!busIds.add(busId)) {
                throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
            }
            String serialPort = text(bus, "serialPort", 1, 512);
            int baud = intValue(bus, "baudRate", 1, 4_000_000);
            int dataBits = intValue(bus, "dataBits", 5, 8);
            String stopBits = enumText(bus, "stopBits", STOP_BITS);
            String parity = enumText(bus, "parity", PARITIES);
            int delay = intValue(bus, "transmitDelayMs", 0, 60_000);
            boolean rs485 = bool(bus, "rs485Mode");
            JsonNode devices = bus.get("devices");
            if (!devices.isArray() || devices.isEmpty()) {
                throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
            }
            List<CollectorDevice> deviceResult = new ArrayList<>();
            Set<String> deviceIds = new HashSet<>();
            for (JsonNode device : devices) {
                object(device, DEVICE_FIELDS);
                String deviceId = id(device, "deviceId");
                if (!deviceIds.add(deviceId)) {
                    throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
                }
                String identification = text(device, "deviceIdentification", 1, 256);
                int unitId = intValue(device, "unitId", 1, 247);
                long interval = integer(device, "pollIntervalMs", 1, 86_400_000L);
                long timeout = integer(device, "requestTimeoutMs", 1, 60_000L);
                int retries = intValue(device, "maxRetries", 0, 20);
                JsonNode points = device.get("points");
                if (!points.isArray() || points.isEmpty()) {
                    throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
                }
                List<CollectorPoint> pointResult = new ArrayList<>();
                Set<String> pointCodes = new HashSet<>();
                for (JsonNode point : points) {
                    object(point, POINT_FIELDS);
                    String propertyCode = text(point, "propertyCode", 1, 256);
                    if (!pointCodes.add(propertyCode)) {
                        throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
                    }
                    String function = enumText(point, "function", FUNCTIONS);
                    int address = intValue(point, "address", 0, 65_535);
                    int quantity = intValue(point, "quantity", 1, 120);
                    String dataType = text(point, "dataType", 1, 128);
                    String byteOrder = enumText(point, "byteOrder", ORDERS);
                    String wordOrder = enumText(point, "wordOrder", ORDERS);
                    String scale = decimal(point, "scale");
                    String offset = decimal(point, "offset");
                    String priority = enumText(point, "dataPriority", PRIORITIES);
                    boolean writable = bool(point, "writable");
                    String pollGroup = text(point, "pollGroup", 1, 256);
                    pointResult.add(new CollectorPoint(propertyCode, function, address, quantity, dataType,
                            byteOrder, wordOrder, scale, offset, priority, writable, pollGroup));
                }
                deviceResult.add(new CollectorDevice(deviceId, identification, unitId, interval, timeout,
                        retries, pointResult));
            }
            result.add(new CollectorSerialBus(busId, serialPort, baud, dataBits, stopBits, parity, delay, rs485,
                    deviceResult));
        }
        return new CollectorConfigSnapshot(SCHEMA_VERSION, product, workload, tenant, siteId, siteCode, version,
                generatedAt, result, null);
    }

    private static void object(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
            }
        }
    }

    private static String requiredText(JsonNode object, String field, int min, int max) {
        return text(object, field, min, max);
    }

    private static String text(JsonNode object, String field, int min, int max) {
        JsonNode node = object.get(field);
        if (node == null || !node.isTextual() || node.textValue().length() < min || node.textValue().length() > max
                || node.textValue().codePoints().anyMatch(cp -> cp < 0x20)) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        return node.textValue();
    }

    private static String id(JsonNode object, String field) {
        String value = text(object, field, 1, 128);
        if (!value.matches(ID_PATTERN)) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        return value;
    }

    private static String enumText(JsonNode object, String field, Set<String> values) {
        String value = text(object, field, 1, 128);
        if (!values.contains(value)) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        return value;
    }

    private static String decimal(JsonNode object, String field) {
        String value = text(object, field, 1, 1024);
        if (!value.matches(DECIMAL_PATTERN) || "+0".equals(value) || "-0".equals(value)) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        return value;
    }

    private static boolean bool(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isBoolean()) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        return value.booleanValue();
    }

    private static int intValue(JsonNode object, String field, long min, long max) {
        long value = integer(object, field, min, max);
        if (value > Integer.MAX_VALUE) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        return (int) value;
    }

    private static long integer(JsonNode object, String field, long min, long max) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < min || value.longValue() > max) {
            throw failure(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID);
        }
        return value.longValue();
    }

    private static CollectorConfigStateException failure(CollectorConfigErrorCode code) {
        return new CollectorConfigStateException(code);
    }

    private static CollectorConfigStateException failure(CollectorConfigErrorCode code, Throwable cause) {
        return new CollectorConfigStateException(code, cause);
    }

    public record DecodedSnapshot(CollectorConfigSnapshot snapshot, byte[] canonicalBytes, String payloadSha256) {
        public DecodedSnapshot {
            canonicalBytes = canonicalBytes.clone();
        }
    }
}
