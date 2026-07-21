package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "数据集图片异步导入任务状态")
@Data
public class DatasetImageImportTaskRespVO {

    @Schema(description = "任务 ID")
    private String taskId;

    @Schema(description = "状态：processing / completed / failed / cancelled")
    private String status;

    @Schema(description = "已处理图片数（解压+上传进度）")
    private int processedCount;

    @Schema(description = "待处理图片总数；无法预先确定时为 0")
    private int totalCount;

    @Schema(description = "图片导入结果（completed 时有值）")
    private DatasetImageUploadRespVO result;

    @Schema(description = "标注路径导入结果（completed 时有值）")
    private DatasetAnnotationImportResultVO annotationResult;

    @Schema(description = "失败原因（failed 时有值）")
    private String errorMessage;
}
