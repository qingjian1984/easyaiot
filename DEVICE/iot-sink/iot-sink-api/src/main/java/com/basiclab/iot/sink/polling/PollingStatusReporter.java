package com.basiclab.iot.sink.polling;

/** Collector status boundary; implementations persist only the closed observed summary. */
@FunctionalInterface
public interface PollingStatusReporter {
    void report(CollectorConfigObservation observation);
}
