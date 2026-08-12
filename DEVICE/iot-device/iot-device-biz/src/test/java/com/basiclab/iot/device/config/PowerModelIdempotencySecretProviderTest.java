package com.basiclab.iot.device.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PowerModelIdempotencySecretProvider 合同：合法文件返回 secret，非法文件 fail-closed，
 * 异常消息绝不包含 secret 值/哈希/字节样本（宪法 §2.5 + 评审 deploy-attempt §4）。
 * 校验语义与 .scripts/docker/power_model_secret_file_preflight.ps1 同源。
 */
class PowerModelIdempotencySecretProviderTest {

    private static final byte[] SECRET_32 =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void emptyPathReturnsEmptySecret() {
        assertEquals(0, new PowerModelIdempotencySecretProvider("").getSecret().length);
    }

    @Test
    void nullPathReturnsEmptySecret() {
        assertEquals(0, new PowerModelIdempotencySecretProvider(null).getSecret().length);
    }

    @Test
    void valid32ByteAsciiFileReturnsSecret(@TempDir Path tempDir) throws Exception {
        Path file = writeSecret(tempDir, SECRET_32);
        assertArrayEquals(SECRET_32, new PowerModelIdempotencySecretProvider(file.toString()).getSecret());
    }

    @Test
    void valid64ByteBase64FileReturnsSecret(@TempDir Path tempDir) throws Exception {
        byte[] secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                .getBytes(StandardCharsets.UTF_8);
        Path file = writeSecret(tempDir, secret);
        assertArrayEquals(secret, new PowerModelIdempotencySecretProvider(file.toString()).getSecret());
    }

    @Test
    void multibyteUtf8AtLeast32BytesIsAccepted(@TempDir Path tempDir) throws Exception {
        // 中文 UTF-8 每字符 3 字节；16 字符 = 48 字节 ≥ 32，且为合法 UTF-8
        byte[] secret = "一二三四五六七八九十一二三四五六".getBytes(StandardCharsets.UTF_8);
        assertTrue(secret.length >= 32);
        Path file = writeSecret(tempDir, secret);
        assertArrayEquals(secret, new PowerModelIdempotencySecretProvider(file.toString()).getSecret());
    }

    @Test
    void getSecretReturnsDefensiveCopy(@TempDir Path tempDir) throws Exception {
        Path file = writeSecret(tempDir, SECRET_32);
        PowerModelIdempotencySecretProvider provider =
                new PowerModelIdempotencySecretProvider(file.toString());
        byte[] first = provider.getSecret();
        first[0] = 0;
        assertEquals(SECRET_32[0], provider.getSecret()[0]);
    }

    @Test
    void relativePathRejected() {
        assertInvalid("relative/path/secret", "not_absolute");
    }

    @Test
    void missingFileRejected(@TempDir Path tempDir) {
        assertInvalid(tempDir.resolve("nonexistent").toString(), "not_regular_file");
    }

    @Test
    void directoryRejected(@TempDir Path tempDir) {
        assertInvalid(tempDir.toString(), "not_regular_file");
    }

    @Test
    void bomRejected(@TempDir Path tempDir) throws Exception {
        byte[] bomSecret = new byte[3 + 32];
        bomSecret[0] = (byte) 0xEF;
        bomSecret[1] = (byte) 0xBB;
        bomSecret[2] = (byte) 0xBF;
        System.arraycopy(SECRET_32, 0, bomSecret, 3, 32);
        assertInvalid(writeSecret(tempDir, bomSecret).toString(), "bom_present");
    }

    @Test
    void newlineLfRejected(@TempDir Path tempDir) throws Exception {
        byte[] bad = new byte[33];
        System.arraycopy(SECRET_32, 0, bad, 0, 32);
        bad[32] = 0x0A;
        assertInvalid(writeSecret(tempDir, bad).toString(), "newline_present");
    }

    @Test
    void newlineCrRejected(@TempDir Path tempDir) throws Exception {
        byte[] bad = new byte[33];
        System.arraycopy(SECRET_32, 0, bad, 0, 32);
        bad[32] = 0x0D;
        assertInvalid(writeSecret(tempDir, bad).toString(), "newline_present");
    }

    @Test
    void nulRejected(@TempDir Path tempDir) throws Exception {
        byte[] bad = new byte[33];
        System.arraycopy(SECRET_32, 0, bad, 0, 32);
        bad[32] = 0x00;
        assertInvalid(writeSecret(tempDir, bad).toString(), "nul_present");
    }

    @Test
    void shortSecretRejected(@TempDir Path tempDir) throws Exception {
        assertInvalid(writeSecret(tempDir, "short".getBytes(StandardCharsets.UTF_8)).toString(),
                "length_below_32");
    }

    @Test
    void invalidUtf8Rejected(@TempDir Path tempDir) throws Exception {
        byte[] bad = new byte[32];
        for (int i = 0; i < 32; i++) {
            bad[i] = (byte) 0xFF;
        }
        assertInvalid(writeSecret(tempDir, bad).toString(), "invalid_utf8");
    }

    @Test
    void errorMessageNeverLeaksSecretMaterial(@TempDir Path tempDir) throws Exception {
        byte[] bomSecret = new byte[3 + 32];
        bomSecret[0] = (byte) 0xEF;
        bomSecret[1] = (byte) 0xBB;
        bomSecret[2] = (byte) 0xBF;
        System.arraycopy(SECRET_32, 0, bomSecret, 3, 32);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new PowerModelIdempotencySecretProvider(writeSecret(tempDir, bomSecret).toString()));
        String msg = e.getMessage();
        assertTrue(msg.startsWith("POWER_MODEL_IDEMPOTENCY_SECRET_INVALID"));
        assertFalse(msg.contains("0123456789"));
        assertFalse(msg.contains("abcdef"));
    }

    private static Path writeSecret(Path tempDir, byte[] content) throws Exception {
        Path file = tempDir.resolve("secret");
        Files.write(file, content);
        return file;
    }

    private static void assertInvalid(String path, String reason) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new PowerModelIdempotencySecretProvider(path));
        assertTrue(e.getMessage().contains(reason),
                "expected reason " + reason + " in " + e.getMessage());
    }
}
