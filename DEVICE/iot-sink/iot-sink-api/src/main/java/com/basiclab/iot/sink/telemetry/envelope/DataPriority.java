package com.basiclab.iot.sink.telemetry.envelope;

/**
 * TD-002 §7 五级数据优先级 + rank 映射（SAFETY=1 ... NORMAL_TELEMETRY=5）。
 * 容量淘汰顺序固定 NORMAL_TELEMETRY → CONTROL_FEEDBACK → METERING_TOTAL → ALARM → SAFETY；
 * SAFETY（rank=1）永不自动淘汰。
 */
public enum DataPriority {
    SAFETY(1),
    ALARM(2),
    METERING_TOTAL(3),
    CONTROL_FEEDBACK(4),
    NORMAL_TELEMETRY(5);

    private final int rank;

    DataPriority(int rank) {
        this.rank = rank;
    }

    /** SQLite outbox priority_rank 列值（越小优先级越高）。 */
    public int rank() {
        return rank;
    }
}
