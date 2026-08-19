package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.service.model.JcsCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TD-001 §6/§6.1：collector-config/1.0 与 1.1 快照的发布侧合同。
 *
 * <p>本类在持久化之前完成 fail-closed 校验，并只生成一次 canonical 文本；
 * 发布单 payload、SHA-256、长度以及后续下发必须复用 {@link Artifact} 中的同一
 * UTF-8 字节事实。它不从 device.extension 猜测站点、超时、重试、优先级或轮询组。</p>
 */
public final class CollectorConfigSnapshotContract {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String SCHEMA_VERSION_V1_1 = "1.1";
    public static final String CANONICALIZATION_VERSION = "jcs-rfc8785-v1";
    public static final String CODE_INVALID = "COLLECTOR_CONFIG_SNAPSHOT_INVALID";
    public static final String CODE_FACT_MISSING = "COLLECTOR_CONFIG_SOURCE_FACT_MISSING";

    private static final Pattern DECIMAL_ID = Pattern.compile("^[0-9]+$");
    private static final Pattern DECIMAL = Pattern.compile("^[+-]?(0|[1-9][0-9]*)(\\.[0-9]+)?$");
    private static final Set<String> ROOT_FIELDS = fields("schemaVersion", "workloadId", "tenantId",
            "siteId", "siteCode", "configVersion", "generatedAt", "serialBuses");
    private static final Set<String> ROOT_FIELDS_V1_1 = fields("schemaVersion", "productIdentification",
            "workloadId", "tenantId", "siteId", "siteCode", "configVersion", "generatedAt",
            "serialBuses");
    private static final Set<String> BUS_FIELDS = fields("busId", "serialPort", "baudRate", "dataBits",
            "stopBits", "parity", "transmitDelayMs", "rs485Mode", "devices");
    private static final Set<String> DEVICE_FIELDS = fields("deviceId", "deviceIdentification", "unitId",
            "pollIntervalMs", "requestTimeoutMs", "maxRetries", "points");
    private static final Set<String> POINT_FIELDS = fields("propertyCode", "function", "address", "quantity",
            "dataType", "byteOrder", "wordOrder", "scale", "offset", "dataPriority", "writable", "pollGroup");
    private static final Set<String> FUNCTIONS = fields("COIL", "DISCRETE_INPUT", "HOLDING_REGISTER", "INPUT_REGISTER");
    private static final Set<String> PRIORITIES = fields("SAFETY", "ALARM", "METERING_TOTAL", "CONTROL_FEEDBACK", "NORMAL_TELEMETRY");
    private static final Set<String> PARITIES = fields("NONE", "EVEN", "ODD");
    private static final Set<String> STOP_BITS = fields("1", "1.5", "2");
    private static final Set<String> ORDERS = fields("BIG_ENDIAN", "LITTLE_ENDIAN");

    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();

    public Artifact validateAndCanonicalize(JsonNode root) {
        if (root != null && root.isObject() && root.has("schemaVersion")
                && root.get("schemaVersion").isTextual()
                && SCHEMA_VERSION_V1_1.equals(root.get("schemaVersion").textValue())) {
            return validateAndCanonicalizeV11(root);
        }
        return validateAndCanonicalizeV1(root);
    }

    /**
     * Validates a historical 1.0 snapshot without changing its field set or
     * canonical bytes.  Keep this path stable for already persisted releases.
     */
    private Artifact validateAndCanonicalizeV1(JsonNode root) {
        requireObject(root, "$", ROOT_FIELDS);
        requireEquals(text(root, "schemaVersion", "$"), SCHEMA_VERSION, "$.schemaVersion");
        nonBlank(text(root, "workloadId", "$"), "$.workloadId");
        decimalId(text(root, "tenantId", "$"), "$.tenantId");
        decimalId(text(root, "siteId", "$"), "$.siteId");
        nonBlank(text(root, "siteCode", "$"), "$.siteCode");
        positiveInteger(root.get("configVersion"), "$.configVersion", Long.MAX_VALUE);
        try {
            OffsetDateTime.parse(text(root, "generatedAt", "$"));
        } catch (DateTimeParseException e) {
            invalid("$.generatedAt 必须是带偏移 RFC 3339 时间");
        }

        JsonNode buses = array(root, "serialBuses", "$", false);
        Set<String> busIds = new HashSet<String>();
        for (int i = 0; i < buses.size(); i++) {
            validateBus(buses.get(i), "$.serialBuses[" + i + "]", busIds);
        }

        String canonical = canonicalizer.canonicalize(root);
        byte[] utf8 = canonical.getBytes(StandardCharsets.UTF_8);
        String prefixedHash = canonicalizer.contentHash(root);
        return new Artifact(canonical, utf8, prefixedHash.substring("sha256:".length()));
    }

    /** ConfigSnapshot 1.1: 1.0 plus the server-injected product identity. */
    public Artifact validateAndCanonicalizeV11(JsonNode root) {
        requireObject(root, "$", ROOT_FIELDS_V1_1);
        requireEquals(text(root, "schemaVersion", "$"), SCHEMA_VERSION_V1_1,
                "$.schemaVersion");
        requireProductIdentification(text(root, "productIdentification", "$"),
                "$.productIdentification");
        nonBlank(text(root, "workloadId", "$"), "$.workloadId");
        decimalId(text(root, "tenantId", "$"), "$.tenantId");
        decimalId(text(root, "siteId", "$"), "$.siteId");
        nonBlank(text(root, "siteCode", "$"), "$.siteCode");
        positiveInteger(root.get("configVersion"), "$.configVersion", Long.MAX_VALUE);
        try {
            OffsetDateTime.parse(text(root, "generatedAt", "$"));
        } catch (DateTimeParseException e) {
            invalid("$.generatedAt 必须是带偏移 RFC 3339 时间");
        }

        JsonNode buses = array(root, "serialBuses", "$", false);
        Set<String> busIds = new HashSet<String>();
        for (int i = 0; i < buses.size(); i++) {
            validateBus(buses.get(i), "$.serialBuses[" + i + "]", busIds);
        }

        String canonical = canonicalizer.canonicalize(root);
        byte[] utf8 = canonical.getBytes(StandardCharsets.UTF_8);
        String prefixedHash = canonicalizer.contentHash(root);
        return new Artifact(canonical, utf8, prefixedHash.substring("sha256:".length()));
    }

    /** Shared product identity rule for the API path and ConfigSnapshot 1.1. */
    public static String requireProductIdentification(String value, String path) {
        if (value == null || value.trim().isEmpty()) {
            invalid(path + " 不得为空");
        }
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 128) {
            invalid(path + " 长度必须在 1～128 个字符之间");
        }
        return value;
    }

    private static void validateBus(JsonNode bus, String path, Set<String> busIds) {
        requireObject(bus, path, BUS_FIELDS);
        String busId = nonBlank(text(bus, "busId", path), path + ".busId");
        unique(busIds, busId, path + ".busId");
        nonBlank(text(bus, "serialPort", path), path + ".serialPort");
        positiveInteger(bus.get("baudRate"), path + ".baudRate", 4_000_000L);
        integerRange(bus.get("dataBits"), path + ".dataBits", 5, 8);
        enumValue(text(bus, "stopBits", path), STOP_BITS, path + ".stopBits");
        enumValue(text(bus, "parity", path), PARITIES, path + ".parity");
        integerRange(bus.get("transmitDelayMs"), path + ".transmitDelayMs", 0, 60_000);
        bool(bus, "rs485Mode", path);
        JsonNode devices = array(bus, "devices", path, false);
        Set<String> deviceIds = new HashSet<String>();
        Set<Long> unitIds = new HashSet<Long>();
        for (int i = 0; i < devices.size(); i++) {
            validateDevice(devices.get(i), path + ".devices[" + i + "]", deviceIds, unitIds);
        }
    }

    private static void validateDevice(JsonNode device, String path, Set<String> deviceIds,
                                       Set<Long> unitIds) {
        requireObject(device, path, DEVICE_FIELDS);
        String deviceId = text(device, "deviceId", path);
        decimalId(deviceId, path + ".deviceId");
        unique(deviceIds, deviceId, path + ".deviceId");
        nonBlank(text(device, "deviceIdentification", path), path + ".deviceIdentification");
        long unitId = integerRange(device.get("unitId"), path + ".unitId", 1, 247);
        unique(unitIds, unitId, path + ".unitId");
        positiveInteger(device.get("pollIntervalMs"), path + ".pollIntervalMs", 86_400_000L);
        positiveInteger(device.get("requestTimeoutMs"), path + ".requestTimeoutMs", 60_000L);
        integerRange(device.get("maxRetries"), path + ".maxRetries", 0, 20);
        JsonNode points = array(device, "points", path, false);
        Set<String> pointCodes = new HashSet<String>();
        for (int i = 0; i < points.size(); i++) {
            validatePoint(points.get(i), path + ".points[" + i + "]", pointCodes);
        }
    }

    private static void validatePoint(JsonNode point, String path, Set<String> pointCodes) {
        requireObject(point, path, POINT_FIELDS);
        String code = nonBlank(text(point, "propertyCode", path), path + ".propertyCode");
        unique(pointCodes, code, path + ".propertyCode");
        enumValue(text(point, "function", path), FUNCTIONS, path + ".function");
        integerRange(point.get("address"), path + ".address", 0, 65_535);
        integerRange(point.get("quantity"), path + ".quantity", 1, 120);
        nonBlank(text(point, "dataType", path), path + ".dataType");
        enumValue(text(point, "byteOrder", path), ORDERS, path + ".byteOrder");
        enumValue(text(point, "wordOrder", path), ORDERS, path + ".wordOrder");
        decimal(text(point, "scale", path), path + ".scale");
        decimal(text(point, "offset", path), path + ".offset");
        enumValue(text(point, "dataPriority", path), PRIORITIES, path + ".dataPriority");
        bool(point, "writable", path);
        nonBlank(text(point, "pollGroup", path), path + ".pollGroup");
    }

    private static void requireObject(JsonNode node, String path, Set<String> exactFields) {
        if (node == null || !node.isObject()) {
            invalid(path + " 必须是对象");
        }
        Set<String> actual = new HashSet<String>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) actual.add(names.next());
        if (!actual.equals(exactFields)) {
            invalid(path + " 字段集合不匹配，缺失=" + difference(exactFields, actual)
                    + "，额外=" + difference(actual, exactFields));
        }
    }

    private static JsonNode array(JsonNode parent, String field, String path, boolean allowEmpty) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray() || (!allowEmpty && value.size() == 0)) {
            invalid(path + "." + field + " 必须是非空数组");
        }
        return value;
    }

    private static String text(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) invalid(path + "." + field + " 必须是字符串");
        return value.textValue();
    }

    private static void bool(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) invalid(path + "." + field + " 必须是布尔值");
    }

    private static long positiveInteger(JsonNode node, String path, long max) {
        return integerRange(node, path, 1, max);
    }

    private static long integerRange(JsonNode node, String path, long min, long max) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) invalid(path + " 必须是整数");
        long value = node.longValue();
        if (value < min || value > max) invalid(path + " 超出范围 [" + min + "," + max + "]");
        return value;
    }

    private static String nonBlank(String value, String path) {
        if (value == null || value.trim().isEmpty()) invalid(path + " 不得为空");
        return value;
    }

    private static void decimalId(String value, String path) {
        if (!DECIMAL_ID.matcher(value).matches()) invalid(path + " 必须是无符号十进制 ID 字符串");
    }

    private static void decimal(String value, String path) {
        if (!DECIMAL.matcher(value).matches() || "-0".equals(value)) {
            invalid(path + " 必须是非指数十进制字符串且不得为 -0");
        }
    }

    private static void enumValue(String value, Set<String> allowed, String path) {
        if (!allowed.contains(value)) invalid(path + " 不在允许枚举 " + allowed);
    }

    private static <T> void unique(Set<T> values, T value, String path) {
        if (!values.add(value)) invalid(path + " 重复: " + value);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<String>(left);
        result.removeAll(right);
        return result;
    }

    private static Set<String> fields(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    private static void requireEquals(String actual, String expected, String path) {
        if (!expected.equals(actual)) invalid(path + " 仅支持 " + expected);
    }

    private static void invalid(String detail) {
        throw new IllegalArgumentException(CODE_INVALID + ": " + detail);
    }

    public static IllegalArgumentException missingFact(String factPath) {
        return new IllegalArgumentException(CODE_FACT_MISSING + ": " + factPath);
    }

    /** 已验证且可直接持久化/下发的单一 canonical 字节事实。 */
    public static final class Artifact {
        private final String canonical;
        private final byte[] utf8;
        private final String sha256;

        Artifact(String canonical, byte[] utf8, String sha256) {
            this.canonical = canonical;
            this.utf8 = utf8.clone();
            this.sha256 = sha256;
        }

        public String canonical() { return canonical; }
        public byte[] utf8() { return utf8.clone(); }
        public String sha256() { return sha256; }
        public long lengthBytes() { return utf8.length; }
    }
}
