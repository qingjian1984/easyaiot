package com.basiclab.iot.device.config;

import com.basiclab.iot.device.service.model.PowerModelTemplateContentValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * TD-005 模板 API 的冻结 Schema 装配。
 * 默认不装配；显式开启模板 API 时对资源完整性执行启动期 fail-closed 校验。
 */
@Configuration
@ConditionalOnProperty(prefix = "easyaiot.power-model", name = "template-api-enabled",
        havingValue = "true")
public class PowerModelTemplateValidatorConfiguration {

    static final String SCHEMA_RESOURCE =
            "schemas/power-model/easyaiot-power-model-template.schema.json";
    static final String SCHEMA_SHA256 =
            "2431b8e7f25414aff89468d1b1daced2d10ce064d80b0816791912c7272bbae5";
    private static final int MAX_SCHEMA_BYTES = 64 * 1024;

    @Bean
    public PowerModelTemplateContentValidator powerModelTemplateContentValidator(
            ObjectMapper objectMapper) {
        return loadValidator(objectMapper, new ClassPathResource(SCHEMA_RESOURCE), SCHEMA_SHA256);
    }

    static PowerModelTemplateContentValidator loadValidator(ObjectMapper objectMapper,
                                                             Resource resource,
                                                             String expectedSha256) {
        try {
            byte[] bytes;
            try (InputStream input = resource.getInputStream()) {
                bytes = StreamUtils.copyToByteArray(input);
            }
            if (bytes.length == 0 || bytes.length > MAX_SCHEMA_BYTES) {
                throw invalid("冻结 Schema 大小不合法: " + bytes.length);
            }
            if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
                    && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) {
                throw invalid("冻结 Schema 禁止 UTF-8 BOM");
            }
            String actual = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    expectedSha256.getBytes(StandardCharsets.US_ASCII))) {
                throw invalid("冻结 Schema SHA-256 不匹配");
            }
            String json = strictUtf8(bytes);
            JsonNode schema = objectMapper.readTree(json);
            if (schema == null || !schema.isObject()) {
                throw invalid("冻结 Schema 根节点必须是对象");
            }
            return new PowerModelTemplateContentValidator(schema);
        } catch (IOException | NoSuchAlgorithmException error) {
            throw invalid("冻结 Schema 无法读取或校验", error);
        }
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            throw invalid("冻结 Schema 不是严格 UTF-8", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(String.format("%02x", current & 0xff));
        }
        return value.toString();
    }

    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException("POWER_MODEL_TEMPLATE_SCHEMA_STARTUP_INVALID: " + detail);
    }

    private static IllegalStateException invalid(String detail, Exception cause) {
        return new IllegalStateException("POWER_MODEL_TEMPLATE_SCHEMA_STARTUP_INVALID: " + detail,
                cause);
    }
}
