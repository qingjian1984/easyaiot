package com.basiclab.iot.device.config;

import com.basiclab.iot.device.service.model.PowerModelTemplateContentValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerModelTemplateValidatorConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(PowerModelTemplateValidatorConfiguration.class);

    @Test
    void validatorIsAbsentByDefault() {
        contextRunner.run(context ->
                assertTrue(context.getBeansOfType(PowerModelTemplateContentValidator.class)
                        .isEmpty()));
    }

    @Test
    void explicitTemplateApiFlagLoadsFrozenClasspathSchema() {
        contextRunner.withPropertyValues("easyaiot.power-model.template-api-enabled=true")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.getBeansOfType(PowerModelTemplateContentValidator.class)
                            .size() == 1);
                });
    }

    @Test
    void classpathSchemaHasExpectedDigestAndStrictEncoding() {
        assertDoesNotThrow(() -> PowerModelTemplateValidatorConfiguration.loadValidator(
                new ObjectMapper(),
                new ClassPathResource(PowerModelTemplateValidatorConfiguration.SCHEMA_RESOURCE),
                PowerModelTemplateValidatorConfiguration.SCHEMA_SHA256));
    }

    @Test
    void digestDriftFailsClosed() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PowerModelTemplateValidatorConfiguration.loadValidator(
                        new ObjectMapper(),
                        new ByteArrayResource("{}".getBytes(StandardCharsets.UTF_8)),
                        PowerModelTemplateValidatorConfiguration.SCHEMA_SHA256));
        assertTrue(error.getMessage().contains("SHA-256 不匹配"));
    }

    @Test
    void bomFailsClosedBeforeJsonParsing() {
        byte[] bom = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '{', '}'};
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PowerModelTemplateValidatorConfiguration.loadValidator(
                        new ObjectMapper(), new ByteArrayResource(bom),
                        PowerModelTemplateValidatorConfiguration.SCHEMA_SHA256));
        assertTrue(error.getMessage().contains("禁止 UTF-8 BOM"));
    }

    @Test
    void malformedUtf8FailsClosed() throws Exception {
        byte[] invalidUtf8 = {(byte) 0xc3, (byte) 0x28};
        StringBuilder hash = new StringBuilder(64);
        for (byte current : MessageDigest.getInstance("SHA-256").digest(invalidUtf8)) {
            hash.append(String.format("%02x", current & 0xff));
        }
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PowerModelTemplateValidatorConfiguration.loadValidator(
                        new ObjectMapper(), new ByteArrayResource(invalidUtf8), hash.toString()));
        assertTrue(error.getMessage().contains("不是严格 UTF-8"));
    }
}
