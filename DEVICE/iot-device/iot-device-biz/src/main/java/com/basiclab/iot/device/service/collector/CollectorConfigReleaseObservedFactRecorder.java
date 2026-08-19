package com.basiclab.iot.device.service.collector;

/** AGENT_ACCEPTED 与迟到/不匹配回报的结构化可观测出口；不把接单伪装成终态。 */
public interface CollectorConfigReleaseObservedFactRecorder {

    void record(CollectorConfigReleaseObservedFact fact, String outcome);
}
