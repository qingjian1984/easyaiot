package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TD-001 §9.2 Poller 采集结果 → {@link TelemetryEnvelope} 列表映射。
 *
 * <p>简化字段映射（标注"后续精确化"）：
 * <ul>
 *   <li>siteCode = "pending"（TD-004 站点关系后续注入）</li>
 *   <li>quality = GOOD（采集成功默认；异常质量后续 IndustrialDeviceConfig 注入）</li>
 *   <li>dataPriority = NORMAL_TELEMETRY（默认；SAFETY/ALARM 后续点位配置）</li>
 *   <li>sequence = 0（后续 CollectorConfigSnapshot 精确化）</li>
 *   <li>configVersion = 0（后续快照版本）</li>
 * </ul>
 */
public final class PollingResultMapper {

    private PollingResultMapper() {
    }

    /**
     * 将采集 values Map 映射为 TelemetryEnvelope 列表（每个属性一个 envelope）。
     *
     * @param device       设备 DO（提供 tenantId/deviceIdentification）
     * @param values       采集结果（propertyCode → value）
     * @param protocolType 协议类型（如 "modbus-rtu"，作为 source）
     * @return envelope 列表（空 Map 返回空列表）
     */
    public static List<TelemetryEnvelope> toEnvelopes(
            DeviceDO device, Map<String, Object> values, String protocolType) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        String now = Instant.now().toString();
        String tenantId = String.valueOf(device.getTenantId());
        String deviceIdentification = device.getDeviceIdentification();
        List<TelemetryEnvelope> envelopes = new ArrayList<>(values.size());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            envelopes.add(new TelemetryEnvelope(
                    TelemetryEnvelope.SCHEMA_VERSION,
                    TelemetryEnvelope.CANONICALIZATION_VERSION,
                    EnvelopeCanonicalCodec.generateMessageId(),
                    EnvelopeCanonicalCodec.generateMessageId(),
                    tenantId,
                    "pending",
                    deviceIdentification,
                    entry.getKey(),
                    String.valueOf(entry.getValue()),
                    TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                    TelemetryQuality.GOOD,
                    DataPriority.NORMAL_TELEMETRY,
                    now, now,
                    0,
                    protocolType,
                    0
            ));
        }
        return envelopes;
    }
}
