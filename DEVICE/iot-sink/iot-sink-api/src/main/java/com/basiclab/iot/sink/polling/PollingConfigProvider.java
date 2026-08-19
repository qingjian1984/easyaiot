package com.basiclab.iot.sink.polling;

import java.util.Optional;

/** Local snapshot boundary used by collector Profile; it has no central-service methods. */
public interface PollingConfigProvider {
    Optional<CollectorConfigSnapshot> current();

    Optional<CollectorConfigSnapshot> candidate(long version);

    /**
     * Reconcile the local desired snapshot through a prepare/commit graph
     * boundary. Implementations must persist only after prepare succeeds and
     * must invoke replace only after active persistence succeeds.
     */
    CollectorConfigObservation reconcile(GraphApplier graphApplier);

    interface GraphApplier {
        /** Build and validate a candidate graph without publishing it. */
        void prepare(CollectorConfigSnapshot snapshot);

        /** Atomically publish the prepared graph after durable active state. */
        void replace(CollectorConfigSnapshot snapshot);

        /** Restore the previous graph after a post-prepare failure. */
        void restore(CollectorConfigSnapshot snapshot);
    }
}
