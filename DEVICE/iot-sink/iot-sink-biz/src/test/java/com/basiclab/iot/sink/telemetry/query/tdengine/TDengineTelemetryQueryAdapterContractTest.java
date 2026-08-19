package com.basiclab.iot.sink.telemetry.query.tdengine;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.query.AggregationType;
import com.basiclab.iot.sink.telemetry.query.Granularity;
import com.basiclab.iot.sink.telemetry.query.TelemetryAggregatePoint;
import com.basiclab.iot.sink.telemetry.query.TelemetryAggregateQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetryLatestQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetryLatestSample;
import com.basiclab.iot.sink.telemetry.query.TelemetryQueryPort;
import com.basiclab.iot.sink.telemetry.query.TelemetryRawPage;
import com.basiclab.iot.sink.telemetry.query.TelemetryRawQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetrySeries;
import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.WriteBatchResult;
import com.basiclab.iot.sink.telemetry.store.WriteItemResult;
import com.basiclab.iot.sink.telemetry.store.WriteStatus;
import com.basiclab.iot.sink.telemetry.store.tdengine.TDengineTelemetryStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-003 §16 full 档查询合同（真实 TDengine REST，默认 localhost:6041 root/taosdata）：
 * 写入→raw 分页/最新值/INTERVAL 聚合回读一致性；服务不可用按 NOT_RUN_LOCAL_ENV 跳过。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TDengineTelemetryQueryAdapterContractTest {

    private static final String HOST = System.getenv().getOrDefault("TDENGINE_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("TDENGINE_PORT", "6041"));
    private static final String USER = System.getenv().getOrDefault("TDENGINE_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("TDENGINE_PASSWORD", "taosdata");
    private static final String TENANT = "999888777";

    private TDengineTelemetryStore store;
    private TelemetryQueryPort query;
    private String device;
    private long baseTs;

    @BeforeAll
    void setup() {
        store = new TDengineTelemetryStore(HOST, PORT, USER, PASSWORD);
        try {
            store.appendBatch(List.of(sample("probe", "1.0", System.currentTimeMillis())));
        } catch (Throwable unavailable) {
            Assumptions.assumeTrue(false,
                    "NOT_RUN_LOCAL_ENV: TDengine is unavailable at " + HOST + ":" + PORT);
        }
        query = new TDengineTelemetryQueryAdapter(HOST, PORT, USER, PASSWORD);
        device = "dev-tq-" + Long.toUnsignedString(System.nanoTime() % 100000, 36);
        // 对齐分钟边界的基准，保证 INTERVAL 桶可预测
        baseTs = System.currentTimeMillis() / 60_000 * 60_000 - 120_000;
        // 种子：voltage 分钟桶 A 两点(220/230) + 桶 B 一点(240)；current 一点
        WriteBatchResult seeded = store.appendBatch(List.of(
                sample("q1", "220.0", baseTs),
                sample("q2", "230.0", baseTs + 30_000),
                sample("q3", "240.0", baseTs + 90_000),
                sample("q4", "7.5", baseTs)));
        assertTrue(seeded.items().stream().allMatch(item ->
                item.status() == WriteStatus.STORED || item.status() == WriteStatus.DUPLICATE),
                "seed must be stored");
    }

    @Test
    @Order(1)
    void rawReadsBackWrittenSamplesDescending() {
        TelemetryRawPage page = query.queryRaw(new TelemetryRawQuery(
                TENANT, List.of(new TelemetrySeries(device, "voltage")),
                baseTs - 1000, baseTs + 200_000, 1, 10));
        assertEquals(3, page.totalRows());
        assertEquals(3, page.rows().size());
        assertEquals(baseTs + 90_000, page.rows().get(0).collectedAtMs());
        assertEquals(0, new BigDecimal("240.0")
                .compareTo(BigDecimal.valueOf(page.rows().get(0).value().doubleValue())));
        assertEquals("GOOD", page.rows().get(0).quality());
    }

    @Test
    @Order(2)
    void minuteIntervalAggregatesBuckets() {
        List<TelemetryAggregatePoint> points = query.aggregate(new TelemetryAggregateQuery(
                TENANT, List.of(new TelemetrySeries(device, "voltage")),
                baseTs - 1000, baseTs + 200_000, Granularity.MINUTE, AggregationType.AVG));
        assertEquals(2, points.size(), "expected two minute buckets");
        assertEquals(0, new BigDecimal("225.0")
                .compareTo(BigDecimal.valueOf(points.get(0).value().doubleValue())));
        assertEquals(2, points.get(0).sampleCount());
        assertEquals(0, new BigDecimal("240.0")
                .compareTo(BigDecimal.valueOf(points.get(1).value().doubleValue())));
    }

    @Test
    @Order(3)
    void latestPerSeriesReturnsNewest() {
        List<TelemetryLatestSample> latest = query.latest(new TelemetryLatestQuery(
                TENANT, List.of(new TelemetrySeries(device, "voltage"),
                new TelemetrySeries(device, "current"))));
        assertEquals(2, latest.size());
        TelemetryLatestSample voltage = latest.stream()
                .filter(row -> "voltage".equals(row.propertyCode())).findFirst().orElseThrow();
        assertEquals(baseTs + 90_000, voltage.collectedAtMs());
    }

    private TelemetrySample sample(String suffix, String value, long ts) {
        String messageId = device + "-" + suffix;
        String canonical = "{\"schemaVersion\":\"1.0\",\"messageId\":\"" + messageId + "\","
                + "\"requestId\":\"req-" + messageId + "\",\"tenantId\":\"" + TENANT + "\","
                + "\"siteCode\":\"site-tq\",\"deviceIdentification\":\"" + device + "\","
                + (suffix.equals("q4")
                    ? "\"propertyCode\":\"current\","
                    : "\"propertyCode\":\"voltage\",")
                + "\"value\":\"" + value + "\",\"collectedAt\":\"2026-08-13T00:00:00Z\","
                + "\"source\":\"contract-test\"}";
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        return new TelemetrySample(messageId, "req-" + messageId, TENANT, "site-tq",
                device, suffix.equals("q4") ? "current" : "voltage",
                bytes, sha256(bytes), ts, 1, "contract-test", 1);
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
