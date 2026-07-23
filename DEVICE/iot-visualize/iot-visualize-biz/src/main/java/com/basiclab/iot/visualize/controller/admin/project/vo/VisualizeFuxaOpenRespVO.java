package com.basiclab.iot.visualize.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - FUXA 免登打开地址 Response VO")
@Data
public class VisualizeFuxaOpenRespVO {

    @Schema(description = "浏览器打开的 URL（含 SSO 桥接或直跳路径）")
    private String url;

    @Schema(description = "是否走了 SSO 代登录")
    private Boolean sso;

    @Schema(description = "打开模式：edit / preview")
    private String mode;

    @Schema(description = "是否只读打开（演示保护或 forcePreview 导致 edit 被降级为 preview）")
    private Boolean readOnly;

}
