package com.basiclab.iot.device;

import com.basiclab.iot.common.constant.ServiceNameConstants;
import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotReqDTO;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import java.util.List;

/**
 * TD-004 §14.1 内部查询契约。Feign tenant interceptor 只透传认证上下文；
 * 请求 DTO 故意不包含 tenantId，防止业务参数扩大租户范围。
 */
@Validated
@FeignClient(contextId = "powerObjectQueryApi", value = ServiceNameConstants.IOT_DEVICE)
public interface PowerObjectQueryApi {

    @PostMapping("/internal-api/device/power-object-snapshots/collector")
    R<List<PowerCollectorObjectSnapshotRespDTO>> queryCollectorSnapshots(
            @Valid @RequestBody PowerCollectorObjectSnapshotReqDTO request);
}
