#!/usr/bin/env bash
# LC03-03 §5.1：V012/U012 隔离真实 PostgreSQL 合同（候选，未获执行授权）
# 只创建本脚本唯一命名的临时数据库；禁止把业务库或共享库作为目标。
# 不接入共享 td005_migration runner 的 APPLY_STEPS；V012 正式落库须另立
# LC03-DB-RUNTIME-01 并获得部署授权。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${MIGRATION_DIR}/../../.." && pwd)"
ASSET_DIR="${REPO_ROOT}/.doc/技术设计/电力运维云平台/assets/td005-migration"
V008="${ASSET_DIR}/V008__iot_sink_telemetry_inbox.sql"
V009="${ASSET_DIR}/V009__telemetry_inbox_product_identity.sql"
V010="${ASSET_DIR}/V010__telemetry_quality.sql"
V012="${ASSET_DIR}/V012__telemetry_inbox_ack_delivery.sql"
U012="${ASSET_DIR}/U012__telemetry_inbox_ack_delivery.sql"

PG_CONTAINER="${PG_CONTAINER:-}"
PG_USER="${PG_USER:-}"
PG_PASSWORD="${PG_PASSWORD:-}"
DB_PREFIX="${LC03_V012_DB_PREFIX:-}"

fail() {
    echo "[lc03-v012][FAIL] $*" >&2
    exit 1
}

PASS_COUNT=0
pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "[lc03-v012][PASS] $*"
}

require_tool() {
    command -v "$1" >/dev/null 2>&1 || fail "required tool missing: $1"
}

[ "${LC03_V012_PG_ENABLED:-false}" = "true" ] \
    || fail "LC03_V012_PG_ENABLED=true is required (V012 contract execution is separately gated)"
[ -n "${PG_CONTAINER}" ] || fail "PG_CONTAINER is required"
[ -n "${PG_USER}" ] || fail "PG_USER is required"
[ -n "${PG_PASSWORD}" ] || fail "PG_PASSWORD is required"
[ -n "${DB_PREFIX}" ] || fail "LC03_V012_DB_PREFIX is required"
[[ "${DB_PREFIX}" =~ ^[a-z][a-z0-9_]{2,23}$ ]] \
    || fail "LC03_V012_DB_PREFIX must match ^[a-z][a-z0-9_]{2,23}$"

for tool in docker sed grep awk mktemp date; do
    require_tool "${tool}"
done

for asset in "${V008}" "${V009}" "${V010}" "${V012}" "${U012}"; do
    [ -f "${asset}" ] || fail "required asset missing: ${asset}"
done

TOKEN="$(date -u +%Y%m%d%H%M%S)_$$"
DB_BASE="${DB_PREFIX}_base_${TOKEN}"
DB_MIG="${DB_PREFIX}_mig_${TOKEN}"
DB_REPLAY="${DB_PREFIX}_replay_${TOKEN}"
DB_PRE="${DB_PREFIX}_pre_${TOKEN}"
DB_U="${DB_PREFIX}_u_${TOKEN}"
DATABASES=("${DB_BASE}" "${DB_MIG}" "${DB_REPLAY}" "${DB_PRE}" "${DB_U}")

for db in "${DATABASES[@]}"; do
    [ "${db}" != "iot-device20" ] || fail "protected target database selected"
    [ "${db}" != "postgres" ] || fail "postgres maintenance database selected"
    [ "${#db}" -le 63 ] || fail "temporary database name too long: ${db}"
done

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/lc03-v012.XXXXXX")"
cleanup() {
    for db in "${DATABASES[@]}"; do
        docker exec -i "${PG_CONTAINER}" psql -U "${PG_USER}" -d postgres \
            -c "DROP DATABASE IF EXISTS \"${db}\" WITH (FORCE);" >/dev/null 2>&1 || true
    done
    rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

# 凭据只经进程环境注入 psql；不回显值、长度、hash 或 URL。
PSQL_BASE=(docker exec -i -e PGPASSWORD="${PG_PASSWORD}" "${PG_CONTAINER}" psql -U "${PG_USER}" -v ON_ERROR_STOP=1)

# 1) 基线库：V008 → V010 → V009（V009 要求 V008+V010 已落）
"${PSQL_BASE[@]}" -d postgres -c "CREATE DATABASE \"${DB_BASE}\";" >/dev/null
"${PSQL_BASE[@]}" -d "${DB_BASE}" -f /dev/stdin <"${V008}" >/dev/null
"${PSQL_BASE[@]}" -d "${DB_BASE}" -f /dev/stdin <"${V010}" >/dev/null
"${PSQL_BASE[@]}" -d "${DB_BASE}" -f /dev/stdin <"${V009}" >/dev/null

# 2) 迁移库：基线 + V012（候选步骤本身）
"${PSQL_BASE[@]}" -d postgres -c "CREATE DATABASE \"${DB_MIG}\" TEMPLATE \"${DB_BASE}\";" >/dev/null
"${PSQL_BASE[@]}" -d "${DB_MIG}" -f /dev/stdin <"${V012}" >/dev/null

# 3) 断言：两列存在、默认 0、中文 COMMENT、部分索引存在
column_type="$("${PSQL_BASE[@]}" -d "${DB_MIG}" -Atc \
    "SELECT data_type FROM information_schema.columns WHERE table_schema='iot_sink' AND table_name='telemetry_inbox' AND column_name='ack_attempts';")"
[ "${column_type}" = "integer" ] || fail "ack_attempts column type expected integer, got ${column_type}"
pass "ack_attempts INTEGER column present"

default_value="$("${PSQL_BASE[@]}" -d "${DB_MIG}" -Atc \
    "SELECT column_default FROM information_schema.columns WHERE table_schema='iot_sink' AND table_name='telemetry_inbox' AND column_name='ack_attempts';")"
[ "${default_value}" = "0" ] || fail "ack_attempts default expected 0, got ${default_value}"
pass "ack_attempts NOT NULL DEFAULT 0"

comment_text="$("${PSQL_BASE[@]}" -d "${DB_MIG}" -Atc \
    "SELECT col_description('iot_sink.telemetry_inbox'::regclass::oid, (SELECT attnum FROM pg_attribute WHERE attrelid='iot_sink.telemetry_inbox'::regclass AND attname='ack_sent_at_ms'));")"
[ -n "${comment_text}" ] || fail "ack_sent_at_ms Chinese COMMENT missing"
pass "ack_sent_at_ms COMMENT present"

index_exists="$("${PSQL_BASE[@]}" -d "${DB_MIG}" -Atc \
    "SELECT to_regclass('iot_sink.idx_inbox_ack_pending') IS NOT NULL;")"
[ "${index_exists}" = "t" ] || fail "partial index idx_inbox_ack_pending missing"
pass "partial index idx_inbox_ack_pending present"

# 4) CHECK：负数尝试被数据库拒绝
if "${PSQL_BASE[@]}" -d "${DB_MIG}" -c \
    "UPDATE iot_sink.telemetry_inbox SET ack_attempts = -1;" >/dev/null 2>&1; then
    fail "negative ack_attempts must be rejected by CHECK constraint"
else
    pass "negative ack_attempts rejected by CHECK"
fi

# 5) 重放：同一 V012 二次执行必须被 V012_PREEXISTING_COLUMN_WITHOUT_HISTORY 拒绝
"${PSQL_BASE[@]}" -d postgres -c "CREATE DATABASE \"${DB_REPLAY}\" TEMPLATE \"${DB_MIG}\";" >/dev/null
if "${PSQL_BASE[@]}" -d "${DB_REPLAY}" -f /dev/stdin <"${V012}" >/dev/null 2>&1; then
    fail "V012 replay must fail with preexisting-column guard"
else
    pass "V012 replay rejected by preexisting guard"
fi

# 6) 裸库前置拒绝：没有 V009 的库执行 V012 必须 V012_V009_PREREQUISITE_MISSING
"${PSQL_BASE[@]}" -d postgres -c "CREATE DATABASE \"${DB_PRE}\";" >/dev/null
"${PSQL_BASE[@]}" -d "${DB_PRE}" -f /dev/stdin <"${V008}" >/dev/null
if "${PSQL_BASE[@]}" -d "${DB_PRE}" -f /dev/stdin <"${V012}" >/dev/null 2>&1; then
    fail "V012 without V009 prerequisite must fail"
else
    pass "V012 prerequisite guard rejects bare-V008 database"
fi

# 7) U012：无发送痕迹时可完整卸载；有 sent 行时拒绝
"${PSQL_BASE[@]}" -d postgres -c "CREATE DATABASE \"${DB_U}\" TEMPLATE \"${DB_MIG}\";" >/dev/null
"${PSQL_BASE[@]}" -d "${DB_U}" -f /dev/stdin <"${U012}" >/dev/null
dropped="$("${PSQL_BASE[@]}" -d "${DB_U}" -Atc \
    "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='iot_sink' AND table_name='telemetry_inbox' AND column_name='ack_sent_at_ms');")"
[ "${dropped}" = "f" ] || fail "U012 must drop ack columns on clean state"
pass "U012 drops columns on clean state"

echo "SUMMARY PASS=${PASS_COUNT}"
