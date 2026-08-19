package com.basiclab.iot.node.service.collector.config;

import java.util.List;

/** 单次 job 的有界结果，不携带 canonical 或外部响应正文。 */
public final class CollectorConfigDispatchBatchResult {

    private final List<CollectorConfigDispatchOutcome> outcomes;
    private final boolean reentrant;

    public CollectorConfigDispatchBatchResult(List<CollectorConfigDispatchOutcome> outcomes,
                                              boolean reentrant) {
        this.outcomes = List.copyOf(outcomes);
        this.reentrant = reentrant;
    }

    public List<CollectorConfigDispatchOutcome> getOutcomes() { return outcomes; }

    public boolean isReentrant() { return reentrant; }
}
