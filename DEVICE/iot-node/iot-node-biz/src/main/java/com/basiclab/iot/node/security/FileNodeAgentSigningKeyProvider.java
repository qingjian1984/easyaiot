package com.basiclab.iot.node.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从安装目录外的 0600 文件读取 current/previous 节点 key。 */
public final class FileNodeAgentSigningKeyProvider implements NodeAgentSigningKeyProvider {

    private final Path keyFile;

    public FileNodeAgentSigningKeyProvider(Path keyFile) {
        this.keyFile = keyFile;
    }

    @Override
    public List<NodeAgentSigningKey> findKeys(long nodeId) {
        try {
            verifyPermissions();
            Map<String, String> values = parse();
            String configuredNode = values.get("nodeId");
            if (configuredNode != null && !configuredNode.isBlank()
                    && Long.parseLong(configuredNode) != nodeId) {
                return Collections.emptyList();
            }
            String currentId = values.getOrDefault("currentKeyId", "").trim();
            byte[] current = values.getOrDefault("currentKey", "")
                    .getBytes(StandardCharsets.UTF_8);
            if (currentId.isEmpty() || current.length < 32) {
                return Collections.emptyList();
            }
            List<NodeAgentSigningKey> result = new ArrayList<>();
            result.add(new NodeAgentSigningKey(nodeId, currentId, current));
            String previousId = values.getOrDefault("previousKeyId", "").trim();
            byte[] previous = values.getOrDefault("previousKey", "")
                    .getBytes(StandardCharsets.UTF_8);
            if (!previousId.isEmpty() && !previousId.equals(currentId) && previous.length >= 32) {
                result.add(new NodeAgentSigningKey(nodeId, previousId, previous));
            }
            return result;
        } catch (IOException | RuntimeException e) {
            return Collections.emptyList();
        }
    }

    private Map<String, String> parse() throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String raw : Files.readAllLines(keyFile, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
        return values;
    }

    private void verifyPermissions() throws IOException {
        if (!Files.isRegularFile(keyFile)) {
            throw new IOException("missing signing key file");
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(keyFile);
            Set<PosixFilePermission> unsafe = EnumSet.of(
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);
            unsafe.retainAll(permissions);
            if (!unsafe.isEmpty()) {
                throw new IOException("signing key file permissions are too broad");
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows qualification is a separate OPEN-RUNTIME gate; do not invent ACL semantics here.
        }
    }
}
