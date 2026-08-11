package com.basiclab.iot.device.service.idempotency;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * TD-004 §7.12 / TD-005 §11：电力域写 API 共用的 PostgreSQL 幂等争抢与终态重放端口。
 * 调用方事务负责把业务事实与幂等终态一起提交；本端口不持有客户端 key 原文。
 */
@Component
public class JdbcPowerIdempotencyStore {

    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPowerIdempotencyStore(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    /** 首次争抢返回 PROCEED；终态返回 REPLAY；处理中与冲突直接返回稳定错误。 */
    public Claim claim(Scope scope, byte[] requestHash) {
        Objects.requireNonNull(scope, "scope");
        requireHash(requestHash, "requestHash");
        MapSqlParameterSource params = params(scope).addValue("requestHash", requestHash);
        int inserted = jdbc.update("INSERT INTO public.power_idempotency_record"
                        + " (tenant_id,principal_type,principal_id,operation,key_hash,request_hash,state)"
                        + " VALUES (:tenantId,:principalType,:principalId,:operation,:keyHash,:requestHash,'IN_PROGRESS')"
                        + " ON CONFLICT (tenant_id,principal_type,principal_id,operation,key_hash)"
                        + " DO NOTHING", params);
        if (inserted == 1) {
            return Claim.proceed();
        }

        List<StoredRecord> rows = jdbc.query(
                "SELECT request_hash,state,http_status,response_payload::text AS response_payload,result_ref"
                        + " FROM public.power_idempotency_record"
                        + " WHERE tenant_id=:tenantId AND principal_type=:principalType"
                        + " AND principal_id=:principalId AND operation=:operation AND key_hash=:keyHash"
                        + " FOR UPDATE", params,
                (rs, rowNum) -> new StoredRecord(rs.getBytes("request_hash"),
                        rs.getString("state"), (Integer) rs.getObject("http_status"),
                        rs.getString("response_payload"), rs.getString("result_ref")));
        if (rows.size() != 1) {
            fail("IDEMPOTENCY_STATE_CORRUPT", "幂等记录不存在或不唯一");
        }
        StoredRecord record = rows.get(0);
        if (!MessageDigest.isEqual(record.requestHash, requestHash)) {
            fail("IDEMPOTENCY_KEY_REUSED", "相同 key 被用于不同请求，绝不覆盖");
        }
        if ("IN_PROGRESS".equals(record.state)) {
            fail("IDEMPOTENCY_IN_PROGRESS", "相同请求仍在处理，可稍后重试");
        }
        if (!"SUCCEEDED".equals(record.state) && !"FAILED_FINAL".equals(record.state)) {
            fail("IDEMPOTENCY_STATE_CORRUPT", "幂等记录状态非法");
        }
        if (record.httpStatus == null) {
            fail("IDEMPOTENCY_STATE_CORRUPT", "幂等终态缺少 HTTP 状态");
        }
        return Claim.replay(record.state, record.httpStatus, record.responsePayload,
                record.resultRef);
    }

    public void completeSuccess(Scope scope, int httpStatus, String responsePayload,
                                String resultRef) {
        complete(scope, "SUCCEEDED", httpStatus, responsePayload, resultRef);
    }

    public void completeFinalFailure(Scope scope, int httpStatus, String responsePayload,
                                     String resultRef) {
        complete(scope, "FAILED_FINAL", httpStatus, responsePayload, resultRef);
    }

    private void complete(Scope scope, String state, int httpStatus, String responsePayload,
                          String resultRef) {
        Objects.requireNonNull(scope, "scope");
        if (httpStatus < 100 || httpStatus > 599) {
            fail("IDEMPOTENCY_RESPONSE_INVALID", "HTTP 状态码超出范围");
        }
        if (responsePayload != null
                && responsePayload.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            fail("IDEMPOTENCY_RESPONSE_INVALID", "可重放响应超过 16KiB");
        }
        int updated = jdbc.update("UPDATE public.power_idempotency_record SET"
                        + " state=:state,http_status=:httpStatus,"
                        + " response_payload=CAST(:responsePayload AS jsonb),result_ref=:resultRef,"
                        + " updated_at=CURRENT_TIMESTAMP"
                        + " WHERE tenant_id=:tenantId AND principal_type=:principalType"
                        + " AND principal_id=:principalId AND operation=:operation AND key_hash=:keyHash"
                        + " AND state='IN_PROGRESS'",
                params(scope).addValue("state", state).addValue("httpStatus", httpStatus)
                        .addValue("responsePayload", responsePayload).addValue("resultRef", resultRef));
        if (updated != 1) {
            fail("IDEMPOTENCY_STATE_CORRUPT", "幂等终态推进失败");
        }
    }

    private static MapSqlParameterSource params(Scope scope) {
        return new MapSqlParameterSource("tenantId", scope.tenantId)
                .addValue("principalType", scope.principalType)
                .addValue("principalId", scope.principalId)
                .addValue("operation", scope.operation)
                .addValue("keyHash", scope.keyHash);
    }

    private static void requireHash(byte[] value, String field) {
        if (value == null || value.length != 32) {
            fail("IDEMPOTENCY_SCOPE_INVALID", field + " 必须是 32 字节摘要");
        }
    }

    private static void fail(String code, String detail) {
        throw new IllegalArgumentException(code + ": " + detail);
    }

    public static final class Scope {
        private final long tenantId;
        private final String principalType;
        private final String principalId;
        private final String operation;
        private final byte[] keyHash;

        public Scope(long tenantId, String principalType, String principalId, String operation,
                     byte[] keyHash) {
            if (tenantId <= 0) fail("IDEMPOTENCY_SCOPE_INVALID", "tenantId 必须为正数");
            if (!"USER".equals(principalType) && !"SERVICE".equals(principalType)) {
                fail("IDEMPOTENCY_SCOPE_INVALID", "principalType 非法");
            }
            if (principalId == null || principalId.isEmpty() || principalId.length() > 64) {
                fail("IDEMPOTENCY_SCOPE_INVALID", "principalId 非法");
            }
            if (operation == null || operation.isEmpty() || operation.length() > 64) {
                fail("IDEMPOTENCY_SCOPE_INVALID", "operation 非法");
            }
            requireHash(keyHash, "keyHash");
            this.tenantId = tenantId;
            this.principalType = principalType;
            this.principalId = principalId;
            this.operation = operation;
            this.keyHash = keyHash.clone();
        }
    }

    public static final class Claim {
        public enum Outcome { PROCEED, REPLAY }
        private final Outcome outcome;
        private final String state;
        private final Integer httpStatus;
        private final String responsePayload;
        private final String resultRef;

        private Claim(Outcome outcome, String state, Integer httpStatus,
                      String responsePayload, String resultRef) {
            this.outcome = outcome;
            this.state = state;
            this.httpStatus = httpStatus;
            this.responsePayload = responsePayload;
            this.resultRef = resultRef;
        }

        static Claim proceed() { return new Claim(Outcome.PROCEED, null, null, null, null); }
        static Claim replay(String state, int status, String payload, String resultRef) {
            return new Claim(Outcome.REPLAY, state, status, payload, resultRef);
        }
        public Outcome outcome() { return outcome; }
        public String state() { return state; }
        public Integer httpStatus() { return httpStatus; }
        public String responsePayload() { return responsePayload; }
        public String resultRef() { return resultRef; }
    }

    private static final class StoredRecord {
        final byte[] requestHash;
        final String state;
        final Integer httpStatus;
        final String responsePayload;
        final String resultRef;

        StoredRecord(byte[] requestHash, String state, Integer httpStatus,
                     String responsePayload, String resultRef) {
            this.requestHash = requestHash;
            this.state = state;
            this.httpStatus = httpStatus;
            this.responsePayload = responsePayload;
            this.resultRef = resultRef;
        }
    }
}
