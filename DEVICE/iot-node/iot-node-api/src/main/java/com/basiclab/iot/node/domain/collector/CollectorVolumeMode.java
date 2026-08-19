package com.basiclab.iot.node.domain.collector;

/** WorkloadSpec 1.0 当前唯一允许的数据卷模式。 */
public enum CollectorVolumeMode {

    RW("rw");

    private final String value;

    CollectorVolumeMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
