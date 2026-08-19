package com.basiclab.iot.node.domain.collector;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * WorkloadSpec 1.0 的不可变类型化 DTO。
 *
 * <p>该对象只承载已经通过 provider-side 合同校验的数据；它不包含 command、env、files
 * 或其它通用工作负载字段，也不负责启动进程、访问 NODE 或写入数据库。</p>
 */
public final class CollectorWorkloadSpec {

    public static final String SPEC_VERSION = "1.0";
    public static final String WORKLOAD_TYPE = "iot-sink-collector";
    public static final String SPRING_PROFILE = "collector";
    public static final String CONFIG_TARGET_PATH = "/var/lib/easyaiot/config/active.json";
    public static final String OUTBOX_CONTAINER_PATH = "/var/lib/easyaiot/outbox";
    public static final String VOLUME_NAME = "outbox";

    /** 供 schema/DTO/validator 合同测试使用的稳定字段集合。 */
    public static final Set<String> ROOT_FIELDS = fields(
            "specVersion", "workloadType", "workloadId", "nodeId", "image", "springProfile",
            "config", "resources", "serialDevices", "volumes", "brokerRef", "updatePolicy");
    public static final Set<String> IMAGE_FIELDS = fields("repository", "digest");
    public static final Set<String> CONFIG_FIELDS = fields("version", "sha256", "targetPath");
    public static final Set<String> RESOURCE_FIELDS = fields("cpuCores", "memoryBytes");
    public static final Set<String> SERIAL_DEVICE_FIELDS = fields(
            "hostPath", "containerPath", "hardwareFingerprint", "readOnly");
    public static final Set<String> VOLUME_FIELDS = fields("name", "hostPath", "containerPath", "mode");
    public static final Set<String> UPDATE_POLICY_FIELDS = fields(
            "dispatchAckTimeoutSeconds", "configApplyTimeoutSeconds", "healthWindowSeconds", "autoRollback");

    private final String specVersion;
    private final String workloadType;
    private final String workloadId;
    private final String nodeId;
    private final ImageSpec image;
    private final String springProfile;
    private final ConfigSpec config;
    private final ResourcesSpec resources;
    private final List<SerialDeviceSpec> serialDevices;
    private final List<VolumeSpec> volumes;
    private final String brokerRef;
    private final UpdatePolicySpec updatePolicy;

    public CollectorWorkloadSpec(String specVersion,
                                 String workloadType,
                                 String workloadId,
                                 String nodeId,
                                 ImageSpec image,
                                 String springProfile,
                                 ConfigSpec config,
                                 ResourcesSpec resources,
                                 List<SerialDeviceSpec> serialDevices,
                                 List<VolumeSpec> volumes,
                                 String brokerRef,
                                 UpdatePolicySpec updatePolicy) {
        this.specVersion = specVersion;
        this.workloadType = workloadType;
        this.workloadId = workloadId;
        this.nodeId = nodeId;
        this.image = image;
        this.springProfile = springProfile;
        this.config = config;
        this.resources = resources;
        this.serialDevices = List.copyOf(serialDevices);
        this.volumes = List.copyOf(volumes);
        this.brokerRef = brokerRef;
        this.updatePolicy = updatePolicy;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public String getWorkloadType() {
        return workloadType;
    }

    public String getWorkloadId() {
        return workloadId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public ImageSpec getImage() {
        return image;
    }

    public String getSpringProfile() {
        return springProfile;
    }

    public ConfigSpec getConfig() {
        return config;
    }

    public ResourcesSpec getResources() {
        return resources;
    }

    public List<SerialDeviceSpec> getSerialDevices() {
        return serialDevices;
    }

    public List<VolumeSpec> getVolumes() {
        return volumes;
    }

    public String getBrokerRef() {
        return brokerRef;
    }

    public UpdatePolicySpec getUpdatePolicy() {
        return updatePolicy;
    }

    public static Set<String> fields(String... names) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Collections.addAll(values, names);
        return Collections.unmodifiableSet(values);
    }

    public static final class ImageSpec {
        private final String repository;
        private final String digest;

        public ImageSpec(String repository, String digest) {
            this.repository = repository;
            this.digest = digest;
        }

        public String getRepository() {
            return repository;
        }

        public String getDigest() {
            return digest;
        }
    }

    public static final class ConfigSpec {
        private final long version;
        private final String sha256;
        private final String targetPath;

        public ConfigSpec(long version, String sha256, String targetPath) {
            this.version = version;
            this.sha256 = sha256;
            this.targetPath = targetPath;
        }

        public long getVersion() {
            return version;
        }

        public String getSha256() {
            return sha256;
        }

        public String getTargetPath() {
            return targetPath;
        }
    }

    public static final class ResourcesSpec {
        private final String cpuCores;
        private final long memoryBytes;

        public ResourcesSpec(String cpuCores, long memoryBytes) {
            this.cpuCores = cpuCores;
            this.memoryBytes = memoryBytes;
        }

        public String getCpuCores() {
            return cpuCores;
        }

        public long getMemoryBytes() {
            return memoryBytes;
        }
    }

    public static final class SerialDeviceSpec {
        private final String hostPath;
        private final String containerPath;
        private final String hardwareFingerprint;
        private final boolean readOnly;

        public SerialDeviceSpec(String hostPath, String containerPath,
                                String hardwareFingerprint, boolean readOnly) {
            this.hostPath = hostPath;
            this.containerPath = containerPath;
            this.hardwareFingerprint = hardwareFingerprint;
            this.readOnly = readOnly;
        }

        public String getHostPath() {
            return hostPath;
        }

        public String getContainerPath() {
            return containerPath;
        }

        public String getHardwareFingerprint() {
            return hardwareFingerprint;
        }

        public boolean isReadOnly() {
            return readOnly;
        }
    }

    public static final class VolumeSpec {
        private final String name;
        private final String hostPath;
        private final String containerPath;
        private final CollectorVolumeMode mode;

        public VolumeSpec(String name, String hostPath, String containerPath, CollectorVolumeMode mode) {
            this.name = name;
            this.hostPath = hostPath;
            this.containerPath = containerPath;
            this.mode = mode;
        }

        public String getName() {
            return name;
        }

        public String getHostPath() {
            return hostPath;
        }

        public String getContainerPath() {
            return containerPath;
        }

        public CollectorVolumeMode getMode() {
            return mode;
        }
    }

    public static final class UpdatePolicySpec {
        private final long dispatchAckTimeoutSeconds;
        private final long configApplyTimeoutSeconds;
        private final long healthWindowSeconds;
        private final boolean autoRollback;

        public UpdatePolicySpec(long dispatchAckTimeoutSeconds,
                                long configApplyTimeoutSeconds,
                                long healthWindowSeconds,
                                boolean autoRollback) {
            this.dispatchAckTimeoutSeconds = dispatchAckTimeoutSeconds;
            this.configApplyTimeoutSeconds = configApplyTimeoutSeconds;
            this.healthWindowSeconds = healthWindowSeconds;
            this.autoRollback = autoRollback;
        }

        public long getDispatchAckTimeoutSeconds() {
            return dispatchAckTimeoutSeconds;
        }

        public long getConfigApplyTimeoutSeconds() {
            return configApplyTimeoutSeconds;
        }

        public long getHealthWindowSeconds() {
            return healthWindowSeconds;
        }

        public boolean isAutoRollback() {
            return autoRollback;
        }
    }
}
