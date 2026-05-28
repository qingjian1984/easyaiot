package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "标注数据集导出（YOLO ZIP）请求")
@Data
public class DatasetAnnotationExportReqVO {

    @Schema(description = "训练集比例", example = "0.7")
    private Double trainRatio = 0.7;

    @Schema(description = "验证集比例", example = "0.2")
    private Double valRatio = 0.2;

    @Schema(description = "测试集比例", example = "0.1")
    private Double testRatio = 0.1;

    @Schema(description = "样本选择：all / annotated / unannotated", example = "all")
    private String sampleSelection = "all";

    @Schema(description = "导出的类别名称列表")
    @NotEmpty(message = "请至少选择一个导出类别")
    private List<String> selectedClasses;

    @Schema(description = "导出文件前缀（可选）")
    private String exportPrefix;
}
