package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryUpstreamTopicParser;
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
    private String topicFilter = TelemetryUpstreamTopicParser.sharedSubscriptionFilter();
    private String username = "";
    private String password = "";
    /** ADR-018 key id; the key material is resolved outside the repository. */
    private String authorityKeyId = "";

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
    public String getAuthorityKeyId() { return authorityKeyId; }
    public void setAuthorityKeyId(String authorityKeyId) { this.authorityKeyId = authorityKeyId; }

    /** Fail closed before a real center subscription can be created. */
    public void validateForEnabledSubscriber() {
        if (!TelemetryUpstreamTopicParser.sharedSubscriptionFilter().equals(topicFilter)) {
            throw new IllegalStateException("TELEMETRY_MQTT_TOPIC_FILTER_INVALID");
        }
        if (!validIdentity(clientId)) {
            throw new IllegalStateException("TELEMETRY_MQTT_CLIENT_ID_INVALID");
        }
        if (!validIdentity(username) || password == null || password.isBlank()) {
            throw new IllegalStateException("TELEMETRY_MQTT_CREDENTIALS_MISSING");
        }
        if (authorityKeyId == null || authorityKeyId.isBlank()) {
            throw new IllegalStateException("SERVICE_AUTH_KEY_UNKNOWN");
        }
    }

    private static boolean validIdentity(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '/' || character == '+' || character == '#'
                    || character == '\u0000' || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }
}
