package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "本地路径导入请求")
@Data
public class DatasetAnnotationPathImportReqVO {

    @Schema(description = "数据集根目录绝对路径")
    @NotBlank(message = "请填写数据集目录的绝对路径")
    private String path;
}
