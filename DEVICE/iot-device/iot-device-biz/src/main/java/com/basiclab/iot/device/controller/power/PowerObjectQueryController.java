package com.basiclab.iot.device.controller.power;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.device.PowerObjectQueryApi;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotReqDTO;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotRespDTO;
import com.basiclab.iot.device.service.power.PowerObjectQueryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PowerObjectQueryApi 的 provider 薄适配；tenant 由请求安全上下文建立。 */
@Validated
@RestController
public class PowerObjectQueryController implements PowerObjectQueryApi {

    private final PowerObjectQueryService service;

    public PowerObjectQueryController(PowerObjectQueryService service) {
        this.service = service;
    }

    @Override
    public R<List<PowerCollectorObjectSnapshotRespDTO>> queryCollectorSnapshots(
            PowerCollectorObjectSnapshotReqDTO request) {
        return R.ok(service.queryCollectorSnapshots(request));
    }
}
