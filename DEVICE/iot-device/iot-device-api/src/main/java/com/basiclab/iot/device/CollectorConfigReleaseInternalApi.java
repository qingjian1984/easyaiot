package com.basiclab.iot.device;

import com.basiclab.iot.common.constant.ServiceNameConstants;
import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseDetailDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedRequestDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedResponseDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleasePendingDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

/** M1-LC-02A §4.2：iot-node 使用的 collector release 内部 Feign 合同。 */
@Validated
@FeignClient(contextId = "collectorConfigReleaseInternalApi", value = ServiceNameConstants.IOT_DEVICE)
public interface CollectorConfigReleaseInternalApi {

    String BASE_PATH = "/internal-api/device/collector-config/releases";
    String PENDING_PATH = BASE_PATH + "/pending";
    String DETAIL_PATH = BASE_PATH + "/{releaseId}";
    String OBSERVED_PATH = BASE_PATH + "/{releaseId}/observed";

    @GetMapping(PENDING_PATH)
    R<List<CollectorConfigReleasePendingDTO>> listPending(
            @RequestParam("limit") @Min(1) @Max(100) Integer limit);

    @GetMapping(DETAIL_PATH)
    R<CollectorConfigReleaseDetailDTO> getDetail(
            @PathVariable("releaseId") String releaseId);

    @PostMapping(OBSERVED_PATH)
    R<CollectorConfigReleaseObservedResponseDTO> reportObserved(
            @PathVariable("releaseId") String releaseId,
            @Valid @RequestBody CollectorConfigReleaseObservedRequestDTO request);
}
