package com.basiclab.iot.node.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** LC02A-0 节点请求 signer；派发器在 LC02A-4 中显式调用。 */
public final class NodeAgentRequestSigner {

    public static final String NODE_ID_HEADER = "X-EasyAIoT-Node-Id";
    public static final String KEY_ID_HEADER = "X-EasyAIoT-Key-Id";
    public static final String TIMESTAMP_HEADER = "X-EasyAIoT-Timestamp";
    public static final String NONCE_HEADER = "X-EasyAIoT-Nonce";
    public static final String BODY_SHA256_HEADER = "X-EasyAIoT-Body-SHA256";
    public static final String SIGNATURE_HEADER = "X-EasyAIoT-Signature";

    private final NodeAgentSigningKeyProvider keyProvider;
    private final Clock clock;
    private final SecureRandom random;

    public NodeAgentRequestSigner(NodeAgentSigningKeyProvider keyProvider) {
        this(keyProvider, Clock.systemUTC(), new SecureRandom());
    }

    public NodeAgentRequestSigner(NodeAgentSigningKeyProvider keyProvider, Clock clock,
                                  SecureRandom random) {
        this.keyProvider = keyProvider;
        this.clock = clock;
        this.random = random;
    }

    public Map<String, String> sign(long nodeId, String method, String path, byte[] body) {
        List<NodeAgentSigningKey> keys = keyProvider.findKeys(nodeId);
        if (keys.isEmpty()) {
            throw new IllegalStateException("AGENT_SIGNING_KEY_UNAVAILABLE");
        }
        NodeAgentSigningKey key = keys.get(0);
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String nonce = randomNonce();
        String bodyHash = sha256Hex(body);
        String canonical = String.join("\n", method.toUpperCase(), path, timestamp, nonce,
                bodyHash, String.valueOf(nodeId), key.getKeyId());
        Map<String, String> result = new LinkedHashMap<>();
        result.put(NODE_ID_HEADER, String.valueOf(nodeId));
        result.put(KEY_ID_HEADER, key.getKeyId());
        result.put(TIMESTAMP_HEADER, timestamp);
        result.put(NONCE_HEADER, nonce);
        result.put(BODY_SHA256_HEADER, bodyHash);
        result.put(SIGNATURE_HEADER, hmacSha256Hex(key.secretCopy(), canonical));
        return result;
    }

    private String randomNonce() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return hex(bytes);
    }

    private static String sha256Hex(byte[] body) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String hmacSha256Hex(byte[] secret, String canonical) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
