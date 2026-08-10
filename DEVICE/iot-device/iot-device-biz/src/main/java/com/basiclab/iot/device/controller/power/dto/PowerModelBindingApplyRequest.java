package com.basiclab.iot.device.controller.power.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * TD-005 §11.4：产品物模型绑定应用请求。
 * tenant、操作者、修订号、配置版本和事件 ID 均由服务端生成或从安全上下文取得。
 */
public class PowerModelBindingApplyRequest {

    private String templateCode;
    private String templateVersion;
    private Long nodeId;
    private JsonNode bindingSnapshot;
    private JsonNode collectorSnapshot;

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(String templateVersion) {
        this.templateVersion = templateVersion;
    }

    public Long getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long nodeId) {
        this.nodeId = nodeId;
    }

    public JsonNode getBindingSnapshot() {
        return bindingSnapshot;
    }

    public void setBindingSnapshot(JsonNode bindingSnapshot) {
        this.bindingSnapshot = bindingSnapshot;
    }

    public JsonNode getCollectorSnapshot() {
        return collectorSnapshot;
    }

    public void setCollectorSnapshot(JsonNode collectorSnapshot) {
        this.collectorSnapshot = collectorSnapshot;
    }
}
