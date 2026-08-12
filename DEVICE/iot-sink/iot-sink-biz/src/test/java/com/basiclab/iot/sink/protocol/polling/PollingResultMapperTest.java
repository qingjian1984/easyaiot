package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollingResultMapperTest {

    @Test
    void mapsPublishedSnapshotFactsAndSkipsRawDiagnostic() {
        IndustrialDeviceConfig config = config("site-a", 7L,
                point("voltage-a", "METERING_TOTAL"), point("breaker-closed", "SAFETY"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("voltage-a", new BigDecimal("220.50"));
        values.put("breaker-closed", true);
        values.put("_raw", Map.of("voltage-a", "01 03 02 00 DC"));

        List<TelemetryEnvelope> result = PollingResultMapper.toEnvelopes(
                device(), config, values, "MODBUS_RTU");

        assertEquals(2, result.size());
        assertEquals("220.50", result.get(0).value());
        assertEquals(DataPriority.METERING_TOTAL, result.get(0).dataPriority());
        assertEquals("1", result.get(1).value());
        assertEquals(DataPriority.SAFETY, result.get(1).dataPriority());
        assertEquals("site-a", result.get(0).siteCode());
        assertEquals(7L, result.get(0).configVersion());
        assertEquals("modbus-rtu", result.get(0).source());
        assertTrue(result.get(1).sequence() > result.get(0).sequence());
    }

    @Test
    void rejectsPlaceholderSiteAndMissingPriority() {
        assertThrows(IllegalArgumentException.class, () -> PollingResultMapper.toEnvelopes(
                device(), config("pending", 1L, point("voltage-a", "METERING_TOTAL")),
                Map.of("voltage-a", 220), "MODBUS_RTU"));
        assertThrows(IllegalArgumentException.class, () -> PollingResultMapper.toEnvelopes(
                device(), config("site-a", 1L, point("voltage-a", null)),
                Map.of("voltage-a", 220), "MODBUS_RTU"));
    }

    @Test
    void rejectsUnknownPropertyAndNonDecimalValue() {
        IndustrialDeviceConfig config = config("site-a", 1L,
                point("voltage-a", "NORMAL_TELEMETRY"));
        assertThrows(IllegalArgumentException.class, () -> PollingResultMapper.toEnvelopes(
                device(), config, Map.of("unknown", 1), "MODBUS_RTU"));
        assertThrows(IllegalArgumentException.class, () -> PollingResultMapper.toEnvelopes(
                device(), config, Map.of("voltage-a", "1e3"), "MODBUS_RTU"));
    }

    private static DeviceDO device() {
        return DeviceDO.builder().tenantId(123L).deviceIdentification("meter-01").build();
    }

    private static IndustrialDeviceConfig config(String siteCode, Long version,
                                                  IndustrialDeviceConfig.Point... points) {
        IndustrialDeviceConfig config = new IndustrialDeviceConfig();
        config.setSiteCode(siteCode);
        config.setConfigVersion(version);
        config.setPoints(List.of(points));
        return config;
    }

    private static IndustrialDeviceConfig.Point point(String code, String priority) {
        IndustrialDeviceConfig.Point point = new IndustrialDeviceConfig.Point();
        point.setPropertyCode(code);
        point.setDataPriority(priority);
        return point;
    }
}
