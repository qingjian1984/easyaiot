package com.basiclab.iot.device.domain.device.authority;

/** Deterministic cardinality of an exact product/device registration query. */
public enum ResolutionStatus {
    RESOLVED,
    NOT_FOUND,
    AMBIGUOUS
}
