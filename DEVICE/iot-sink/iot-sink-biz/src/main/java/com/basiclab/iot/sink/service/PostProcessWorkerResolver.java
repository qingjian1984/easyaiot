package com.basiclab.iot.sink.service;

import java.util.List;

/**
 * 解析后处理 Worker HTTP 地址（集群节点绑定）
 */
public interface PostProcessWorkerResolver {

    /**
     * 按 taskId 轮询选择一个运行中的 Worker 基址，如 http://10.0.0.5:19680
     */
    String resolveWorkerBaseUrl(Integer taskId);

    List<String> listWorkerBaseUrls(Integer taskId);
}
