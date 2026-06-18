package com.basiclab.iot.sink.service;

import com.basiclab.iot.sink.domain.model.PostProcessRequestMessage;

import java.util.Map;

/**
 * 算法后处理：入队、分发 Worker、结果落库与告警
 */
public interface PostProcessService {

    void enqueue(PostProcessRequestMessage message);

    void dispatchAndPublishResult(PostProcessRequestMessage request);

    void persistResultAndDispatchAlerts(Map<String, Object> resultMessage);
}
