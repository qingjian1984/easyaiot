package com.basiclab.iot.sink.telemetry.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD §4.5 查询配额合同（纯单元，无数据库）：series ≤10、跨度 ≤31 天、
 * pageSize ≤1000、累计 ≤100,000、非法范围/租户拒绝。
 */
class QueryQuotaContractTest {

    private static final long DAY_MS = 24L * 3600 * 1000;
    private static final String TENANT = "999888777";

    private static List<TelemetrySeries> series(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new TelemetrySeries("dev-" + i, "prop-" + i))
                .toList();
    }

    @Test
    void seriesOverTenIsQuotaExceeded() {
        assertThrows(QueryQuotaExceededException.class,
                () -> new TelemetryRawQuery(TENANT, series(11), 0, DAY_MS, 1, 100));
        assertThrows(QueryQuotaExceededException.class,
                () -> new TelemetryAggregateQuery(TENANT, series(11), 0, DAY_MS,
                        Granularity.HOUR, AggregationType.AVG));
        assertThrows(QueryQuotaExceededException.class,
                () -> new TelemetryLatestQuery(TENANT, series(11)));
    }

    @Test
    void emptySeriesRejected() {
        assertThrows(QueryQuotaExceededException.class,
                () -> new TelemetryRawQuery(TENANT, List.of(), 0, DAY_MS, 1, 100));
        assertThrows(QueryQuotaExceededException.class,
                () -> new TelemetryLatestQuery(TENANT, List.of()));
    }

    @Test
    void rawRangeOver31DaysIsQuotaExceeded() {
        assertThrows(QueryQuotaExceededException.class,
                () -> new TelemetryRawQuery(TENANT, series(1), 0, 31 * DAY_MS + 1, 1, 100));
    }

    @Test
    void rawRangeExactly31DaysAccepted() {
        new TelemetryRawQuery(TENANT, series(1), 0, 31 * DAY_MS, 1, 100);
    }

    @Test
    void pageSizeBoundsEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryRawQuery(TENANT, series(1), 0, DAY_MS, 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryRawQuery(TENANT, series(1), 0, DAY_MS, 1, 1001));
        new TelemetryRawQuery(TENANT, series(1), 0, DAY_MS, 1, 1000);
    }

    @Test
    void cumulativeRowsOverHundredThousandRejected() {
        // 累计配额在适配器层由 offset+pageSize 判定：构造 pageNo=100/pageSize=1000 →
        // offset=99,000 + 1000 = 100,000 恰好允许；pageNo=101 → 100,000+ 越界。
        TelemetryRawQuery boundary = new TelemetryRawQuery(TENANT, series(1), 0, DAY_MS, 100, 1000);
        assertEquals(99_000L, boundary.offset());
        assertEquals(TelemetryQueryQuota.MAX_TOTAL_ROWS, boundary.offset() + boundary.pageSize());

        TelemetryRawQuery over = new TelemetryRawQuery(TENANT, series(1), 0, DAY_MS, 101, 1000);
        assertTrue(over.offset() + over.pageSize() > TelemetryQueryQuota.MAX_TOTAL_ROWS,
                "pageNo=101/pageSize=1000 must exceed cumulative quota");
    }

    @Test
    void aggregateRejectsRawGranularityAndInvalidRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAggregateQuery(TENANT, series(1), 0, DAY_MS,
                        Granularity.RAW, AggregationType.AVG));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAggregateQuery(TENANT, series(1), DAY_MS, 0,
                        Granularity.HOUR, AggregationType.AVG));
    }

    @Test
    void blankTenantRejectedFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryRawQuery(" ", series(1), 0, DAY_MS, 1, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryLatestQuery(null, series(1)));
    }

    @Test
    void quotaConstantsMatchPrd() {
        assertEquals(10, TelemetryQueryQuota.MAX_SERIES);
        assertEquals(31L * DAY_MS, TelemetryQueryQuota.MAX_RAW_RANGE_MS);
        assertEquals(1000, TelemetryQueryQuota.MAX_PAGE_SIZE);
        assertEquals(100_000L, TelemetryQueryQuota.MAX_TOTAL_ROWS);
        assertEquals(60L, Granularity.MINUTE.bucketSeconds());
        assertEquals(3600L, Granularity.HOUR.bucketSeconds());
        assertEquals(86400L, Granularity.DAY.bucketSeconds());
    }
}
