package com.basiclab.iot.device.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TD-005 fail-closed 直接文件 provider：读取仓库外 HMAC Secret 文件并严格校验。
 *
 * <p>替代 Spring Config Tree 间接注入——后者在生产容器多属性源环境下不稳定，导致
 * {@link PowerModelActivationGuard} 反复 {@code POWER_MODEL_IDEMPOTENCY_SECRET_INVALID}。
 * 见 {@code iot-device-configtree-runtime-repair-deploy-attempt-20260811.md} §4。
 *
 * <p>路径为空时返回空 {@code byte[]}（mini 或未启用写链时合法，不读文件不校验）；
 * 路径非空时严格校验绝对路径/普通文件/严格 UTF-8/无 BOM/无换行/无 NUL/≥32 字节，
 * 任一不符抛 {@code POWER_MODEL_IDEMPOTENCY_SECRET_INVALID} 终止启动。
 *
 * <p>宪法 §2.5：异常消息只含原因类别，绝不包含 secret 值/哈希/字节样本/路径细节。
 * 校验语义与 {@code .scripts/docker/power_model_secret_file_preflight.ps1} 同源（部署侧 + 应用层双保险）。
 */
@Component
public final class PowerModelIdempotencySecretProvider {

    static final int MIN_BYTES = 32;

    private final byte[] secret;

    public PowerModelIdempotencySecretProvider(
            @Value("${easyaiot.power-model.idempotency-hmac-secret-file:}") String secretFilePath) {
        this.secret = load(secretFilePath);
    }

    private static byte[] load(String secretFilePath) {
        if (secretFilePath == null || secretFilePath.isEmpty()) {
            return new byte[0];
        }
        Path path = Paths.get(secretFilePath);
        if (!path.isAbsolute()) {
            throw fail("not_absolute");
        }
        if (!Files.isRegularFile(path)) {
            throw fail("not_regular_file");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw fail("file_not_readable");
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            throw fail("bom_present");
        }
        if (!isValidStrictUtf8(bytes)) {
            throw fail("invalid_utf8");
        }
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v == 0x0A || v == 0x0D) {
                throw fail("newline_present");
            }
            if (v == 0x00) {
                throw fail("nul_present");
            }
        }
        if (bytes.length < MIN_BYTES) {
            throw fail("length_below_32");
        }
        return bytes;
    }

    private static boolean isValidStrictUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static IllegalStateException fail(String reason) {
        return new IllegalStateException("POWER_MODEL_IDEMPOTENCY_SECRET_INVALID: " + reason);
    }

    /** 返回 secret 克隆，防外部修改；mini 或未启用时长度为 0。 */
    public byte[] getSecret() {
        return secret.clone();
    }
}
