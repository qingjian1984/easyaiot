package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-005 §5.1：成员 code 在各自成员类型内唯一（Schema 外语义合同）。
 */
class TemplateMemberValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateMemberValidator validator = new TemplateMemberValidator();

    @Test
    void uniqueCodesAcrossMemberTypesAreAccepted() throws IOException {
        JsonNode template = objectMapper.readTree(
                "{\"properties\":[{\"propertyCode\":\"voltage-a\"},{\"propertyCode\":\"current-a\"}],"
                        + "\"events\":[{\"eventCode\":\"measurement-abnormal\"}],"
                        + "\"services\":[{\"serviceCode\":\"reset-energy\"}]}");
        assertDoesNotThrow(() -> validator.requireUniqueMemberCodes(template));
    }

    @Test
    void duplicatePropertyCodeIsRejected() throws IOException {
        JsonNode template = objectMapper.readTree(
                "{\"properties\":[{\"propertyCode\":\"voltage-a\"},{\"propertyCode\":\"voltage-a\"}]}");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.requireUniqueMemberCodes(template));
        assertTrue(error.getMessage().startsWith("MODEL_MEMBER_CODE_DUPLICATE"));
        assertTrue(error.getMessage().contains("voltage-a"), "错误信息须指出重复 code");
    }

    @Test
    void duplicateEventAndServiceCodesAreRejected() throws IOException {
        JsonNode duplicateEvent = objectMapper.readTree(
                "{\"events\":[{\"eventCode\":\"alarm\"},{\"eventCode\":\"alarm\"}]}");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.requireUniqueMemberCodes(duplicateEvent))
                .getMessage().startsWith("MODEL_MEMBER_CODE_DUPLICATE"));

        JsonNode duplicateService = objectMapper.readTree(
                "{\"services\":[{\"serviceCode\":\"control\"},{\"serviceCode\":\"control\"}]}");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.requireUniqueMemberCodes(duplicateService))
                .getMessage().startsWith("MODEL_MEMBER_CODE_DUPLICATE"));
    }

    @Test
    void sameCodeInDifferentMemberTypesIsAllowed() throws IOException {
        JsonNode template = objectMapper.readTree(
                "{\"properties\":[{\"propertyCode\":\"status\"}],"
                        + "\"events\":[{\"eventCode\":\"status\"}],"
                        + "\"services\":[{\"serviceCode\":\"status\"}]}");
        assertDoesNotThrow(() -> validator.requireUniqueMemberCodes(template),
                "唯一性按成员类型分别约束，跨类型同 code 不冲突");
    }
}
