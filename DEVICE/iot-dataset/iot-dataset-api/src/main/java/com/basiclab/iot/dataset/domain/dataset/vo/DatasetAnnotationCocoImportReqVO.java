package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "COCO instances JSON 导入请求")
@Data
public class DatasetAnnotationCocoImportReqVO {

    @Schema(description = "instances JSON 绝对路径")
    @NotBlank(message = "请填写 COCO instances JSON 的绝对路径")
    private String cocoJson;

    @Schema(description = "图片根目录（可选）")
    private String imagesRoot;
}
