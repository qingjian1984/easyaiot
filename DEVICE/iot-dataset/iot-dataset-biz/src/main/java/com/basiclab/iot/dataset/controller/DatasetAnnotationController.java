package com.basiclab.iot.dataset.controller;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.dataset.domain.dataset.vo.*;
import com.basiclab.iot.dataset.service.DatasetAnnotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

import static com.basiclab.iot.common.domain.CommonResult.success;

/**
 * 图像数据集标注 — 导入/导出（对齐 auto-labeling V9.13.0）
 */
@Tag(name = "管理后台 - 数据集标注导入导出")
@RestController
@RequestMapping("/dataset")
@Validated
public class DatasetAnnotationController {

    @Resource
    private DatasetAnnotationService datasetAnnotationService;

    @PostMapping("/{datasetId}/annotation/export")
    @Operation(summary = "导出 YOLO 格式数据集 ZIP")
    public void exportAnnotationDataset(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody DatasetAnnotationExportReqVO reqVO,
            HttpServletResponse response) throws IOException {
        datasetAnnotationService.exportYoloZip(datasetId, reqVO, response);
    }

    @PostMapping("/{datasetId}/annotation/import-folder")
    @Operation(summary = "上传图片文件夹到数据集")
    public CommonResult<DatasetAnnotationImportResultVO> importImageFolder(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam("files") List<MultipartFile> files) {
        return success(datasetAnnotationService.importImageFolder(datasetId, files));
    }

    @PostMapping("/{datasetId}/annotation/import-labelme")
    @Operation(summary = "导入 LabelMe 数据集文件夹")
    public CommonResult<DatasetAnnotationImportResultVO> importLabelmeFolder(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam("files") List<MultipartFile> files) {
        return success(datasetAnnotationService.importLabelmeFolder(datasetId, files));
    }

    @PostMapping("/{datasetId}/annotation/import-path")
    @Operation(summary = "导入 ImageFolder（LabelMe / COCO / YOLO 回退）")
    public CommonResult<DatasetAnnotationImportResultVO> importImageFolderPath(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody DatasetAnnotationPathImportReqVO reqVO) {
        return success(datasetAnnotationService.importImageFolderPath(datasetId, reqVO.getPath()));
    }

    @PostMapping("/{datasetId}/annotation/import-yolo-path")
    @Operation(summary = "导入 YOLO 数据集（仅 .txt）")
    public CommonResult<DatasetAnnotationImportResultVO> importYoloPath(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody DatasetAnnotationPathImportReqVO reqVO) {
        return success(datasetAnnotationService.importYoloPath(datasetId, reqVO.getPath()));
    }

    @PostMapping("/{datasetId}/annotation/import-coco-path")
    @Operation(summary = "导入 COCO instances JSON")
    public CommonResult<DatasetAnnotationImportResultVO> importCocoPath(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody DatasetAnnotationCocoImportReqVO reqVO) {
        return success(datasetAnnotationService.importCocoPath(datasetId, reqVO));
    }

    @GetMapping("/annotation/cloud-datasets")
    @Operation(summary = "列出云平台图片数据集（供导入选择）")
    public CommonResult<List<DatasetRespVO>> listCloudDatasets() {
        return success(datasetAnnotationService.listCloudDatasets());
    }

    @PostMapping("/{datasetId}/annotation/cloud-import")
    @Operation(summary = "从云平台数据集导入")
    public CommonResult<DatasetAnnotationImportResultVO> cloudImport(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody DatasetAnnotationCloudImportReqVO reqVO) {
        return success(datasetAnnotationService.cloudImport(datasetId, reqVO));
    }

    @PostMapping("/{datasetId}/annotation/cloud-export")
    @Operation(summary = "导出到云平台（新建数据集）")
    public CommonResult<DatasetAnnotationImportResultVO> cloudExport(
            @PathVariable("datasetId") Long datasetId,
            @Valid @RequestBody DatasetAnnotationCloudExportReqVO reqVO) {
        return success(datasetAnnotationService.cloudExport(datasetId, reqVO));
    }

    @PostMapping("/{datasetId}/annotation/extract-frames")
    @Operation(summary = "视频抽帧并添加到数据集")
    public CommonResult<DatasetAnnotationImportResultVO> extractFrames(
            @PathVariable("datasetId") Long datasetId,
            @Parameter(description = "视频文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "抽帧间隔（帧）") @RequestParam(value = "frame_interval", defaultValue = "30") int frameInterval) {
        return success(datasetAnnotationService.extractFramesFromVideo(datasetId, file, frameInterval));
    }
}
