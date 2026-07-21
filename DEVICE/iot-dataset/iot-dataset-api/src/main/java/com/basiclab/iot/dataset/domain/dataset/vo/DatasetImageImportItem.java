package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批量导入单张图片条目
 */
@Schema(description = "数据集图片批量导入条目")
@Data
public class DatasetImageImportItem {

    @Schema(description = "图片文件名")
    private String filename;

    @Schema(description = "图片二进制数据（与 filePath 二选一）")
    private byte[] data;

    @Schema(description = "标注 JSON")
    private String annotationsJson;

    @Schema(description = "图片宽度")
    private Integer width;

    @Schema(description = "图片高度")
    private Integer height;

    @Schema(description = "是否标注完成 0/1")
    private Integer completed;

    @Schema(description = "是否训练集 0/1")
    private Integer isTrain;

    @Schema(description = "是否验证集 0/1")
    private Integer isValidation;

    @Schema(description = "是否测试集 0/1")
    private Integer isTest;
}
