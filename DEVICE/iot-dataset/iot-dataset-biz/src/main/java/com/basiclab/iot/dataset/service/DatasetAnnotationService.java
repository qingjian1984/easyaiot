package com.basiclab.iot.dataset.service;

import com.basiclab.iot.dataset.domain.dataset.vo.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

/**
 * 图像数据集标注 — 导入/导出（对齐 auto-labeling V9.13.0）
 */
public interface DatasetAnnotationService {

    void exportYoloZip(Long datasetId, @Valid DatasetAnnotationExportReqVO reqVO, HttpServletResponse response) throws IOException;

    DatasetAnnotationImportResultVO importImageFolder(Long datasetId, List<MultipartFile> files);

    DatasetAnnotationImportResultVO importLabelmeFolder(Long datasetId, List<MultipartFile> files);

    DatasetAnnotationImportResultVO importImageFolderPath(Long datasetId, String path);

    DatasetAnnotationImportResultVO importYoloPath(Long datasetId, String path);

    DatasetAnnotationImportResultVO importCocoPath(Long datasetId, @Valid DatasetAnnotationCocoImportReqVO reqVO);

    List<DatasetRespVO> listCloudDatasets();

    DatasetAnnotationImportResultVO cloudImport(Long targetDatasetId, @Valid DatasetAnnotationCloudImportReqVO reqVO);

    DatasetAnnotationImportResultVO cloudExport(Long sourceDatasetId, @Valid DatasetAnnotationCloudExportReqVO reqVO);

    DatasetAnnotationImportResultVO extractFramesFromVideo(Long datasetId, MultipartFile file, int frameInterval);
}
