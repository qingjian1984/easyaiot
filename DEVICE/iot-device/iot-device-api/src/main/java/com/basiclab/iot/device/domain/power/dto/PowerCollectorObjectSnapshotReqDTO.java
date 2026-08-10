package com.basiclab.iot.device.domain.power.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/** TD-004 §14.1 collector 发布前的电力对象快照批量请求。 */
@Data
@ApiModel("collector 电力对象快照请求")
public class PowerCollectorObjectSnapshotReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** tenant 只允许来自认证上下文，故本 DTO 不提供 tenantId 字段。 */
    @NotEmpty(message = "deviceIdentifications 不能为空")
    @Size(max = 500, message = "deviceIdentifications 单次最多 500 个")
    @ApiModelProperty(value = "设备标识集合；精确匹配且不做 trim/大小写转换", required = true)
    private List<String> deviceIdentifications;
}
