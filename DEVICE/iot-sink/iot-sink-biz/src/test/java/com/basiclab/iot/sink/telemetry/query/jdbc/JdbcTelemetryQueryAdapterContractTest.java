package com.basiclab.iot.sink.telemetry.query.jdbc;

import com.basiclab.iot.sink.telemetry.query.AggregationType;
import com.basiclab.iot.sink.telemetry.query.Granularity;
import com.basiclab.iot.sink.telemetry.query.TelemetryAggregatePoint;
import com.basiclab.iot.sink.telemetry.query.TelemetryAggregateQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetryLatestQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetryLatestSample;
import com.basiclab.iot.sink.telemetry.query.TelemetryQueryPort;
import com.basiclab.iot.sink.telemetry.query.TelemetryRawPage;
import com.basiclab.iot.sink.telemetry.query.TelemetryRawQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetrySampleView;
import com.basiclab.iot.sink.telemetry.query.TelemetrySeries;
import com.basiclab.iot.sink.telemetry.store.jdbc.JdbcTelemetryStore;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-003 §16 查询端口 PG 合同测试（真实 iot-device20）：
 * 租户隔离、series 过滤、分页排序、date_bin 聚合正确性、latest 每序列一行。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcTelemetryQueryAdapterContractTest {

    private static final String PG_URL = System.getenv().getOrDefault("TD008_PG_URL",
            "jdbc:postgresql://localhost:5432/iot-device20");
    private static final String PG_USER = System.getenv().getOrDefault("TD008_PG_USERNAME", "postgres");
    private static final String PG_PASSWORD = System.getenv().getOrDefault("TD008_PG_PASSWORD",
            System.getenv().getOrDefault("TD005_PG_PASSWORD", ""));

    private static final long TENANT = 999_777_666L;
    private static final long OTHER_TENANT = 999_777_667L;

    private PooledDataSource dataSource;
    private TelemetryQueryPort query;
    private JdbcTelemetryStore store;

    private static final long T0 = 1_755_000_000_000L; // 固定基准毫秒

    @BeforeAll
    void setup() {
        dataSource = new PooledDataSource("org.postgresql.Driver", PG_URL, PG_USER, PG_PASSWORD);
        try (java.sql.Connection ignored = dataSource.getConnection()) {
            // Local PostgreSQL is optional for this contract suite.
        } catch (Exception e) {
            dataSource.forceCloseAll();
            dataSource = null;
            Assumptions.assumeTrue(false,
                    "NOT_RUN_LOCAL_ENV: PostgreSQL is unavailable at " + PG_URL);
        }
        query = new JdbcTelemetryQueryAdapter(dataSource);
        store = new JdbcTelemetryStore(dataSource);
        cleanup();
        seed();
    }

    @AfterAll
    void cleanup() {
        if (dataSource != null) {
            new org.springframework.jdbc.core.JdbcTemplate(dataSource).update(
                    "DELETE FROM iot_sink.telemetry_sample WHERE tenant_id IN (?, ?)",
                    TENANT, OTHER_TENANT);
            dataSource.forceCloseAll();
        }
    }

    /** dev-q: voltage 3 个点（1 分钟间隔内 2 个 + 下一分钟 1 个）；dev-q: current 1 个点。 */
    private void seed() {
        store.appendBatch(List.of(
                sample("q-msg-1", TENANT, "dev-q", "voltage", "220.5", T0),
                sample("q-msg-2", TENANT, "dev-q", "voltage", "230.0", T0 + 30_000),
                sample("q-msg-3", TENANT, "dev-q", "voltage", "240.0", T0 + 90_000),
                sample("q-msg-4", TENANT, "dev-q", "current", "5.5", T0),
                // 其他租户同设备同测点：隔离验证
                sample("q-msg-o1", OTHER_TENANT, "dev-q", "voltage", "999.9", T0)));
    }

    @Test
    void rawQueryFiltersBySeriesAndSortsDescending() {
        TelemetryRawPage page = query.queryRaw(new TelemetryRawQuery(
                String.valueOf(TENANT),
                List.of(new TelemetrySeries("dev-q", "voltage")),
                T0, T0 + 200_000, 1, 10));
        assertEquals(3, page.totalRows());
        assertEquals(3, page.rows().size());
        // 时间倒序
        assertEquals(T0 + 90_000, page.rows().get(0).collectedAtMs());
        assertValueEquals("240.0", page.rows().get(0).value());
        // quality 列落库前 GOOD 兜底
        assertEquals("GOOD", page.rows().get(0).quality());
    }

    @Test
    void rawQueryPaginates() {
        TelemetryRawQuery first = new TelemetryRawQuery(String.valueOf(TENANT),
                List.of(new TelemetrySeries("dev-q", "voltage")),
                T0, T0 + 200_000, 1, 2);
        TelemetryRawPage pageOne = query.queryRaw(first);
        assertEquals(3, pageOne.totalRows());
        assertEquals(2, pageOne.rows().size());

        TelemetryRawPage pageTwo = query.queryRaw(new TelemetryRawQuery(String.valueOf(TENANT),
                List.of(new TelemetrySeries("dev-q", "voltage")),
                T0, T0 + 200_000, 2, 2));
        assertEquals(1, pageTwo.rows().size());
        assertEquals(T0, pageTwo.rows().get(0).collectedAtMs());
    }

    @Test
    void tenantIsolationIsFailClosed() {
        TelemetryRawPage otherTenant = query.queryRaw(new TelemetryRawQuery(
                String.valueOf(OTHER_TENANT),
                List.of(new TelemetrySeries("dev-q", "voltage")),
                T0, T0 + 200_000, 1, 10));
        // 其他租户只看到自己那条
        assertEquals(1, otherTenant.totalRows());
        assertValueEquals("999.9", otherTenant.rows().get(0).value());

        // TENANT 视角看不到 OTHER_TENANT 的 999.9
        TelemetryRawPage own = query.queryRaw(new TelemetryRawQuery(
                String.valueOf(TENANT),
                List.of(new TelemetrySeries("dev-q", "voltage")),
                T0, T0 + 200_000, 1, 10));
        own.rows().forEach(row ->
                assertTrue(row.value().compareTo(new BigDecimal("900")) < 0,
                        "cross-tenant value leaked"));
    }

    @Test
    void minuteAggregationBucketsAndAggregates() {
        List<TelemetryAggregatePoint> points = query.aggregate(new TelemetryAggregateQuery(
                String.valueOf(TENANT),
                List.of(new TelemetrySeries("dev-q", "voltage")),
                T0, T0 + 200_000, Granularity.MINUTE, AggregationType.AVG));
        assertEquals(2, points.size());
        // 桶起点对齐 T0 所在分钟（T0 = 1755000000000, 分钟对齐）
        long minuteAligned = T0 - (T0 % 60_000);
        assertEquals(minuteAligned, points.get(0).bucketStartMs());
        // 第一分钟 220.5/230.0 → 225.25；第二分钟 240.0
        assertEquals(0, new BigDecimal("225.25").compareTo(points.get(0).value()));
        assertEquals(2, points.get(0).sampleCount());
        assertEquals(0, new BigDecimal("240.0").compareTo(points.get(1).value()));
        assertEquals(1, points.get(1).sampleCount());
    }

    @Test
    void aggregationFunctionsCoverPrdSet() {
        String tenant = String.valueOf(TENANT);
        List<TelemetrySeries> s = List.of(new TelemetrySeries("dev-q", "voltage"));
        assertEquals(0, new BigDecimal("220.5").compareTo(query.aggregate(
                new TelemetryAggregateQuery(tenant, s, T0, T0 + 200_000,
                        Granularity.MINUTE, AggregationType.MIN)).get(0).value()));
        assertEquals(0, new BigDecimal("240.0").compareTo(query.aggregate(
                new TelemetryAggregateQuery(tenant, s, T0, T0 + 200_000,
                        Granularity.MINUTE, AggregationType.MAX)).get(1).value()));
        // SUM 按桶：第一分钟 220.5+230.0=450.5；跨桶总和用 sampleCount 语义另证
        assertEquals(0, new BigDecimal("450.5").compareTo(query.aggregate(
                new TelemetryAggregateQuery(tenant, s, T0, T0 + 200_000,
                        Granularity.MINUTE, AggregationType.SUM)).get(0).value()));
        assertEquals(3, query.aggregate(
                new TelemetryAggregateQuery(tenant, s, T0, T0 + 200_000,
                        Granularity.MINUTE, AggregationType.COUNT)).stream()
                .mapToLong(TelemetryAggregatePoint::sampleCount).sum());
    }

    @Test
    void latestReturnsOneRowPerSeries() {
        List<TelemetryLatestSample> latest = query.latest(new TelemetryLatestQuery(
                String.valueOf(TENANT),
                List.of(new TelemetrySeries("dev-q", "voltage"),
                        new TelemetrySeries("dev-q", "current"))));
        assertEquals(2, latest.size());
        TelemetryLatestSample voltage = latest.stream()
                .filter(row -> row.propertyCode().equals("voltage")).findFirst().orElseThrow();
        assertValueEquals("240.0", voltage.value());
        assertEquals(T0 + 90_000, voltage.collectedAtMs());
        assertNotNull(voltage.quality());
        // 其他租户 latest 不可见 TENANT 数据
        List<TelemetryLatestSample> other = query.latest(new TelemetryLatestQuery(
                String.valueOf(OTHER_TENANT),
                List.of(new TelemetrySeries("dev-q", "voltage"))));
        assertValueEquals("999.9", other.get(0).value());
    }


    /** numeric(20,6) 列 scale=6，值断言统一 compareTo（忽略尾随零）。 */
    private static void assertValueEquals(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertTrue(0 == new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private static com.basiclab.iot.sink.telemetry.store.TelemetrySample sample(
            String messageId, long tenant, String device, String property, String value, long atMs) {
        String canonical = "{\"schemaVersion\":\"1.0\",\"messageId\":\"" + messageId + "\","
                + "\"requestId\":\"req-" + messageId + "\",\"tenantId\":\"" + tenant + "\","
                + "\"siteCode\":\"site-q\",\"deviceIdentification\":\"" + device + "\","
                + "\"propertyCode\":\"" + property + "\",\"value\":\"" + value + "\","
                + "\"collectedAt\":\"2026-08-13T00:00:00Z\",\"source\":\"contract-test\"}";
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        return new com.basiclab.iot.sink.telemetry.store.TelemetrySample(
                messageId, "req-" + messageId, String.valueOf(tenant), "site-q",
                device, property, bytes, hash, atMs, 1, "contract-test", 1);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest(bytes)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
