package com.basiclab.iot.device.service.collector;

import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseDetailDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedRequestDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedResponseDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedStatus;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleasePendingDTO;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** M1-LC-02A §4.2 release pending/detail/observed 业务服务。 */
@Service
public class CollectorConfigReleaseInternalService {

    public static final int MIN_PENDING_LIMIT = 1;
    public static final int MAX_PENDING_LIMIT = 100;

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final int MAX_ERROR_DETAIL = 256;

    private final CollectorConfigReleaseInternalRepository repository;
    private final CollectorConfigReleaseObservedFactRecorder factRecorder;

    public CollectorConfigReleaseInternalService(
            CollectorConfigReleaseInternalRepository repository,
            CollectorConfigReleaseObservedFactRecorder factRecorder) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.factRecorder = Objects.requireNonNull(factRecorder, "factRecorder");
    }

    public List<CollectorConfigReleasePendingDTO> listPending(int limit) {
        if (limit < MIN_PENDING_LIMIT || limit > MAX_PENDING_LIMIT) {
            throw invalid("COLLECTOR_RELEASE_LIMIT_INVALID");
        }
        return repository.findPending(limit).stream()
                .map(CollectorConfigReleaseInternalService::pending)
                .collect(Collectors.toList());
    }

    public CollectorConfigReleaseDetailDTO detail(String releaseId) {
        long id = positiveId(releaseId, "releaseId");
        Optional<CollectorConfigReleaseInternalRepository.ReleaseRecord> value =
                repository.findById(id);
        return value.map(CollectorConfigReleaseInternalService::detail)
                .orElseThrow(() -> invalid("COLLECTOR_RELEASE_NOT_FOUND"));
    }

    public CollectorConfigReleaseObservedResponseDTO observe(
            String pathReleaseId, CollectorConfigReleaseObservedRequestDTO request) {
        if (request == null) throw invalid("COLLECTOR_OBSERVED_INVALID");
        long pathId = positiveId(pathReleaseId, "releaseId");
        long bodyId = positiveId(request.getReleaseId(), "releaseId");
        if (pathId != bodyId) throw invalid("COLLECTOR_OBSERVED_RELEASE_MISMATCH");
        long tenantId = positiveId(request.getTenantId(), "tenantId");
        long nodeId = positiveId(request.getNodeId(), "nodeId");
        long configVersion = positiveId(request.getConfigVersion(), "configVersion");
        String workloadId = requiredWorkload(request.getWorkloadId());
        String hash = requiredHash(request.getPayloadSha256());
        CollectorConfigReleaseObservedStatus observedStatus = request.getStatus();
        if (observedStatus == null) throw invalid("COLLECTOR_OBSERVED_STATUS_INVALID");
        validateObservedAt(request.getObservedAt());

        String errorCode = normalizeErrorCode(observedStatus, request.getErrorCode());
        String errorDetail = normalizeErrorDetail(observedStatus, request.getErrorDetailSanitized());
        CollectorConfigReleaseInternalRepository.ObservedRecord observed =
                new CollectorConfigReleaseInternalRepository.ObservedRecord(
                        bodyId, tenantId, nodeId, workloadId, configVersion, hash,
                        observedStatus, errorCode, errorDetail);
        CollectorConfigReleaseInternalRepository.ObservedCasResult result =
                repository.observe(observed);

        CollectorConfigReleaseObservedFact fact = new CollectorConfigReleaseObservedFact(
                bodyId, tenantId, nodeId, workloadId, configVersion, observedStatus);
        if (observedStatus == CollectorConfigReleaseObservedStatus.AGENT_ACCEPTED
                || result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.MISMATCH
                || result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.STALE) {
            factRecorder.record(fact, result.outcome().name());
        }

        CollectorConfigReleaseObservedResponseDTO response =
                new CollectorConfigReleaseObservedResponseDTO();
        response.setReleaseId(Long.toString(bodyId));
        response.setStatus(observedStatus);
        response.setAccepted(result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.APPLIED
                || result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.FAILED
                || result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.AGENT_ACCEPTED
                || result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.IDEMPOTENT);
        response.setTerminal(result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.APPLIED
                || result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.FAILED
                || result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.IDEMPOTENT);
        response.setIdempotent(result.outcome() == CollectorConfigReleaseInternalRepository.Outcome.IDEMPOTENT);
        return response;
    }

    private static CollectorConfigReleasePendingDTO pending(
            CollectorConfigReleaseInternalRepository.ReleaseRecord row) {
        CollectorConfigReleasePendingDTO dto = new CollectorConfigReleasePendingDTO();
        fillCommon(dto, row);
        return dto;
    }

    private static CollectorConfigReleaseDetailDTO detail(
            CollectorConfigReleaseInternalRepository.ReleaseRecord row) {
        CollectorConfigReleaseDetailDTO dto = new CollectorConfigReleaseDetailDTO();
        dto.setReleaseId(Long.toString(row.releaseId()));
        dto.setTenantId(Long.toString(row.tenantId()));
        dto.setNodeId(Long.toString(row.nodeId()));
        dto.setWorkloadId(row.workloadId());
        dto.setConfigVersion(Long.toString(row.configVersion()));
        dto.setSchemaVersion(row.schemaVersion());
        dto.setCanonicalizationVersion(row.canonicalizationVersion());
        dto.setPayloadCanonical(row.payloadCanonical());
        dto.setPayloadSha256(row.payloadSha256());
        dto.setCanonicalLengthBytes(row.canonicalLengthBytes());
        dto.setPublishedAt(row.publishedAt());
        return dto;
    }

    private static void fillCommon(CollectorConfigReleasePendingDTO dto,
                                   CollectorConfigReleaseInternalRepository.ReleaseRecord row) {
        dto.setReleaseId(Long.toString(row.releaseId()));
        dto.setTenantId(Long.toString(row.tenantId()));
        dto.setNodeId(Long.toString(row.nodeId()));
        dto.setWorkloadId(row.workloadId());
        dto.setConfigVersion(Long.toString(row.configVersion()));
        dto.setSchemaVersion(row.schemaVersion());
        dto.setCanonicalizationVersion(row.canonicalizationVersion());
        dto.setPayloadSha256(row.payloadSha256());
        dto.setCanonicalLengthBytes(row.canonicalLengthBytes());
        dto.setPublishedAt(row.publishedAt());
    }

    private static long positiveId(String value, String field) {
        if (value == null || !DECIMAL_ID.matcher(value).matches()) {
            throw invalid("COLLECTOR_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid("COLLECTOR_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
    }

    private static String requiredWorkload(String workloadId) {
        if (workloadId == null || workloadId.trim().isEmpty()
                || workloadId.length() > 128 || workloadId.indexOf('\u0000') >= 0) {
            throw invalid("COLLECTOR_WORKLOAD_INVALID");
        }
        return workloadId;
    }

    private static String requiredHash(String hash) {
        if (hash == null || !SHA256.matcher(hash).matches()) {
            throw invalid("COLLECTOR_PAYLOAD_HASH_INVALID");
        }
        return hash;
    }

    private static void validateObservedAt(String observedAt) {
        if (observedAt == null || observedAt.trim().isEmpty() || observedAt.length() > 64) {
            throw invalid("COLLECTOR_OBSERVED_AT_INVALID");
        }
        try {
            OffsetDateTime.parse(observedAt);
        } catch (DateTimeParseException e) {
            throw invalid("COLLECTOR_OBSERVED_AT_INVALID");
        }
    }

    private static String normalizeErrorCode(CollectorConfigReleaseObservedStatus status,
                                             String errorCode) {
        String value = errorCode == null ? "" : errorCode;
        if (status == CollectorConfigReleaseObservedStatus.FAILED) {
            if (!ERROR_CODE.matcher(value).matches()) {
                throw invalid("COLLECTOR_ERROR_CODE_INVALID");
            }
            return value;
        }
        if (!value.isEmpty()) throw invalid("COLLECTOR_ERROR_FIELDS_INVALID");
        return null;
    }

    private static String normalizeErrorDetail(CollectorConfigReleaseObservedStatus status,
                                               String errorDetail) {
        String value = errorDetail == null ? "" : errorDetail;
        if (status != CollectorConfigReleaseObservedStatus.FAILED) {
            if (!value.isEmpty()) throw invalid("COLLECTOR_ERROR_FIELDS_INVALID");
            return null;
        }
        return sanitize(value);
    }

    /** 只保留低风险稳定字符，避免把调用方异常、token 或 payload 写回数据库/响应。 */
    private static String sanitize(String detail) {
        StringBuilder safe = new StringBuilder(Math.min(detail.length(), MAX_ERROR_DETAIL));
        for (int i = 0; i < detail.length() && safe.length() < MAX_ERROR_DETAIL; i++) {
            char value = detail.charAt(i);
            safe.append((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9') || value == ' ' || value == '.'
                    || value == '_' || value == ':' || value == '/' || value == '-'
                    ? value : '_');
        }
        return safe.toString();
    }

    private static CollectorConfigReleaseInternalException invalid(String code) {
        return new CollectorConfigReleaseInternalException(code);
    }
}
