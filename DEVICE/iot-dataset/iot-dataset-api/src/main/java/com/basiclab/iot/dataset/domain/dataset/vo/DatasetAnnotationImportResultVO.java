package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Schema(description = "标注数据导入结果")
@Data
@Builder
public class DatasetAnnotationImportResultVO {

    @Schema(description = "导入/复制成功的图片数")
    private Integer imagesCopied;

    @Schema(description = "含 LabelMe 标注的图片数")
    private Integer labelmeImages;

    @Schema(description = "含 COCO 标注的图片数")
    private Integer cocoImages;

    @Schema(description = "含 YOLO .txt 标注的图片数")
    private Integer yoloImages;

    @Schema(description = "提示信息")
    private String hint;

    @Schema(description = "云平台新建数据集 ID（导出到云时使用）")
    private Long cloudDatasetId;

    @Schema(description = "更新图片数（云导出）")
    private Integer updatedImages;

    @Schema(description = "新建图片数（云导出）")
    private Integer createdImages;

    @Schema(description = "本次导入涉及的类别名称")
    private List<String> classes;

    @Schema(description = "本次新建的标签数量")
    private Integer tagsCreated;
}
