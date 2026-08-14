package com.basiclab.iot.node.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeAgentSigningKeyProviderTest {

    private static final String CURRENT = "synthetic-node-key-012345678901234567890";
    private static final String PREVIOUS = "synthetic-old-key-012345678901234567890123";

    @Test
    void readsCurrentAndPreviousWithoutExposingSecretInToString() throws Exception {
        Path file = Files.createTempFile("node-signing", ".key");
        Files.writeString(file, "nodeId=7\ncurrentKeyId=k2\ncurrentKey=" + CURRENT
                + "\npreviousKeyId=k1\npreviousKey=" + PREVIOUS + "\n", StandardCharsets.UTF_8);
        FileNodeAgentSigningKeyProvider provider = new FileNodeAgentSigningKeyProvider(file);

        List<NodeAgentSigningKey> keys = provider.findKeys(7);
        assertEquals(2, keys.size());
        assertEquals("k2", keys.get(0).getKeyId());
        assertEquals(0, provider.findKeys(8).size());
        Files.deleteIfExists(file);
    }

    @Test
    void signerBindsNodeAndKeyIdentityToCanonicalHeaders() {
        NodeAgentSigningKey key = new NodeAgentSigningKey(7, "k2", CURRENT.getBytes(StandardCharsets.UTF_8));
        NodeAgentRequestSigner signer = new NodeAgentRequestSigner(
                nodeId -> List.of(key),
                Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC),
                new java.security.SecureRandom());

        var headers = signer.sign(7, "PUT", "/workload/collector/config", "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("7", headers.get(NodeAgentRequestSigner.NODE_ID_HEADER));
        assertEquals("k2", headers.get(NodeAgentRequestSigner.KEY_ID_HEADER));
        assertEquals(64, headers.get(NodeAgentRequestSigner.SIGNATURE_HEADER).length());
        assertEquals(32, headers.get(NodeAgentRequestSigner.NONCE_HEADER).length());
    }
}
