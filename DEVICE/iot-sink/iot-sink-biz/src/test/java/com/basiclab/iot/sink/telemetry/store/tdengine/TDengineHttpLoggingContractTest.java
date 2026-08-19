package com.basiclab.iot.sink.telemetry.store.tdengine;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** OPEN03-08A-S1 §26: Apache HTTP credentials/header/wire logging must stay closed. */
class TDengineHttpLoggingContractTest {

    @Test
    void publicConfigurationClosesSensitiveApacheLoggers() throws IOException {
        String application = resource("application.yaml");

        assertEquals("OFF", level(application, "org.apache.http.headers"));
        assertEquals("OFF", level(application, "org.apache.http.wire"));
        assertEquals("WARN", level(application, "org.apache.http.impl"));

        String loggingSection = application.substring(application.indexOf("logging:"));
        assertFalse(loggingSection.contains("Authorization:"));
        assertFalse(loggingSection.contains("user="));
        assertFalse(loggingSection.contains("password="));
    }

    @Test
    void testAppenderDoesNotEnableSensitiveApacheLoggers() throws IOException {
        String logback = resource("logback-test.xml");

        assertEquals("OFF", level(logback, "org.apache.http.headers"));
        assertEquals("OFF", level(logback, "org.apache.http.wire"));
        assertEquals("WARN", level(logback, "org.apache.http.impl"));
        assertFalse(logback.contains("Authorization:"));
        assertFalse(logback.contains("user="));
        assertFalse(logback.contains("password="));

        assertFalse(LoggerFactory.getLogger("org.apache.http.headers").isTraceEnabled());
        assertFalse(LoggerFactory.getLogger("org.apache.http.headers").isDebugEnabled());
        assertFalse(LoggerFactory.getLogger("org.apache.http.wire").isTraceEnabled());
        assertFalse(LoggerFactory.getLogger("org.apache.http.wire").isDebugEnabled());
        assertFalse(LoggerFactory.getLogger("org.apache.http.impl").isDebugEnabled());
    }

    private static String resource(String name) throws IOException {
        try (InputStream stream = TDengineHttpLoggingContractTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("missing test resource: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String level(String text, String logger) {
        Pattern pattern = Pattern.compile("(?m)^\\s*(?:<logger name=\\\""
                + Pattern.quote(logger)
                + "\\\" level=\\\"|"
                + Pattern.quote(logger)
                + ":\\s*)([A-Z]+)");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("missing logger level: " + logger);
        }
        return matcher.group(1);
    }
}
