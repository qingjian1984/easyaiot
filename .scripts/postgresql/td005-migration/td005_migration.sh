#!/usr/bin/env bash
# ============================================================================
# TD-005 受控迁移执行器（候选实现 / Spike）
#
# 状态：Review Candidate；ADR-013 1.4.0 仍为 Proposed
# 用途：仅在临时/评审库执行，不构成生产迁移授权；批准前不得对生产库执行
#
# 用法：
#   ./td005_migration.sh dry-run [--db <database>]
#   ./td005_migration.sh apply [--db <database>] [--step M15|M16|V001|V002] [--approval <id>] [--yes]
#   ./td005_migration.sh uninstall [--db <database>] [--approval <id>] [--yes]
#   ./td005_migration.sh check-comments [--db <database>]
#
# 环境变量：
#   PG_CONTAINER  PostgreSQL 容器名（默认 postgres-server；空则使用本地 psql）
#   PG_USER       数据库用户（默认 postgres）
#   PG_PASSWORD   数据库密码（无默认值，禁止硬编码）
#   PG_HOST       PG_PORT 本地 psql 连接参数（容器模式忽略）
#   PG_DB         目标数据库（默认 iot-device20）
#   LOCK_KEY      advisory lock 键值（默认 913005，为 TD005_MIG_LOCK 的评审候选
#                 稳定键；冻结前如需更换必须同步 ADR-013 配置清单）
#   APPROVAL      生产审批单 ID（apply/uninstall 必填）
#   BACKUP_DIR    备份目录（apply/uninstall 必填；执行前自动 pg_dump 并写入证据）
#   REQUIRE_IDEMPOTENCY  1 时 precheck 强制要求 power_idempotency_record
#   CONNECT_TIMEOUT      PostgreSQL 连接超时秒数（默认 3）
#   LOCK_WAIT            advisory lock / 锁等待上限（默认 15s，映射 lock_timeout）
#   STATEMENT_TIMEOUT    事务型步骤语句超时（默认 15s）
#   M15_STATEMENT_TIMEOUT  M15 CONCURRENTLY 独立语句超时（默认 30min）
#   RETRY_MAX / RETRY_BASE_DELAY / RETRY_MAX_DELAY
#                        可重试错误（锁忙/连接/超时）的重试次数与退避（默认 3/1s/4s）
#   SKIP_PRECHECK       1 时跳过运行时画像 precheck
#   M15_SQL / M16_SQL / V001_SQL / V002_SQL / U001_SQL  步骤 SQL 路径覆盖
#
# 执行模型（ADR-013 1.4.0，DBA/架构专项处置 H-01～H-04）：
#   1. 单 psql 会话内先完成全部校验（hash、INVALID index、索引签名），
#      任何校验失败零业务 DDL 变化（仅 history 引导表幂等建立）；
#   2. 校验全部通过后才按依赖顺序执行 M15 → M16 → V001 → V002；
#   3. 锁等待与语句超时通过 lock_timeout/statement_timeout 强制有界；
#   4. 失败按错误码分类，仅可重试错误按有界退避重跑（步骤幂等跳过）；
#   5. 成功/失败均落 schema_migration_history（FAILED 含脱敏错误摘要）。
# ============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

MODE="${1:-dry-run}"
shift || true

PG_DB="${PG_DB:-iot-device20}"
PG_USER="${PG_USER:-postgres}"
PG_HOST="${PG_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5432}"
LOCK_KEY="${LOCK_KEY:-913005}"
CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-3}"
LOCK_WAIT="${LOCK_WAIT:-15s}"
STATEMENT_TIMEOUT="${STATEMENT_TIMEOUT:-15s}"
M15_STATEMENT_TIMEOUT="${M15_STATEMENT_TIMEOUT:-30min}"
RETRY_MAX="${RETRY_MAX:-3}"
RETRY_BASE_DELAY="${RETRY_BASE_DELAY:-1}"
RETRY_MAX_DELAY="${RETRY_MAX_DELAY:-4}"
REQUIRE_IDEMPOTENCY="${REQUIRE_IDEMPOTENCY:-0}"
SKIP_PRECHECK="${SKIP_PRECHECK:-0}"
STEP_ONLY="${STEP_ONLY:-}"
YES="${YES:-0}"
export PGCONNECT_TIMEOUT="${CONNECT_TIMEOUT}"

ASSET_DIR="${REPO_ROOT}/.doc/技术设计/电力运维云平台/assets/td005-migration"
M15_SQL="${M15_SQL:-${SCRIPT_DIR}/steps/M15__product_tenant_unique_concurrently.sql}"
M16_SQL="${M16_SQL:-${SCRIPT_DIR}/steps/M16__product_tenant_unique_attach.sql}"
V001_SQL="${V001_SQL:-${ASSET_DIR}/V001__power_model_version_audit_outbox.sql}"
V002_SQL="${V002_SQL:-${ASSET_DIR}/V002__power_product_model_binding.sql}"
U001_SQL="${U001_SQL:-${ASSET_DIR}/U001__power_model_version_binding_audit_outbox.sql}"
CHECK_SQL="${SCRIPT_DIR}/check_ddl_comments.sql"
PREPROFILE_SQL="${SCRIPT_DIR}/precheck_runtime_profile.sql"

APPLY_STEPS=(M15 M16 V001 V002)

while [ $# -gt 0 ]; do
    case "$1" in
        --db) PG_DB="$2"; shift 2 ;;
        --approval) APPROVAL="$2"; shift 2 ;;
        --step) STEP_ONLY="$2"; shift 2 ;;
        --skip-precheck) SKIP_PRECHECK=1; shift ;;
        --yes) YES=1; shift ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

log() { echo "[td005-migration][$(date +%FT%T%z)] $*"; }
fail() { echo "[td005-migration][ERROR] $*" >&2; exit 1; }
fail_validation() { echo "[td005-migration][VALIDATION] $*" >&2; exit 2; }

step_sql_path() {
    case "$1" in
        M15) echo "${M15_SQL}" ;;
        M16) echo "${M16_SQL}" ;;
        V001) echo "${V001_SQL}" ;;
        V002) echo "${V002_SQL}" ;;
        U001) echo "${U001_SQL}" ;;
        *) fail "unknown step: $1" ;;
    esac
}

step_selected() {
    [ -z "${STEP_ONLY}" ] && return 0
    [ "${STEP_ONLY}" = "$1" ]
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

psql_base() {
    if [ -n "${PG_CONTAINER:-}" ]; then
        local env_args=(-e "PGPASSWORD=${PG_PASSWORD}" -e "PGCONNECT_TIMEOUT=${CONNECT_TIMEOUT}")
        [ -n "${PG_HOST:-}" ] && env_args+=(-e "PGHOST=${PG_HOST}")
        [ -n "${PG_PORT:-}" ] && env_args+=(-e "PGPORT=${PG_PORT}")
        echo "docker exec -i ${env_args[*]} ${PG_CONTAINER} psql -U ${PG_USER} -d ${PG_DB} -v ON_ERROR_STOP=1"
    else
        echo "psql -h ${PG_HOST} -p ${PG_PORT} -U ${PG_USER} -d ${PG_DB} -v ON_ERROR_STOP=1"
    fi
}

require_approval() {
    if [ -z "${APPROVAL:-}" ]; then
        fail_validation "APPROVAL_MISSING: ${MODE} 需要 --approval <approval-id>"
    fi
}

require_backup_dir() {
    if [ -z "${BACKUP_DIR:-}" ]; then
        fail_validation "BACKUP_MISSING: ${MODE} 必须设置 BACKUP_DIR（ADR-013：apply 前备份为 MUST）"
    fi
}

run_precheck() {
    [ "${SKIP_PRECHECK}" = "1" ] && { log "precheck SKIPPED"; return 0; }
    local tmp_sql output rc
    tmp_sql="$(mktemp)"
    {
        echo "SET app.require_idempotency = :'require_idempotency';"
        cat "${PREPROFILE_SQL}"
    } > "${tmp_sql}"
    output="$($(psql_base) -q -tA -v require_idempotency="${REQUIRE_IDEMPOTENCY}" < "${tmp_sql}" 2>&1)"
    rc=$?
    rm -f "${tmp_sql}"
    [ $rc -eq 0 ] || fail "precheck failed (exit=${rc})"
    if [ -n "${output}" ]; then
        echo "${output}"
        fail_validation "PROFILE_ANOMALY: runtime profile precheck failed"
    fi
    log "precheck PASS"
}

BACKUP_FILE=""
backup_before_apply() {
    mkdir -p "${BACKUP_DIR}"
    BACKUP_FILE="${BACKUP_DIR}/${PG_DB}_$(date +%Y%m%d_%H%M%S).sql"
    if [ -n "${PG_CONTAINER:-}" ]; then
        docker exec -e "PGPASSWORD=${PG_PASSWORD}" "${PG_CONTAINER}" \
            pg_dump -U "${PG_USER}" -d "${PG_DB}" > "${BACKUP_FILE}" \
            || fail "backup failed: pg_dump exit=$?"
    else
        PGPASSWORD="${PG_PASSWORD}" pg_dump -h "${PG_HOST}" -p "${PG_PORT}" \
            -U "${PG_USER}" -d "${PG_DB}" > "${BACKUP_FILE}" \
            || fail "backup failed: pg_dump exit=$?"
    fi
    [ -s "${BACKUP_FILE}" ] || fail "backup produced empty file: ${BACKUP_FILE}"
    log "backup written: ${BACKUP_FILE}"
}

history_bootstrap() {
    cat <<'SQL'
CREATE TABLE IF NOT EXISTS public.schema_migration_history (
    id BIGSERIAL PRIMARY KEY,
    migration_id VARCHAR(128) NOT NULL,
    script_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('SUCCEEDED','FAILED','SKIPPED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    executed_by VARCHAR(64) NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}',
    UNIQUE (migration_id)
);
CREATE INDEX IF NOT EXISTS idx_schema_migration_history_status_started
    ON public.schema_migration_history (status, started_at DESC);
COMMENT ON TABLE public.schema_migration_history IS '受控迁移执行器历史表（迁移 ID + SHA-256 执行事实）';
COMMENT ON COLUMN public.schema_migration_history.id IS '主键';
COMMENT ON COLUMN public.schema_migration_history.migration_id IS '迁移 ID（同一迁移全局唯一）';
COMMENT ON COLUMN public.schema_migration_history.script_sha256 IS '迁移脚本 SHA-256（同 ID 异 hash 必须阻断）';
COMMENT ON COLUMN public.schema_migration_history.status IS '执行状态（SUCCEEDED/FAILED；SKIPPED 仅为返回语义不落库）';
COMMENT ON COLUMN public.schema_migration_history.started_at IS '执行开始时间';
COMMENT ON COLUMN public.schema_migration_history.finished_at IS '执行结束时间';
COMMENT ON COLUMN public.schema_migration_history.executed_by IS '执行人/执行身份';
COMMENT ON COLUMN public.schema_migration_history.evidence IS '执行证据（审批单、目标库版本、备份文件、输出摘要）';
SQL
}

# 阶段一：全部校验（零业务 DDL）。任何失败在输出中产生对应标记，由 bash 映射退出码 2。
emit_validation_phase() {
    cat <<SQL
\set ON_ERROR_STOP on
SET lock_timeout = :'lock_wait';
SET statement_timeout = :'statement_timeout';
SELECT pg_advisory_lock(:'lock_key'::bigint);
SQL
    history_bootstrap
    local step sha_var
    for step in "${APPLY_STEPS[@]}"; do
        sha_var="$(echo "${step}" | tr 'A-Z' 'a-z')_sha"
        cat <<SQL
SELECT EXISTS (
    SELECT 1 FROM public.schema_migration_history
    WHERE migration_id = '${step}' AND status = 'SUCCEEDED' AND script_sha256 <> :'${sha_var}'
) AS ${sha_var}_mismatch \gset
\if :${sha_var}_mismatch
\echo HASH_MISMATCH ${step}
SELECT 1/0 AS migration_hash_mismatch;
\endif
SQL
    done
    cat <<'SQL'
SELECT EXISTS (
    SELECT 1 FROM pg_index i
    JOIN pg_class c ON c.oid = i.indexrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'uq_product_tenant_identification' AND i.indisvalid = false
) AS invalid_idx \gset
\if :invalid_idx
\echo INVALID_INDEX_DETECTED
SELECT 1/0 AS invalid_index_detected;
\endif
SELECT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_index i ON i.indexrelid = c.oid
    WHERE n.nspname = 'public' AND c.relname = 'uq_product_tenant_identification'
      AND pg_get_indexdef(i.indexrelid) NOT LIKE
          'CREATE UNIQUE INDEX uq_product_tenant_identification ON public.product USING btree (tenant_id, product_identification)%'
) AS idx_sig_mismatch \gset
\if :idx_sig_mismatch
\echo INDEX_SIGNATURE_MISMATCH
SELECT 1/0 AS index_signature_mismatch;
\endif
\echo VALIDATION_PASS
SQL
}

# 阶段二：按依赖顺序执行被选定且未完成的步骤。
emit_execution_phase() {
    local step sha_var
    for step in "${APPLY_STEPS[@]}"; do
        step_selected "${step}" || continue
        sha_var="$(echo "${step}" | tr 'A-Z' 'a-z')_sha"
        cat <<SQL
SELECT NOT EXISTS (
    SELECT 1 FROM public.schema_migration_history
    WHERE migration_id = '${step}' AND status = 'SUCCEEDED' AND script_sha256 = :'${sha_var}'
) AS ${sha_var}_need_run \gset
\if :${sha_var}_need_run
\echo STEP_START ${step}
SELECT clock_timestamp() AS ${sha_var}_started \gset
SQL
        case "${step}" in
            M15)
                cat <<SQL
SELECT to_regclass('public.uq_product_tenant_identification') IS NULL AS m15_need_index \gset
\if :m15_need_index
SET statement_timeout = :'m15_statement_timeout';
SQL
                cat "${M15_SQL}"
                cat <<SQL
SET statement_timeout = :'statement_timeout';
\endif
SQL
                ;;
            M16)
                cat <<'SQL'
SELECT NOT EXISTS (
    SELECT 1 FROM pg_constraint c
    JOIN pg_class r ON r.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = r.relnamespace
    WHERE n.nspname = 'public' AND r.relname = 'product'
      AND c.conname = 'uq_product_tenant_identification'
) AS m16_need_attach \gset
\if :m16_need_attach
BEGIN;
SQL
                cat "${M16_SQL}"
                cat <<'SQL'
COMMIT;
\endif
SQL
                ;;
            V001|V002)
                echo "BEGIN;"
                cat "$(step_sql_path "${step}")"
                echo "COMMIT;"
                ;;
        esac
        cat <<SQL
INSERT INTO public.schema_migration_history
    (migration_id, script_sha256, status, started_at, finished_at, executed_by, evidence)
VALUES
    ('${step}', :'${sha_var}', 'SUCCEEDED', :'${sha_var}_started'::timestamptz, clock_timestamp(), :'executor',
     json_build_object('approval', :'approval', 'target', :'db', 'backup', :'backup_file')::jsonb)
ON CONFLICT (migration_id) DO UPDATE SET
    script_sha256 = EXCLUDED.script_sha256,
    status = EXCLUDED.status,
    started_at = EXCLUDED.started_at,
    finished_at = EXCLUDED.finished_at,
    executed_by = EXCLUDED.executed_by,
    evidence = EXCLUDED.evidence;
\echo STEP_DONE ${step}
\else
\echo STEP_SKIPPED ${step}
\endif
SQL
    done
    cat <<'SQL'
SELECT pg_advisory_unlock(:'lock_key'::bigint);
SQL
}

emit_apply_driver() {
    emit_validation_phase
    emit_execution_phase
}

emit_uninstall_driver() {
    cat <<SQL
\set ON_ERROR_STOP on
SET lock_timeout = :'lock_wait';
SET statement_timeout = :'statement_timeout';
SELECT pg_advisory_lock(:'lock_key'::bigint);
SQL
    history_bootstrap
    cat <<'SQL'
SELECT EXISTS (
    SELECT 1 FROM public.schema_migration_history
    WHERE migration_id = 'U001' AND status = 'SUCCEEDED' AND script_sha256 <> :'u001_sha'
) AS u001_mismatch \gset
\if :u001_mismatch
\echo HASH_MISMATCH U001
SELECT 1/0 AS migration_hash_mismatch;
\endif
SELECT NOT EXISTS (
    SELECT 1 FROM public.schema_migration_history
    WHERE migration_id = 'U001' AND status = 'SUCCEEDED' AND script_sha256 = :'u001_sha'
) AS u001_need_run \gset
\if :u001_need_run
\echo STEP_START U001
SELECT clock_timestamp() AS u001_started \gset
SQL
    cat "${U001_SQL}"
    cat <<'SQL'
INSERT INTO public.schema_migration_history
    (migration_id, script_sha256, status, started_at, finished_at, executed_by, evidence)
VALUES
    ('U001', :'u001_sha', 'SUCCEEDED', :'u001_started'::timestamptz, clock_timestamp(), :'executor',
     json_build_object('approval', :'approval', 'target', :'db', 'backup', :'backup_file')::jsonb)
ON CONFLICT (migration_id) DO UPDATE SET
    script_sha256 = EXCLUDED.script_sha256,
    status = EXCLUDED.status,
    started_at = EXCLUDED.started_at,
    finished_at = EXCLUDED.finished_at,
    executed_by = EXCLUDED.executed_by,
    evidence = EXCLUDED.evidence;
\echo STEP_DONE U001
\else
\echo STEP_SKIPPED U001
\endif
SELECT pg_advisory_unlock(:'lock_key'::bigint);
SQL
}

classify_error() {
    local logfile="$1"
    if grep -q "HASH_MISMATCH" "${logfile}"; then echo "HASH_MISMATCH"; return; fi
    if grep -q "INVALID_INDEX_DETECTED" "${logfile}"; then echo "INVALID_INDEX_DETECTED"; return; fi
    if grep -q "INDEX_SIGNATURE_MISMATCH" "${logfile}"; then echo "INDEX_SIGNATURE_MISMATCH"; return; fi
    if grep -q "VALIDATION_PASS" "${logfile}" && ! grep -q "STEP_START" "${logfile}"; then echo "VALIDATION_ABORT"; return; fi
    if grep -qiE "canceling statement due to lock timeout" "${logfile}"; then echo "MIGRATION_LOCK_BUSY"; return; fi
    if grep -qiE "canceling statement due to statement timeout" "${logfile}"; then echo "DEPENDENCY_TIMEOUT"; return; fi
    if grep -qiE "could not connect|Connection refused|timeout expired|server closed the connection|no response from server" "${logfile}"; then echo "PG_CONNECTION_FAILED"; return; fi
    echo "STEP_FAILED"
}

is_retryable() {
    case "$1" in
        MIGRATION_LOCK_BUSY|DEPENDENCY_TIMEOUT|PG_CONNECTION_FAILED) return 0 ;;
        *) return 1 ;;
    esac
}

# 失败落史：以最后一个 STEP_START 标记定位失败步骤（校验阶段失败不落 FAILED）。
record_failure() {
    local logfile="$1" err_code="$2" run_started="$3" failed_step="" step sha digest
    failed_step="$(grep 'STEP_START' "${logfile}" | tail -1 | awk '{print $2}')"
    [ -z "${failed_step}" ] && return 0
    [ "${err_code}" = "PG_CONNECTION_FAILED" ] && return 0
    sha="$(sha256_file "$(step_sql_path "${failed_step}")")"
    digest="$(grep -iE 'ERROR|FATAL' "${logfile}" | head -1 | tr -d '[:cntrl:]' | cut -c1-120)"
    local tmp_sql
    tmp_sql="$(mktemp)"
    {
        history_bootstrap
        cat <<SQL
INSERT INTO public.schema_migration_history
    (migration_id, script_sha256, status, started_at, finished_at, executed_by, evidence)
VALUES
    ('${failed_step}', '${sha}', 'FAILED', '${run_started}'::timestamptz, clock_timestamp(), '${PG_USER:-runner}',
     json_build_object('approval', '${APPROVAL:-}', 'target', '${PG_DB}', 'error_code', '${err_code}', 'error_digest', '${digest}')::jsonb)
ON CONFLICT (migration_id) DO UPDATE SET
    script_sha256 = EXCLUDED.script_sha256,
    status = EXCLUDED.status,
    started_at = EXCLUDED.started_at,
    finished_at = EXCLUDED.finished_at,
    executed_by = EXCLUDED.executed_by,
    evidence = EXCLUDED.evidence;
SQL
    } > "${tmp_sql}"
    $(psql_base) -q < "${tmp_sql}" >/dev/null 2>&1 \
        && log "FAILED recorded: step=${failed_step} code=${err_code}" \
        || log "FAILED record skipped (database unreachable or rejected)"
    rm -f "${tmp_sql}"
}

build_step_vars() {
    local step sha_var
    for step in "${APPLY_STEPS[@]}" U001; do
        sha_var="$(echo "${step}" | tr 'A-Z' 'a-z')_sha"
        echo "-v ${sha_var}=$(sha256_file "$(step_sql_path "${step}")")"
    done
}

run_apply_once() {
    local tmp_sql log_file
    tmp_sql="$(mktemp)"
    log_file="$(mktemp)"
    emit_apply_driver > "${tmp_sql}"
    # shellcheck disable=SC2046
    $(psql_base) \
        -v lock_key="${LOCK_KEY}" \
        -v lock_wait="${LOCK_WAIT}" \
        -v statement_timeout="${STATEMENT_TIMEOUT}" \
        -v m15_statement_timeout="${M15_STATEMENT_TIMEOUT}" \
        $(build_step_vars) \
        -v executor="${PG_USER:-runner}" \
        -v approval="${APPROVAL}" \
        -v db="${PG_DB}" \
        -v backup_file="${BACKUP_FILE}" < "${tmp_sql}" > "${log_file}" 2>&1
    local rc=$?
    cat "${log_file}"
    rm -f "${tmp_sql}"
    LAST_RUN_LOG="${log_file}"
    return $rc
}

run_uninstall_once() {
    local tmp_sql log_file
    tmp_sql="$(mktemp)"
    log_file="$(mktemp)"
    emit_uninstall_driver > "${tmp_sql}"
    # shellcheck disable=SC2046
    $(psql_base) \
        -v lock_key="${LOCK_KEY}" \
        -v lock_wait="${LOCK_WAIT}" \
        -v statement_timeout="${STATEMENT_TIMEOUT}" \
        $(build_step_vars) \
        -v executor="${PG_USER:-runner}" \
        -v approval="${APPROVAL}" \
        -v db="${PG_DB}" \
        -v backup_file="${BACKUP_FILE}" < "${tmp_sql}" > "${log_file}" 2>&1
    local rc=$?
    cat "${log_file}"
    rm -f "${tmp_sql}"
    LAST_RUN_LOG="${log_file}"
    return $rc
}

run_with_retry() {
    local run_fn="$1" attempt=1 delay rc err_code run_started
    while :; do
        run_started="$(date -u '+%Y-%m-%d %H:%M:%S+00')"
        if "${run_fn}"; then
            rm -f "${LAST_RUN_LOG}"
            return 0
        fi
        rc=$?
        err_code="$(classify_error "${LAST_RUN_LOG}")"
        case "${err_code}" in
            HASH_MISMATCH|INVALID_INDEX_DETECTED|INDEX_SIGNATURE_MISMATCH|VALIDATION_ABORT)
                rm -f "${LAST_RUN_LOG}"
                fail_validation "${err_code}: 校验失败，零业务变更"
                ;;
        esac
        if is_retryable "${err_code}" && [ "${attempt}" -lt "${RETRY_MAX}" ]; then
            delay=$(( RETRY_BASE_DELAY * (1 << (attempt - 1)) ))
            [ "${delay}" -gt "${RETRY_MAX_DELAY}" ] && delay="${RETRY_MAX_DELAY}"
            log "${err_code}，第 ${attempt}/${RETRY_MAX} 次失败，${delay}s 后重试（步骤幂等跳过）"
            rm -f "${LAST_RUN_LOG}"
            sleep "${delay}"
            attempt=$(( attempt + 1 ))
        else
            record_failure "${LAST_RUN_LOG}" "${err_code}" "${run_started}"
            rm -f "${LAST_RUN_LOG}"
            fail "${err_code}: ${MODE} failed after ${attempt} attempt(s)"
        fi
    done
}

case "${MODE}" in
    dry-run)
        log "dry-run target=${PG_DB}"
        for step in "${APPLY_STEPS[@]}" U001; do
            log "${step} sha256=$(sha256_file "$(step_sql_path "${step}")")"
        done
        log "steps: M15 (concurrent) -> M16 (attach) -> V001 (txn) -> V002 (txn); U001 uninstall only"
        log "timeouts: connect=${CONNECT_TIMEOUT}s lock_wait=${LOCK_WAIT} statement=${STATEMENT_TIMEOUT} m15=${M15_STATEMENT_TIMEOUT}"
        exit 0
        ;;
    apply)
        require_approval
        require_backup_dir
        run_precheck
        backup_before_apply
        run_with_retry run_apply_once
        log "apply SUCCEEDED target=${PG_DB}"
        ;;
    uninstall)
        require_approval
        require_backup_dir
        run_precheck
        backup_before_apply
        run_with_retry run_uninstall_once
        log "uninstall SUCCEEDED target=${PG_DB}"
        ;;
    check-comments)
        TMP_SQL="$(mktemp)"
        cat "${CHECK_SQL}" > "${TMP_SQL}"
        output="$($(psql_base) -tA < "${TMP_SQL}" 2>&1)"
        rc=$?
        rm -f "${TMP_SQL}"
        [ $rc -eq 0 ] || fail "check-comments failed (exit=${rc})"
        if [ -n "${output}" ]; then
            echo "${output}"
            fail_validation "MIG-009: missing/non-Chinese comments detected"
        fi
        log "MIG-009 PASS: all TD-005 tables/columns have Chinese comments"
        ;;
    *)
        echo "usage: $0 dry-run|apply|uninstall|check-comments" >&2
        exit 2
        ;;
esac
