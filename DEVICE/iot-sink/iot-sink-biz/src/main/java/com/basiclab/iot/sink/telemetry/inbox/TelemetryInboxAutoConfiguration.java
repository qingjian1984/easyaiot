package com.basiclab.iot.sink.telemetry.inbox;

import com.basiclab.iot.sink.telemetry.inbox.jdbc.JdbcTelemetryInbox;
import com.basiclab.iot.sink.telemetry.inbox.jdbc.TelemetryProjectionOrchestrator;
import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.jdbc.JdbcTelemetryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * TD-003 中心 Inbox + TelemetryStore + 投影编排 装配。
 * 仅 standard/full Profile（非 collector）。
 */
@Configuration
@ConditionalOnProperty(name = "easyaiot.telemetry.inbox.enabled", havingValue = "true", matchIfMissing = false)
public class TelemetryInboxAutoConfiguration {

    @Bean
    public TelemetryInboxPort telemetryInboxPort(DataSource dataSource) {
        return new JdbcTelemetryInbox(dataSource);
    }

    @Bean
    public TelemetryStorePort telemetryStorePort(DataSource dataSource) {
        return new JdbcTelemetryStore(dataSource);
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "easyaiot.telemetry.projection.enabled", havingValue = "true", matchIfMissing = true)
    public TelemetryProjectionOrchestrator projectionOrchestrator(DataSource dataSource,
                                                                   TelemetryStorePort store) {
        TelemetryProjectionOrchestrator orchestrator = new TelemetryProjectionOrchestrator(dataSource, store);
        orchestrator.start();
        return orchestrator;
    }
}
