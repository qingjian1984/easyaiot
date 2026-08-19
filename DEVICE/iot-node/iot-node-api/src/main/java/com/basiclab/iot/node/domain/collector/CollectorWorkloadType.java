package com.basiclab.iot.node.domain.collector;

/** WorkloadSpec 1.0 的固定 workload 类型。 */
public enum CollectorWorkloadType {

    IOT_SINK_COLLECTOR("iot-sink-collector");

    private final String value;

    CollectorWorkloadType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
