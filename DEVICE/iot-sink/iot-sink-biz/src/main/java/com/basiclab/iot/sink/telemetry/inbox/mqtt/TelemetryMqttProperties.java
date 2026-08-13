package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 中心 telemetry MQTT subscriber 配置（TD-003 §7）。
 *
 * <p>对称 {@link com.basiclab.iot.sink.outbox.dispatch.CollectorMqttProperties}。
 * subscriber 装配由 {@code @ConditionalOnProperty(easyaiot.telemetry.mqtt.enabled=true)} gate，
 * 本类承载连接参数 + EMQX 凭证（username/password，生产 EMQX 禁匿名时必需）。
 */
@ConfigurationProperties(prefix = "easyaiot.telemetry.mqtt")
public class TelemetryMqttProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 1883;
    private String clientId = "center-inbox";
    private String topicFilter = "/telemetry/#";
    private String username = "";
    private String password = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTopicFilter() { return topicFilter; }
    public void setTopicFilter(String topicFilter) { this.topicFilter = topicFilter; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
