package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotReqDTO;
import com.basiclab.iot.device.domain.power.dto.PowerCollectorObjectSnapshotRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TD-004 §14.1 / V006 的真实 PostgreSQL 合同。fixture 全部位于单连接事务，
 * tearDown 强制回滚；测试不创建或修改 Schema。
 */
class JdbcPowerObjectQueryPostgresIntegrationTest {

    private static final long TENANT_A = 910_006_201L;
    private static final long TENANT_B = 910_006_202L;

    private SingleConnectionDataSource dataSource;
    private Connection connection;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD005_PG_ENABLED")),
                "Set TD005_PG_ENABLED=true to run PostgreSQL contracts");
        String password = System.getenv("TD005_PG_PASSWORD");
        assumeTrue(password != null && !password.isEmpty(),
                "Set TD005_PG_PASSWORD without committing credentials");
        String url = environmentOrDefault("TD005_PG_URL",
                "jdbc:postgresql://localhost:5432/iot-device20");
        String username = environmentOrDefault("TD005_PG_USERNAME", "postgres");

        dataSource = new SingleConnectionDataSource(url, username, password, true);
        connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(dataSource);
        assertEquals("6fac9b429aae2fff34483fedc800f5d54bab8154b16953a85b2cf96f85229064",
                jdbc.queryForObject("SELECT btrim(script_sha256) FROM schema_migration_history"
                        + " WHERE migration_id='V006' AND status='SUCCEEDED'", String.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContextHolder.clear();
        if (connection != null) {
            connection.rollback();
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void returnsTenantScopedReadyNotBoundAndInactiveSnapshots() {
        insertDevice(910_006_211L, TENANT_A, "power-ready", "ENABLE");
        insertDevice(910_006_212L, TENANT_B, "power-ready", "ENABLE");
        insertDevice(910_006_213L, TENANT_A, "power-not-bound", "ENABLE");
        insertDevice(910_006_214L, TENANT_A, "power-inactive", "ENABLE");
        insertDevice(910_006_215L, TENANT_B, "tenant-b-only", "ENABLE");

        insertBoundGraph(TENANT_A, 910_006_211L, 910_006_221L, "site-a", "ACTIVE", 3L);
        insertBoundGraph(TENANT_B, 910_006_212L, 910_006_231L, "site-b", "ACTIVE", 8L);
        insertBoundGraph(TENANT_A, 910_006_214L, 910_006_241L, "site-inactive", "INACTIVE", 4L);

        TenantContextHolder.setTenantId(TENANT_A);
        PowerObjectQueryService service = service();
        List<PowerCollectorObjectSnapshotRespDTO> snapshots = service.queryCollectorSnapshots(
                request("power-ready", "power-not-bound", "power-inactive"));

        assertEquals(3, snapshots.size());
        assertEquals("site-a", snapshots.get(0).getSiteCode());
        assertEquals("READY", snapshots.get(0).getStatus());
        assertEquals("3", snapshots.get(0).getAssignmentVersion());
        assertTrue(snapshots.get(0).getObjectRevision().matches("sha256:[0-9a-f]{64}"));
        assertEquals("NOT_BOUND", snapshots.get(1).getStatus());
        assertEquals("INACTIVE", snapshots.get(2).getStatus());

        assertThrows(ServiceException.class,
                () -> service.queryCollectorSnapshots(request("tenant-b-only")));
    }

    @Test
    void mapperNeverReturnsAnotherTenantRowForSameIdentifier() {
        insertDevice(910_006_251L, TENANT_A, "same-id", "ENABLE");
        insertDevice(910_006_252L, TENANT_B, "same-id", "ENABLE");
        insertBoundGraph(TENANT_A, 910_006_251L, 910_006_261L, "site-tenant-a", "ACTIVE", 1L);
        insertBoundGraph(TENANT_B, 910_006_252L, 910_006_271L, "site-tenant-b", "ACTIVE", 1L);

        List<PowerObjectSnapshotRow> rows = new JdbcPowerObjectSnapshotMapper(dataSource)
                .selectByDeviceIdentifications(TENANT_A, List.of("same-id"));

        assertEquals(1, rows.size());
        assertEquals(TENANT_A, rows.get(0).tenantId());
        assertEquals("site-tenant-a", rows.get(0).siteCode());
    }

    private PowerObjectQueryService service() {
        CapabilityService capability = mock(CapabilityService.class);
        when(capability.isEnabled(PowerObjectQueryService.CAPABILITY_CODE)).thenReturn(true);
        return new PowerObjectQueryService(new JdbcPowerObjectSnapshotMapper(dataSource), capability);
    }

    private void insertDevice(long id, long tenantId, String identification, String status) {
        jdbc.update("INSERT INTO public.device"
                        + " (id, device_identification, device_name, device_status,"
                        + " product_identification, device_sn, tenant_id, deleted)"
                        + " VALUES (?, ?, ?, ?, 'power-contract', ?, ?, 0)",
                id, identification, identification, status, "sn-" + id, tenantId);
    }

    private void insertBoundGraph(long tenantId, long deviceId, long baseId,
                                  String siteCode, String siteStatus, long assignmentVersion) {
        long siteId = baseId;
        long spaceId = baseId + 1;
        long circuitId = baseId + 2;
        long assetId = baseId + 3;
        long assignmentId = baseId + 4;
        jdbc.update("INSERT INTO public.power_site"
                        + " (id, tenant_id, site_code, site_name, owner_dept_id, iana_time_zone,"
                        + " status, version, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, 1, 'Asia/Shanghai', ?, 2, 1, 1)",
                siteId, tenantId, siteCode, siteCode, siteStatus);
        jdbc.update("INSERT INTO public.power_space_node"
                        + " (id, tenant_id, site_id, space_code, space_type, space_name, status,"
                        + " version, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, 'distribution-room', 'room', 'ACTIVE', 3, 1, 1)",
                spaceId, tenantId, siteId, siteCode + "-room");
        jdbc.update("INSERT INTO public.power_circuit"
                        + " (id, tenant_id, site_id, circuit_code, circuit_name, circuit_type, status,"
                        + " version, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, 'line', 'feeder', 'ACTIVE', 4, 1, 1)",
                circuitId, tenantId, siteId, siteCode + "-line");
        jdbc.update("INSERT INTO public.power_device_asset"
                        + " (id, tenant_id, device_id, asset_code, object_type, status, version,"
                        + " created_by, updated_by) VALUES (?, ?, ?, ?, 'switchgear', 'ACTIVE', 5, 1, 1)",
                assetId, tenantId, deviceId, siteCode + "-asset");
        jdbc.update("INSERT INTO public.power_device_assignment"
                        + " (id, tenant_id, device_id, site_id, primary_space_id, primary_circuit_id,"
                        + " valid_from, change_reason, version, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'contract fixture', ?, 1, 1)",
                assignmentId, tenantId, deviceId, siteId, spaceId, circuitId, assignmentVersion);
    }

    private static PowerCollectorObjectSnapshotReqDTO request(String... identifications) {
        PowerCollectorObjectSnapshotReqDTO request = new PowerCollectorObjectSnapshotReqDTO();
        request.setDeviceIdentifications(Arrays.asList(identifications));
        return request;
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
