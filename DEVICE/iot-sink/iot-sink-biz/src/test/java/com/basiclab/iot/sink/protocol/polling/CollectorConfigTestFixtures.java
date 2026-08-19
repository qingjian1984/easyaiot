package com.basiclab.iot.sink.protocol.polling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;

import java.nio.charset.StandardCharsets;

/** Small canonical v1.1 fixture shared by the direct OPEN03-06 tests. */
final class CollectorConfigTestFixtures {
    private CollectorConfigTestFixtures() {
    }

    static byte[] canonical(String workloadId, long version, String propertyCode) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.1");
        root.put("productIdentification", "power-meter");
        root.put("workloadId", workloadId);
        root.put("tenantId", "1");
        root.put("siteId", "2");
        root.put("siteCode", "site-a");
        root.put("configVersion", version);
        root.put("generatedAt", "2026-08-17T00:00:00Z");
        ArrayNode buses = root.putArray("serialBuses");
        ObjectNode bus = buses.addObject();
        bus.put("busId", "bus-1");
        bus.put("serialPort", "COM1");
        bus.put("baudRate", 9600);
        bus.put("dataBits", 8);
        bus.put("stopBits", "1");
        bus.put("parity", "NONE");
        bus.put("transmitDelayMs", 0);
        bus.put("rs485Mode", true);
        ObjectNode device = bus.putArray("devices").addObject();
        device.put("deviceId", "10");
        device.put("deviceIdentification", "meter-10");
        device.put("unitId", 1);
        device.put("pollIntervalMs", 1000);
        device.put("requestTimeoutMs", 5000);
        device.put("maxRetries", 1);
        ObjectNode point = device.putArray("points").addObject();
        point.put("propertyCode", propertyCode);
        point.put("function", "HOLDING_REGISTER");
        point.put("address", 0);
        point.put("quantity", 1);
        point.put("dataType", "UINT16");
        point.put("byteOrder", "BIG_ENDIAN");
        point.put("wordOrder", "BIG_ENDIAN");
        point.put("scale", "1");
        point.put("offset", "0");
        point.put("dataPriority", "METERING_TOTAL");
        point.put("writable", false);
        point.put("pollGroup", "default");
        return new EnvelopeJcsCanonicalizer().canonicalize(root).getBytes(StandardCharsets.UTF_8);
    }
}
