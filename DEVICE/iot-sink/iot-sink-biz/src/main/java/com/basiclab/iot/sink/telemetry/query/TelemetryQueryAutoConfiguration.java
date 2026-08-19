package com.basiclab.iot.sink.telemetry.query;

import com.basiclab.iot.sink.telemetry.query.jdbc.JdbcTelemetryQueryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * TD-003 §16 查询端口装配（standard PG）。full 档 TDengine 读适配器随
 * full 部署评审接入；controller 与端口同条件装配，开关默认关闭（M1 上线窗口启用）。
 */
@Configuration
@ConditionalOnProperty(name = "easyaiot.telemetry.query.enabled", havingValue = "true",
        matchIfMissing = false)
public class TelemetryQueryAutoConfiguration {

    @Bean
    public TelemetryQueryPort telemetryQueryPort(DataSource dataSource) {
        return new JdbcTelemetryQueryAdapter(dataSource);
    }

    @Bean
    public TelemetryQueryController telemetryQueryController(TelemetryQueryPort port) {
        return new TelemetryQueryController(port);
    }
}
