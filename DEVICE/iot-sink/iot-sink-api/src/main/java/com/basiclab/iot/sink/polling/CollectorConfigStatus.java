package com.basiclab.iot.sink.polling;

/** Closed observed state vocabulary shared by the collector and its state writer. */
public enum CollectorConfigStatus {
    WAITING_CONFIG,
    APPLIED,
    FAILED
}
