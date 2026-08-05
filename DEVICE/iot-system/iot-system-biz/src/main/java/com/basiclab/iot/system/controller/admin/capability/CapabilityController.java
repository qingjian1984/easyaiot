package com.basiclab.iot.system.controller.admin.capability;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.system.controller.admin.capability.vo.CapabilityRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;

/** ADR-011 read-only source for frontend capability and quota decisions. */
@Tag(name = "管理后台 - 平台能力")
@RestController
@RequestMapping("/system/capabilities")
public class CapabilityController {

    private final CapabilityService capabilityService;

    public CapabilityController(CapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    @GetMapping
    @PermitAll
    @Operation(summary = "获得当前部署生效的平台能力")
    public CommonResult<CapabilityRespVO> getCapabilities() {
        return CommonResult.success(CapabilityRespVO.from(capabilityService.snapshot()));
    }
}
