package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "导出到云平台（新建数据集）请求")
@Data
public class DatasetAnnotationCloudExportReqVO {

    @Schema(description = "新数据集名称")
    @NotBlank(message = "请填写数据集名称")
    private String name;

    @Schema(description = "版本号")
    @NotBlank(message = "请填写版本")
    private String version;
}
