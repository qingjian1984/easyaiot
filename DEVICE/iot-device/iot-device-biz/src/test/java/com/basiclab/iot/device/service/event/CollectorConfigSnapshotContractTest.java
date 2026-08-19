package com.basiclab.iot.device.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorConfigSnapshotContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CollectorConfigSnapshotContract contract = new CollectorConfigSnapshotContract();

    @Test
    void keepsHistoricalV1GoldenBytesAndHashUnchanged() throws Exception {
        JsonNode fixture = readV1GoldenFixture();
        CollectorConfigSnapshotContract.Artifact artifact =
                contract.validateAndCanonicalize(fixture.get("input"));

        assertEquals(fixture.get("canonicalBase64").asText(),
                Base64.getEncoder().encodeToString(artifact.utf8()));
        assertEquals(fixture.get("bytes").asLong(), artifact.lengthBytes());
        assertEquals(fixture.get("sha256").asText(), artifact.sha256());
    }

    @Test
    void validatesAndProducesOneCanonicalByteFact() throws Exception {
        CollectorConfigSnapshotContract.Artifact artifact =
                contract.validateAndCanonicalize(validSnapshot());

        assertEquals(artifact.canonical(), new String(artifact.utf8(), StandardCharsets.UTF_8));
        assertEquals(artifact.utf8().length, artifact.lengthBytes());
        assertTrue(artifact.sha256().matches("[0-9a-f]{64}"));
        assertEquals(artifact.sha256(), contract.validateAndCanonicalize(validSnapshot()).sha256());
    }

    @Test
    void validatesV11WithServerProductIdentityAndHashesTheIdentity() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode snapshot =
                (com.fasterxml.jackson.databind.node.ObjectNode) validSnapshot();
        snapshot.put("schemaVersion", CollectorConfigSnapshotContract.SCHEMA_VERSION_V1_1);
        snapshot.put("productIdentification", "power-product-1");

        CollectorConfigSnapshotContract.Artifact artifact =
                contract.validateAndCanonicalizeV11(snapshot);

        assertTrue(artifact.canonical().contains("\"productIdentification\":\"power-product-1\""));
        assertEquals(787L, artifact.lengthBytes());
        assertEquals("ce302d258aac817620703e46e32a6e045255274f3bca0e93a0617cf230d997d2",
                artifact.sha256());
        assertEquals(artifact.canonical(), new String(artifact.utf8(), StandardCharsets.UTF_8));
        assertEquals(artifact.sha256(), contract.validateAndCanonicalize(snapshot).sha256());
    }

    @Test
    void rejectsV11BlankOrOverlongProductIdentity() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode blank =
                (com.fasterxml.jackson.databind.node.ObjectNode) validV11Snapshot();
        blank.put("productIdentification", " ");
        IllegalArgumentException blankError = assertThrows(IllegalArgumentException.class,
                () -> contract.validateAndCanonicalizeV11(blank));
        assertTrue(blankError.getMessage().contains(CollectorConfigSnapshotContract.CODE_INVALID));

        com.fasterxml.jackson.databind.node.ObjectNode overlong =
                (com.fasterxml.jackson.databind.node.ObjectNode) validV11Snapshot();
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 129; i++) value.append('p');
        overlong.put("productIdentification", value.toString());
        IllegalArgumentException overlongError = assertThrows(IllegalArgumentException.class,
                () -> contract.validateAndCanonicalizeV11(overlong));
        assertTrue(overlongError.getMessage().contains(CollectorConfigSnapshotContract.CODE_INVALID));
    }

    @Test
    void rejectsV11WrongVersionAndUnknownFields() throws Exception {
        JsonNode wrongVersion = validSnapshot();
        IllegalArgumentException versionError = assertThrows(IllegalArgumentException.class,
                () -> contract.validateAndCanonicalizeV11(wrongVersion));
        assertTrue(versionError.getMessage().contains(CollectorConfigSnapshotContract.CODE_INVALID));

        com.fasterxml.jackson.databind.node.ObjectNode extra =
                (com.fasterxml.jackson.databind.node.ObjectNode) validV11Snapshot();
        extra.put("unexpected", true);
        IllegalArgumentException extraError = assertThrows(IllegalArgumentException.class,
                () -> contract.validateAndCanonicalizeV11(extra));
        assertTrue(extraError.getMessage().contains(CollectorConfigSnapshotContract.CODE_INVALID));
    }

    @Test
    void rejectsProductIdentityInHistoricalClientSourceShape() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode forged =
                (com.fasterxml.jackson.databind.node.ObjectNode) validSnapshot();
        forged.put("productIdentification", "forged-product");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> contract.validateAndCanonicalize(forged));
        assertTrue(error.getMessage().contains(CollectorConfigSnapshotContract.CODE_INVALID));
    }

    @Test
    void rejectsEmptyProductionBusList() throws Exception {
        JsonNode snapshot = validSnapshot();
        ((com.fasterxml.jackson.databind.node.ObjectNode) snapshot)
                .set("serialBuses", MAPPER.createArrayNode());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> contract.validateAndCanonicalize(snapshot));
        assertTrue(error.getMessage().contains(CollectorConfigSnapshotContract.CODE_INVALID));
        assertTrue(error.getMessage().contains("非空数组"));
    }

    @Test
    void rejectsMissingPointFactsInsteadOfUsingDefaults() throws Exception {
        JsonNode snapshot = validSnapshot();
        JsonNode point = snapshot.at("/serialBuses/0/devices/0/points/0");
        ((com.fasterxml.jackson.databind.node.ObjectNode) point).remove("dataPriority");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> contract.validateAndCanonicalize(snapshot));
        assertTrue(error.getMessage().contains("dataPriority"));
    }

    @Test
    void rejectsFloatingBusinessDecimalAndExtraFields() throws Exception {
        JsonNode snapshot = validSnapshot();
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                snapshot.at("/serialBuses/0/devices/0/points/0")).put("scale", 1.0d);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> contract.validateAndCanonicalize(snapshot));
        assertTrue(error.getMessage().contains("scale 必须是字符串"));
    }

    @Test
    void reportsStableSourceFactMissingCode() {
        assertEquals("COLLECTOR_CONFIG_SOURCE_FACT_MISSING: device.siteId",
                CollectorConfigSnapshotContract.missingFact("device.siteId").getMessage());
    }

    private static JsonNode validSnapshot() throws Exception {
        return MAPPER.readTree("{"
                + "\"schemaVersion\":\"1.0\","
                + "\"workloadId\":\"collector-site-1001-a\","
                + "\"tenantId\":\"100\",\"siteId\":\"1001\",\"siteCode\":\"plant-a\","
                + "\"configVersion\":6,\"generatedAt\":\"2026-07-31T09:00:00+08:00\","
                + "\"serialBuses\":[{\"busId\":\"bus-a\","
                + "\"serialPort\":\"/dev/easyaiot/rs485-0\",\"baudRate\":9600,"
                + "\"dataBits\":8,\"stopBits\":\"1\",\"parity\":\"NONE\","
                + "\"transmitDelayMs\":0,\"rs485Mode\":true,\"devices\":[{"
                + "\"deviceId\":\"20001\",\"deviceIdentification\":\"METER-01\","
                + "\"unitId\":1,\"pollIntervalMs\":5000,\"requestTimeoutMs\":1000,"
                + "\"maxRetries\":2,\"points\":[{\"propertyCode\":\"active-power\","
                + "\"function\":\"HOLDING_REGISTER\",\"address\":0,\"quantity\":2,"
                + "\"dataType\":\"FLOAT32\",\"byteOrder\":\"BIG_ENDIAN\","
                + "\"wordOrder\":\"BIG_ENDIAN\",\"scale\":\"1\",\"offset\":\"0\","
                + "\"dataPriority\":\"METERING_TOTAL\",\"writable\":false,"
                + "\"pollGroup\":\"normal\"}]}]}]}");
    }

    private static JsonNode validV11Snapshot() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode snapshot =
                (com.fasterxml.jackson.databind.node.ObjectNode) validSnapshot();
        snapshot.put("schemaVersion", CollectorConfigSnapshotContract.SCHEMA_VERSION_V1_1);
        snapshot.put("productIdentification", "power-product-1");
        return snapshot;
    }

    private static JsonNode readV1GoldenFixture() throws IOException {
        InputStream input = CollectorConfigSnapshotContractTest.class.getResourceAsStream(
                "/collector-config-snapshot/v1-golden.json");
        if (input == null) throw new IllegalStateException("missing v1 collector golden fixture");
        try (InputStream stream = input) {
            return MAPPER.readTree(stream);
        }
    }
}
