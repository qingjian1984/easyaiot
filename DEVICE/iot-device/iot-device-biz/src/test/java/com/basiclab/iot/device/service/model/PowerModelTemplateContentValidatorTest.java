package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerModelTemplateContentValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode validExample;
    private static PowerModelTemplateContentValidator validator;

    @BeforeAll
    static void loadFrozenAssets() throws IOException {
        Path assets = locateAssets();
        JsonNode schema = MAPPER.readTree(Files.newInputStream(
                assets.resolve("easyaiot-power-model-template.schema.json")));
        validExample = MAPPER.readTree(Files.newInputStream(
                assets.resolve("example-standard-meter-1.0.0.json")));
        validator = new PowerModelTemplateContentValidator(schema);
    }

    @Test
    void frozenExamplePassesDraft202012AndSemanticValidation() {
        assertTrue(validator.validate(validExample).isEmpty());
    }

    @Test
    void reportsSchemaAndSemanticErrorsTogether() {
        JsonNode invalid = validExample.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).put("unexpected", true);
        ((com.fasterxml.jackson.databind.node.ArrayNode) invalid.path("properties"))
                .add(invalid.path("properties").get(0).deepCopy());

        List<PowerModelTemplateContentValidator.ValidationError> errors = validator.validate(invalid);

        assertTrue(errors.stream().anyMatch(error ->
                "MODEL_TEMPLATE_SCHEMA_INVALID".equals(error.getCode())));
        assertTrue(errors.stream().anyMatch(error ->
                "MODEL_MEMBER_CODE_DUPLICATE".equals(error.getCode())));
    }

    @Test
    void rejectsExternalSchemaReferenceBeforeValidatorConstruction() throws IOException {
        JsonNode untrusted = MAPPER.readTree("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"$ref\":\"https://attacker.invalid/schema.json\"}");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new PowerModelTemplateContentValidator(untrusted));
        assertTrue(error.getMessage().startsWith("MODEL_TEMPLATE_SCHEMA_UNTRUSTED_REF"));
    }

    private static Path locateAssets() {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && cursor != null; i++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve(".doc/规格/电力运维云平台/assets/model-templates");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("未找到冻结的物模型模板资产目录");
    }
}
