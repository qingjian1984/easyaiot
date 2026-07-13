package com.basiclab.iot.sink.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 算法后处理请求消息（VIDEO 经 HTTP 入队后写入 Kafka request 主题）
 */
@Data
public class PostProcessRequestMessage {

    @JsonAlias("task_id")
    private Integer taskId;

    @JsonAlias("task_name")
    private String taskName;

    @JsonAlias("task_code")
    private String taskCode;

    @JsonAlias("task_type")
    private String taskType;

    @JsonAlias("device_id")
    private String deviceId;

    @JsonAlias("device_name")
    private String deviceName;

    @JsonAlias("frame_number")
    private Integer frameNumber;

    private String timestamp;

    @JsonAlias("timestamp_epoch")
    private Double timestampEpoch;

    private List<Map<String, Object>> detections;

    @JsonAlias("tracked_detections")
    private List<Map<String, Object>> trackedDetections;

    @JsonAlias("tracking_enabled")
    private Boolean trackingEnabled;

    private List<Map<String, Object>> regions;

    @JsonAlias("model_ids")
    private List<Integer> modelIds;

    @JsonAlias("alert_image_path")
    private String alertImagePath;

    @JsonAlias("correlation_id")
    private String correlationId;

    @JsonAlias("alert_class_names")
    private List<String> alertClassNames;

    @JsonAlias("pose_analysis_enabled")
    private Boolean poseAnalysisEnabled;

    @JsonAlias("pose_intent_enabled")
    private Boolean poseIntentEnabled;
}
