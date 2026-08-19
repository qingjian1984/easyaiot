package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.device.CollectorConfigReleaseInternalApi;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseDetailDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedRequestDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedResponseDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedStatus;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleasePendingDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 将 iot-device 的 typed Feign 合同适配到 iot-node 的最小 port。 */
public final class CollectorConfigReleaseClientAdapter implements CollectorConfigReleaseClientPort {

    private final CollectorConfigReleaseInternalApi internalApi;

    public CollectorConfigReleaseClientAdapter(CollectorConfigReleaseInternalApi internalApi) {
        this.internalApi = Objects.requireNonNull(internalApi, "iot-device release API is required");
    }

    @Override
    public List<CollectorConfigReleasePending> listPending(int limit) {
        R<List<CollectorConfigReleasePendingDTO>> response = internalApi.listPending(limit);
        List<CollectorConfigReleasePendingDTO> data = requireData(response);
        if (data == null) {
            return List.of();
        }
        List<CollectorConfigReleasePending> result = new ArrayList<>(data.size());
        for (CollectorConfigReleasePendingDTO item : data) {
            if (item == null) {
                throw unavailable();
            }
            result.add(new CollectorConfigReleasePending(
                    item.getReleaseId(),
                    item.getTenantId(),
                    item.getNodeId(),
                    item.getWorkloadId(),
                    item.getConfigVersion(),
                    item.getSchemaVersion(),
                    item.getCanonicalizationVersion(),
                    item.getPayloadSha256(),
                    item.getCanonicalLengthBytes(),
                    item.getPublishedAt()));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Optional<CollectorConfigReleaseDetail> getDetail(String releaseId) {
        R<CollectorConfigReleaseDetailDTO> response = internalApi.getDetail(releaseId);
        CollectorConfigReleaseDetailDTO data = requireData(response);
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(new CollectorConfigReleaseDetail(
                data.getReleaseId(),
                data.getTenantId(),
                data.getNodeId(),
                data.getWorkloadId(),
                data.getConfigVersion(),
                data.getSchemaVersion(),
                data.getCanonicalizationVersion(),
                data.getPayloadCanonical(),
                data.getPayloadSha256(),
                data.getCanonicalLengthBytes(),
                data.getPublishedAt()));
    }

    @Override
    public CollectorConfigObservedResponse reportObserved(CollectorConfigReleaseObservedReport report) {
        Objects.requireNonNull(report, "observed report is required");
        CollectorConfigReleaseObservedRequestDTO request = new CollectorConfigReleaseObservedRequestDTO();
        request.setReleaseId(report.getReleaseId());
        request.setTenantId(report.getTenantId());
        request.setNodeId(report.getNodeId());
        request.setWorkloadId(report.getWorkloadId());
        request.setConfigVersion(report.getConfigVersion());
        request.setPayloadSha256(report.getPayloadSha256());
        request.setStatus(CollectorConfigReleaseObservedStatus.valueOf(report.getStatus().name()));
        request.setObservedAt(report.getObservedAt());
        request.setErrorCode(report.getErrorCode());
        request.setErrorDetailSanitized(null);

        R<CollectorConfigReleaseObservedResponseDTO> response =
                internalApi.reportObserved(report.getReleaseId(), request);
        CollectorConfigReleaseObservedResponseDTO data = requireData(response);
        if (data == null) {
            throw unavailable();
        }
        CollectorConfigReleaseObservedReport.Status status = data.getStatus() == null
                ? null
                : CollectorConfigReleaseObservedReport.Status.valueOf(data.getStatus().name());
        return new CollectorConfigObservedResponse(
                data.getReleaseId(), status, data.isAccepted(), data.isTerminal(), data.isIdempotent());
    }

    private static <T> T requireData(R<T> response) {
        if (response == null || !response.isSuccess()) {
            throw unavailable();
        }
        return response.getData();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("DEVICE_RELEASE_API_UNAVAILABLE");
    }
}
