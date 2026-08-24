#!/usr/bin/env bash
# LC02-07 §20.6：V009 隔离真实 PostgreSQL 合同
# 只创建本脚本唯一命名的临时数据库；禁止把业务库或共享库作为目标。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${MIGRATION_DIR}/../../.." && pwd)"
ASSET_DIR="${REPO_ROOT}/.doc/技术设计/电力运维云平台/assets/td005-migration"
RUNNER="${MIGRATION_DIR}/td005_migration.sh"
V008="${ASSET_DIR}/V008__iot_sink_telemetry_inbox.sql"
V010="${ASSET_DIR}/V010__telemetry_quality.sql"
V009="${ASSET_DIR}/V009__telemetry_inbox_product_identity.sql"
U009="${ASSET_DIR}/U009__telemetry_inbox_product_identity.sql"
FULL_DUMP="${REPO_ROOT}/.scripts/postgresql/iot-device10.sql"

EXPECTED_V008_SHA="693c0473386048567886b382c8c984ab98a267b7e7ce8659307b0d7395048469"
EXPECTED_V010_SHA="08d809a4453e1e0efd16f29522f0682e3b9a10b20df4f04be4a93f7d200d6662"
EXPECTED_PRECHECK_SHA="ac382af6d06f342b34a21285a61ff6e244f556871e407b155747e3c215aa8cdd"
EXPECTED_INIT_SHA="abfa13d48affbb7e7174d3d9e1738733a39d451868343c35aa30c2e7a3e87edd"
EXPECTED_COMMENT="经 MQTT Topic、认证主体与载荷设备身份校验后持久化的产品路由标识；禁止由站点或属性推断"

PG_CONTAINER="${PG_CONTAINER:-}"
PG_USER="${PG_USER:-}"
PG_PASSWORD="${PG_PASSWORD:-}"
DB_PREFIX="${LC02_V009_DB_PREFIX:-}"

fail() {
    echo "[lc02-v009][FAIL] $*" >&2
    exit 1
}

PASS_COUNT=0
pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "[lc02-v009][PASS] $*"
}

require_tool() {
    command -v "$1" >/dev/null 2>&1 || fail "required tool missing: $1"
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print tolower($1)}'
    else
        shasum -a 256 "$1" | awk '{print tolower($1)}'
    fi
}

[ "${LC02_V009_PG_ENABLED:-false}" = "true" ] \
    || fail "LC02_V009_PG_ENABLED=true is required"
[ -n "${PG_CONTAINER}" ] || fail "PG_CONTAINER is required"
[ -n "${PG_USER}" ] || fail "PG_USER is required"
[ -n "${PG_PASSWORD}" ] || fail "PG_PASSWORD is required"
[ -n "${DB_PREFIX}" ] || fail "LC02_V009_DB_PREFIX is required"
[[ "${DB_PREFIX}" =~ ^[a-z][a-z0-9_]{2,23}$ ]] \
    || fail "LC02_V009_DB_PREFIX must match ^[a-z][a-z0-9_]{2,23}$"

for tool in docker sed grep cmp awk mktemp date; do
    require_tool "${tool}"
done
if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
    fail "sha256sum or shasum is required"
fi

for asset in "${RUNNER}" "${V008}" "${V010}" "${V009}" "${U009}" "${FULL_DUMP}"; do
    [ -f "${asset}" ] || fail "required asset missing: ${asset}"
done

TOKEN="$(date -u +%Y%m%d%H%M%S)_$$"
DB_DEP="${DB_PREFIX}_dep_${TOKEN}"
DB_MIG="${DB_PREFIX}_mig_${TOKEN}"
DB_PRE="${DB_PREFIX}_pre_${TOKEN}"
DB_BASE="${DB_PREFIX}_base_${TOKEN}"
DB_UNON="${DB_PREFIX}_unon_${TOKEN}"
DB_UDEP="${DB_PREFIX}_udep_${TOKEN}"
DATABASES=("${DB_DEP}" "${DB_MIG}" "${DB_PRE}" "${DB_BASE}" "${DB_UNON}" "${DB_UDEP}")

for db in "${DATABASES[@]}"; do
    [ "${db}" != "iot-device20" ] || fail "protected target database selected"
    [ "${db}" != "postgres" ] || fail "postgres maintenance database selected"
    [ "${#db}" -le 63 ] || fail "temporary database name too long: ${db}"
done

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/lc02-v009.XXXXXX")"
BACKUP_ROOT="${TMP_ROOT}/backups"
mkdir -p "${BACKUP_ROOT}"

pg_admin() {
    docker exec -e "PGPASSWORD=${PG_PASSWORD}" "${PG_CONTAINER}" \
        psql -X -v ON_ERROR_STOP=1 -U "${PG_USER}" -d postgres -Atqc "$1"
}

pg_query() {
    local db="$1" sql="$2"
    docker exec -e "PGPASSWORD=${PG_PASSWORD}" "${PG_CONTAINER}" \
        psql -X -v ON_ERROR_STOP=1 -U "${PG_USER}" -d "${db}" -Atqc "${sql}"
}

pg_file() {
    local db="$1" file="$2"
    docker exec -i -e "PGPASSWORD=${PG_PASSWORD}" "${PG_CONTAINER}" \
        psql -X -v ON_ERROR_STOP=1 -U "${PG_USER}" -d "${db}" < "${file}"
}

cleanup_databases() {
    local db
    for db in "${DATABASES[@]}"; do
        pg_admin "DROP DATABASE IF EXISTS \"${db}\" WITH (FORCE);" >/dev/null 2>&1 || true
    done
}

cleanup() {
    set +e
    cleanup_databases
    if [ -n "${TMP_ROOT:-}" ] && [ -d "${TMP_ROOT}" ]; then
        rm -rf -- "${TMP_ROOT}"
    fi
}
trap cleanup EXIT INT TERM

prefix_count() {
    pg_admin "SELECT count(*) FROM pg_database WHERE datname LIKE '${DB_PREFIX}\\_%' ESCAPE '\\';"
}

create_db() {
    pg_admin "CREATE DATABASE \"$1\";" >/dev/null
}

assert_eq() {
    local expected="$1" actual="$2" label="$3"
    [ "${actual}" = "${expected}" ] \
        || fail "${label}: expected=${expected}, actual=${actual}"
    pass "${label}"
}

assert_contains() {
    local text="$1" marker="$2" label="$3"
    grep -Fq -- "${marker}" <<<"${text}" || fail "${label}: marker missing: ${marker}"
    pass "${label}"
}

LAST_OUTPUT=""
LAST_RC=0
capture_apply() {
    local db="$1" step="$2" override="${3:-}" backup_dir="${BACKUP_ROOT}/${db}"
    local -a env_args
    mkdir -p "${backup_dir}"
    env_args=(env
        "PG_CONTAINER=${PG_CONTAINER}"
        "PG_USER=${PG_USER}"
        "PG_PASSWORD=${PG_PASSWORD}"
        "PG_DB=${db}"
        "BACKUP_DIR=${backup_dir}"
        "APPROVAL=LC02-07-CONTRACT"
        "SKIP_PRECHECK=1"
        "RETRY_MAX=1")
    [ -z "${override}" ] || env_args+=("V009_SQL=${override}")
    set +e
    LAST_OUTPUT="$("${env_args[@]}" bash "${RUNNER}" apply \
        --db "${db}" --step "${step}" --approval LC02-07-CONTRACT --yes 2>&1)"
    LAST_RC=$?
    set -e
}

capture_check_comments() {
    local db="$1"
    set +e
    LAST_OUTPUT="$(env PG_CONTAINER="${PG_CONTAINER}" PG_USER="${PG_USER}" \
        PG_PASSWORD="${PG_PASSWORD}" PG_DB="${db}" \
        bash "${RUNNER}" check-comments --db "${db}" 2>&1)"
    LAST_RC=$?
    set -e
}

capture_sql_file() {
    local db="$1" file="$2"
    set +e
    LAST_OUTPUT="$(pg_file "${db}" "${file}" 2>&1)"
    LAST_RC=$?
    set -e
}

column_count() {
    pg_query "$1" "SELECT count(*) FROM information_schema.columns WHERE table_schema='iot_sink' AND table_name='telemetry_inbox' AND column_name='product_identification';"
}

history_value() {
    pg_query "$1" "SELECT COALESCE(script_sha256::text, '') FROM public.schema_migration_history WHERE migration_id='$2';"
}

schema_dump() {
    local db="$1" output="$2"
    docker exec -e "PGPASSWORD=${PG_PASSWORD}" "${PG_CONTAINER}" \
        pg_dump -U "${PG_USER}" -d "${db}" --schema-only --schema=iot_sink \
        --no-owner --no-privileges \
        | sed -E '/^\\(un)?restrict /d; /^-- Dumped from database version /d; /^-- Dumped by pg_dump version /d' \
        > "${output}"
}

history_schema_dump() {
    local db="$1" output="$2"
    docker exec -e "PGPASSWORD=${PG_PASSWORD}" "${PG_CONTAINER}" \
        pg_dump -U "${PG_USER}" -d "${db}" --schema-only \
        --table=public.schema_migration_history --no-owner --no-privileges \
        | sed -E '/^\\(un)?restrict /d; /^-- Dumped from database version /d; /^-- Dumped by pg_dump version /d' \
        > "${output}"
}

assert_eq "0" "$(prefix_count)" "start has no LC02 V009 database residue"
assert_eq "${EXPECTED_V008_SHA}" "$(sha256_file "${V008}")" "V008 protected hash"
assert_eq "${EXPECTED_V010_SHA}" "$(sha256_file "${V010}")" "V010 protected hash"
assert_eq "${EXPECTED_PRECHECK_SHA}" "$(sha256_file "${MIGRATION_DIR}/precheck_runtime_profile.sql")" "precheck protected hash"
assert_eq "${EXPECTED_INIT_SHA}" "$(sha256_file "${REPO_ROOT}/.scripts/docker/init-databases.sh")" "init-databases protected hash"

V009_SHA="$(sha256_file "${V009}")"
[ "${#V009_SHA}" -eq 64 ] || fail "invalid V009 SHA-256"
pass "V009 SHA-256=${V009_SHA}"

V009_EFFECTIVE="$(sed '/^[[:space:]]*--/d' "${V009}")"
if grep -Eqi '(^|[^A-Z_])(INSERT|UPDATE|DELETE|DEFAULT|NOT[[:space:]]+NULL|CREATE[[:space:]]+(UNIQUE[[:space:]]+)?INDEX|ADD[[:space:]]+CONSTRAINT|CASCADE|IF[[:space:]]+NOT[[:space:]]+EXISTS)([^A-Z_]|$)' <<<"${V009_EFFECTIVE}"; then
    fail "V009 contains forbidden DML/default/constraint/index/adoption token"
fi
pass "V009 static additive-only gate"

U009_EFFECTIVE="$(sed '/^[[:space:]]*--/d' "${U009}")"
if grep -Eqi 'CASCADE|(^|[^A-Z_])(INSERT|UPDATE|DELETE)[^;]*schema_migration_history' <<<"${U009_EFFECTIVE}"; then
    fail "U009 contains CASCADE or migration history DML"
fi
pass "U009 static RESTRICT/history gate"

DRY_RUN="$(bash "${RUNNER}" dry-run --db iot-device20)"
assert_contains "${DRY_RUN}" "V009 sha256=${V009_SHA}" "dry-run reports V009 hash"
assert_contains "${DRY_RUN}" "V008 (txn) -> V010 (txn) -> V009 (txn)" "dry-run reports frozen order"

create_db "${DB_DEP}"
capture_apply "${DB_DEP}" V009
assert_eq "2" "${LAST_RC}" "fresh V009 dependency failure exit"
assert_contains "${LAST_OUTPUT}" "DEPENDENCY_NOT_SATISFIED V009" "fresh V009 dependency marker"
assert_eq "0" "$(column_count "${DB_DEP}")" "fresh dependency failure has no V009 column"
assert_eq "0" "$(pg_query "${DB_DEP}" "SELECT count(*) FROM public.schema_migration_history WHERE migration_id='V009';")" "fresh dependency failure has no V009 history"

capture_apply "${DB_DEP}" V008
assert_eq "0" "${LAST_RC}" "dependency database V008 apply"
assert_contains "${LAST_OUTPUT}" "STEP_DONE V008" "dependency database V008 marker"
capture_apply "${DB_DEP}" V009
assert_eq "2" "${LAST_RC}" "V008-only V009 dependency failure exit"
assert_contains "${LAST_OUTPUT}" "DEPENDENCY_NOT_SATISFIED V009" "V008-only missing V010 marker"
assert_eq "0" "$(column_count "${DB_DEP}")" "V008-only dependency failure has no V009 column"

create_db "${DB_MIG}"
capture_apply "${DB_MIG}" V008
assert_eq "0" "${LAST_RC}" "migration database V008 apply"
capture_apply "${DB_MIG}" V010
assert_eq "0" "${LAST_RC}" "migration database V010 apply"

pg_query "${DB_MIG}" "
INSERT INTO iot_sink.telemetry_inbox
    (message_id,message_id_wire,request_id,tenant_id,site_code,device_identification,
     property_code,payload,content_sha256,collected_at_ms,sequence_no,source,config_version,
     projection_state,projection_attempts,projection_lease_until,next_projection_at_ms,
     projected_at_ms,last_projection_error,received_at_ms,updated_at_ms)
VALUES
    ('lc02-v009-message','lc02-v009-wire','lc02-v009-request',987654321,
     'lc02-v009-site','lc02-v009-device','voltage',decode('7b7d','hex'),repeat('a',64),
     1700000000001,19,'modbus-rtu',7,'PROJECTING',3,1700000009999,
     1700000010000,NULL,'sanitized-error',1700000000100,1700000000200);" >/dev/null

ROW_BEFORE="$(pg_query "${DB_MIG}" "SELECT md5(to_jsonb(t)::text) FROM iot_sink.telemetry_inbox t WHERE message_id='lc02-v009-message';")"
CONSTRAINTS_BEFORE="$(pg_query "${DB_MIG}" "SELECT md5(COALESCE(string_agg(conname||':'||pg_get_constraintdef(oid), E'\\n' ORDER BY conname),'')) FROM pg_constraint WHERE conrelid='iot_sink.telemetry_inbox'::regclass;")"
INDEXES_BEFORE="$(pg_query "${DB_MIG}" "SELECT md5(COALESCE(string_agg(indexname||':'||indexdef, E'\\n' ORDER BY indexname),'')) FROM pg_indexes WHERE schemaname='iot_sink' AND tablename='telemetry_inbox';")"

capture_apply "${DB_MIG}" V009
assert_eq "0" "${LAST_RC}" "V009 apply succeeds after V008 and V010"
assert_contains "${LAST_OUTPUT}" "STEP_DONE V009" "V009 success marker"
assert_eq "character varying|128|YES|<NULL>" "$(pg_query "${DB_MIG}" "SELECT data_type||'|'||character_maximum_length||'|'||is_nullable||'|'||COALESCE(column_default,'<NULL>') FROM information_schema.columns WHERE table_schema='iot_sink' AND table_name='telemetry_inbox' AND column_name='product_identification';")" "V009 column contract"
assert_eq "${EXPECTED_COMMENT}" "$(pg_query "${DB_MIG}" "SELECT col_description('iot_sink.telemetry_inbox'::regclass, attnum) FROM pg_attribute WHERE attrelid='iot_sink.telemetry_inbox'::regclass AND attname='product_identification';")" "V009 Chinese comment"
assert_eq "1" "$(pg_query "${DB_MIG}" "SELECT count(*) FROM iot_sink.telemetry_inbox WHERE message_id='lc02-v009-message' AND product_identification IS NULL;")" "existing row receives NULL only"
assert_eq "${ROW_BEFORE}" "$(pg_query "${DB_MIG}" "SELECT md5((to_jsonb(t)-'product_identification')::text) FROM iot_sink.telemetry_inbox t WHERE message_id='lc02-v009-message';")" "old Inbox columns unchanged"
assert_eq "${CONSTRAINTS_BEFORE}" "$(pg_query "${DB_MIG}" "SELECT md5(COALESCE(string_agg(conname||':'||pg_get_constraintdef(oid), E'\\n' ORDER BY conname),'')) FROM pg_constraint WHERE conrelid='iot_sink.telemetry_inbox'::regclass;")" "Inbox constraints unchanged"
assert_eq "${INDEXES_BEFORE}" "$(pg_query "${DB_MIG}" "SELECT md5(COALESCE(string_agg(indexname||':'||indexdef, E'\\n' ORDER BY indexname),'')) FROM pg_indexes WHERE schemaname='iot_sink' AND tablename='telemetry_inbox';")" "Inbox indexes unchanged"
assert_eq "${V009_SHA}" "$(history_value "${DB_MIG}" V009)" "V009 history exact hash"
assert_eq "${EXPECTED_V008_SHA}" "$(history_value "${DB_MIG}" V008)" "V008 history preserved"
assert_eq "${EXPECTED_V010_SHA}" "$(history_value "${DB_MIG}" V010)" "V010 history preserved"

capture_apply "${DB_MIG}" V009
assert_eq "0" "${LAST_RC}" "repeat V009 exits successfully"
assert_contains "${LAST_OUTPUT}" "STEP_SKIPPED V009" "repeat V009 is skipped"

DRIFT_V009="${TMP_ROOT}/V009_drift.sql"
cp "${V009}" "${DRIFT_V009}"
printf '\n-- contract drift\n' >> "${DRIFT_V009}"
capture_apply "${DB_MIG}" V009 "${DRIFT_V009}"
assert_eq "2" "${LAST_RC}" "V009 hash drift exit"
assert_contains "${LAST_OUTPUT}" "HASH_MISMATCH V009" "V009 hash drift marker"
assert_eq "${V009_SHA}" "$(history_value "${DB_MIG}" V009)" "hash drift does not rewrite history"
assert_eq "1" "$(column_count "${DB_MIG}")" "hash drift does not change column"

capture_check_comments "${DB_MIG}"
assert_eq "0" "${LAST_RC}" "COMMENT gate positive exit"
assert_contains "${LAST_OUTPUT}" "MIG-009 PASS" "COMMENT gate positive marker"
pg_query "${DB_MIG}" "COMMENT ON COLUMN iot_sink.telemetry_inbox.product_identification IS NULL;" >/dev/null
capture_check_comments "${DB_MIG}"
assert_eq "2" "${LAST_RC}" "COMMENT gate negative exit"
assert_contains "${LAST_OUTPUT}" "iot_sink.telemetry_inbox|product_identification" "COMMENT gate identifies V009 column"
pg_query "${DB_MIG}" "COMMENT ON COLUMN iot_sink.telemetry_inbox.product_identification IS '${EXPECTED_COMMENT}';" >/dev/null
capture_check_comments "${DB_MIG}"
assert_eq "0" "${LAST_RC}" "COMMENT gate passes after restore"

create_db "${DB_PRE}"
capture_apply "${DB_PRE}" V008
assert_eq "0" "${LAST_RC}" "preexisting-column database V008 apply"
capture_apply "${DB_PRE}" V010
assert_eq "0" "${LAST_RC}" "preexisting-column database V010 apply"
pg_query "${DB_PRE}" "ALTER TABLE iot_sink.telemetry_inbox ADD COLUMN product_identification VARCHAR(128);" >/dev/null
capture_apply "${DB_PRE}" V009
assert_eq "2" "${LAST_RC}" "preexisting column rejection exit"
assert_contains "${LAST_OUTPUT}" "V009_PREEXISTING_COLUMN_WITHOUT_HISTORY" "preexisting column stable marker"
assert_eq "1" "$(column_count "${DB_PRE}")" "preexisting column is not adopted or dropped"
assert_eq "0" "$(pg_query "${DB_PRE}" "SELECT count(*) FROM public.schema_migration_history WHERE migration_id='V009';")" "preexisting column has no adopted history"

SANITIZED_DUMP="${TMP_ROOT}/iot-device10-${DB_BASE}.sql"
sed \
    -e "s/DROP DATABASE IF EXISTS \"iot-device20\"/DROP DATABASE IF EXISTS \"${DB_BASE}\"/" \
    -e "s/CREATE DATABASE \"iot-device20\"/CREATE DATABASE \"${DB_BASE}\"/" \
    -e "s/dbname='iot-device20'/dbname='${DB_BASE}'/" \
    "${FULL_DUMP}" > "${SANITIZED_DUMP}"
grep -Fq "CREATE DATABASE \"${DB_BASE}\"" "${SANITIZED_DUMP}" \
    || fail "sanitized full dump does not target baseline database"
if grep -Eq '^(DROP DATABASE IF EXISTS "iot-device20"|CREATE DATABASE "iot-device20"|\\connect .*iot-device20)' "${SANITIZED_DUMP}"; then
    fail "sanitized full dump still contains protected database command"
fi
capture_sql_file postgres "${SANITIZED_DUMP}"
assert_eq "0" "${LAST_RC}" "full install baseline restores into isolated database"
assert_eq "3" "$(pg_query "${DB_BASE}" "SELECT count(*) FROM public.schema_migration_history;")" "full baseline seeds exactly three migration rows"
assert_eq "V008:${EXPECTED_V008_SHA},V009:${V009_SHA},V010:${EXPECTED_V010_SHA}" "$(pg_query "${DB_BASE}" "SELECT string_agg(migration_id||':'||script_sha256,',' ORDER BY migration_id) FROM public.schema_migration_history;")" "full baseline seed hashes"
assert_eq "1" "$(pg_query "${DB_BASE}" "SELECT count(*) FROM pg_constraint WHERE conrelid='public.schema_migration_history'::regclass AND contype='p';")" "full baseline history primary key"
assert_eq "1" "$(pg_query "${DB_BASE}" "SELECT count(*) FROM pg_constraint WHERE conrelid='iot_sink.telemetry_inbox'::regclass AND contype='p';")" "full baseline Inbox primary key"

for step in V008 V010 V009; do
    capture_apply "${DB_BASE}" "${step}"
    assert_eq "0" "${LAST_RC}" "full baseline ${step} runner exit"
    assert_contains "${LAST_OUTPUT}" "STEP_SKIPPED ${step}" "full baseline ${step} runner skip"
done

MIG_SCHEMA="${TMP_ROOT}/migration-iot-sink.sql"
BASE_SCHEMA="${TMP_ROOT}/baseline-iot-sink.sql"
schema_dump "${DB_MIG}" "${MIG_SCHEMA}"
schema_dump "${DB_BASE}" "${BASE_SCHEMA}"
if ! cmp -s "${MIG_SCHEMA}" "${BASE_SCHEMA}"; then
    diff -u "${MIG_SCHEMA}" "${BASE_SCHEMA}" >&2 || true
    fail "full baseline iot_sink schema differs from V008->V010->V009"
fi
pass "full baseline iot_sink schema equals migration-built schema"

MIG_HISTORY_SCHEMA="${TMP_ROOT}/migration-history.sql"
BASE_HISTORY_SCHEMA="${TMP_ROOT}/baseline-history.sql"
history_schema_dump "${DB_MIG}" "${MIG_HISTORY_SCHEMA}"
history_schema_dump "${DB_BASE}" "${BASE_HISTORY_SCHEMA}"
if ! cmp -s "${MIG_HISTORY_SCHEMA}" "${BASE_HISTORY_SCHEMA}"; then
    diff -u "${MIG_HISTORY_SCHEMA}" "${BASE_HISTORY_SCHEMA}" >&2 || true
    fail "full baseline migration history schema differs from runner bootstrap"
fi
pass "full baseline migration history schema equals runner bootstrap"

pg_admin "CREATE DATABASE \"${DB_UNON}\" WITH TEMPLATE \"${DB_MIG}\";" >/dev/null
pg_admin "CREATE DATABASE \"${DB_UDEP}\" WITH TEMPLATE \"${DB_MIG}\";" >/dev/null

pg_query "${DB_UNON}" "UPDATE iot_sink.telemetry_inbox SET product_identification='product-non-null' WHERE message_id='lc02-v009-message';" >/dev/null
UNON_HISTORY_BEFORE="$(history_value "${DB_UNON}" V009)"
capture_sql_file "${DB_UNON}" "${U009}"
[ "${LAST_RC}" -ne 0 ] || fail "U009 must reject non-null data"
assert_contains "${LAST_OUTPUT}" "U009_NON_NULL_DATA_PRESENT" "U009 non-null refusal marker"
assert_eq "1" "$(column_count "${DB_UNON}")" "U009 non-null refusal keeps column"
assert_eq "product-non-null" "$(pg_query "${DB_UNON}" "SELECT product_identification FROM iot_sink.telemetry_inbox WHERE message_id='lc02-v009-message';")" "U009 non-null refusal keeps data"
assert_eq "${UNON_HISTORY_BEFORE}" "$(history_value "${DB_UNON}" V009)" "U009 non-null refusal keeps history"

pg_query "${DB_UDEP}" "CREATE VIEW iot_sink.v_lc02_v009_dependency AS SELECT product_identification FROM iot_sink.telemetry_inbox;" >/dev/null
UDEP_HISTORY_BEFORE="$(history_value "${DB_UDEP}" V009)"
capture_sql_file "${DB_UDEP}" "${U009}"
[ "${LAST_RC}" -ne 0 ] || fail "U009 must reject dependent view"
assert_eq "1" "$(column_count "${DB_UDEP}")" "U009 RESTRICT failure keeps column"
assert_eq "1" "$(pg_query "${DB_UDEP}" "SELECT count(*) FROM pg_views WHERE schemaname='iot_sink' AND viewname='v_lc02_v009_dependency';")" "U009 RESTRICT failure keeps dependency"
assert_eq "${UDEP_HISTORY_BEFORE}" "$(history_value "${DB_UDEP}" V009)" "U009 RESTRICT failure keeps history"

MIG_HISTORY_BEFORE="$(history_value "${DB_MIG}" V009)"
capture_sql_file "${DB_MIG}" "${U009}"
assert_eq "0" "${LAST_RC}" "U009 all-NULL rehearsal succeeds"
assert_eq "0" "$(column_count "${DB_MIG}")" "U009 drops only product identity column"
assert_eq "${MIG_HISTORY_BEFORE}" "$(history_value "${DB_MIG}" V009)" "U009 success leaves history unchanged"
assert_eq "1" "$(pg_query "${DB_MIG}" "SELECT count(*) FROM iot_sink.telemetry_inbox WHERE message_id='lc02-v009-message';")" "U009 success keeps Inbox row"

BACKUP_COUNT="$(find "${BACKUP_ROOT}" -type f -name '*.sql' -size +0c | wc -l | tr -d '[:space:]')"
[ "${BACKUP_COUNT}" -gt 0 ] || fail "runner produced no non-empty backup"
pass "runner produced ${BACKUP_COUNT} non-empty repository-external temporary backups"

assert_eq "${EXPECTED_V008_SHA}" "$(sha256_file "${V008}")" "V008 protected hash after contracts"
assert_eq "${EXPECTED_V010_SHA}" "$(sha256_file "${V010}")" "V010 protected hash after contracts"
assert_eq "${EXPECTED_PRECHECK_SHA}" "$(sha256_file "${MIGRATION_DIR}/precheck_runtime_profile.sql")" "precheck protected hash after contracts"
assert_eq "${EXPECTED_INIT_SHA}" "$(sha256_file "${REPO_ROOT}/.scripts/docker/init-databases.sh")" "init-databases protected hash after contracts"

cleanup_databases
assert_eq "0" "$(prefix_count)" "end has no LC02 V009 database residue"
rm -rf -- "${TMP_ROOT}"
TMP_ROOT=""
trap - EXIT INT TERM

echo "[lc02-v009] PostgreSQL version: $(docker exec -e "PGPASSWORD=${PG_PASSWORD}" "${PG_CONTAINER}" psql -X -U "${PG_USER}" -d postgres -Atqc 'SHOW server_version;')"
echo "[lc02-v009] V009_SHA256=${V009_SHA}"
echo "[lc02-v009] PASS_ASSERTIONS=${PASS_COUNT}"
echo "[lc02-v009] CLEANUP=temporary databases/backups removed"
