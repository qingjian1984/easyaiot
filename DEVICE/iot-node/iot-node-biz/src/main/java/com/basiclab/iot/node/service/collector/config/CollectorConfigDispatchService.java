package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.node.domain.collector.config.CollectorConfigAgentStatus;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigGetResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigObservedSummaryDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutRequestDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigStateSummaryDTO;
import com.basiclab.iot.node.enums.NodeStatusEnum;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * collector release 的拉取、固定协议派发和 Agent 状态对账实现。
 *
 * <p>该服务没有数据库写权限，也不使用通用工作负载控制面。每条 release
 * 独立分类，失败不会阻断同批其它 release；canonical 只在当前调用栈中短暂存在。</p>
 */
public final class CollectorConfigDispatchService {

    public static final int MIN_PENDING_LIMIT = 1;
    public static final int MAX_PENDING_LIMIT = 100;
    public static final int MAX_CANONICAL_BYTES = 2 * 1024 * 1024;
    public static final long MAX_JSON_SAFE_INTEGER = 9_007_199_254_740_991L;
    public static final String SCHEMA_VERSION = "1.1";
    public static final String CANONICALIZATION_VERSION = "jcs-rfc8785-v1";

    private static final Pattern POSITIVE_DECIMAL = Pattern.compile("^[1-9][0-9]*$");
    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern ERROR_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final List<String> FAILED_ERROR_CODES = List.of(
            "COLLECTOR_CONFIG_NOT_AVAILABLE",
            "COLLECTOR_CONFIG_TOO_LARGE",
            "COLLECTOR_CONFIG_JSON_INVALID",
            "COLLECTOR_CONFIG_SCHEMA_INVALID",
            "COLLECTOR_CONFIG_CANONICAL_INVALID",
            "COLLECTOR_CONFIG_WORKLOAD_MISMATCH",
            "COLLECTOR_CONFIG_VERSION_STALE",
            "COLLECTOR_CONFIG_VERSION_CONFLICT",
            "COLLECTOR_CONFIG_STATE_CORRUPT",
            "COLLECTOR_CONFIG_PERMISSION_INVALID",
            "COLLECTOR_CONFIG_WRITE_FAILED",
            "COLLECTOR_CONFIG_APPLY_FAILED");
    private static final List<String> RETRY_ERROR_CODES = List.of(
            "AGENT_SIGNING_KEY_UNAVAILABLE",
            "AGENT_CONNECTION_FAILED",
            "AGENT_REQUEST_INTERRUPTED",
            "AGENT_RESPONSE_TOO_LARGE",
            "AGENT_RESPONSE_INVALID",
            "COLLECTOR_WORKLOAD_NOT_FOUND",
            "NODE_ENDPOINT_INVALID");

    private final CollectorConfigReleaseClientPort releaseClient;
    private final CollectorNodeAuthorityPort nodeAuthority;
    private final CollectorAgentPort agentClient;
    private final Clock clock;
    private final CollectorConfigDispatchBackoff backoff;
    private final AtomicBoolean running = new AtomicBoolean();

    public CollectorConfigDispatchService(CollectorConfigReleaseClientPort releaseClient,
                                          CollectorNodeAuthorityPort nodeAuthority,
                                          CollectorAgentPort agentClient,
                                          Clock clock,
                                          CollectorConfigDispatchBackoff backoff) {
        this.releaseClient = Objects.requireNonNull(releaseClient, "releaseClient");
        this.nodeAuthority = Objects.requireNonNull(nodeAuthority, "nodeAuthority");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
    }

    public CollectorConfigDispatchService(CollectorConfigReleaseClientPort releaseClient,
                                          CollectorNodeAuthorityPort nodeAuthority,
                                          CollectorAgentPort agentClient) {
        this(releaseClient, nodeAuthority, agentClient, Clock.systemUTC(),
                new CollectorConfigDispatchBackoff());
    }

    /** 拉取一批 pending；最多 100 条且同一进程单飞。 */
    public CollectorConfigDispatchBatchResult dispatchPending(int limit) {
        if (limit < MIN_PENDING_LIMIT || limit > MAX_PENDING_LIMIT) {
            throw new IllegalArgumentException("pending limit must be between 1 and 100");
        }
        if (!running.compareAndSet(false, true)) {
            return new CollectorConfigDispatchBatchResult(
                    List.of(new CollectorConfigDispatchOutcome(null,
                            CollectorConfigDispatchStatus.REENTRANT, "DISPATCH_ALREADY_RUNNING")), true);
        }
        try {
            List<CollectorConfigReleasePending> pending;
            try {
                pending = releaseClient.listPending(limit);
            } catch (RuntimeException error) {
                return new CollectorConfigDispatchBatchResult(List.of(
                        new CollectorConfigDispatchOutcome(null, CollectorConfigDispatchStatus.RETRY,
                                "DEVICE_PENDING_UNAVAILABLE")), false);
            }
            if (pending == null) {
                return new CollectorConfigDispatchBatchResult(List.of(
                        new CollectorConfigDispatchOutcome(null, CollectorConfigDispatchStatus.RETRY,
                                "DEVICE_PENDING_INVALID")), false);
            }
            if (pending.size() > limit) {
                pending = new ArrayList<>(pending.subList(0, limit));
            }
            List<CollectorConfigDispatchOutcome> outcomes = new ArrayList<>();
            for (CollectorConfigReleasePending item : pending) {
                outcomes.add(dispatchOne(item));
            }
            return new CollectorConfigDispatchBatchResult(outcomes, false);
        } finally {
            running.set(false);
        }
    }

    public CollectorConfigDispatchBatchResult dispatchPending() {
        return dispatchPending(MAX_PENDING_LIMIT);
    }

    /** 公开单条入口便于 job 与确定性合同测试复用。 */
    public CollectorConfigDispatchOutcome dispatchOne(CollectorConfigReleasePending pending) {
        if (pending == null) {
            return new CollectorConfigDispatchOutcome(null, CollectorConfigDispatchStatus.RETRY,
                    "PENDING_INVALID");
        }
        if (pending.getReleaseId() != null && !backoff.isDue(pending.getReleaseId())) {
            return new CollectorConfigDispatchOutcome(pending.getReleaseId(),
                    CollectorConfigDispatchStatus.SKIPPED, "BACKOFF_ACTIVE");
        }

        try {
            return withValidatedRelease(pending);
        } catch (RetryIssue issue) {
            return retryFromPending(pending, issue.getCode());
        } catch (ContractIssue issue) {
            return deterministicFailure(pending, issue.getCode());
        } catch (RuntimeException error) {
            // 不把异常类名/message/响应正文带入结果或观测；单条失败只能退避重试。
            return retryFromPending(pending, "DISPATCH_INTERNAL_RETRY");
        }
    }

    /**
     * Canonical 文本只在该调用栈内传给固定 Agent 请求；ValidatedRelease 本身不保存它。
     * 这样方法返回后，退避项、结果对象和服务字段都不会持有快照内容。
     */
    private CollectorConfigDispatchOutcome withValidatedRelease(
            CollectorConfigReleasePending pending) {
        return validateReleaseAndLoadDetail(pending,
                (release, payloadCanonical) -> dispatchValidated(pending, release, payloadCanonical));
    }

    private CollectorConfigDispatchOutcome dispatchValidated(CollectorConfigReleasePending pending,
                                                              ValidatedRelease release,
                                                              String payloadCanonical) {
        CollectorNodeEndpoint node = loadOnlineNode(release.nodeId);
        CollectorConfigPutRequestDTO request = putRequest(release, payloadCanonical);
        CollectorConfigPutResponseDTO putResponse;
        try {
            try {
                putResponse = agentClient.putConfig(node, request);
            } catch (CollectorAgentClient.CollectorAgentException error) {
                return classifyAgentFailure(pending, release, error);
            }
        } finally {
            // Drop the canonical String from the request DTO immediately after the PUT attempt.
            request.setPayloadCanonical(null);
        }
        if (!acceptedForRelease(putResponse, release)) {
            return retry(pending, release, "AGENT_RESPONSE_INVALID");
        }
        if (!reportAccepted(release)) {
            return retry(pending, release, "DEVICE_ACCEPT_REPORT_UNAVAILABLE");
        }

        CollectorConfigGetResponseDTO state;
        try {
            state = agentClient.getConfig(node, release.workloadId);
        } catch (CollectorAgentClient.CollectorAgentException error) {
            return retry(pending, release, stableAgentRetryCode(error.getStableCode()));
        }
        CollectorConfigDispatchOutcome outcome = reconcile(release, state);
        if (outcome.getStatus() == CollectorConfigDispatchStatus.APPLIED
                || outcome.getStatus() == CollectorConfigDispatchStatus.FAILED) {
            backoff.clear(release.releaseId);
        }
        return outcome;
    }

    private CollectorConfigDispatchOutcome validateReleaseAndLoadDetail(
            CollectorConfigReleasePending pending,
            ValidatedReleaseAction action) {
        String releaseId = requiredPositiveId(pending.getReleaseId(), "releaseId", false);
        String tenantId = requiredPositiveId(pending.getTenantId(), "tenantId", false);
        String nodeId = requiredPositiveId(pending.getNodeId(), "nodeId", false);
        String workloadId = requiredText(pending.getWorkloadId(), "workloadId");
        String configVersion = requiredSafeVersion(pending.getConfigVersion(), "configVersion");
        requireEquals(SCHEMA_VERSION, pending.getSchemaVersion(), "COLLECTOR_CONFIG_SCHEMA_INVALID");
        requireEquals(CANONICALIZATION_VERSION, pending.getCanonicalizationVersion(),
                "COLLECTOR_CONFIG_CANONICAL_INVALID");
        String hash = requiredHash(pending.getPayloadSha256(), "payloadSha256");
        long length = requiredLength(pending.getCanonicalLengthBytes());
        requiredText(pending.getPublishedAt(), "publishedAt");

        CollectorConfigReleaseDetail detail;
        try {
            Optional<CollectorConfigReleaseDetail> optional = releaseClient.getDetail(releaseId);
            detail = optional.orElseThrow(() -> new RetryIssue("RELEASE_DETAIL_NOT_FOUND"));
        } catch (ContractIssue issue) {
            throw issue;
        } catch (RuntimeException error) {
            throw new RetryIssue("DEVICE_DETAIL_UNAVAILABLE");
        }
        if (!same(pending.getReleaseId(), detail.getReleaseId())
                || !same(pending.getTenantId(), detail.getTenantId())
                || !same(pending.getNodeId(), detail.getNodeId())
                || !same(pending.getWorkloadId(), detail.getWorkloadId())
                || !same(pending.getConfigVersion(), detail.getConfigVersion())
                || !same(pending.getSchemaVersion(), detail.getSchemaVersion())
                || !same(pending.getCanonicalizationVersion(), detail.getCanonicalizationVersion())
                || !same(pending.getPayloadSha256(), detail.getPayloadSha256())
                || !Objects.equals(pending.getCanonicalLengthBytes(), detail.getCanonicalLengthBytes())
                || !same(pending.getPublishedAt(), detail.getPublishedAt())) {
            throw new ContractIssue("RELEASE_DETAIL_IDENTITY_MISMATCH");
        }
        String detailVersion = requiredSafeVersion(detail.getConfigVersion(), "detail.configVersion");
        String detailHash = requiredHash(detail.getPayloadSha256(), "detail.payloadSha256");
        long detailLength = requiredLength(detail.getCanonicalLengthBytes());
        if (!configVersion.equals(detailVersion) || !hash.equals(detailHash) || length != detailLength) {
            throw new ContractIssue("RELEASE_DETAIL_CONTRACT_INVALID");
        }
        byte[] canonical = null;
        try {
            canonical = strictUtf8(detail.getPayloadCanonical());
            if (canonical.length != length) {
                throw new ContractIssue("COLLECTOR_CONFIG_LENGTH_MISMATCH");
            }
            if (!hash.equals(sha256(canonical))) {
                throw new ContractIssue("COLLECTOR_CONFIG_HASH_MISMATCH");
            }
            ValidatedRelease release = new ValidatedRelease(releaseId, tenantId, nodeId, workloadId,
                    configVersion, Long.parseLong(configVersion), hash, length);
            return action.apply(release, detail.getPayloadCanonical());
        } finally {
            if (canonical != null) {
                Arrays.fill(canonical, (byte) 0);
            }
        }
    }

    private CollectorNodeEndpoint loadOnlineNode(String nodeIdText) {
        long nodeId = parseLong(nodeIdText, "nodeId", false);
        Optional<CollectorNodeEndpoint> node;
        try {
            node = nodeAuthority.findById(nodeId);
        } catch (RuntimeException error) {
            throw new RetryIssue("NODE_LOOKUP_UNAVAILABLE");
        }
        CollectorNodeEndpoint endpoint = node.orElseThrow(() -> new RetryIssue("NODE_NOT_FOUND"));
        if (endpoint.getNodeId() != nodeId) {
            throw new RetryIssue("NODE_ID_MISMATCH");
        }
        if (!NodeStatusEnum.ONLINE.getStatus().equals(endpoint.getStatus())) {
            throw new RetryIssue("NODE_OFFLINE");
        }
        validateHost(endpoint.getHost());
        if (endpoint.getAgentPort() < 1 || endpoint.getAgentPort() > 65535) {
            throw new RetryIssue("NODE_AGENT_PORT_INVALID");
        }
        return endpoint;
    }

    private CollectorConfigPutRequestDTO putRequest(ValidatedRelease release,
                                                     String payloadCanonical) {
        CollectorConfigPutRequestDTO request = new CollectorConfigPutRequestDTO();
        request.setWorkloadId(release.workloadId);
        request.setConfigVersion(release.configVersion);
        request.setSchemaVersion(SCHEMA_VERSION);
        request.setCanonicalizationVersion(CANONICALIZATION_VERSION);
        request.setPayloadCanonical(payloadCanonical);
        request.setPayloadSha256(release.payloadSha256);
        request.setCanonicalLengthBytes(release.canonicalLengthBytes);
        return request;
    }

    private boolean acceptedForRelease(CollectorConfigPutResponseDTO response,
                                       ValidatedRelease release) {
        return response != null
                && (response.getStatus() == CollectorConfigAgentStatus.ACCEPTED
                || response.getStatus() == CollectorConfigAgentStatus.IDEMPOTENT)
                && same(response.getWorkloadId(), release.workloadId)
                && Objects.equals(response.getConfigVersion(), release.configVersion)
                && same(response.getPayloadSha256(), release.payloadSha256);
    }

    private boolean reportAccepted(ValidatedRelease release) {
        CollectorConfigReleaseObservedReport report = new CollectorConfigReleaseObservedReport(
                release.releaseId, release.tenantId, release.nodeId, release.workloadId,
                release.configVersionText, release.payloadSha256,
                CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED,
                clock.instant().toString(), null);
        try {
            CollectorConfigObservedResponse response = releaseClient.reportObserved(report);
            return acceptedReport(response, release, CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private CollectorConfigDispatchOutcome reconcile(ValidatedRelease release,
                                                      CollectorConfigGetResponseDTO state) {
        if (state == null || !same(state.getWorkloadId(), release.workloadId)) {
            return retry(release, "AGENT_WORKLOAD_MISMATCH");
        }
        CollectorConfigObservedSummaryDTO observed = state.getObserved();
        if (observed == null || observed.getStatus() == null
                || !same(observed.getWorkloadId(), release.workloadId)
                || !Objects.equals(observed.getConfigVersion(), release.configVersion)
                || !same(observed.getPayloadSha256(), release.payloadSha256)) {
            return retry(release, "AGENT_OBSERVED_NOT_READY");
        }
        CollectorConfigAgentStatus status = observed.getStatus();
        if (status == CollectorConfigAgentStatus.APPLIED) {
            if (!stateSummaryMatches(state.getDesired(), release)
                    || !stateSummaryMatches(state.getActive(), release)) {
                return retry(release, "AGENT_ACTIVE_NOT_APPLIED");
            }
            if (observed.getErrorCode() != null && !observed.getErrorCode().isBlank()) {
                return retry(release, "AGENT_OBSERVED_INVALID");
            }
            if (!reportTerminal(release, CollectorConfigReleaseObservedReport.Status.APPLIED, null)) {
                return retry(release, "DEVICE_APPLIED_REPORT_UNAVAILABLE");
            }
            return new CollectorConfigDispatchOutcome(release.releaseId,
                    CollectorConfigDispatchStatus.APPLIED, "APPLIED");
        }
        if (status == CollectorConfigAgentStatus.FAILED) {
            if (!stateSummaryMatches(state.getDesired(), release)) {
                return retry(release, "AGENT_DESIRED_NOT_MATCHED");
            }
            String errorCode = stableAgentErrorCode(observed.getErrorCode());
            if (!reportTerminal(release, CollectorConfigReleaseObservedReport.Status.FAILED, errorCode)) {
                return retry(release, "DEVICE_FAILED_REPORT_UNAVAILABLE");
            }
            return new CollectorConfigDispatchOutcome(release.releaseId,
                    CollectorConfigDispatchStatus.FAILED, errorCode);
        }
        return retry(release, "AGENT_STATUS_NOT_TERMINAL");
    }

    private boolean stateSummaryMatches(CollectorConfigStateSummaryDTO summary,
                                        ValidatedRelease release) {
        return summary != null && Boolean.TRUE.equals(summary.getPresent())
                && Objects.equals(summary.getConfigVersion(), release.configVersion)
                && same(summary.getPayloadSha256(), release.payloadSha256)
                && Objects.equals(summary.getCanonicalLengthBytes(), release.canonicalLengthBytes)
                && SCHEMA_VERSION.equals(summary.getSchemaVersion());
    }

    private boolean reportTerminal(ValidatedRelease release,
                                   CollectorConfigReleaseObservedReport.Status status,
                                   String errorCode) {
        CollectorConfigReleaseObservedReport report = new CollectorConfigReleaseObservedReport(
                release.releaseId, release.tenantId, release.nodeId, release.workloadId,
                release.configVersionText, release.payloadSha256, status,
                clock.instant().toString(), errorCode);
        try {
            CollectorConfigObservedResponse response = releaseClient.reportObserved(report);
            return acceptedReport(response, release, status);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean acceptedReport(CollectorConfigObservedResponse response,
                                          ValidatedRelease release,
                                          CollectorConfigReleaseObservedReport.Status status) {
        return response != null && response.isAccepted()
                && same(response.getReleaseId(), release.releaseId)
                && response.getStatus() == status;
    }

    private CollectorConfigDispatchOutcome classifyAgentFailure(
            CollectorConfigReleasePending pending,
            ValidatedRelease release,
            CollectorAgentClient.CollectorAgentException error) {
        if (error.getKind() == CollectorAgentClient.Kind.DETERMINISTIC) {
            String code = mapAgentFailureCode(error.getStableCode());
            if (reportTerminal(release, CollectorConfigReleaseObservedReport.Status.FAILED, code)) {
                backoff.clear(release.releaseId);
                return new CollectorConfigDispatchOutcome(release.releaseId,
                        CollectorConfigDispatchStatus.FAILED, code);
            }
            return retry(pending, release, "DEVICE_FAILED_REPORT_UNAVAILABLE");
        }
        return retry(pending, release, stableAgentRetryCode(error.getStableCode()));
    }

    private CollectorConfigDispatchOutcome deterministicFailure(CollectorConfigReleasePending pending,
                                                                 String code) {
        String releaseId = pending == null ? null : pending.getReleaseId();
        if (!validText(releaseId)) {
            return new CollectorConfigDispatchOutcome(releaseId, CollectorConfigDispatchStatus.FAILED, code);
        }
        String tenantId = pending.getTenantId();
        String nodeId = pending.getNodeId();
        String workloadId = pending.getWorkloadId();
        String version = pending.getConfigVersion();
        String hash = pending.getPayloadSha256();
        if (!reportablePendingIdentity(releaseId, tenantId, nodeId, workloadId, version, hash)) {
            return retryFromPending(pending, "PENDING_INVALID");
        }
        String stable = mapAgentFailureCode(code);
        try {
            CollectorConfigObservedResponse response = releaseClient.reportObserved(
                    new CollectorConfigReleaseObservedReport(releaseId, tenantId, nodeId, workloadId,
                            version, hash, CollectorConfigReleaseObservedReport.Status.FAILED,
                            clock.instant().toString(), stable));
            if (response != null && response.isAccepted()
                    && same(response.getReleaseId(), releaseId)
                    && response.getStatus() == CollectorConfigReleaseObservedReport.Status.FAILED) {
                backoff.clear(releaseId);
                return new CollectorConfigDispatchOutcome(releaseId,
                        CollectorConfigDispatchStatus.FAILED, stable);
            }
        } catch (RuntimeException ignored) {
            // report failure is retried with a bounded key only.
        }
        return retryFromPending(pending, "DEVICE_FAILED_REPORT_UNAVAILABLE");
    }

    private static boolean reportablePendingIdentity(String releaseId,
                                                     String tenantId,
                                                     String nodeId,
                                                     String workloadId,
                                                     String version,
                                                     String hash) {
        try {
            requiredPositiveId(releaseId, "releaseId", false);
            requiredPositiveId(tenantId, "tenantId", false);
            requiredPositiveId(nodeId, "nodeId", false);
            requiredText(workloadId, "workloadId");
            requiredSafeVersion(version, "configVersion");
            requiredHash(hash, "payloadSha256");
            return true;
        } catch (ContractIssue invalid) {
            return false;
        }
    }

    private CollectorConfigDispatchOutcome retry(ValidatedRelease release, String code) {
        backoff.recordRetry(release.releaseId, release.configVersionText, release.payloadSha256);
        return new CollectorConfigDispatchOutcome(release.releaseId,
                CollectorConfigDispatchStatus.RETRY, stableInternalCode(code));
    }

    private CollectorConfigDispatchOutcome retry(CollectorConfigReleasePending pending,
                                                 ValidatedRelease release,
                                                 String code) {
        return retry(release, code);
    }

    private CollectorConfigDispatchOutcome retryFromPending(CollectorConfigReleasePending pending,
                                                             String code) {
        String releaseId = pending == null ? null : pending.getReleaseId();
        String version = pending == null ? null : pending.getConfigVersion();
        String hash = pending == null ? null : pending.getPayloadSha256();
        if (validBackoffKey(releaseId, version, hash)) {
            backoff.recordRetry(releaseId, version, hash);
        }
        return new CollectorConfigDispatchOutcome(releaseId, CollectorConfigDispatchStatus.RETRY,
                stableInternalCode(code));
    }

    private static boolean validBackoffKey(String releaseId, String version, String hash) {
        if (!validText(releaseId) || !validText(version)
                || !HASH.matcher(hash == null ? "" : hash).matches()) {
            return false;
        }
        try {
            parseLong(releaseId, "releaseId", false);
            parseLong(version, "configVersion", true);
            return true;
        } catch (ContractIssue invalid) {
            return false;
        }
    }

    private static String stableAgentErrorCode(String code) {
        if (code != null && ERROR_CODE.matcher(code).matches() && FAILED_ERROR_CODES.contains(code)) {
            return code;
        }
        return "COLLECTOR_CONFIG_APPLY_FAILED";
    }

    private static String mapAgentFailureCode(String code) {
        if ("CONFIG_VERSION_STALE".equals(code)) {
            return "COLLECTOR_CONFIG_VERSION_STALE";
        }
        if ("CONFIG_VERSION_CONFLICT".equals(code)) {
            return "COLLECTOR_CONFIG_VERSION_CONFLICT";
        }
        return stableAgentErrorCode(code);
    }

    private static String stableAgentRetryCode(String code) {
        if (code != null && RETRY_ERROR_CODES.contains(code)) {
            return code;
        }
        if (code != null && code.matches("AGENT_HTTP_[1-5][0-9]{2}")) {
            return code;
        }
        return "AGENT_REQUEST_FAILED";
    }

    private static String stableInternalCode(String code) {
        if (code == null || !ERROR_CODE.matcher(code).matches()) {
            return "COLLECTOR_CONFIG_APPLY_FAILED";
        }
        return code;
    }

    private static String requiredPositiveId(String value, String field, boolean jsonSafe) {
        parseLong(value, field, jsonSafe);
        return value;
    }

    private static String requiredSafeVersion(String value, String field) {
        parseLong(value, field, true);
        return value;
    }

    private static long parseLong(String value, String field, boolean jsonSafe) {
        if (value == null || !POSITIVE_DECIMAL.matcher(value).matches()) {
            throw new ContractIssue("RELEASE_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0 || (jsonSafe && parsed > MAX_JSON_SAFE_INTEGER)) {
                throw new ContractIssue("RELEASE_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new ContractIssue("RELEASE_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
    }

    private static long requiredLength(Long value) {
        if (value == null || value <= 0 || value > MAX_CANONICAL_BYTES) {
            throw new ContractIssue("COLLECTOR_CONFIG_LENGTH_INVALID");
        }
        return value;
    }

    private static String requiredHash(String value, String field) {
        if (value == null || !HASH.matcher(value).matches()) {
            throw new ContractIssue("RELEASE_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
        return value;
    }

    private static String requiredText(String value, String field) {
        if (!validText(value)) {
            throw new ContractIssue("RELEASE_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
        return value;
    }

    private static void requireEquals(String expected, String actual, String code) {
        if (!expected.equals(actual)) {
            throw new ContractIssue(code);
        }
    }

    private static byte[] strictUtf8(String value) {
        if (value == null) {
            throw new ContractIssue("COLLECTOR_CONFIG_CANONICAL_INVALID");
        }
        try {
            CharBuffer chars = CharBuffer.wrap(value);
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(chars);
            byte[] result = new byte[bytes.remaining()];
            bytes.get(result);
            if (result.length == 0 || result.length > MAX_CANONICAL_BYTES) {
                throw new ContractIssue("COLLECTOR_CONFIG_LENGTH_INVALID");
            }
            return result;
        } catch (CharacterCodingException error) {
            throw new ContractIssue("COLLECTOR_CONFIG_CANONICAL_INVALID");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static void validateHost(String host) {
        if (!validText(host) || !host.equals(host.trim()) || host.contains("://")
                || host.indexOf('/') >= 0 || host.indexOf('\\') >= 0 || host.indexOf('@') >= 0
                || host.indexOf('?') >= 0 || host.indexOf('#') >= 0) {
            throw new RetryIssue("NODE_HOST_INVALID");
        }
        for (int i = 0; i < host.length(); i++) {
            if (Character.isISOControl(host.charAt(i))) {
                throw new RetryIssue("NODE_HOST_INVALID");
            }
        }
    }

    private static boolean same(String left, String right) {
        return Objects.equals(left, right);
    }

    private static boolean validText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ValidatedRelease {
        private final String releaseId;
        private final String tenantId;
        private final String nodeId;
        private final String workloadId;
        private final String configVersionText;
        private final long configVersion;
        private final String payloadSha256;
        private final long canonicalLengthBytes;

        private ValidatedRelease(String releaseId, String tenantId, String nodeId, String workloadId,
                                 String configVersionText, long configVersion, String payloadSha256,
                                 long canonicalLengthBytes) {
            this.releaseId = releaseId;
            this.tenantId = tenantId;
            this.nodeId = nodeId;
            this.workloadId = workloadId;
            this.configVersionText = configVersionText;
            this.configVersion = configVersion;
            this.payloadSha256 = payloadSha256;
            this.canonicalLengthBytes = canonicalLengthBytes;
        }
    }

    @FunctionalInterface
    private interface ValidatedReleaseAction {
        CollectorConfigDispatchOutcome apply(ValidatedRelease release, String payloadCanonical);
    }

    private static class ContractIssue extends RuntimeException {
        private final String code;

        private ContractIssue(String code) {
            this.code = code;
        }

        protected String getCode() {
            return code;
        }
    }

    private static final class RetryIssue extends ContractIssue {
        private RetryIssue(String code) {
            super(code);
        }
    }
}
