package com.basiclab.iot.sink.telemetry.query;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.domain.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PRD §4.5 遥测查询/导出 API（TD-003 §16）。tenantId 一律取登录态（fail-closed），
 * 客户端传入的租户字段一律忽略；配额超限返回稳定码 TELEMETRY_QUERY_QUOTA_EXCEEDED。
 *
 * <p>由组件扫描注册（center 配置扫描 com.basiclab.iot），类级
 * {@code @ConditionalOnProperty} 与 {@link TelemetryQueryAutoConfiguration} 同开关
 * （easyaiot.telemetry.query.enabled，默认关闭）——@RestController 必须在类上
 * 才会被 MVC 注册映射，因此用注解条件而非 @Bean 装配。</p>
 */
@Tag(name = "遥测查询")
@RestController
@ConditionalOnProperty(name = "easyaiot.telemetry.query.enabled", havingValue = "true",
        matchIfMissing = false)
@RequestMapping("/telemetry")
public class TelemetryQueryController {

    private final TelemetryQueryPort queryPort;

    public TelemetryQueryController(TelemetryQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public record RawRequest(
            List<SeriesItem> series,
            Long fromMs,
            Long toMs,
            Integer pageNo,
            Integer pageSize
    ) {
    }

    public record AggregateRequest(
            List<SeriesItem> series,
            Long fromMs,
            Long toMs,
            String granularity,
            String aggregation
    ) {
    }

    public record LatestRequest(List<SeriesItem> series) {
    }

    public record SeriesItem(String deviceIdentification, String propertyCode) {
    }

    public record ExportRequest(
            List<SeriesItem> series,
            Long fromMs,
            Long toMs
    ) {
    }

    @PostMapping("/raw")
    @Operation(summary = "原始样本分页查询（series ≤10、跨度 ≤31 天）")
    public CommonResult<TelemetryRawPage> raw(@RequestBody RawRequest request) {
        TelemetryRawQuery query = new TelemetryRawQuery(
                requireTenant(), series(request.series()),
                requireTime(request.fromMs()), requireTime(request.toMs()),
                request.pageNo() == null ? 1 : request.pageNo(),
                request.pageSize() == null ? 100 : request.pageSize());
        return CommonResult.success(queryPort.queryRaw(query));
    }

    @PostMapping("/aggregate")
    @Operation(summary = "粒度聚合查询（分钟/小时/日 × min/max/avg/sum/count）")
    public CommonResult<List<TelemetryAggregatePoint>> aggregate(@RequestBody AggregateRequest request) {
        TelemetryAggregateQuery query = new TelemetryAggregateQuery(
                requireTenant(), series(request.series()),
                requireTime(request.fromMs()), requireTime(request.toMs()),
                Granularity.valueOf(request.granularity()),
                AggregationType.valueOf(request.aggregation()));
        return CommonResult.success(queryPort.aggregate(query));
    }

    @PostMapping("/latest")
    @Operation(summary = "每序列最新值（实时页轮询）")
    public CommonResult<List<TelemetryLatestSample>> latest(@RequestBody LatestRequest request) {
        TelemetryLatestQuery query = new TelemetryLatestQuery(
                requireTenant(), series(request.series()));
        return CommonResult.success(queryPort.latest(query));
    }

    @PostMapping("/export")
    @Operation(summary = "同步 CSV 导出（受原始查询同一配额约束）")
    public ResponseEntity<byte[]> export(@RequestBody ExportRequest request) {
        TelemetryRawQuery query = new TelemetryRawQuery(
                requireTenant(), series(request.series()),
                requireTime(request.fromMs()), requireTime(request.toMs()),
                1, TelemetryQueryQuota.MAX_PAGE_SIZE);
        // 导出受同一配额约束：翻页至累计上限或数据尽
        List<TelemetrySampleView> all = new ArrayList<>();
        int pageNo = 1;
        while (all.size() < TelemetryQueryQuota.MAX_TOTAL_ROWS) {
            TelemetryRawPage page = queryPort.queryRaw(new TelemetryRawQuery(
                    query.tenantId(), query.series(), query.fromMs(), query.toMs(),
                    pageNo, TelemetryQueryQuota.MAX_PAGE_SIZE));
            all.addAll(page.rows());
            if ((long) pageNo * TelemetryQueryQuota.MAX_PAGE_SIZE >= page.totalRows()) {
                break;
            }
            pageNo++;
        }
        byte[] csv = toCsv(all);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=telemetry-export.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    /** 登录态租户缺失即拒绝（PRD §4.5 租户隔离 fail-closed）。 */
    private static String requireTenant() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant context required");
        }
        return String.valueOf(tenantId);
    }

    private static long requireTime(Long value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("fromMs/toMs required");
        }
        return value;
    }

    private static List<TelemetrySeries> series(List<SeriesItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("series required");
        }
        return items.stream()
                .map(item -> new TelemetrySeries(item.deviceIdentification(), item.propertyCode()))
                .toList();
    }

    private static byte[] toCsv(List<TelemetrySampleView> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write("device,property,value,collectedAtMs,receivedAtMs,quality,messageId\n"
                    .getBytes(StandardCharsets.UTF_8));
            for (TelemetrySampleView row : rows) {
                out.write((csv(row.deviceIdentification()) + "," + csv(row.propertyCode()) + ","
                        + (row.value() == null ? "" : row.value().toPlainString()) + ","
                        + row.collectedAtMs() + "," + row.receivedAtMs() + ","
                        + csv(row.quality()) + "," + csv(row.messageId()) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new IllegalStateException("csv build failed", e);
        }
        return out.toByteArray();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(",") || value.contains("\"") || value.contains("\n")
                ? '"' + value.replace("\"", "\"\"") + '"'
                : value;
    }
}
