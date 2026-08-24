#!/usr/bin/env bash
# LC02-08 §21.6：Inbox 产品身份、历史 NULL 补全与同键并发隔离合同。
# 该脚本默认拒绝运行，并且只创建本次唯一前缀的临时 PostgreSQL 数据库。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${MIGRATION_DIR}/../../.." && pwd)"
ASSET_DIR="${REPO_ROOT}/.doc/技术设计/电力运维云平台/assets/td005-migration"
V008="${ASSET_DIR}/V008__iot_sink_telemetry_inbox.sql"
V010="${ASSET_DIR}/V010__telemetry_quality.sql"
V009="${ASSET_DIR}/V009__telemetry_inbox_product_identity.sql"

PG_ENABLED="${LC02_08_PG_ENABLED:-false}"
PG_CONTAINER="${PG_CONTAINER:-}"
PG_USER="${PG_USER:-}"
PG_PASSWORD="${PG_PASSWORD:-}"
DB_PREFIX="${LC02_08_DB_PREFIX:-}"
JDBC_HOST="${LC02_08_JDBC_HOST:-}"
JDBC_PORT="${LC02_08_JDBC_PORT:-}"
PREFIX_LENGTH="${#DB_PREFIX}"

EXPECTED_V009_SHA="48416787b7fc886cc3274be53f3a38c60f9a9dd93ca205e3f0311d54a8eafbde"

fail() {
    echo "[lc02-08][FAIL] $*" >&2
    exit 1
}

pass() {
    echo "[lc02-08][PASS] $*"
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

[ "${PG_ENABLED}" = "true" ] || fail "LC02_08_PG_ENABLED=true is required"
[ -n "${PG_CONTAINER}" ] || fail "PG_CONTAINER is required"
[ -n "${PG_USER}" ] || fail "PG_USER is required"
[ -n "${PG_PASSWORD}" ] || fail "PG_PASSWORD is required"
[ -n "${DB_PREFIX}" ] || fail "LC02_08_DB_PREFIX is required"
[ -n "${JDBC_HOST}" ] || fail "LC02_08_JDBC_HOST is required"
[ -n "${JDBC_PORT}" ] || fail "LC02_08_JDBC_PORT is required"
[[ "${DB_PREFIX}" =~ ^[a-z][a-z0-9_]{2,23}$ ]] \
    || fail "LC02_08_DB_PREFIX must match ^[a-z][a-z0-9_]{2,23}$"
[[ "${JDBC_PORT}" =~ ^[0-9]{1,5}$ ]] || fail "LC02_08_JDBC_PORT must be numeric"

for tool in docker mktemp date awk grep sed rg mvn; do
    require_tool "${tool}"
done
if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
    fail "sha256sum or shasum is required"
fi

for asset in "${V008}" "${V010}" "${V009}"; do
    [ -f "${asset}" ] || fail "required migration asset missing"
done

V009_SHA="$(sha256_file "${V009}")"
[ "${V009_SHA}" = "${EXPECTED_V009_SHA}" ] \
    || fail "V009 protected hash mismatch"
pass "V009 protected hash verified"

TOKEN="$(date -u +%Y%m%d%H%M%S)_$$"
MAIN_DB="${DB_PREFIX}_${TOKEN}"
MISSING_DB="${DB_PREFIX}_missing_${TOKEN}"
DATABASES=("${MAIN_DB}" "${MISSING_DB}")
for db in "${DATABASES[@]}"; do
    [ "${db}" != "iot-device20" ] || fail "protected target database selected"
    [ "${db}" != "postgres" ] || fail "postgres maintenance database selected"
    [ "${#db}" -le 63 ] || fail "temporary database name too long"
done

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/lc02-08-inbox.XXXXXX")"
MAVEN_REPORT="${TMP_ROOT}/maven-report.log"
LAST_OUTPUT="${TMP_ROOT}/last-output.log"

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

prefix_count() {
    pg_admin "SELECT count(*) FROM pg_database WHERE left(datname, ${PREFIX_LENGTH}+1) = '${DB_PREFIX}_';"
}

create_db() {
    pg_admin "CREATE DATABASE \"$1\";" >/dev/null
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
    local residual
    residual="$(prefix_count 2>/dev/null || echo UNKNOWN)"
    if [ "${residual}" = "0" ]; then
        echo "[lc02-08][PASS] temporary database prefix residue=0"
    else
        echo "[lc02-08][FAIL] temporary database prefix residue=${residual}" >&2
    fi
    rm -rf -- "${TMP_ROOT}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

[ "$(prefix_count)" = "0" ] || fail "temporary database prefix residue exists"
pass "temporary database prefix is clean before start"

create_db "${MAIN_DB}"
create_db "${MISSING_DB}"

# Main fixture is exactly V008 -> V010 -> V009.  The missing-column fixture is
# intentionally stopped after V010 for the fail-closed INSERT test.
pg_file "${MAIN_DB}" "${V008}" >/dev/null
pg_file "${MAIN_DB}" "${V010}" >/dev/null
pg_file "${MAIN_DB}" "${V009}" >/dev/null
pg_file "${MISSING_DB}" "${V008}" >/dev/null
pg_file "${MISSING_DB}" "${V010}" >/dev/null
pass "isolated fixtures built from V008 -> V010 -> V009 assets"

PG_VERSION="$(pg_query "${MAIN_DB}" "SELECT version();")"
[ -n "${PG_VERSION}" ] || fail "PostgreSQL version query returned empty"
echo "[lc02-08][INFO] PostgreSQL version=${PG_VERSION}"

MAIN_JDBC_URL="jdbc:postgresql://${JDBC_HOST}:${JDBC_PORT}/${MAIN_DB}"
MISSING_JDBC_URL="jdbc:postgresql://${JDBC_HOST}:${JDBC_PORT}/${MISSING_DB}"

set +e
env \
    LC02_08_PG_URL="${MAIN_JDBC_URL}" \
    LC02_08_PG_USERNAME="${PG_USER}" \
    LC02_08_PG_PASSWORD="${PG_PASSWORD}" \
    LC02_08_MISSING_V009_PG_URL="${MISSING_JDBC_URL}" \
    mvn -f "${REPO_ROOT}/DEVICE/pom.xml" \
      -pl iot-sink/iot-sink-biz -am test \
      -Dtest=InboxEnvelopeProductIdentityContractTest,CenterTelemetryIngressHandlerTest,JdbcTelemetryInboxContractTest,JdbcTelemetryInboxProductIdentityContractTest,JdbcTelemetryInboxFailureContractTest,InboxReceiveResultContractTest,TelemetryInboxAutoConfigurationTest,TelemetryStoreBatchContractTest \
      -DfailIfNoTests=false -Dmaven.test.skip=false -Dsurefire.printSummary=true \
      >"${MAVEN_REPORT}" 2>&1
MAVEN_RC=$?
set -e
[ "${MAVEN_RC}" -eq 0 ] || fail "Maven direct contract suite failed (rc=${MAVEN_RC})"

SUMMARY_LINES="$(grep -E 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' "${MAVEN_REPORT}" || true)"
[ -n "${SUMMARY_LINES}" ] || fail "Maven report contains no Surefire test summaries"
if grep -Eq 'Failures: [1-9][0-9]*|Errors: [1-9][0-9]*|Skipped: [1-9][0-9]*' <<<"${SUMMARY_LINES}"; then
    fail "Maven report contains non-zero Failures/Errors/Skipped"
fi
for test_class in \
    InboxEnvelopeProductIdentityContractTest \
    CenterTelemetryIngressHandlerTest \
    JdbcTelemetryInboxContractTest \
    JdbcTelemetryInboxProductIdentityContractTest \
    JdbcTelemetryInboxFailureContractTest \
    InboxReceiveResultContractTest \
    TelemetryInboxAutoConfigurationTest \
    TelemetryStoreBatchContractTest; do
    grep -Eq "Tests run: .*${test_class}" "${MAVEN_REPORT}" \
        || fail "Maven report did not prove ${test_class} executed"
done
pass "direct contracts: every specified class ran with Failures=0 Errors=0 Skipped=0"

# Compile the entire affected reactor, including TDengine contract fixtures,
# without executing the external TDengine integration class.
mvn -f "${REPO_ROOT}/DEVICE/pom.xml" -pl iot-sink/iot-sink-biz -am \
    test-compile -DskipTests '-Dmaven.test.skip=false' >"${LAST_OUTPUT}" 2>&1 \
    || fail "affected reactor compile failed"
pass "affected reactor test-compile passed"

rg -n "productIdentification|product_identification" \
    "${REPO_ROOT}/DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox" \
    "${REPO_ROOT}/DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox" \
    >/dev/null \
    || fail "product identity boundary scan found no references"
pass "product identity boundary scan passed"

git -C "${REPO_ROOT}" diff --check -- \
    DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/InboxEnvelope.java \
    DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInbox.java \
    DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/CenterTelemetryIngressHandler.java \
    DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/TelemetryEnvelopeDecoder.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/InboxEnvelopeProductIdentityContractTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxAutoConfigurationTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxContractTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxFailureContractTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxProductIdentityContractTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/route/CenterTelemetryIngressHandlerTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/store/TelemetryStoreBatchContractTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/store/tdengine/TDengineTelemetryStoreContractTest.java \
    .scripts/postgresql/td005-migration/tests/lc02_08_inbox_product_contract.sh
pass "LC02-08 scoped git diff --check passed"

# The explicit success-path assertion makes cleanup failure affect the exit
# status; EXIT remains only a best-effort safety net for interrupted runs.
cleanup_databases
RESIDUAL="$(prefix_count)"
[ "${RESIDUAL}" = "0" ] || fail "temporary database prefix residue after success path: ${RESIDUAL}"
pass "temporary databases explicitly cleaned with residue=0"
