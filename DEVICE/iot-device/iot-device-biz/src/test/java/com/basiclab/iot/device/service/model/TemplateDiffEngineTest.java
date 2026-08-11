package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TD-005 §7.1：服务端按结构化 diff 计算最低 SemVer 增量。
 */
class TemplateDiffEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateDiffEngine diffEngine = new TemplateDiffEngine();

    @Test
    void removingRequiredPropertyRequiresMajor() throws IOException {
        JsonNode base = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"unit\":\"V\",\"required\":true}]");
        JsonNode target = template("[]");
        assertEquals(ModelSemVer.Bump.MAJOR, diffEngine.diff(base, target).minimumBump());
    }

    @Test
    void changingTypeOrUnitSemanticsRequiresMajor() throws IOException {
        JsonNode base = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"unit\":\"V\",\"required\":false}]");
        JsonNode typeChanged = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"DOUBLE\",\"unit\":\"V\",\"required\":false}]");
        assertEquals(ModelSemVer.Bump.MAJOR, diffEngine.diff(base, typeChanged).minimumBump());

        JsonNode unitChanged = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"unit\":\"kV\",\"required\":false}]");
        assertEquals(ModelSemVer.Bump.MAJOR, diffEngine.diff(base, unitChanged).minimumBump());
    }

    @Test
    void tighteningRangeRequiresMajorRelaxingIsMinor() throws IOException {
        JsonNode base = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"min\":0,\"max\":300,\"required\":false}]");
        JsonNode tightened = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"min\":10,\"max\":300,\"required\":false}]");
        assertEquals(ModelSemVer.Bump.MAJOR, diffEngine.diff(base, tightened).minimumBump());

        JsonNode relaxed = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"min\":0,\"max\":500,\"required\":false}]");
        assertEquals(ModelSemVer.Bump.MINOR, diffEngine.diff(base, relaxed).minimumBump());
    }

    @Test
    void raisingRequiredRequiresMajor() throws IOException {
        JsonNode base = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":false}]");
        JsonNode raised = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":true}]");
        assertEquals(ModelSemVer.Bump.MAJOR, diffEngine.diff(base, raised).minimumBump());
    }

    @Test
    void addingOptionalMemberIsMinorAddingRequiredPropertyIsMajor() throws IOException {
        JsonNode base = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":false}]");
        JsonNode optionalAdded = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":false},"
                        + "{\"propertyCode\":\"current-a\",\"dataType\":\"FLOAT\",\"required\":false}]");
        assertEquals(ModelSemVer.Bump.MINOR, diffEngine.diff(base, optionalAdded).minimumBump());

        JsonNode requiredAdded = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":false},"
                        + "{\"propertyCode\":\"current-a\",\"dataType\":\"FLOAT\",\"required\":true}]");
        assertEquals(ModelSemVer.Bump.MAJOR, diffEngine.diff(base, requiredAdded).minimumBump());
    }

    @Test
    void changingHighRiskSemanticsRequiresMajor() throws IOException {
        JsonNode base = objectMapper.readTree(
                "{\"properties\":[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":false}],"
                        + "\"services\":[{\"serviceCode\":\"remote-open\",\"riskLevel\":\"MEDIUM\","
                        + "\"idempotency\":\"REQUIRED\",\"inputs\":[],\"outputs\":[]}]}");
        JsonNode riskChanged = objectMapper.readTree(
                "{\"properties\":[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":false}],"
                        + "\"services\":[{\"serviceCode\":\"remote-open\",\"riskLevel\":\"HIGH_RISK\","
                        + "\"idempotency\":\"REQUIRED\",\"inputs\":[],\"outputs\":[]}]}");
        assertEquals(ModelSemVer.Bump.MAJOR, diffEngine.diff(base, riskChanged).minimumBump());
    }

    @Test
    void displayOnlyChangeIsPatch() throws IOException {
        JsonNode base = objectMapper.readTree(
                "{\"templateName\":\"标准电表\",\"properties\":[{\"propertyCode\":\"voltage-a\","
                        + "\"propertyName\":\"A 相电压\",\"dataType\":\"FLOAT\",\"required\":false}]}");
        JsonNode renamed = objectMapper.readTree(
                "{\"templateName\":\"标准电表（修订）\",\"properties\":[{\"propertyCode\":\"voltage-a\","
                        + "\"propertyName\":\"A 相电压（display）\",\"dataType\":\"FLOAT\",\"required\":false}]}");
        TemplateDiffEngine.DiffResult result = diffEngine.diff(base, renamed);
        assertEquals(ModelSemVer.Bump.PATCH, result.minimumBump());
    }

    @Test
    void identicalContentKeepsPatchFloor() throws IOException {
        JsonNode base = template(
                "[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":false}]");
        assertEquals(ModelSemVer.Bump.PATCH, diffEngine.diff(base, base).minimumBump());
    }

    @Test
    void versionAndTemplateIdentityMetadataDoNotForceMajor() throws IOException {
        JsonNode base = objectMapper.readTree("{\"schemaVersion\":\"1.0.0\","
                + "\"templateCode\":\"std-meter\",\"templateKind\":\"STANDARD\","
                + "\"deviceType\":\"METER\",\"version\":\"1.0.0\","
                + "\"properties\":[{\"propertyCode\":\"voltage-a\","
                + "\"dataType\":\"FLOAT\",\"required\":false}]}");
        JsonNode target = objectMapper.readTree("{\"schemaVersion\":\"1.0.0\","
                + "\"templateCode\":\"vendor-meter\",\"templateKind\":\"VENDOR\","
                + "\"deviceType\":\"METER\",\"version\":\"2.0.0\","
                + "\"base\":{\"templateCode\":\"std-meter\",\"version\":\"1.0.0\"},"
                + "\"properties\":[{\"propertyCode\":\"voltage-a\","
                + "\"dataType\":\"FLOAT\",\"required\":false}]}");
        assertEquals(ModelSemVer.Bump.PATCH, diffEngine.diff(base, target).minimumBump());
    }

    @Test
    void highestSeverityWinsAcrossMemberTypes() throws IOException {
        JsonNode base = objectMapper.readTree(
                "{\"properties\":[{\"propertyCode\":\"voltage-a\",\"dataType\":\"FLOAT\",\"required\":false}],"
                        + "\"events\":[{\"eventCode\":\"alarm\"}]}");
        // 事件 display 变化（PATCH）+ 属性类型变化（MAJOR）→ MAJOR
        JsonNode target = objectMapper.readTree(
                "{\"properties\":[{\"propertyCode\":\"voltage-a\",\"dataType\":\"INT32\",\"required\":false}],"
                        + "\"events\":[{\"eventCode\":\"alarm\",\"description\":\"文档修订\"}]}");
        assertEquals(ModelSemVer.Bump.MAJOR, diffEngine.diff(base, target).minimumBump());
    }

    private JsonNode template(String propertiesJson) throws IOException {
        return objectMapper.readTree("{\"properties\":" + propertiesJson + "}");
    }
}
