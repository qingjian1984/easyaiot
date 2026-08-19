package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigErrorCode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectorConfigSchemaContractTest {
    private static final String WORKLOAD = "collector-site-1001-a";

    @Test
    void allThreeSchemaCopiesRemainByteAndHashIdentical() throws Exception {
        byte[] sink;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "schema/collector/v1.1/collector-config-snapshot-v1.1.json")) {
            if (stream == null) {
                throw new IllegalStateException("collector schema resource is missing from the test classpath");
            }
            sink = stream.readAllBytes();
        }
        Path repository = repositoryRoot();
        byte[] device = Files.readAllBytes(repository.resolve(Path.of("DEVICE", "iot-device", "iot-device-api", "src",
                "main", "resources", "schema", "collector", "v1.1", "collector-config-snapshot-v1.1.json")));
        byte[] node = Files.readAllBytes(repository.resolve(Path.of("NODE", "schemas",
                "collector-config-snapshot-v1.1.json")));
        assertArrayEquals(sink, device);
        assertArrayEquals(sink, node);
        assertEquals("52FCC23AE0DF65BE19C902E604611A4078ABF9E89B13EF91E5DC05D088C7A28A",
                hex(MessageDigest.getInstance("SHA-256").digest(sink)));
    }

    @Test
    void codecRequiresJcsOriginalBytesAndRejectsDuplicateOrWrongIdentity() {
        CollectorConfigSnapshotCodec codec = new CollectorConfigSnapshotCodec();
        byte[] valid = CollectorConfigTestFixtures.canonical(WORKLOAD, 1, "voltage");
        assertEquals(1, codec.decode(valid, WORKLOAD).snapshot().configVersion());
        byte[] pretty = (new String(valid, StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_CANONICAL_INVALID,
                assertThrows(CollectorConfigStateException.class, () -> codec.decode(pretty, WORKLOAD)).errorCode());
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_WORKLOAD_MISMATCH,
                assertThrows(CollectorConfigStateException.class, () -> codec.decode(valid, "other")).errorCode());
        byte[] duplicate = new String(valid, StandardCharsets.UTF_8)
                .replaceFirst("\\\"siteCode\\\":\\\"site-a\\\"", "\\\"siteCode\\\":\\\"site-a\\\",\\\"siteCode\\\":\\\"site-a\\\"")
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_JSON_INVALID,
                assertThrows(CollectorConfigStateException.class, () -> codec.decode(duplicate, WORKLOAD)).errorCode());
    }

    @Test
    void codecRejectsOversizeMalformedUnknownMissingAndUnsafeVersions() {
        CollectorConfigSnapshotCodec codec = new CollectorConfigSnapshotCodec();
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_TOO_LARGE,
                assertThrows(CollectorConfigStateException.class,
                        () -> codec.decode(new byte[CollectorConfigSnapshotCodec.MAX_PAYLOAD_BYTES + 1], WORKLOAD))
                        .errorCode());
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_JSON_INVALID,
                assertThrows(CollectorConfigStateException.class,
                        () -> codec.decode("{".getBytes(StandardCharsets.UTF_8), WORKLOAD)).errorCode());

        String valid = new String(CollectorConfigTestFixtures.canonical(WORKLOAD, 1, "voltage"),
                StandardCharsets.UTF_8);
        byte[] unknown = ("{\"unknown\":1," + valid.substring(1)).getBytes(StandardCharsets.UTF_8);
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID,
                assertThrows(CollectorConfigStateException.class, () -> codec.decode(unknown, WORKLOAD)).errorCode());
        byte[] missing = valid.replace("\"siteCode\":\"site-a\",", "").getBytes(StandardCharsets.UTF_8);
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID,
                assertThrows(CollectorConfigStateException.class, () -> codec.decode(missing, WORKLOAD)).errorCode());
        byte[] unsafeVersion = CollectorConfigTestFixtures.canonical(WORKLOAD, 0, "voltage");
        assertEquals(CollectorConfigErrorCode.COLLECTOR_CONFIG_SCHEMA_INVALID,
                assertThrows(CollectorConfigStateException.class,
                        () -> codec.decode(unsafeVersion, WORKLOAD)).errorCode());
    }

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder();
        for (byte item : value) {
            out.append(String.format("%02X", item));
        }
        return out.toString();
    }

    private static Path repositoryRoot() {
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("NODE").resolve("schemas"))
                    && Files.isDirectory(cursor.resolve("DEVICE"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("repository root cannot be located from " + System.getProperty("user.dir"));
    }
}
