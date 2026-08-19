package com.basiclab.iot.node.service.collector.config;

/** iot-device observed CAS 的脱敏响应。 */
public final class CollectorConfigObservedResponse {

    private final String releaseId;
    private final CollectorConfigReleaseObservedReport.Status status;
    private final boolean accepted;
    private final boolean terminal;
    private final boolean idempotent;

    public CollectorConfigObservedResponse(String releaseId,
                                           CollectorConfigReleaseObservedReport.Status status,
                                           boolean accepted,
                                           boolean terminal,
                                           boolean idempotent) {
        this.releaseId = releaseId;
        this.status = status;
        this.accepted = accepted;
        this.terminal = terminal;
        this.idempotent = idempotent;
    }

    public String getReleaseId() { return releaseId; }

    public CollectorConfigReleaseObservedReport.Status getStatus() { return status; }

    public boolean isAccepted() { return accepted; }

    public boolean isTerminal() { return terminal; }

    public boolean isIdempotent() { return idempotent; }
}
