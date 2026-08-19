package com.basiclab.iot.node.service.collector;

import com.basiclab.iot.node.domain.collector.CollectorDeploymentProfile;
import com.basiclab.iot.node.domain.collector.CollectorVolumeMode;
import com.basiclab.iot.node.domain.collector.CollectorWorkloadSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * provider-side WorkloadSpec 1.0 合同校验器。
 *
 * <p>校验器只解析 Schema、检查 allowlist/能力边界并构建不可变 artifact；它不调用 NODE、
 * 不写数据库、不生成 Compose，也不启动任何 workload。</p>
 */
public final class CollectorWorkloadSpecValidator {

    public static final String SCHEMA_RESOURCE =
            "schema/collector/workload/v1/collector-workload-spec-v1.json";
    private static final Pattern DECIMAL = Pattern.compile("^(0|[1-9][0-9]*)(\\.[0-9]+)?$");
    private static final Pattern DIGEST = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern CONTAINER_SERIAL = Pattern.compile("^/dev/easyaiot/rs485-[0-9]{1,2}$");
    private static final Pattern FINGERPRINT = Pattern.compile("^[A-Za-z0-9:_./-]+$");
    private static final BigInteger BIGINT_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final long MIN_MEMORY_BYTES = 1L;
    private static final long MIN_TIMEOUT_SECONDS = 1L;
    private static final long MAX_DISPATCH_TIMEOUT_SECONDS = 3_600L;
    private static final long MAX_CONFIG_TIMEOUT_SECONDS = 7_200L;
    private static final long MAX_HEALTH_WINDOW_SECONDS = 3_600L;

    private final ObjectMapper objectMapper;
    private final JsonNode schema;
    private final Set<String> allowedRepositories;
    private final Path collectorRoot;
    private final Set<Path> serialDeviceAllowlist;
    private final BigDecimal maxCpuCores;
    private final long maxMemoryBytes;

    /**
     * 安装器必须显式注入 capability 配额；本包不提供物理/生产默认配额，也不读取中心配置或数据库。
     */
    public CollectorWorkloadSpecValidator(ObjectMapper objectMapper,
                                          Set<String> allowedRepositories,
                                          Path collectorRoot,
                                          Set<Path> serialDeviceAllowlist,
                                          BigDecimal maxCpuCores,
                                          long maxMemoryBytes) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.schema = loadSchema(objectMapper);
        if (allowedRepositories == null || allowedRepositories.isEmpty()
                || allowedRepositories.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("COLLECTOR_WORKLOAD_CONFIGURATION_INVALID: 镜像白名单不能为空");
        }
        this.allowedRepositories = Set.copyOf(allowedRepositories);
        this.collectorRoot = normalizeConfiguredRoot(collectorRoot);
        this.serialDeviceAllowlist = normalizeSerialAllowlist(serialDeviceAllowlist);
        if (maxCpuCores == null || maxCpuCores.signum() <= 0
                || maxMemoryBytes < MIN_MEMORY_BYTES) {
            throw new IllegalArgumentException("COLLECTOR_WORKLOAD_CONFIGURATION_INVALID: 资源上限不合法");
        }
        this.maxCpuCores = maxCpuCores.stripTrailingZeros();
        this.maxMemoryBytes = maxMemoryBytes;
    }

    /** 默认按 standard 语义验证。 */
    public CollectorWorkloadSpecArtifact validateAndBuild(JsonNode payload) {
        return validateAndBuild(payload, CollectorDeploymentProfile.STANDARD);
    }

    /**
     * 先执行仓库 Schema 的结构/类型校验，再执行 provider allowlist、路径和能力语义校验。
     */
    public CollectorWorkloadSpecArtifact validateAndBuild(JsonNode payload,
                                                          CollectorDeploymentProfile profile) {
        if (profile == null) {
            throw invalid("COLLECTOR_WORKLOAD_PROFILE_INVALID", "$", "部署档位不能为空");
        }
        if (profile == CollectorDeploymentProfile.MINI) {
            throw invalid("COLLECTOR_WORKLOAD_PROFILE_UNSUPPORTED", "$", "mini 不启用电力 collector");
        }
        validateAgainstSchema(payload, schema, "$");
        if (payload == null || !payload.isObject()) {
            throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", "$", "根节点必须是 object");
        }

        String nodeId = text(payload, "nodeId");
        requireBigintDecimal(nodeId, "$.nodeId");
        JsonNode image = payload.get("image");
        String repository = text(image, "repository");
        if (!allowedRepositories.contains(repository)) {
            throw invalid("COLLECTOR_WORKLOAD_IMAGE_REPOSITORY_FORBIDDEN",
                    "$.image.repository", "镜像仓库不在安装侧白名单");
        }
        String digest = text(image, "digest");
        if (!DIGEST.matcher(digest).matches()) {
            throw invalid("COLLECTOR_WORKLOAD_IMAGE_DIGEST_INVALID",
                    "$.image.digest", "镜像必须使用小写 sha256 digest");
        }

        JsonNode config = payload.get("config");
        long configVersion = integralLong(config.get("version"), "$.config.version");
        String configSha256 = text(config, "sha256");
        if (!HASH.matcher(configSha256).matches()) {
            throw invalid("COLLECTOR_WORKLOAD_CONFIG_INVALID", "$.config.sha256", "配置摘要必须是 64 位小写十六进制");
        }
        String targetPath = text(config, "targetPath");
        if (!CollectorWorkloadSpec.CONFIG_TARGET_PATH.equals(targetPath)) {
            throw invalid("COLLECTOR_WORKLOAD_CONFIG_INVALID", "$.config.targetPath",
                    "配置目标必须是固定容器路径");
        }

        JsonNode resources = payload.get("resources");
        String cpuCores = text(resources, "cpuCores");
        BigDecimal cpu = parsePositiveDecimal(cpuCores, "$.resources.cpuCores");
        if (cpu.compareTo(maxCpuCores) > 0) {
            throw invalid("COLLECTOR_WORKLOAD_RESOURCE_LIMIT_EXCEEDED",
                    "$.resources.cpuCores", "超出安装侧 CPU 配额");
        }
        long memoryBytes = integralLong(resources.get("memoryBytes"), "$.resources.memoryBytes");
        if (memoryBytes < MIN_MEMORY_BYTES || memoryBytes > maxMemoryBytes) {
            throw invalid("COLLECTOR_WORKLOAD_RESOURCE_LIMIT_EXCEEDED",
                    "$.resources.memoryBytes", "超出安装侧内存配额");
        }

        List<CollectorWorkloadSpec.SerialDeviceSpec> serialDevices = validateSerialDevices(
                payload.get("serialDevices"));
        List<CollectorWorkloadSpec.VolumeSpec> volumes = validateVolumes(
                payload.get("volumes"), text(payload, "workloadId"));

        String brokerRef = text(payload, "brokerRef");
        String expectedBrokerPrefix = "secret://node/" + nodeId + "/collector/";
        if (!brokerRef.startsWith(expectedBrokerPrefix)) {
            throw invalid("COLLECTOR_WORKLOAD_BROKER_REF_INVALID", "$.brokerRef",
                    "Broker 引用必须绑定当前 nodeId");
        }

        JsonNode policy = payload.get("updatePolicy");
        long dispatchTimeout = boundedTimeout(policy.get("dispatchAckTimeoutSeconds"),
                "$.updatePolicy.dispatchAckTimeoutSeconds", MAX_DISPATCH_TIMEOUT_SECONDS);
        long configTimeout = boundedTimeout(policy.get("configApplyTimeoutSeconds"),
                "$.updatePolicy.configApplyTimeoutSeconds", MAX_CONFIG_TIMEOUT_SECONDS);
        long healthWindow = boundedTimeout(policy.get("healthWindowSeconds"),
                "$.updatePolicy.healthWindowSeconds", MAX_HEALTH_WINDOW_SECONDS);

        CollectorWorkloadSpec spec = new CollectorWorkloadSpec(
                text(payload, "specVersion"),
                text(payload, "workloadType"),
                text(payload, "workloadId"),
                nodeId,
                new CollectorWorkloadSpec.ImageSpec(repository, digest),
                text(payload, "springProfile"),
                new CollectorWorkloadSpec.ConfigSpec(configVersion, configSha256, targetPath),
                new CollectorWorkloadSpec.ResourcesSpec(cpuCores, memoryBytes),
                serialDevices,
                volumes,
                brokerRef,
                new CollectorWorkloadSpec.UpdatePolicySpec(
                        dispatchTimeout, configTimeout, healthWindow,
                        policy.get("autoRollback").booleanValue()));

        ObjectNode canonicalPayload = payload.deepCopy();
        ((ObjectNode) canonicalPayload.get("config")).put("targetPath", targetPath);
        for (int i = 0; i < serialDevices.size(); i++) {
            ((ObjectNode) canonicalPayload.withArray("serialDevices").get(i))
                    .put("hostPath", serialDevices.get(i).getHostPath());
        }
        for (int i = 0; i < volumes.size(); i++) {
            ((ObjectNode) canonicalPayload.withArray("volumes").get(i))
                    .put("hostPath", volumes.get(i).getHostPath());
        }
        byte[] canonicalBytes = canonicalBytes(canonicalPayload);
        return new CollectorWorkloadSpecArtifact(spec, canonicalBytes, sha256(canonicalBytes));
    }

    private List<CollectorWorkloadSpec.SerialDeviceSpec> validateSerialDevices(JsonNode devices) {
        List<CollectorWorkloadSpec.SerialDeviceSpec> result = new ArrayList<>(devices.size());
        Set<String> hostPaths = new HashSet<>();
        Set<String> containerPaths = new HashSet<>();
        for (int i = 0; i < devices.size(); i++) {
            JsonNode device = devices.get(i);
            String path = text(device, "hostPath");
            String normalized = requireSerialPath(path, "$.serialDevices[" + i + "].hostPath");
            if (!hostPaths.add(normalized)) {
                throw invalid("COLLECTOR_WORKLOAD_SERIAL_INVALID",
                        "$.serialDevices[" + i + "].hostPath", "串口路径重复");
            }
            String containerPath = text(device, "containerPath");
            if (!CONTAINER_SERIAL.matcher(containerPath).matches()
                    || !containerPaths.add(containerPath)) {
                throw invalid("COLLECTOR_WORKLOAD_SERIAL_INVALID",
                        "$.serialDevices[" + i + "].containerPath", "容器串口映射不合法或重复");
            }
            String fingerprint = text(device, "hardwareFingerprint");
            if (!FINGERPRINT.matcher(fingerprint).matches()) {
                throw invalid("COLLECTOR_WORKLOAD_SERIAL_INVALID",
                        "$.serialDevices[" + i + "].hardwareFingerprint", "硬件指纹字符不合法");
            }
            result.add(new CollectorWorkloadSpec.SerialDeviceSpec(
                    normalized, containerPath, fingerprint, device.get("readOnly").booleanValue()));
        }
        return result;
    }

    private List<CollectorWorkloadSpec.VolumeSpec> validateVolumes(JsonNode volumes, String workloadId) {
        List<CollectorWorkloadSpec.VolumeSpec> result = new ArrayList<>(volumes.size());
        Set<String> names = new HashSet<>();
        for (int i = 0; i < volumes.size(); i++) {
            JsonNode volume = volumes.get(i);
            String name = text(volume, "name");
            if (!CollectorWorkloadSpec.VOLUME_NAME.equals(name) || !names.add(name)) {
                throw invalid("COLLECTOR_WORKLOAD_VOLUME_INVALID",
                        "$.volumes[" + i + "].name", "只允许唯一 outbox 卷");
            }
            String hostPath = requireCollectorPath(
                    text(volume, "hostPath"), "$.volumes[" + i + "].hostPath");
            Path expected = collectorRoot.resolve(workloadId).resolve("outbox").normalize();
            if (!expected.equals(Path.of(hostPath).toAbsolutePath().normalize())) {
                throw invalid("COLLECTOR_WORKLOAD_VOLUME_INVALID",
                        "$.volumes[" + i + "].hostPath", "outbox 路径必须绑定 workloadId");
            }
            if (!CollectorWorkloadSpec.OUTBOX_CONTAINER_PATH.equals(text(volume, "containerPath"))
                    || !CollectorVolumeMode.RW.getValue().equals(text(volume, "mode"))) {
                throw invalid("COLLECTOR_WORKLOAD_VOLUME_INVALID",
                        "$.volumes[" + i + "]", "outbox 只允许固定 rw 映射");
            }
            result.add(new CollectorWorkloadSpec.VolumeSpec(
                    name, hostPath, text(volume, "containerPath"), CollectorVolumeMode.RW));
        }
        return result;
    }

    private String requireCollectorPath(String raw, String path) {
        Path normalized = normalizePath(raw, path);
        if (normalized.equals(collectorRoot) || !normalized.startsWith(collectorRoot)) {
            throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path,
                    "路径必须位于 collector 专用根且不得是根目录");
        }
        ensureNoSymlinkEscape(normalized, collectorRoot, path);
        return normalized.toString().replace('\\', '/');
    }

    private String requireSerialPath(String raw, String path) {
        Path normalized = normalizePath(raw, path);
        if (normalized.equals(normalized.getRoot()) || isDockerSocket(normalized)) {
            throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "串口路径不允许是根目录或 Docker socket");
        }
        for (Path allowed : serialDeviceAllowlist) {
            if (allowed.equals(normalized)) {
                ensureSerialSymlinkSafe(normalized, path);
                return normalized.toString().replace('\\', '/');
            }
        }
        throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "串口路径不在安装侧白名单");
    }

    /**
     * Linux `/dev/serial/by-id` 本身常见为指向 `/dev/tty*` 的 symlink；允许其在 `/dev` 内解析，
     * 但拒绝被替换为跳出设备根的链接。不存在的设备路径不做 IO 创建，交由后续 Agent 探测。
     */
    private static void ensureSerialSymlinkSafe(Path candidate, String path) {
        String portable = candidate.toString().replace('\\', '/');
        if (!portable.startsWith("/dev/") && !portable.contains(":/dev/")) {
            return;
        }
        Path serialRoot = Path.of("/dev").toAbsolutePath().normalize();
        if (!Files.exists(serialRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path existing = candidate;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return;
        }
        try {
            Path realRoot = serialRoot.toRealPath();
            Path realExisting = existing.toRealPath();
            if (!realExisting.startsWith(realRoot)) {
                throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "串口符号链接逃逸设备根");
            }
            Path cursor = serialRoot;
            Path relative = serialRoot.relativize(existing);
            for (Path part : relative) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor) && !cursor.toRealPath().startsWith(realRoot)) {
                    throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "串口符号链接逃逸设备根");
                }
            }
        } catch (IOException error) {
            throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "串口路径无法安全解析");
        }
    }

    private Path normalizePath(String raw, String path) {
        if (raw == null || raw.isBlank()) {
            throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "路径不能为空");
        }
        String portable = raw.replace('\\', '/');
        for (String segment : portable.split("/")) {
            if ("..".equals(segment)) {
                throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "路径不得包含 ..");
            }
        }
        Path candidate;
        try {
            candidate = Path.of(portable);
        } catch (RuntimeException error) {
            throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "路径格式不合法");
        }
        // Java Windows Path 对“/var/...”报告为无盘符 rooted path；WorkloadSpec 使用
        // Linux/POSIX 规范路径，因此显式把前导 / 视为绝对路径，再统一做 normalize。
        if (!candidate.isAbsolute() && !portable.startsWith("/")) {
            throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "路径必须是绝对路径");
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (normalized.equals(normalized.getRoot()) || isDockerSocket(normalized)) {
            throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "路径不得是根目录或 Docker socket");
        }
        return normalized;
    }

    private void ensureNoSymlinkEscape(Path candidate, Path root, String path) {
        Path existing = candidate;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return;
        }
        try {
            Path rootReal = realPathIfExists(root);
            Path existingReal = existing.toRealPath();
            if (rootReal != null && !existingReal.startsWith(rootReal)) {
                throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "路径存在符号链接逃逸");
            }
            Path cursor = root;
            Path relative = root.relativize(existing);
            for (Path part : relative) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor)) {
                    Path real = cursor.toRealPath();
                    if (rootReal != null && !real.startsWith(rootReal)) {
                        throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "路径存在符号链接逃逸");
                    }
                }
            }
        } catch (IOException error) {
            throw invalid("COLLECTOR_WORKLOAD_PATH_INVALID", path, "路径无法安全解析");
        }
    }

    private static Path realPathIfExists(Path path) throws IOException {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS) ? path.toRealPath() : null;
    }

    private static boolean isDockerSocket(Path path) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.endsWith("/docker.sock")
                || normalized.endsWith("/docker_engine")
                || normalized.equals("docker.sock");
    }

    private static Path normalizeConfiguredRoot(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("COLLECTOR_WORKLOAD_CONFIGURATION_INVALID: collector 根不能为空");
        }
        Path normalized = root.toAbsolutePath().normalize();
        if (normalized.equals(normalized.getRoot())) {
            throw new IllegalArgumentException("COLLECTOR_WORKLOAD_CONFIGURATION_INVALID: collector 根不得是文件系统根");
        }
        return normalized;
    }

    private static Set<Path> normalizeSerialAllowlist(Set<Path> allowlist) {
        if (allowlist == null || allowlist.isEmpty()) {
            throw new IllegalArgumentException("COLLECTOR_WORKLOAD_CONFIGURATION_INVALID: 串口白名单不能为空");
        }
        Set<Path> values = new LinkedHashSet<>();
        for (Path path : allowlist) {
            if (path == null) {
                throw new IllegalArgumentException("COLLECTOR_WORKLOAD_CONFIGURATION_INVALID: 串口白名单包含空路径");
            }
            Path normalized = path.toAbsolutePath().normalize();
            if (normalized.equals(normalized.getRoot())) {
                throw new IllegalArgumentException("COLLECTOR_WORKLOAD_CONFIGURATION_INVALID: 串口白名单不得包含根目录");
            }
            values.add(normalized);
        }
        return Set.copyOf(values);
    }

    private static BigDecimal parsePositiveDecimal(String value, String path) {
        if (value == null || !DECIMAL.matcher(value).matches()) {
            throw invalid("COLLECTOR_WORKLOAD_RESOURCE_INVALID", path,
                    "资源必须是正十进制字符串，不得使用浮点或指数");
        }
        try {
            BigDecimal decimal = new BigDecimal(value);
            if (decimal.signum() <= 0 || decimal.compareTo(BigDecimal.ZERO) == 0
                    || value.startsWith("-0")) {
                throw invalid("COLLECTOR_WORKLOAD_RESOURCE_INVALID", path, "资源必须大于零");
            }
            return decimal;
        } catch (NumberFormatException error) {
            throw invalid("COLLECTOR_WORKLOAD_RESOURCE_INVALID", path, "资源十进制字符串无法解析");
        }
    }

    private static void requireBigintDecimal(String value, String path) {
        if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) {
            throw invalid("COLLECTOR_WORKLOAD_ID_INVALID", path, "ID 必须是不带符号和指数的十进制字符串");
        }
        try {
            BigInteger integer = new BigInteger(value);
            if (integer.signum() <= 0 || integer.compareTo(BIGINT_MAX) > 0) {
                throw invalid("COLLECTOR_WORKLOAD_ID_INVALID", path, "ID 超出 bigint 范围");
            }
        } catch (NumberFormatException error) {
            throw invalid("COLLECTOR_WORKLOAD_ID_INVALID", path, "ID 无法解析");
        }
    }

    private static long boundedTimeout(JsonNode value, String path, long max) {
        long number = integralLong(value, path);
        if (number < MIN_TIMEOUT_SECONDS || number > max) {
            throw invalid("COLLECTOR_WORKLOAD_POLICY_INVALID", path, "超出超时策略上限");
        }
        return number;
    }

    private static long integralLong(JsonNode value, String path) {
        if (value == null || !value.isIntegralNumber()) {
            throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "必须是 JSON integer，禁止小数");
        }
        try {
            return value.bigIntegerValue().longValueExact();
        } catch (ArithmeticException error) {
            throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "整数超出 Java long 范围");
        }
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value == null || !value.isTextual() ? null : value.textValue();
    }

    private static JsonNode loadSchema(ObjectMapper mapper) {
        try (InputStream input = CollectorWorkloadSpecValidator.class.getClassLoader()
                .getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("COLLECTOR_WORKLOAD_SCHEMA_INVALID: Schema 资源不存在");
            }
            JsonNode schema = mapper.readTree(input);
            if (schema == null || !schema.isObject()) {
                throw new IllegalStateException("COLLECTOR_WORKLOAD_SCHEMA_INVALID: Schema 根节点不是 object");
            }
            return schema;
        } catch (IOException error) {
            throw new IllegalStateException("COLLECTOR_WORKLOAD_SCHEMA_INVALID: Schema 无法读取", error);
        }
    }

    /** 仅实现本合同实际使用的 JSON Schema 2020-12 子集，Schema 仍是字段/类型事实源。 */
    private static void validateAgainstSchema(JsonNode value, JsonNode schema, String path) {
        if (value == null) {
            throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "字段缺失");
        }
        JsonNode type = schema.get("type");
        if (type != null && type.isTextual() && !matchesType(value, type.textValue())) {
            throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path,
                    "类型不匹配，期望 " + type.textValue());
        }
        JsonNode constant = schema.get("const");
        if (constant != null && !constant.equals(value)) {
            throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "不允许的固定值");
        }
        JsonNode enumeration = schema.get("enum");
        if (enumeration != null && enumeration.isArray()) {
            boolean found = false;
            for (JsonNode item : enumeration) {
                if (item.equals(value)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "不在允许的枚举值中");
            }
        }
        if (value.isObject()) {
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                for (JsonNode field : required) {
                    if (field.isTextual() && !value.has(field.textValue())) {
                        throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID",
                                path + "." + field.textValue(), "必填字段缺失");
                    }
                }
            }
            JsonNode properties = schema.get("properties");
            boolean closed = schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").booleanValue();
            Iterator<String> names = value.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                JsonNode childSchema = properties == null ? null : properties.get(name);
                if (closed && childSchema == null) {
                    throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID",
                            path + "." + name, "未知字段被拒绝");
                }
                if (childSchema != null) {
                    validateAgainstSchema(value.get(name), childSchema, path + "." + name);
                }
            }
        } else if (value.isArray()) {
            int size = value.size();
            int minItems = schema.path("minItems").asInt(-1);
            int maxItems = schema.path("maxItems").asInt(-1);
            if (minItems >= 0 && size < minItems || maxItems >= 0 && size > maxItems) {
                throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "数组长度超出限制");
            }
            JsonNode itemSchema = schema.get("items");
            if (itemSchema != null) {
                for (int i = 0; i < size; i++) {
                    validateAgainstSchema(value.get(i), itemSchema, path + "[" + i + "]");
                }
            }
        } else if (value.isTextual()) {
            int length = value.textValue().codePointCount(0, value.textValue().length());
            int minLength = schema.path("minLength").asInt(-1);
            int maxLength = schema.path("maxLength").asInt(-1);
            if (minLength >= 0 && length < minLength || maxLength >= 0 && length > maxLength) {
                throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "字符串长度超出限制");
            }
            JsonNode pattern = schema.get("pattern");
            if (pattern != null && pattern.isTextual()
                    && !Pattern.compile(pattern.textValue()).matcher(value.textValue()).matches()) {
                throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "字符串格式不符合 Schema");
            }
        } else if (value.isIntegralNumber()) {
            BigInteger integer = value.bigIntegerValue();
            JsonNode minimum = schema.get("minimum");
            JsonNode maximum = schema.get("maximum");
            if (minimum != null && integer.compareTo(minimum.bigIntegerValue()) < 0
                    || maximum != null && integer.compareTo(maximum.bigIntegerValue()) > 0) {
                throw invalid("COLLECTOR_WORKLOAD_SCHEMA_INVALID", path, "整数超出 Schema 上限");
            }
        }
    }

    private static boolean matchesType(JsonNode value, String type) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "number" -> value.isNumber();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private byte[] canonicalBytes(JsonNode value) {
        try {
            StringBuilder canonical = new StringBuilder();
            writeCanonical(value, canonical);
            return canonical.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw invalid("COLLECTOR_WORKLOAD_CANONICAL_INVALID", "$", "canonical JSON 生成失败");
        }
    }

    private void writeCanonical(JsonNode value, StringBuilder output) throws IOException {
        if (value.isObject()) {
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            output.append('{');
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    output.append(',');
                }
                String name = names.get(i);
                output.append(objectMapper.writeValueAsString(name)).append(':');
                writeCanonical(value.get(name), output);
            }
            output.append('}');
        } else if (value.isArray()) {
            output.append('[');
            for (int i = 0; i < value.size(); i++) {
                if (i > 0) {
                    output.append(',');
                }
                writeCanonical(value.get(i), output);
            }
            output.append(']');
        } else if (value.isTextual()) {
            output.append(objectMapper.writeValueAsString(value.textValue()));
        } else if (value.isBoolean() || value.isNull()) {
            output.append(value.toString());
        } else if (value.isIntegralNumber()) {
            output.append(value.bigIntegerValue().toString());
        } else {
            throw invalid("COLLECTOR_WORKLOAD_CANONICAL_INVALID", "$", "canonical JSON 禁止浮点节点");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(value & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK 缺少 SHA-256", error);
        }
    }

    private static CollectorWorkloadSpecValidationException invalid(String code,
                                                                    String path,
                                                                    String detail) {
        return new CollectorWorkloadSpecValidationException(code, path + ": " + detail);
    }
}
