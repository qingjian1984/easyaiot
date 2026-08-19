package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigObservation;
import com.basiclab.iot.sink.polling.PollingStatusReporter;

import java.util.Objects;

/** Writes only the closed observed summary through the local state provider. */
public final class LocalFilePollingStatusReporter implements PollingStatusReporter {
    private final LocalFilePollingConfigProvider provider;

    public LocalFilePollingStatusReporter(LocalFilePollingConfigProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public void report(CollectorConfigObservation observation) {
        provider.writeObserved(observation);
    }
}
