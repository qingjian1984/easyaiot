package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TD-005 §23 门禁 3：生产 Java 实现必须消费与 Python/Node 相同的 JCS golden。
 * golden 以 Base64 保存精确 canonical 字节，禁止任何重序列化假设。
 */
class JcsGoldenContractTest {

    private static final String GOLDEN_PATH = ".doc/规格/电力运维云平台/assets/model-templates/verification/jcs-golden.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JcsCanonicalizer canonicalizer = new JcsCanonicalizer();

    @Test
    void javaCanonicalizerMustReproduceEveryGoldenCase() throws IOException {
        JsonNode golden = objectMapper.readTree(Files.newBufferedReader(findWorkspaceRoot().resolve(GOLDEN_PATH)));
        assertEquals("jcs-rfc8785-v1", golden.path("canonicalizationVersion").asText());
        assertEquals("SHA-256", golden.path("hashAlgorithm").asText());

        for (JsonNode goldenCase : golden.path("cases")) {
            Path inputPath = findWorkspaceRoot()
                    .resolve(".doc/规格/电力运维云平台/assets/model-templates/verification")
                    .resolve(goldenCase.path("input").asText())
                    .normalize();
            JsonNode input = objectMapper.readTree(Files.newBufferedReader(inputPath));

            String canonical = canonicalizer.canonicalize(input);
            byte[] canonicalBytes = canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            assertEquals(goldenCase.path("canonicalBase64").asText(),
                    Base64.getEncoder().encodeToString(canonicalBytes),
                    "canonical 字节必须与 golden 一致: " + goldenCase.path("name").asText());
            assertEquals(goldenCase.path("bytes").asInt(), canonicalBytes.length,
                    "canonical 字节数必须与 golden 一致: " + goldenCase.path("name").asText());
            assertEquals("sha256:" + goldenCase.path("sha256").asText(),
                    canonicalizer.contentHash(input),
                    "contentHash 必须与 golden 一致: " + goldenCase.path("name").asText());
        }
    }

    @Test
    void canonicalizationMustBeOrderInsensitiveAndContentSensitive() throws IOException {
        JsonNode left = objectMapper.readTree("{\"b\":1,\"a\":{\"y\":[true,null],\"x\":\"电\"}}");
        JsonNode right = objectMapper.readTree("{\"a\":{\"x\":\"电\",\"y\":[true,null]},\"b\":1}");
        assertEquals(canonicalizer.canonicalize(left), canonicalizer.canonicalize(right),
                "键序不同、内容相同必须得到相同 canonical");

        JsonNode changed = objectMapper.readTree("{\"b\":2,\"a\":{\"y\":[true,null],\"x\":\"电\"}}");
        assertNotEquals(canonicalizer.contentHash(left), canonicalizer.contentHash(changed),
                "任一业务字段变化必须改变 contentHash");
    }

    @Test
    void nonFiniteNumbersMustBeRejected() {
        JcsCanonicalizer strict = new JcsCanonicalizer();
        assertThrows(IllegalArgumentException.class,
                () -> strict.canonicalize(objectMapper.getNodeFactory().numberNode(Double.NaN)),
                "RFC 8785 不允许非有限数值");
        assertThrows(IllegalArgumentException.class,
                () -> strict.canonicalize(objectMapper.getNodeFactory().numberNode(Double.POSITIVE_INFINITY)));
    }

    private Path findWorkspaceRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(GOLDEN_PATH))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到 TD-005 JCS golden 资产");
    }
}
