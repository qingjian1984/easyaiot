package com.basiclab.iot.dataset.service;

import com.basiclab.iot.dataset.domain.dataset.vo.DatasetAnnotationCocoImportReqVO;
import com.basiclab.iot.dataset.domain.dataset.vo.DatasetAnnotationImportResultVO;
import com.basiclab.iot.dataset.domain.dataset.vo.DatasetImageImportTaskRespVO;
import com.basiclab.iot.dataset.domain.dataset.vo.DatasetYoloImportReqVO;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * 数据集图片异步导入任务
 */
public interface DatasetImageImportTaskService {

    /**
     * 提交 ZIP 文件异步导入（分片合并后调用）
     *
     * @param zipPath    合并后的 ZIP 路径
     * @param datasetId  数据集 ID
     * @param onFinished 导入结束后的清理回调（如删除临时目录）
     * @return 任务 ID
     */
    String submitZipImport(Path zipPath, Long datasetId, Runnable onFinished);

    /**
     * 查询导入任务状态
     */
    DatasetImageImportTaskRespVO getTask(String taskId);

    /**
     * 取消进行中的导入任务
     */
    void cancelTask(String taskId);

    String submitAnnotationImport(Supplier<DatasetAnnotationImportResultVO> importer);

    String submitYoloPathImport(Long datasetId, DatasetYoloImportReqVO reqVO);

    String submitImageFolderPathImport(Long datasetId, String path);

    String submitCocoPathImport(Long datasetId, DatasetAnnotationCocoImportReqVO reqVO);
}
