package com.basiclab.iot.node.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Schema(description = "调度 - 分配节点 Request VO")
@Data
public class NodeSchedulerAllocateReqVO {

    @Schema(description = "工作负载类型: ai_service | algorithm_task | stream_forward | srs_live | srs_ai | zlm")
    @NotBlank(message = "工作负载类型不能为空")
    private String workloadType;

    @Schema(description = "工作负载 ID")
    @NotBlank(message = "工作负载 ID 不能为空")
    private String workloadId;

    @Schema(description = "调度需求")
    private Requirements requirements;

    @Schema(description = "是否 Sticky 绑定")
    private Boolean sticky;

    @Data
    public static class Requirements {
        private List<String> capabilities;
        private Integer gpuCount;
        private Integer minGpuMemMb;
        private String region;
        private List<Long> excludeNodeIds;
    }

}
