package com.basiclab.iot.sink.telemetry.query;

/**
 * 查询超出 PRD §4.5 配额。稳定错误码 TELEMETRY_QUERY_QUOTA_EXCEEDED；
 * 提示调用方提高聚合粒度、缩小范围或改走导出。
 */
public final class QueryQuotaExceededException extends RuntimeException {

    public static final String CODE = "TELEMETRY_QUERY_QUOTA_EXCEEDED";

    public QueryQuotaExceededException(String detail) {
        super(CODE + ": " + detail);
    }
}
