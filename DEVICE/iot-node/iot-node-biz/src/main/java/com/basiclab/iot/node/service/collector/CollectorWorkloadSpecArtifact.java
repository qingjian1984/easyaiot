package com.basiclab.iot.node.service.collector;

import com.basiclab.iot.node.domain.collector.CollectorWorkloadSpec;

import java.util.Arrays;
import java.util.Objects;

/**
 * WorkloadSpec 通过合同校验后的一次性不可变 artifact。
 * canonical bytes、hash 和长度均从同一份字节数组生成，并且不暴露可变数组引用。
 */
public final class CollectorWorkloadSpecArtifact {

    private final CollectorWorkloadSpec spec;
    private final byte[] canonicalBytes;
    private final String sha256;
    private final long canonicalLengthBytes;

    public CollectorWorkloadSpecArtifact(CollectorWorkloadSpec spec,
                                         byte[] canonicalBytes,
                                         String sha256) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.canonicalBytes = Arrays.copyOf(
                Objects.requireNonNull(canonicalBytes, "canonicalBytes"), canonicalBytes.length);
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.canonicalLengthBytes = this.canonicalBytes.length;
    }

    public CollectorWorkloadSpec getSpec() {
        return spec;
    }

    public byte[] getCanonicalBytes() {
        return Arrays.copyOf(canonicalBytes, canonicalBytes.length);
    }

    public String getSha256() {
        return sha256;
    }

    public long getCanonicalLengthBytes() {
        return canonicalLengthBytes;
    }
}
