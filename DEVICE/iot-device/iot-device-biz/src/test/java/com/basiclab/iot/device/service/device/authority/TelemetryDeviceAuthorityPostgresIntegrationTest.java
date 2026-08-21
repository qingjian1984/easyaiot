package com.basiclab.iot.device.service.device.authority;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL authority fixture. It uses one rollback-only connection and
 * never creates or migrates schema; credentials come only from process env.
 */
class TelemetryDeviceAuthorityPostgresIntegrationTest {

    private SingleConnectionDataSource dataSource;
    private Connection connection;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("LC02_AUTHORITY_PG_ENABLED")),
                "Set LC02_AUTHORITY_PG_ENABLED=true to run PostgreSQL authority fixture");
        String password = System.getenv("LC02_AUTHORITY_PG_PASSWORD");
        assumeTrue(password != null && !password.isEmpty(),
                "Set LC02_AUTHORITY_PG_PASSWORD without committing credentials");
        String url = environmentOrDefault("LC02_AUTHORITY_PG_URL",
                "jdbc:postgresql://localhost:5432/iot-device20");
        String username = environmentOrDefault("LC02_AUTHORITY_PG_USER", "postgres");
        dataSource = new SingleConnectionDataSource(url, username, password, true);
        connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.rollback();
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void exactAuthorityQueryDistinguishesZeroOneAndTwoRowsAcrossTenants() {
        insertDevice(930_006_001L, 930_006_101L, "auth-prod-0", "auth-dev-0", 0);
        insertDevice(930_006_002L, 930_006_102L, "auth-prod-1", "auth-dev-1", 0);
        insertDevice(930_006_003L, 930_006_103L, "auth-prod-2", "auth-dev-2", 0);
        insertDevice(930_006_004L, 930_006_104L, "auth-prod-2", "auth-dev-2", 0);
        insertDevice(930_006_005L, 930_006_105L, "auth-prod-deleted", "auth-dev-deleted", 1);

        assertEquals(0, candidates("auth-prod-missing", "auth-dev-missing").size());
        assertEquals(List.of("930006102"), candidates("auth-prod-1", "auth-dev-1"));
        assertEquals(List.of("930006103", "930006104"),
                candidates("auth-prod-2", "auth-dev-2"));
        assertEquals(0, candidates("auth-prod-deleted", "auth-dev-deleted").size());
    }

    private List<String> candidates(String product, String device) {
        return jdbc.query("SELECT tenant_id FROM device"
                        + " WHERE product_identification = ?"
                        + " AND device_identification = ?"
                        + " AND deleted = 0 ORDER BY id ASC LIMIT 2",
                (resultSet, rowNum) -> Long.toString(resultSet.getLong(1)), product, device);
    }

    private void insertDevice(long id, long tenantId, String product, String device, int deleted) {
        jdbc.update("INSERT INTO public.device"
                        + " (id, device_identification, device_name, device_status,"
                        + " product_identification, device_sn, tenant_id, deleted)"
                        + " VALUES (?, ?, ?, 'ENABLE', ?, ?, ?, ?)",
                id, device, device, product, "sn-" + id, tenantId, deleted);
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
