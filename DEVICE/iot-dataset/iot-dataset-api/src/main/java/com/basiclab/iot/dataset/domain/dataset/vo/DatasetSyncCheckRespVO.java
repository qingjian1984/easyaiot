package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据集同步 Minio 前置条件检查结果
 */
@Schema(description = "数据集同步条件检查 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetSyncCheckRespVO {

    @Schema(description = "是否已划分训练/验证/测试用途")
    private Boolean usageAllocated;

    @Schema(description = "是否全部完成标注")
    private Boolean annotationCompleted;

    @Schema(description = "是否满足同步到 Minio 的全部条件")
    private Boolean syncReady;

    @Schema(description = "数据集中图片总数")
    private Integer totalImages;

    @Schema(description = "未划分用途的图片数量")
    private Integer unallocatedCount;

    @Schema(description = "未完成标注的图片数量")
    private Integer unannotatedCount;
}
