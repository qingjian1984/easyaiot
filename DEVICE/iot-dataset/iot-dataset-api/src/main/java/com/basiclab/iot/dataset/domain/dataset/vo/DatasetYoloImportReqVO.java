package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "YOLO 本地路径导入请求")
@Data
public class DatasetYoloImportReqVO {

    public static final String MODE_ANNOTATIONS_ONLY = "annotations_only";
    public static final String MODE_OVERWRITE = "overwrite";

    @NotBlank(message = "请填写 YOLO 数据集目录的绝对路径")
    @Schema(description = "数据集根目录绝对路径")
    private String path;

    @NotEmpty(message = "请确认所有 YOLO 类别名称")
    @Schema(description = "YOLO 类别 ID 到数据集标签名称的映射")
    private Map<Integer, String> classMapping = new LinkedHashMap<>();

    @AssertTrue(message = "请检查并确认所有 YOLO 类别名称")
    @Schema(description = "是否已人工检查并确认类别名称")
    private boolean classMappingConfirmed;

    @NotBlank(message = "请选择 YOLO 导入模式")
    @Pattern(regexp = "annotations_only|overwrite", message = "不支持的 YOLO 导入模式")
    @Schema(description = "annotations_only：同名图片仅更新标注；overwrite：覆盖图片和标注")
    private String importMode = MODE_ANNOTATIONS_ONLY;

    @Schema(description = "存在缺少标注的图片时是否继续导入")
    private Boolean allowMissingLabels = false;

    @Schema(description = "是否保留 train / valid / test 划分")
    private Boolean preserveSplits = true;
}
