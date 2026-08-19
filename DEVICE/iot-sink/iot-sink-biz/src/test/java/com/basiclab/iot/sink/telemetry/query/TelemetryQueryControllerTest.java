package com.basiclab.iot.sink.telemetry.query;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.sink.telemetry.query.TelemetryQueryController.RawRequest;
import com.basiclab.iot.sink.telemetry.query.TelemetryQueryController.SeriesItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller 合同（无 MVC 容器，直测映射逻辑）：
 * 租户注入 fail-closed、series 映射、配额异常透传、CSV 转义。
 */
class TelemetryQueryControllerTest {

    private final RecordingPort port = new RecordingPort();
    private TelemetryQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new TelemetryQueryController(port);
        TenantContextHolder.setTenantId(42L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantComesFromLoginContextNotRequest() {
        controller.latest(new TelemetryQueryController.LatestRequest(
                List.of(new SeriesItem("dev", "prop"))));
        assertEquals("42", port.lastLatest.tenantId());
    }

    @Test
    void missingTenantContextFailsClosed() {
        TenantContextHolder.setTenantId(null);
        assertThrows(IllegalArgumentException.class,
                () -> controller.latest(new TelemetryQueryController.LatestRequest(
                        List.of(new SeriesItem("dev", "prop")))));
    }

    @Test
    void rawMapsSeriesAndDefaultsPaging() {
        controller.raw(new RawRequest(
                List.of(new SeriesItem("dev-1", "v"), new SeriesItem("dev-2", "i")),
                0L, 86_400_000L, null, null));
        assertNotNull(port.lastRaw);
        assertEquals(2, port.lastRaw.series().size());
        assertEquals(1, port.lastRaw.pageNo());
        assertEquals(100, port.lastRaw.pageSize());
    }

    @Test
    void quotaExceededPropagatesStableCode() {
        assertThrows(QueryQuotaExceededException.class,
                () -> controller.raw(new RawRequest(
                        List.of(new SeriesItem("d", "p")),
                        0L, 31L * 24 * 3600 * 1000 + 1, 1, 100)));
        assertThrows(QueryQuotaExceededException.class,
                () -> controller.raw(new RawRequest(
                        seriesOf(11), 0L, 1000L, 1, 100)));
    }

    @Test
    void aggregateMapsEnumsAndRejectsRaw() {
        controller.aggregate(new TelemetryQueryController.AggregateRequest(
                List.of(new SeriesItem("d", "p")), 0L, 1000L, "HOUR", "MAX"));
        assertEquals(Granularity.HOUR, port.lastAggregate.granularity());
        assertEquals(AggregationType.MAX, port.lastAggregate.aggregation());

        assertThrows(IllegalArgumentException.class,
                () -> controller.aggregate(new TelemetryQueryController.AggregateRequest(
                        List.of(new SeriesItem("d", "p")), 0L, 1000L, "RAW", "MAX")));
    }

    @Test
    void invalidEnumsAndBlankSeriesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.aggregate(new TelemetryQueryController.AggregateRequest(
                        List.of(new SeriesItem("d", "p")), 0L, 1000L, "WEEK", "MAX")));
        assertThrows(IllegalArgumentException.class,
                () -> controller.raw(new RawRequest(null, 0L, 1000L, 1, 10)));
    }

    @Test
    void csvEscapesSeparatorsAndQuotes() throws Exception {
        byte[] csv = controller.export(new TelemetryQueryController.ExportRequest(
                List.of(new SeriesItem("dev,x", "pro\"p")),
                port.fixedFrom, port.fixedTo)).getBody();
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("\"dev,x\""), "comma field must be quoted: " + text);
        assertTrue(text.contains("\"pro\"\"p\""), "inner quote must double: " + text);
        assertTrue(text.startsWith("device,property,value"));
        assertTrue(text.contains("220.5"), "fixed value must appear");
    }

    private static List<SeriesItem> seriesOf(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new SeriesItem("d" + i, "p" + i))
                .toList();
    }

    /** 记录型假端口：raw 返回固定一行供导出验证。 */
    private static final class RecordingPort implements TelemetryQueryPort {
        TelemetryRawQuery lastRaw;
        TelemetryAggregateQuery lastAggregate;
        TelemetryLatestQuery lastLatest;
        final long fixedFrom = 0L;
        final long fixedTo = 31L * 24 * 3600 * 1000;

        @Override
        public TelemetryRawPage queryRaw(TelemetryRawQuery query) {
            this.lastRaw = query;
            TelemetrySampleView row = new TelemetrySampleView(
                    query.series().get(0).deviceIdentification(),
                    query.series().get(0).propertyCode(),
                    new BigDecimal("220.5"), fixedFrom, fixedFrom, "GOOD", "m-1");
            return new TelemetryRawPage(1, query.pageNo(), query.pageSize(), List.of(row));
        }

        @Override
        public List<TelemetryAggregatePoint> aggregate(TelemetryAggregateQuery query) {
            this.lastAggregate = query;
            return List.of();
        }

        @Override
        public List<TelemetryLatestSample> latest(TelemetryLatestQuery query) {
            this.lastLatest = query;
            return List.of();
        }
    }
}
