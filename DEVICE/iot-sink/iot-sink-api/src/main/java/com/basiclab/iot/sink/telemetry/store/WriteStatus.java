package com.basiclab.iot.sink.telemetry.store;

/** Per-sample write outcome; a batch never collapses these into one boolean. */
public enum WriteStatus {
    STORED,
    DUPLICATE,
    RETRYABLE_FAILED,
    FINAL_FAILED
}
