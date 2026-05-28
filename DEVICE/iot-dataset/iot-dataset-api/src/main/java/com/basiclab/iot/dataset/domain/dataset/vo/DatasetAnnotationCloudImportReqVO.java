package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "从云平台数据集导入请求")
@Data
public class DatasetAnnotationCloudImportReqVO {

    @Schema(description = "源云平台数据集 ID")
    @NotNull(message = "请选择云平台数据集")
    private Long sourceDatasetId;
}
