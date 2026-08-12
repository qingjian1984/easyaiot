package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;
import java.sql.SQLException;

/**
 * TD-002 collector Profile outbox 装配（仅 collector 启用，中心形态不装配）。
 */
@Configuration
@Profile("collector")
@ConditionalOnProperty(name = "easyaiot.outbox.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SqliteOutboxConfig.class)
public class SqliteOutboxAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    public SqliteTelemetryOutbox sqliteTelemetryOutbox(SqliteOutboxConfig config) throws SQLException {
        Path db = Path.of(config.getVolumePath()).resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        return new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), config.getQueueCapacity());
    }
}
