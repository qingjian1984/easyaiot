package com.basiclab.iot.sink.outbox.dispatch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * collector MQTT publisher + ACK subscriber 配置。
 * 候选值待 TD-001 压测冻结。
 */
@ConfigurationProperties(prefix = "easyaiot.collector.mqtt")
public class CollectorMqttProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 1883;
    private String clientId = "collector-telemetry";
    private String username = "";
    private String password = "";
    private int qos = 1;
    private boolean cleanSession = false;
    private int keepAliveSeconds = 60;
    private int maxInflight = 32;
    private String ackTopicPrefix = "/telemetry/ack/";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }
    public boolean isCleanSession() { return cleanSession; }
    public void setCleanSession(boolean cleanSession) { this.cleanSession = cleanSession; }
    public int getKeepAliveSeconds() { return keepAliveSeconds; }
    public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
    public int getMaxInflight() { return maxInflight; }
    public void setMaxInflight(int maxInflight) { this.maxInflight = maxInflight; }
    public String getAckTopicPrefix() { return ackTopicPrefix; }
    public void setAckTopicPrefix(String ackTopicPrefix) { this.ackTopicPrefix = ackTopicPrefix; }
}
