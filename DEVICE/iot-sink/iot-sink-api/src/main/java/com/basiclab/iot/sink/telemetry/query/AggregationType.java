package com.basiclab.iot.sink.telemetry.query;

/**
 * TD-003 §16 聚合函数集合（M1 固定五类，PRD §4.5：最大、最小、平均、累计、计数）。
 */
public enum AggregationType {

    MIN,

    MAX,

    AVG,

    /** 累计值：对桶内样本求和（电能量等累计型测点）。 */
    SUM,

    COUNT
}
