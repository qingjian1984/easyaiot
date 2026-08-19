package com.basiclab.iot.sink.telemetry.query.jdbc;

import com.basiclab.iot.sink.telemetry.query.AggregationType;
import com.basiclab.iot.sink.telemetry.query.Granularity;
import com.basiclab.iot.sink.telemetry.query.TelemetryAggregatePoint;
import com.basiclab.iot.sink.telemetry.query.TelemetryAggregateQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetryLatestQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetryLatestSample;
import com.basiclab.iot.sink.telemetry.query.TelemetryQueryPort;
import com.basiclab.iot.sink.telemetry.query.TelemetryQueryQuota;
import com.basiclab.iot.sink.telemetry.query.TelemetryRawPage;
import com.basiclab.iot.sink.telemetry.query.TelemetryRawQuery;
import com.basiclab.iot.sink.telemetry.query.TelemetrySampleView;
import com.basiclab.iot.sink.telemetry.query.TelemetrySeries;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * TD-003 §16 查询端口 standard adapter（PostgreSQL）。
 *
 * <p>走 idx_sample_query(tenant_id, device_identification, property_code,
 * collected_at_ms DESC)；聚合用 date_bin 桶 + min/max/avg/sum/count。
 * quality/received_at_ms 列由 V010 迁移补齐，落库前以 GOOD/collected_at 兜底
 * （SQL 按列存在性在 V010 后切换，见 {@link QualityColumns}）。</p>
 */
public final class JdbcTelemetryQueryAdapter implements TelemetryQueryPort {

    /** V010 前后兼容：构造时探测列存在性，避免落库窗口前的硬失败。 */
    private final boolean qualityColumnsPresent;

    private final JdbcTemplate jdbc;

    public JdbcTelemetryQueryAdapter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.qualityColumnsPresent = probeQualityColumns();
    }

    private boolean probeQualityColumns() {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.columns"
                            + " WHERE table_schema='iot_sink' AND table_name='telemetry_sample'"
                            + " AND column_name='quality')", Boolean.class));
        } catch (Exception error) {
            return false;
        }
    }

    @Override
    public TelemetryRawPage queryRaw(TelemetryRawQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        enforceCumulativeQuota(query);

        String qualityExpr = qualityColumnsPresent ? "quality" : "'GOOD' AS quality";
        String receivedExpr = qualityColumnsPresent ? "received_at_ms" : "collected_at_ms AS received_at_ms";

        StringBuilder where = new StringBuilder(
                "tenant_id = ? AND collected_at_ms >= ? AND collected_at_ms <= ? AND (");
        List<Object> args = new ArrayList<>();
        args.add(Long.parseLong(query.tenantId()));
        args.add(query.fromMs());
        args.add(query.toMs());
        appendSeriesFilter(where, args, query.series());
        where.append(')');

        String countSql = "SELECT count(*) FROM iot_sink.telemetry_sample WHERE " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        long totalRows = total == null ? 0L : total;

        String pageSql = "SELECT device_identification, property_code, value_numeric,"
                + " collected_at_ms, message_id, " + qualityExpr + ", " + receivedExpr
                + " FROM iot_sink.telemetry_sample WHERE " + where
                + " ORDER BY collected_at_ms DESC, property_code LIMIT ? OFFSET ?";
        args.add(query.pageSize());
        args.add(query.offset());
        List<TelemetrySampleView> rows = jdbc.query(pageSql, (rs, i) -> mapSample(rs), args.toArray());
        return new TelemetryRawPage(totalRows, query.pageNo(), query.pageSize(), rows);
    }

    @Override
    public List<TelemetryAggregatePoint> aggregate(TelemetryAggregateQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        long bucketMs = query.granularity().bucketSeconds() * 1000L;
        String sqlAgg = switch (query.aggregation()) {
            case MIN -> "min(value_numeric)";
            case MAX -> "max(value_numeric)";
            case AVG -> "avg(value_numeric)";
            case SUM -> "sum(value_numeric)";
            case COUNT -> "count(value_numeric)";
        };

        StringBuilder where = new StringBuilder(
                "tenant_id = ? AND collected_at_ms >= ? AND collected_at_ms <= ? AND (");
        List<Object> args = new ArrayList<>();
        args.add(Long.parseLong(query.tenantId()));
        args.add(query.fromMs());
        args.add(query.toMs());
        appendSeriesFilter(where, args, query.series());
        where.append(')');

        // date_bin 对齐 epoch（UTC）；输入 ms 转 timestamp 再按桶宽对齐回 ms。
        String bucketExpr = "(extract(epoch from date_bin('"
                + pgInterval(query.granularity())
                + "', to_timestamp(collected_at_ms / 1000.0), timestamp '1970-01-01')) * 1000)::bigint";
        String qualityExpr = qualityColumnsPresent ? "min(quality)" : "'GOOD'";

        String sql = "SELECT device_identification, property_code, " + bucketExpr + " AS bucket_ms,"
                + " " + sqlAgg + " AS agg_value, count(*) AS sample_count, " + qualityExpr + " AS quality"
                + " FROM iot_sink.telemetry_sample WHERE " + where
                + " GROUP BY device_identification, property_code, bucket_ms"
                + " ORDER BY bucket_ms ASC, device_identification, property_code";
        return jdbc.query(sql, (rs, i) -> new TelemetryAggregatePoint(
                rs.getString("device_identification"),
                rs.getString("property_code"),
                rs.getLong("bucket_ms"),
                rs.getBigDecimal("agg_value"),
                rs.getLong("sample_count"),
                rs.getString("quality")), args.toArray());
    }

    @Override
    public List<TelemetryLatestSample> latest(TelemetryLatestQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        // DISTINCT ON 取每序列按 collected_at 最新行，走 idx_sample_query 前缀。
        String qualityExpr = qualityColumnsPresent ? "quality" : "'GOOD' AS quality";
        String receivedExpr = qualityColumnsPresent ? "received_at_ms" : "collected_at_ms AS received_at_ms";

        List<TelemetryLatestSample> result = new ArrayList<>(query.series().size());
        for (TelemetrySeries series : query.series()) {
            String sql = "SELECT DISTINCT ON (device_identification, property_code)"
                    + " device_identification, property_code, value_numeric, collected_at_ms,"
                    + " " + qualityExpr + ", " + receivedExpr
                    + " FROM iot_sink.telemetry_sample"
                    + " WHERE tenant_id = ? AND device_identification = ? AND property_code = ?"
                    + " ORDER BY device_identification, property_code, collected_at_ms DESC";
            List<TelemetryLatestSample> rows = jdbc.query(sql, (rs, i) -> new TelemetryLatestSample(
                    rs.getString("device_identification"),
                    rs.getString("property_code"),
                    rs.getBigDecimal("value_numeric"),
                    rs.getLong("collected_at_ms"),
                    rs.getLong("received_at_ms"),
                    rs.getString("quality")),
                    Long.parseLong(query.tenantId()),
                    series.deviceIdentification(), series.propertyCode());
            result.addAll(rows);
        }
        return result;
    }

    /** 累计行数配额：offset+pageSize 不得越过 100,000 行上限。 */
    private static void enforceCumulativeQuota(TelemetryRawQuery query) {
        if (query.offset() + query.pageSize() > TelemetryQueryQuota.MAX_TOTAL_ROWS) {
            throw new com.basiclab.iot.sink.telemetry.query.QueryQuotaExceededException(
                    "cumulative rows exceed " + TelemetryQueryQuota.MAX_TOTAL_ROWS);
        }
    }

    private static void appendSeriesFilter(StringBuilder where, List<Object> args,
                                           List<TelemetrySeries> series) {
        for (int i = 0; i < series.size(); i++) {
            if (i > 0) {
                where.append(" OR ");
            }
            where.append("(device_identification = ? AND property_code = ?)");
            args.add(series.get(i).deviceIdentification());
            args.add(series.get(i).propertyCode());
        }
    }

    private static TelemetrySampleView mapSample(ResultSet rs) throws SQLException {
        return new TelemetrySampleView(
                rs.getString("device_identification"),
                rs.getString("property_code"),
                rs.getBigDecimal("value_numeric"),
                rs.getLong("collected_at_ms"),
                rs.getLong("received_at_ms"),
                rs.getString("quality"),
                rs.getString("message_id"));
    }

    private static String pgInterval(Granularity granularity) {
        return switch (granularity) {
            case MINUTE -> "1 minute";
            case HOUR -> "1 hour";
            case DAY -> "1 day";
            case RAW -> throw new IllegalArgumentException("RAW has no interval");
        };
    }

    static String aggregationSql(AggregationType type) {
        return switch (type) {
            case MIN -> "min(value_numeric)";
            case MAX -> "max(value_numeric)";
            case AVG -> "avg(value_numeric)";
            case SUM -> "sum(value_numeric)";
            case COUNT -> "count(value_numeric)";
        };
    }
}
