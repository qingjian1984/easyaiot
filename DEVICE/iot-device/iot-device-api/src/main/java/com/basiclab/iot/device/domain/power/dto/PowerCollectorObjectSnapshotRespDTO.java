package com.basiclab.iot.device.domain.power.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** TD-004 §14.1 服务端生成的不可变对象关系查询快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("collector 电力对象快照响应")
public class PowerCollectorObjectSnapshotRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("认证租户编号（十进制字符串）")
    private String tenantId;
    @ApiModelProperty("设备标识原值")
    private String deviceIdentification;
    @ApiModelProperty("站点规范编码")
    private String siteCode;
    @ApiModelProperty("主空间规范编码，可为空")
    private String spaceCode;
    @ApiModelProperty("主回路规范编码，可为空")
    private String circuitCode;
    @ApiModelProperty("设备资产版本（十进制字符串）")
    private String deviceAssetVersion;
    @ApiModelProperty("当前归属版本（十进制字符串）")
    private String assignmentVersion;
    @ApiModelProperty("服务端确定性对象修订，READY 时非空")
    private String objectRevision;
    @ApiModelProperty("READY / NOT_BOUND / INACTIVE")
    private String status;
    @ApiModelProperty("仅 status=READY 时为 true")
    private boolean active;
}
