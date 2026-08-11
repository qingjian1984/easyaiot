package com.basiclab.iot.device.controller.power.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** TD-005 §11.1：创建或完整替换草稿内容；版本及身份字段均从 content 取得并交叉校验。 */
public class PowerModelTemplateDraftWriteRequest {
    private JsonNode content;

    public JsonNode getContent() { return content; }
    public void setContent(JsonNode content) { this.content = content; }
}
