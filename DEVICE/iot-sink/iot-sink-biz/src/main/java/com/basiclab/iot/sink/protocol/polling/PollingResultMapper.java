package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorDevice;
import com.basiclab.iot.sink.polling.CollectorPoint;
import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * TD-001 §9.2 Poller 采集结果 → {@link TelemetryEnvelope} 列表映射。
 *
 * <p>collector 只接受已发布快照提供的 siteCode/configVersion/dataPriority；缺失事实直接拒绝，
 * 不生成占位站点或按当前模板猜测历史优先级。原始协议报文键 {@code _raw} 仅用于诊断，
 * 不作为物模型遥测写入 outbox。
 */
public final class PollingResultMapper {

    private static final Pattern DECIMAL = Pattern.compile("^[+-]?(0|[1-9][0-9]*)(\\.[0-9]+)?$");
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() * 1000L);

    private PollingResultMapper() {
    }

    /**
     * 将采集 values Map 映射为 TelemetryEnvelope 列表（每个属性一个 envelope）。
     *
     * @param device       设备 DO（提供 tenantId/deviceIdentification）
     * @param config       已应用的 collector 设备配置
     * @param values       采集结果（propertyCode → value，可包含诊断键 _raw）
     * @param protocolType 协议类型（如 "modbus-rtu"，作为 source）
     * @return envelope 列表（空 Map 返回空列表）
     */
    public static List<TelemetryEnvelope> toEnvelopes(
            DeviceDO device, IndustrialDeviceConfig config,
            Map<String, Object> values, String protocolType) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (device == null || device.getTenantId() == null) {
            throw new IllegalArgumentException("collector telemetry requires tenantId");
        }
        if (config == null || config.getSiteCode() == null || config.getSiteCode().isBlank()
                || isPlaceholderSite(config.getSiteCode())) {
            throw new IllegalArgumentException("collector telemetry requires published siteCode");
        }
        if (config.getConfigVersion() == null || config.getConfigVersion() <= 0
                || config.getConfigVersion() > TelemetryEnvelope.MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("collector telemetry requires positive configVersion");
        }
        if (device.getDeviceIdentification() == null || device.getDeviceIdentification().isBlank()) {
            throw new IllegalArgumentException("collector telemetry requires deviceIdentification");
        }
        if (protocolType == null || protocolType.isBlank()) {
            throw new IllegalArgumentException("collector telemetry requires protocolType");
        }

        Map<String, DataPriority> priorities = pointPriorities(config);
        String now = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
        String tenantId = device.getTenantId().toString();
        String source = protocolType.toLowerCase(Locale.ROOT).replace('_', '-');
        List<TelemetryEnvelope> envelopes = new ArrayList<>(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || "_raw".equals(entry.getKey())) {
                continue;
            }
            DataPriority priority = priorities.get(entry.getKey());
            if (priority == null) {
                throw new IllegalArgumentException("collector telemetry property is not in published snapshot: "
                        + entry.getKey());
            }
            envelopes.add(new TelemetryEnvelope(
                    TelemetryEnvelope.SCHEMA_VERSION,
                    TelemetryEnvelope.CANONICALIZATION_VERSION,
                    EnvelopeCanonicalCodec.generateMessageId(),
                    EnvelopeCanonicalCodec.generateMessageId(),
                    tenantId,
                    config.getSiteCode(),
                    device.getDeviceIdentification(),
                    entry.getKey(),
                    decimalString(entry.getValue(), entry.getKey()),
                    TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                    TelemetryQuality.GOOD,
                    priority,
                    now, now,
                    nextSequence(),
                    source,
                    config.getConfigVersion()
            ));
        }
        return envelopes;
    }

    /** Collector-only mapping from the local immutable snapshot; no central DO is consulted. */
    public static List<TelemetryEnvelope> toEnvelopes(
            CollectorConfigSnapshot snapshot, CollectorDevice device,
            Map<String, Object> values, String protocolType) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (snapshot == null || snapshot.tenantId() == null || snapshot.tenantId().isBlank()
                || snapshot.siteCode() == null || snapshot.siteCode().isBlank()
                || snapshot.configVersion() < 1 || snapshot.configVersion() > TelemetryEnvelope.MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("collector telemetry requires published snapshot identity");
        }
        if (device == null || device.deviceIdentification() == null || device.deviceIdentification().isBlank()) {
            throw new IllegalArgumentException("collector telemetry requires deviceIdentification");
        }
        if (protocolType == null || protocolType.isBlank()) {
            throw new IllegalArgumentException("collector telemetry requires protocolType");
        }
        Map<String, DataPriority> priorities = new HashMap<>();
        for (CollectorPoint point : device.points()) {
            if (point == null || point.propertyCode() == null || point.propertyCode().isBlank()) {
                continue;
            }
            DataPriority priority;
            try {
                priority = DataPriority.valueOf(point.dataPriority());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("unsupported dataPriority for " + point.propertyCode(), e);
            }
            if (priorities.putIfAbsent(point.propertyCode(), priority) != null) {
                throw new IllegalArgumentException("duplicate collector propertyCode: " + point.propertyCode());
            }
        }
        String now = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
        String source = protocolType.toLowerCase(Locale.ROOT).replace('_', '-');
        List<TelemetryEnvelope> envelopes = new ArrayList<>(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || "_raw".equals(entry.getKey())) {
                continue;
            }
            DataPriority priority = priorities.get(entry.getKey());
            if (priority == null) {
                throw new IllegalArgumentException("collector telemetry property is not in published snapshot: "
                        + entry.getKey());
            }
            envelopes.add(new TelemetryEnvelope(
                    TelemetryEnvelope.SCHEMA_VERSION,
                    TelemetryEnvelope.CANONICALIZATION_VERSION,
                    EnvelopeCanonicalCodec.generateMessageId(),
                    EnvelopeCanonicalCodec.generateMessageId(),
                    snapshot.tenantId(), snapshot.siteCode(), device.deviceIdentification(), entry.getKey(),
                    decimalString(entry.getValue(), entry.getKey()),
                    TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING, TelemetryQuality.GOOD, priority,
                    now, now, nextSequence(), source, snapshot.configVersion()));
        }
        return envelopes;
    }

    private static Map<String, DataPriority> pointPriorities(IndustrialDeviceConfig config) {
        Map<String, DataPriority> priorities = new HashMap<>();
        if (config.getPoints() == null) {
            return priorities;
        }
        for (IndustrialDeviceConfig.Point point : config.getPoints()) {
            if (point == null || !point.hasResolvedPropertyCode()) {
                continue;
            }
            if (point.getDataPriority() == null || point.getDataPriority().isBlank()) {
                throw new IllegalArgumentException("collector point requires dataPriority: "
                        + point.resolvedPropertyCode());
            }
            DataPriority priority;
            try {
                priority = DataPriority.valueOf(point.getDataPriority());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unsupported dataPriority for "
                        + point.resolvedPropertyCode() + ": " + point.getDataPriority(), e);
            }
            if (priorities.putIfAbsent(point.resolvedPropertyCode(), priority) != null) {
                throw new IllegalArgumentException("duplicate collector propertyCode: "
                        + point.resolvedPropertyCode());
            }
        }
        return priorities;
    }

    private static String decimalString(Object value, String propertyCode) {
        String decimal;
        if (value instanceof Boolean bool) {
            decimal = bool ? "1" : "0";
        } else if (value instanceof BigDecimal bigDecimal) {
            decimal = bigDecimal.toPlainString();
        } else if (value instanceof BigInteger || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            decimal = value.toString();
        } else if (value instanceof Float number) {
            if (!Float.isFinite(number)) {
                throw invalidDecimal(propertyCode, value);
            }
            decimal = new BigDecimal(Float.toString(number)).stripTrailingZeros().toPlainString();
        } else if (value instanceof Double number) {
            if (!Double.isFinite(number)) {
                throw invalidDecimal(propertyCode, value);
            }
            decimal = BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        } else {
            decimal = String.valueOf(value);
        }
        if (!DECIMAL.matcher(decimal).matches() || "-0".equals(decimal)) {
            throw invalidDecimal(propertyCode, value);
        }
        return decimal;
    }

    private static IllegalArgumentException invalidDecimal(String propertyCode, Object value) {
        return new IllegalArgumentException("collector telemetry value must be a non-exponent decimal string, property="
                + propertyCode + ", value=" + value);
    }

    private static long nextSequence() {
        long wallClockFloor = System.currentTimeMillis() * 1000L;
        long sequence = SEQUENCE.updateAndGet(previous -> Math.max(previous + 1, wallClockFloor));
        if (sequence > TelemetryEnvelope.MAX_SAFE_INTEGER) {
            throw new IllegalStateException("collector sequence exceeds JSON safe integer range");
        }
        return sequence;
    }

    private static boolean isPlaceholderSite(String siteCode) {
        return "pending".equalsIgnoreCase(siteCode) || "unassigned".equalsIgnoreCase(siteCode);
    }
}
