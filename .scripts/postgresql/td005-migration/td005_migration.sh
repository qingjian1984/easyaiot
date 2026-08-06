#!/usr/bin/env bash
# ============================================================================
# TD-005 受控迁移执行器（候选实现 / Spike）
#
# 状态：Review Candidate；ADR-013 1.1.0 仍为 Proposed
# 用途：仅在临时/评审库执行，不构成生产迁移授权；批准前不得对生产库执行
#
# 用法：
#   ./td005_migration.sh dry-run [--db <database>]
#   ./td005_migration.sh apply [--db <database>] [--approval <id>] [--yes]
#   ./td005_migration.sh uninstall [--db <database>] [--approval <id>] [--yes]
#   ./td005_migration.sh check-comments [--db <database>]
#
# 环境变量：
#   PG_CONTAINER  PostgreSQL 容器名（默认 postgres-server；空则使用本地 psql）
#   PG_USER       数据库用户（默认 postgres）
#   PG_PASSWORD   数据库密码（无默认值，禁止硬编码）
#   PG_HOST       PG_PORT 本地 psql 连接参数（容器模式忽略）
#   PG_DB         目标数据库（默认 iot-device20）
#   LOCK_KEY      advisory lock 键值（默认 913005）
#   APPROVAL      生产审批单 ID
#   BACKUP_DIR    备份目录（apply 前自动 pg_dump）
#   REQUIRE_IDEMPOTENCY  1 时 precheck 强制要求 power_idempotency_record
#   CONNECT_TIMEOUT      PostgreSQL 连接超时（默认 3s）
#   SKIP_PRECHECK       1 时跳过运行时画像 precheck
#   M15_SQL / V001_SQL / U001_SQL  步骤 SQL 路径覆盖
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
REQUIRE_IDEMPOTENCY="${REQUIRE_IDEMPOTENCY:-0}"
SKIP_PRECHECK="${SKIP_PRECHECK:-0}"
export PGCONNECT_TIMEOUT="${CONNECT_TIMEOUT}"

DEFAULT_V001_SQL="${REPO_ROOT}/.doc/技术设计/电力运维云平台/assets/td005-migration/V001__power_model_version_binding_audit_outbox.sql"
DEFAULT_U001_SQL="${REPO_ROOT}/.doc/技术设计/电力运维云平台/assets/td005-migration/U001__power_model_version_binding_audit_outbox.sql"
M15_SQL="${M15_SQL:-${SCRIPT_DIR}/steps/M15__product_tenant_unique_concurrently.sql}"
V001_SQL="${V001_SQL:-${DEFAULT_V001_SQL}}"
U001_SQL="${U001_SQL:-${DEFAULT_U001_SQL}}"
CHECK_SQL="${SCRIPT_DIR}/check_ddl_comments.sql"
PREPROFILE_SQL="${SCRIPT_DIR}/precheck_runtime_profile.sql"

while [ $# -gt 0 ]; do
    case "$1" in
        --db) PG_DB="$2"; shift 2 ;;
        --approval) APPROVAL="$2"; shift 2 ;;
        --skip-precheck) SKIP_PRECHECK=1; shift ;;
        --yes) YES=1; shift ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

log() { echo "[td005-migration][$(date +%FT%T%z)] $*"; }
fail() { echo "[td005-migration][ERROR] $*" >&2; exit 1; }
fail_validation() { echo "[td005-migration][VALIDATION] $*" >&2; exit 2; }

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

backup_before_apply() {
    [ "${MODE}" = "apply" ] || return 0
    [ -n "${BACKUP_DIR:-}" ] || return 0
    mkdir -p "${BACKUP_DIR}"
    local backup_file="${BACKUP_DIR}/${PG_DB}_$(date +%Y%m%d_%H%M%S).sql"
    if [ -n "${PG_CONTAINER:-}" ]; then
        docker exec -e "PGPASSWORD=${PG_PASSWORD}" "${PG_CONTAINER}" \
            pg_dump -U "${PG_USER}" -d "${PG_DB}" > "${backup_file}"
    else
        PGPASSWORD="${PG_PASSWORD}" pg_dump -h "${PG_HOST}" -p "${PG_PORT}" \
            -U "${PG_USER}" -d "${PG_DB}" > "${backup_file}"
    fi
    log "backup written: ${backup_file}"
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
SQL
}

emit_apply_driver() {
    local m15_sha v001_sha
    m15_sha="$(sha256_file "${M15_SQL}")"
    v001_sha="$(sha256_file "${V001_SQL}")"
    cat <<SQL
\set ON_ERROR_STOP on
SELECT pg_advisory_lock(:'lock_key'::bigint);
SQL
    history_bootstrap
    cat <<SQL
SELECT EXISTS (
    SELECT 1 FROM public.schema_migration_history
    WHERE migration_id = 'M15' AND status = 'SUCCEEDED' AND script_sha256 <> :'m15_sha'
) AS m15_mismatch \gset
\if :m15_mismatch
\echo HASH_MISMATCH M15
SELECT 1/0 AS migration_hash_mismatch;
\endif
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
SELECT NOT EXISTS (
    SELECT 1 FROM public.schema_migration_history
    WHERE migration_id = 'M15' AND status = 'SUCCEEDED' AND script_sha256 = :'m15_sha'
) AS need_run \gset
\if :need_run
SELECT to_regclass('public.uq_product_tenant_identification') IS NULL AS need_index \gset
\if :need_index
SQL
    cat "${M15_SQL}"
    cat <<SQL
\endif
INSERT INTO public.schema_migration_history
    (migration_id, script_sha256, status, started_at, finished_at, executed_by, evidence)
VALUES
    ('M15', :'m15_sha', 'SUCCEEDED', now(), now(), :'executor',
     json_build_object('approval', :'approval', 'target', :'db', 'note', 'index-or-step-done')::jsonb);
\endif
SELECT EXISTS (
    SELECT 1 FROM public.schema_migration_history
    WHERE migration_id = 'V001' AND status = 'SUCCEEDED' AND script_sha256 <> :'v001_sha'
) AS v001_mismatch \gset
\if :v001_mismatch
\echo HASH_MISMATCH V001
SELECT 1/0 AS migration_hash_mismatch;
\endif
SELECT NOT EXISTS (
    SELECT 1 FROM public.schema_migration_history
    WHERE migration_id = 'V001' AND status = 'SUCCEEDED' AND script_sha256 = :'v001_sha'
) AS need_run \gset
\if :need_run
BEGIN;
SQL
    cat "${V001_SQL}"
    cat <<SQL
COMMIT;
INSERT INTO public.schema_migration_history
    (migration_id, script_sha256, status, started_at, finished_at, executed_by, evidence)
VALUES
    ('V001', :'v001_sha', 'SUCCEEDED', now(), now(), :'executor',
     json_build_object('approval', :'approval', 'target', :'db')::jsonb);
\endif
SELECT pg_advisory_unlock(:'lock_key'::bigint);
SQL
}

emit_uninstall_driver() {
    local u001_sha
    u001_sha="$(sha256_file "${U001_SQL}")"
    cat <<SQL
\set ON_ERROR_STOP on
SELECT pg_advisory_lock(:'lock_key'::bigint);
SQL
    history_bootstrap
    cat <<SQL
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
) AS need_run \gset
\if :need_run
SQL
    cat "${U001_SQL}"
    cat <<SQL
INSERT INTO public.schema_migration_history
    (migration_id, script_sha256, status, started_at, finished_at, executed_by, evidence)
VALUES
    ('U001', :'u001_sha', 'SUCCEEDED', now(), now(), :'executor',
     json_build_object('approval', :'approval', 'target', :'db')::jsonb);
\endif
SELECT pg_advisory_unlock(:'lock_key'::bigint);
SQL
}

case "${MODE}" in
    dry-run)
        log "dry-run target=${PG_DB}"
        log "M15 sha256=$(sha256_file "${M15_SQL}")"
        log "V001 sha256=$(sha256_file "${V001_SQL}")"
        log "U001 sha256=$(sha256_file "${U001_SQL}")"
        log "steps: M15 (concurrent) -> V001 (transaction) -> U001 (uninstall only)"
        exit 0
        ;;
    apply)
        require_approval
        run_precheck
        backup_before_apply
        TMP_SQL="$(mktemp)"
        emit_apply_driver > "${TMP_SQL}"
        # shellcheck disable=SC2046
        $(psql_base) \
            -v lock_key="${LOCK_KEY}" \
            -v m15_sha="$(sha256_file "${M15_SQL}")" \
            -v v001_sha="$(sha256_file "${V001_SQL}")" \
            -v executor="${PG_USER:-runner}" \
            -v approval="${APPROVAL}" \
            -v db="${PG_DB}" < "${TMP_SQL}"
        rc=$?
        rm -f "${TMP_SQL}"
        [ $rc -eq 0 ] || fail "apply failed (exit=${rc})"
        log "apply SUCCEEDED target=${PG_DB}"
        ;;
    uninstall)
        require_approval
        run_precheck
        backup_before_apply
        TMP_SQL="$(mktemp)"
        emit_uninstall_driver > "${TMP_SQL}"
        # shellcheck disable=SC2046
        $(psql_base) \
            -v lock_key="${LOCK_KEY}" \
            -v u001_sha="$(sha256_file "${U001_SQL}")" \
            -v executor="${PG_USER:-runner}" \
            -v approval="${APPROVAL}" \
            -v db="${PG_DB}" < "${TMP_SQL}"
        rc=$?
        rm -f "${TMP_SQL}"
        [ $rc -eq 0 ] || fail "uninstall failed (exit=${rc})"
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
        log "MIG-009 PASS: all V001 tables/columns have Chinese comments"
        ;;
    *)
        echo "usage: $0 dry-run|apply|uninstall|check-comments" >&2
        exit 2
        ;;
esac
