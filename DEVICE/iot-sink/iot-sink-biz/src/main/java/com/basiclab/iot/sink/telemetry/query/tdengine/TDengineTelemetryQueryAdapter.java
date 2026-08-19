package com.basiclab.iot.sink.telemetry.query.tdengine;

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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * TD-003 §16 查询端口 full adapter（TDengine REST）。
 *
 * <p>引擎差异处理：device/property 是 TAG，INTERVAL 聚合与部分投影不能把 tag
 * 作为 SELECT 列返回——聚合与 latest 按 series 逐个查询（tag 作 WHERE 条件），
 * 结果从循环变量回填标识；raw 查询 tag 列可返回但需显式别名。
 * 聚合用 {@code _wstart, INTERVAL(n)}（等价 PG date_bin）。</p>
 */
public final class TDengineTelemetryQueryAdapter implements TelemetryQueryPort {

    private final String url;
    private final Properties props;
    private final boolean qualityColumnsPresent;

    public TDengineTelemetryQueryAdapter(String host, int port, String username,
                                         String password) {
        String auth = "?user=" + username + "&password=" + password;
        this.url = "jdbc:TAOS-RS://" + host + ":" + port + "/iot_telemetry" + auth;
        this.props = new Properties();
        this.qualityColumnsPresent = probeQualityColumns();
    }

    /** 测试可见：列探测结果。 */
    boolean qualityColumnsPresent() {
        return qualityColumnsPresent;
    }

    private boolean probeQualityColumns() {
        try (Connection c = DriverManager.getConnection(url, props);
             PreparedStatement p = c.prepareStatement(
                     "SELECT quality FROM iot_telemetry.telemetry_sample LIMIT 1")) {
            p.executeQuery();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TelemetryRawPage queryRaw(TelemetryRawQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        if (query.offset() + query.pageSize() > TelemetryQueryQuota.MAX_TOTAL_ROWS) {
            throw new com.basiclab.iot.sink.telemetry.query.QueryQuotaExceededException(
                    "cumulative rows exceed " + TelemetryQueryQuota.MAX_TOTAL_ROWS);
        }
        // tag 列可 SELECT，但以别名返回保证驱动可寻址
        String qualitySel = qualityColumnsPresent ? "quality" : "'GOOD' AS quality";
        String receivedSel = qualityColumnsPresent
                ? "cast(received_at AS BIGINT) AS received_ms"
                : "cast(ts AS BIGINT) AS received_ms";

        String where = " WHERE tenant_id = ? AND ts >= ? AND ts <= ? AND (";
        List<TelemetrySampleView> rows = new ArrayList<>();
        long total = 0;
        StringBuilder sql = new StringBuilder(
                "SELECT device_identification AS device_id, property_code AS prop_code,"
                        + " value_numeric AS val, cast(ts AS BIGINT) AS collected_ms,"
                        + " message_id AS msg_id, " + qualitySel + ", " + receivedSel
                        + " FROM iot_telemetry.telemetry_sample" + where);
        List<Object> args = new ArrayList<>();
        args.add(Long.parseLong(query.tenantId()));
        args.add(new java.sql.Timestamp(query.fromMs()));
        args.add(new java.sql.Timestamp(query.toMs()));
        for (int i = 0; i < query.series().size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("(device_identification = ? AND property_code = ?)");
            args.add(query.series().get(i).deviceIdentification());
            args.add(query.series().get(i).propertyCode());
        }
        sql.append(") ORDER BY ts DESC LIMIT ? OFFSET ?");
        args.add(query.pageSize());
        args.add(query.offset());

        try (Connection c = DriverManager.getConnection(url, props);
             PreparedStatement p = c.prepareStatement(sql.toString())) {
            bind(p, args);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    rows.add(new TelemetrySampleView(
                            rs.getString("device_id"),
                            rs.getString("prop_code"),
                            toBigDecimal(rs.getObject("val")),
                            rs.getLong("collected_ms"),
                            rs.getLong("received_ms"),
                            rs.getString("quality"),
                            rs.getString("msg_id")));
                }
            }
            // 同条件计数
            StringBuilder countSql = new StringBuilder(
                    "SELECT count(*) FROM iot_telemetry.telemetry_sample"
                            + " WHERE tenant_id = ? AND ts >= ? AND ts <= ? AND (");
            List<Object> countArgs = new ArrayList<>();
            countArgs.add(Long.parseLong(query.tenantId()));
            countArgs.add(new java.sql.Timestamp(query.fromMs()));
            countArgs.add(new java.sql.Timestamp(query.toMs()));
            for (int i = 0; i < query.series().size(); i++) {
                if (i > 0) {
                    countSql.append(" OR ");
                }
                countSql.append("(device_identification = ? AND property_code = ?)");
                countArgs.add(query.series().get(i).deviceIdentification());
                countArgs.add(query.series().get(i).propertyCode());
            }
            countSql.append(')');
            try (PreparedStatement cp = c.prepareStatement(countSql.toString())) {
                bind(cp, countArgs);
                try (ResultSet crs = cp.executeQuery()) {
                    if (crs.next()) {
                        total = crs.getLong(1);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("TDENGINE_QUERY_FAILED", e);
        }
        return new TelemetryRawPage(total, query.pageNo(), query.pageSize(), rows);
    }

    @Override
    public List<TelemetryAggregatePoint> aggregate(TelemetryAggregateQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        String interval = switch (query.granularity()) {
            case MINUTE -> "1m";
            case HOUR -> "1h";
            case DAY -> "1d";
            case RAW -> throw new IllegalArgumentException("RAW has no interval");
        };
        String aggFn = switch (query.aggregation()) {
            case MIN -> "min(value_numeric)";
            case MAX -> "max(value_numeric)";
            case AVG -> "avg(value_numeric)";
            case SUM -> "sum(value_numeric)";
            case COUNT -> "count(value_numeric)";
        };
        String qualitySel = qualityColumnsPresent ? "min(quality) AS quality" : "'GOOD' AS quality";

        List<TelemetryAggregatePoint> points = new ArrayList<>();
        // tag 不能作为 INTERVAL SELECT 列：按 series 循环，tag 作 WHERE 条件
        for (TelemetrySeries series : query.series()) {
            String sql = "SELECT _wstart AS bucket_ts, " + aggFn + " AS agg_value,"
                    + " count(*) AS sample_count, " + qualitySel
                    + " FROM iot_telemetry.telemetry_sample"
                    + " WHERE tenant_id = ? AND device_identification = ? AND property_code = ?"
                    + " AND ts >= ? AND ts <= ? INTERVAL(" + interval + ')';
            try (Connection c = DriverManager.getConnection(url, props);
                 PreparedStatement p = c.prepareStatement(sql)) {
                p.setLong(1, Long.parseLong(query.tenantId()));
                p.setString(2, series.deviceIdentification());
                p.setString(3, series.propertyCode());
                p.setTimestamp(4, new java.sql.Timestamp(query.fromMs()));
                p.setTimestamp(5, new java.sql.Timestamp(query.toMs()));
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        points.add(new TelemetryAggregatePoint(
                                series.deviceIdentification(),
                                series.propertyCode(),
                                rs.getTimestamp("bucket_ts").getTime(),
                                toBigDecimal(rs.getObject("agg_value")),
                                rs.getLong("sample_count"),
                                rs.getString("quality")));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("TDENGINE_QUERY_FAILED", e);
            }
        }
        points.sort(java.util.Comparator.comparingLong(TelemetryAggregatePoint::bucketStartMs));
        return points;
    }

    @Override
    public List<TelemetryLatestSample> latest(TelemetryLatestQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        String qualitySel = qualityColumnsPresent ? "last(quality) AS quality" : "'GOOD' AS quality";
        String receivedSel = qualityColumnsPresent
                ? "cast(last(received_at) AS BIGINT) AS received_ms"
                : "cast(last(ts) AS BIGINT) AS received_ms";

        List<TelemetryLatestSample> result = new ArrayList<>();
        for (TelemetrySeries series : query.series()) {
            String sql = "SELECT last(value_numeric) AS val, last(ts) AS t, "
                    + qualitySel + ", " + receivedSel
                    + " FROM iot_telemetry.telemetry_sample"
                    + " WHERE tenant_id = ? AND device_identification = ? AND property_code = ?";
            try (Connection c = DriverManager.getConnection(url, props);
                 PreparedStatement p = c.prepareStatement(sql)) {
                p.setLong(1, Long.parseLong(query.tenantId()));
                p.setString(2, series.deviceIdentification());
                p.setString(3, series.propertyCode());
                try (ResultSet rs = p.executeQuery()) {
                    if (rs.next() && rs.getTimestamp("t") != null) {
                        result.add(new TelemetryLatestSample(
                                series.deviceIdentification(),
                                series.propertyCode(),
                                toBigDecimal(rs.getObject("val")),
                                rs.getTimestamp("t").getTime(),
                                rs.getLong("received_ms"),
                                rs.getString("quality")));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("TDENGINE_QUERY_FAILED", e);
            }
        }
        return result;
    }

    private static void bind(PreparedStatement p, List<Object> args) throws Exception {
        for (int i = 0; i < args.size(); i++) {
            p.setObject(i + 1, args.get(i));
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }
}
