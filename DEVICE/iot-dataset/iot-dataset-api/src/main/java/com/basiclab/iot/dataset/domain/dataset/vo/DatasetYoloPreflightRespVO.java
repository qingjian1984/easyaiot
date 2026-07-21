package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "YOLO 本地数据集导入预检结果")
@Data
public class DatasetYoloPreflightRespVO {

    private String rootPath;
    private boolean importable;
    private int imageCount;
    private int matchedLabelCount;
    private int missingLabelCount;
    private int invalidLabelCount;
    private int duplicateImageNameCount;
    private List<SplitSummary> splits = new ArrayList<>();
    private List<ClassInfo> classes = new ArrayList<>();
    private List<String> existingTags = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class SplitSummary {
        private String split;
        private int imageCount;
        private int matchedLabelCount;
    }

    @Data
    public static class ClassInfo {
        private int classId;
        private String detectedName;
        private String suggestedName;
        private boolean manualNameRequired;
        private boolean existingTag;
    }
}
