package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.node.domain.collector.config.CollectorConfigAgentStatus;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigGetResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigObservedSummaryDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutRequestDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigStateSummaryDTO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorConfigDispatchServiceTest {

    private static final String RELEASE_ID = "1001";
    private static final String TENANT_ID = "10";
    private static final String NODE_ID = "21";
    private static final String WORKLOAD_ID = "collector-site-1001-a";
    private static final String PAYLOAD =
            "{\"configVersion\":1,\"schemaVersion\":\"1.1\",\"workloadId\":\"collector-site-1001-a\"}";
    private static final String HASH = sha256(PAYLOAD);

    @Test
    void acceptedThenAppliedReportsOnlyAcceptedAndApplied() {
        FakeReleaseClient release = new FakeReleaseClient(pending());
        FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        CollectorConfigDispatchService service = service(release, agent, node());

        CollectorConfigDispatchOutcome result = service.dispatchOne(pending());

        assertEquals(CollectorConfigDispatchStatus.APPLIED, result.getStatus());
        assertEquals(List.of(
                CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED,
                CollectorConfigReleaseObservedReport.Status.APPLIED), release.statuses());
        assertFalse(release.reports.get(0).getErrorCode() != null);
        assertEquals("/workload/collector/config", agent.putPath);
        assertEquals("/workload/collector/collector-site-1001-a", agent.getPath);
    }

    @Test
    void idempotentPutAndBatchFailureDoNotBlockOtherRelease() {
        CollectorConfigReleasePending first = pending();
        CollectorConfigReleasePending second = new CollectorConfigReleasePending(
                "1002", TENANT_ID, NODE_ID, "collector-site-1001-b", "2", "1.1",
                "jcs-rfc8785-v1", HASH, (long) PAYLOAD.getBytes(StandardCharsets.UTF_8).length,
                "2026-08-17T10:00:00Z");
        FakeReleaseClient release = new FakeReleaseClient(first, second);
        FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        agent.failWorkloadId = second.getWorkloadId();
        CollectorConfigDispatchService service = service(release, agent, node());

        CollectorConfigDispatchBatchResult result = service.dispatchPending(2);

        assertEquals(2, result.getOutcomes().size());
        assertEquals(CollectorConfigDispatchStatus.APPLIED, result.getOutcomes().get(0).getStatus());
        assertEquals(CollectorConfigDispatchStatus.RETRY, result.getOutcomes().get(1).getStatus());
        assertEquals(2, release.reports.size());
        assertEquals(CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED,
                release.reports.get(0).getStatus());
        assertEquals(CollectorConfigReleaseObservedReport.Status.APPLIED,
                release.reports.get(1).getStatus());
    }

    @Test
    void mismatchedDetailReportsFailedAndNeverSendsAgentRequest() {
        FakeReleaseClient release = new FakeReleaseClient(pending());
        release.details.put(RELEASE_ID, new CollectorConfigReleaseDetail(
                RELEASE_ID, TENANT_ID, NODE_ID, WORKLOAD_ID, "9", "1.1",
                "jcs-rfc8785-v1", PAYLOAD, HASH, (long) PAYLOAD.getBytes(StandardCharsets.UTF_8).length,
                "2026-08-17T10:00:00Z"));
        FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        CollectorConfigDispatchService service = service(release, agent, node());

        CollectorConfigDispatchOutcome result = service.dispatchOne(pending());

        assertEquals(CollectorConfigDispatchStatus.FAILED, result.getStatus());
        assertEquals(0, agent.putCalls);
        assertEquals(CollectorConfigReleaseObservedReport.Status.FAILED,
                release.reports.get(0).getStatus());
        assertEquals("COLLECTOR_CONFIG_APPLY_FAILED", release.reports.get(0).getErrorCode());
    }

    @Test
    void waitingDegradedAndLateObservedRetryWithoutTerminalFailureOrTimeout() {
        FakeReleaseClient release = new FakeReleaseClient(pending());
        FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.WAITING_CONFIG);
        CollectorConfigDispatchService service = service(release, agent, node());

        CollectorConfigDispatchOutcome result = service.dispatchOne(pending());

        assertEquals(CollectorConfigDispatchStatus.RETRY, result.getStatus());
        assertEquals(List.of(CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED), release.statuses());
        assertFalse(release.statuses().contains(CollectorConfigReleaseObservedReport.Status.FAILED));
        assertFalse(release.statuses().toString().contains("APPLY_TIMEOUT"));
        assertEquals(1, serviceBackoff(service).size());
    }

    @Test
    void nodeMissingOrOfflineIsRetryable() {
        FakeReleaseClient release = new FakeReleaseClient(pending());
        FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        CollectorConfigDispatchService missing = service(release, agent, null);
        assertEquals(CollectorConfigDispatchStatus.RETRY, missing.dispatchOne(pending()).getStatus());
        assertTrue(release.reports.isEmpty());

        FakeReleaseClient offlineRelease = new FakeReleaseClient(pending());
        CollectorConfigDispatchService offline = service(offlineRelease, agent,
                new CollectorNodeEndpoint(21, "offline", "127.0.0.1", 9100));
        assertEquals(CollectorConfigDispatchStatus.RETRY, offline.dispatchOne(pending()).getStatus());
        assertTrue(offlineRelease.reports.isEmpty());
    }

    @Test
    void failedObservedAllowlistIsForwardedButUnknownCodeIsSanitized() {
        FakeReleaseClient release = new FakeReleaseClient(pending());
        FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.FAILED);
        agent.failureCode = "NOT_SAFE_TO_FORWARD";
        CollectorConfigDispatchService service = service(release, agent, node());

        CollectorConfigDispatchOutcome result = service.dispatchOne(pending());

        assertEquals(CollectorConfigDispatchStatus.FAILED, result.getStatus());
        assertEquals("COLLECTOR_CONFIG_APPLY_FAILED", result.getStableCode());
        assertEquals("COLLECTOR_CONFIG_APPLY_FAILED", release.reports.get(1).getErrorCode());
    }

    @Test
    void backoffIsExponentialBoundedAndDoesNotRetainCanonical() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-17T10:00:00Z"));
        CollectorConfigDispatchBackoff backoff = new CollectorConfigDispatchBackoff(
                clock, Duration.ofSeconds(1), Duration.ofSeconds(60));
        CollectorConfigDispatchBackoff.Entry first = backoff.recordRetry(RELEASE_ID, "1", HASH);
        clock.advance(Duration.ofSeconds(1));
        CollectorConfigDispatchBackoff.Entry second = backoff.recordRetry(RELEASE_ID, "1", HASH);

        assertEquals(1, first.getAttempt());
        assertEquals(2, second.getAttempt());
        assertEquals(2, Duration.between(clock.instant(), second.getNextAttemptAt()).toSeconds());
        assertTrue(backoff.snapshot().stream().noneMatch(entry ->
                entry.getClass().getDeclaredFields()[0].getName().toLowerCase().contains("canonical")));
    }

    @Test
    void agentVersionConflictsReportStableFailedCode() {
        for (String agentCode : List.of("CONFIG_VERSION_STALE", "CONFIG_VERSION_CONFLICT")) {
            FakeReleaseClient release = new FakeReleaseClient(pending());
            FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
            agent.putFailure = new CollectorAgentClient.CollectorAgentException(
                    CollectorAgentClient.Kind.DETERMINISTIC, 409, agentCode);
            CollectorConfigDispatchOutcome result = service(release, agent, node())
                    .dispatchOne(pending());

            assertEquals(CollectorConfigDispatchStatus.FAILED, result.getStatus());
            assertEquals("COLLECTOR_CONFIG_VERSION_" + agentCode.substring("CONFIG_VERSION_".length()),
                    result.getStableCode());
            assertEquals(List.of(CollectorConfigReleaseObservedReport.Status.FAILED), release.statuses());
            assertEquals(result.getStableCode(), release.reports.get(0).getErrorCode());
        }
    }

    @Test
    void agentSecurityConnectionAndServerFailuresOnlyRetry() {
        List<CollectorAgentClient.CollectorAgentException> failures = List.of(
                new CollectorAgentClient.CollectorAgentException(
                        CollectorAgentClient.Kind.RETRYABLE, 401, "AGENT_UNAUTHORIZED"),
                new CollectorAgentClient.CollectorAgentException(
                        CollectorAgentClient.Kind.RETRYABLE, -1, "AGENT_CONNECTION_FAILED"),
                new CollectorAgentClient.CollectorAgentException(
                        CollectorAgentClient.Kind.RETRYABLE, 500, "AGENT_HTTP_500"));
        for (CollectorAgentClient.CollectorAgentException failure : failures) {
            FakeReleaseClient release = new FakeReleaseClient(pending());
            FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
            agent.putFailure = failure;

            CollectorConfigDispatchOutcome result = service(release, agent, node())
                    .dispatchOne(pending());

            assertEquals(CollectorConfigDispatchStatus.RETRY, result.getStatus());
            assertTrue(release.reports.isEmpty());
            assertFalse(release.statuses().contains(CollectorConfigReleaseObservedReport.Status.FAILED));
        }
    }

    @Test
    void oldNewVersionAndHashObservedFactsOnlyRetryAfterAccepted() {
        for (long observedVersion : List.of(0L, 2L)) {
            FakeReleaseClient release = new FakeReleaseClient(pending());
            FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
            agent.observedConfigVersion = observedVersion;

            CollectorConfigDispatchOutcome result = service(release, agent, node())
                    .dispatchOne(pending());

            assertEquals(CollectorConfigDispatchStatus.RETRY, result.getStatus());
            assertEquals(List.of(CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED),
                    release.statuses());
            assertEquals(1, agent.getCalls);
        }

        FakeReleaseClient release = new FakeReleaseClient(pending());
        FakeAgent agent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        agent.observedHash = "f".repeat(64);
        CollectorConfigDispatchOutcome result = service(release, agent, node()).dispatchOne(pending());

        assertEquals(CollectorConfigDispatchStatus.RETRY, result.getStatus());
        assertEquals(List.of(CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED),
                release.statuses());
        assertFalse(release.statuses().contains(CollectorConfigReleaseObservedReport.Status.FAILED));
    }

    @Test
    void canonicalLengthAndHashDriftFailWithoutAgentWrite() {
        long payloadLength = PAYLOAD.getBytes(StandardCharsets.UTF_8).length;
        CollectorConfigReleasePending lengthDrift = new CollectorConfigReleasePending(
                RELEASE_ID, TENANT_ID, NODE_ID, WORKLOAD_ID, "1", "1.1",
                "jcs-rfc8785-v1", HASH, payloadLength + 1, "2026-08-17T10:00:00Z");
        FakeReleaseClient lengthRelease = new FakeReleaseClient(lengthDrift);
        FakeAgent lengthAgent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        CollectorConfigDispatchOutcome lengthResult = service(lengthRelease, lengthAgent, node())
                .dispatchOne(lengthDrift);

        assertEquals(CollectorConfigDispatchStatus.FAILED, lengthResult.getStatus());
        assertEquals(0, lengthAgent.putCalls);
        assertEquals(List.of(CollectorConfigReleaseObservedReport.Status.FAILED), lengthRelease.statuses());
        assertEquals("COLLECTOR_CONFIG_APPLY_FAILED", lengthRelease.reports.get(0).getErrorCode());

        String wrongHash = "f".repeat(64);
        CollectorConfigReleasePending hashDrift = new CollectorConfigReleasePending(
                RELEASE_ID, TENANT_ID, NODE_ID, WORKLOAD_ID, "1", "1.1",
                "jcs-rfc8785-v1", wrongHash, payloadLength, "2026-08-17T10:00:00Z");
        FakeReleaseClient hashRelease = new FakeReleaseClient(hashDrift);
        FakeAgent hashAgent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        CollectorConfigDispatchOutcome hashResult = service(hashRelease, hashAgent, node())
                .dispatchOne(hashDrift);

        assertEquals(CollectorConfigDispatchStatus.FAILED, hashResult.getStatus());
        assertEquals(0, hashAgent.putCalls);
        assertEquals(List.of(CollectorConfigReleaseObservedReport.Status.FAILED), hashRelease.statuses());
        assertEquals("COLLECTOR_CONFIG_APPLY_FAILED", hashRelease.reports.get(0).getErrorCode());
    }

    @Test
    void temporaryAcceptedOrTerminalReportFailureOnlyRetries() {
        FakeReleaseClient acceptedFailure = new FakeReleaseClient(pending());
        acceptedFailure.reportFailures.add(CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED);
        FakeAgent acceptedAgent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        CollectorConfigDispatchOutcome acceptedResult = service(acceptedFailure, acceptedAgent, node())
                .dispatchOne(pending());

        assertEquals(CollectorConfigDispatchStatus.RETRY, acceptedResult.getStatus());
        assertEquals(0, acceptedAgent.getCalls);
        assertTrue(acceptedFailure.reports.isEmpty());

        FakeReleaseClient terminalFailure = new FakeReleaseClient(pending());
        terminalFailure.reportFailures.add(CollectorConfigReleaseObservedReport.Status.APPLIED);
        FakeAgent terminalAgent = new FakeAgent(CollectorConfigAgentStatus.APPLIED);
        CollectorConfigDispatchOutcome terminalResult = service(terminalFailure, terminalAgent, node())
                .dispatchOne(pending());

        assertEquals(CollectorConfigDispatchStatus.RETRY, terminalResult.getStatus());
        assertEquals(List.of(CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED),
                terminalFailure.statuses());
        assertFalse(terminalFailure.statuses().contains(CollectorConfigReleaseObservedReport.Status.FAILED));
    }

    @Test
    void concurrentDispatchIsSingleFlight() throws Exception {
        FakeReleaseClient release = new FakeReleaseClient(pending());
        release.listPendingEntered = new CountDownLatch(1);
        release.listPendingRelease = new CountDownLatch(1);
        CollectorConfigDispatchService service = service(release,
                new FakeAgent(CollectorConfigAgentStatus.APPLIED), node());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<CollectorConfigDispatchBatchResult> first = executor.submit(() -> service.dispatchPending(1));
        try {
            assertTrue(release.listPendingEntered.await(2, TimeUnit.SECONDS));
            CollectorConfigDispatchBatchResult second = service.dispatchPending(1);
            assertTrue(second.isReentrant());
            assertEquals(CollectorConfigDispatchStatus.REENTRANT, second.getOutcomes().get(0).getStatus());
            release.listPendingRelease.countDown();
            assertFalse(first.get(2, TimeUnit.SECONDS).isReentrant());
        } finally {
            release.listPendingRelease.countDown();
            executor.shutdownNow();
        }
    }

    private static CollectorConfigDispatchService service(FakeReleaseClient release,
                                                           FakeAgent agent,
                                                           CollectorNodeEndpoint node) {
        return new CollectorConfigDispatchService(
                release,
                ignored -> Optional.ofNullable(node),
                agent,
                Clock.fixed(Instant.parse("2026-08-17T10:01:00Z"), ZoneOffset.UTC),
                new CollectorConfigDispatchBackoff());
    }

    private static CollectorConfigDispatchBackoff serviceBackoff(CollectorConfigDispatchService service) {
        try {
            var field = CollectorConfigDispatchService.class.getDeclaredField("backoff");
            field.setAccessible(true);
            return (CollectorConfigDispatchBackoff) field.get(service);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static CollectorConfigReleasePending pending() {
        return new CollectorConfigReleasePending(RELEASE_ID, TENANT_ID, NODE_ID, WORKLOAD_ID,
                "1", "1.1", "jcs-rfc8785-v1", HASH,
                (long) PAYLOAD.getBytes(StandardCharsets.UTF_8).length,
                "2026-08-17T10:00:00Z");
    }

    private static CollectorNodeEndpoint node() {
        return new CollectorNodeEndpoint(21, "online", "127.0.0.1", 9100);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static final class FakeReleaseClient implements CollectorConfigReleaseClientPort {
        private final List<CollectorConfigReleasePending> pending;
        private final List<CollectorConfigReleaseObservedReport> reports = new ArrayList<>();
        private final Map<String, CollectorConfigReleaseDetail> details = new HashMap<>();
        private final Set<CollectorConfigReleaseObservedReport.Status> reportFailures = new HashSet<>();
        private CountDownLatch listPendingEntered;
        private CountDownLatch listPendingRelease;

        private FakeReleaseClient(CollectorConfigReleasePending... pending) {
            this.pending = List.of(pending);
            for (CollectorConfigReleasePending item : pending) {
                details.put(item.getReleaseId(), new CollectorConfigReleaseDetail(
                        item.getReleaseId(), item.getTenantId(), item.getNodeId(), item.getWorkloadId(),
                        item.getConfigVersion(), item.getSchemaVersion(), item.getCanonicalizationVersion(),
                        PAYLOAD, item.getPayloadSha256(), item.getCanonicalLengthBytes(),
                        item.getPublishedAt()));
            }
        }

        @Override
        public List<CollectorConfigReleasePending> listPending(int limit) {
            if (listPendingEntered != null) {
                listPendingEntered.countDown();
            }
            if (listPendingRelease != null) {
                try {
                    if (!listPendingRelease.await(2, TimeUnit.SECONDS)) {
                        throw new AssertionError("list pending release latch timed out");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            }
            return pending;
        }

        @Override
        public Optional<CollectorConfigReleaseDetail> getDetail(String releaseId) {
            return Optional.ofNullable(details.get(releaseId));
        }

        @Override
        public CollectorConfigObservedResponse reportObserved(CollectorConfigReleaseObservedReport report) {
            if (reportFailures.contains(report.getStatus())) {
                throw new IllegalStateException("temporary report failure");
            }
            reports.add(report);
            return new CollectorConfigObservedResponse(report.getReleaseId(), report.getStatus(),
                    true, report.getStatus() != CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED,
                    false);
        }

        private List<CollectorConfigReleaseObservedReport.Status> statuses() {
            return reports.stream().map(CollectorConfigReleaseObservedReport::getStatus).toList();
        }
    }

    private static final class FakeAgent implements CollectorAgentPort {
        private final CollectorConfigAgentStatus observedStatus;
        private String failureCode;
        private String failWorkloadId;
        private CollectorAgentClient.CollectorAgentException putFailure;
        private Long observedConfigVersion;
        private String observedHash;
        private int putCalls;
        private int getCalls;
        private String putPath;
        private String getPath;

        private FakeAgent(CollectorConfigAgentStatus observedStatus) {
            this.observedStatus = observedStatus;
        }

        @Override
        public CollectorConfigPutResponseDTO putConfig(CollectorNodeEndpoint node,
                                                        CollectorConfigPutRequestDTO request) {
            putCalls++;
            putPath = CollectorAgentClient.PUT_PATH;
            if (putFailure != null) {
                throw putFailure;
            }
            if (request.getWorkloadId().equals(failWorkloadId)) {
                throw new CollectorAgentClient.CollectorAgentException(
                        CollectorAgentClient.Kind.RETRYABLE, 503, "AGENT_UNAVAILABLE");
            }
            CollectorConfigPutResponseDTO response = new CollectorConfigPutResponseDTO();
            response.setStatus(CollectorConfigAgentStatus.IDEMPOTENT);
            response.setWorkloadId(request.getWorkloadId());
            response.setConfigVersion(request.getConfigVersion());
            response.setPayloadSha256(request.getPayloadSha256());
            return response;
        }

        @Override
        public CollectorConfigGetResponseDTO getConfig(CollectorNodeEndpoint node, String workloadId) {
            getCalls++;
            getPath = CollectorAgentClient.GET_PATH_PREFIX + workloadId;
            CollectorConfigGetResponseDTO response = new CollectorConfigGetResponseDTO();
            response.setWorkloadId(workloadId);
            response.setDesired(summary(1));
            response.setActive(observedStatus == CollectorConfigAgentStatus.APPLIED ? summary(1) : summary(0));
            CollectorConfigObservedSummaryDTO observed = new CollectorConfigObservedSummaryDTO();
            observed.setWorkloadId(workloadId);
            observed.setStatus(observedStatus);
            observed.setConfigVersion(observedConfigVersion == null ? 1L : observedConfigVersion);
            observed.setPayloadSha256(observedHash == null ? HASH : observedHash);
            observed.setObservedAt("2026-08-17T10:01:00Z");
            observed.setErrorCode(failureCode);
            response.setObserved(observed);
            return response;
        }

        private CollectorConfigStateSummaryDTO summary(long version) {
            CollectorConfigStateSummaryDTO summary = new CollectorConfigStateSummaryDTO();
            summary.setPresent(version > 0);
            summary.setSchemaVersion("1.1");
            summary.setConfigVersion(version > 0 ? version : 0L);
            summary.setPayloadSha256(version > 0 ? HASH : "0".repeat(64));
            summary.setCanonicalLengthBytes(version > 0
                    ? (long) PAYLOAD.getBytes(StandardCharsets.UTF_8).length : 1L);
            return summary;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }

        private void advance(Duration duration) { instant = instant.plus(duration); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
