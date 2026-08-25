#!/usr/bin/env bash
# P02-M2-02A：V011/U011 review-only runner 纯静态与假命令合同。
# 本脚本不连接 PostgreSQL、Docker 或任何网络地址，不执行真实 DDL。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${MIGRATION_DIR}/../../.." && pwd)"
RUNNER="${MIGRATION_DIR}/td005_migration.sh"
V011="${REPO_ROOT}/.doc/技术设计/电力运维云平台/assets/td006-migration/V011__alarm_core_candidate.sql"
U011="${REPO_ROOT}/.doc/技术设计/电力运维云平台/assets/td006-migration/U011__alarm_core_candidate.sql"
COMMENTS="${MIGRATION_DIR}/check_ddl_comments.sql"

fail() {
    echo "[p02-v011][FAIL] $*" >&2
    exit 1
}

PASS_COUNT=0
pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "[p02-v011][PASS] $*"
}

assert_contains() {
    local text="$1" marker="$2" label="$3"
    grep -Fq -- "${marker}" <<<"${text}" || fail "${label}: missing ${marker}"
    pass "${label}"
}

assert_not_contains() {
    local text="$1" marker="$2" label="$3"
    ! grep -Fq -- "${marker}" <<<"${text}" || fail "${label}: unexpected ${marker}"
    pass "${label}"
}

assert_eq() {
    local expected="$1" actual="$2" label="$3"
    [ "${expected}" = "${actual}" ] || fail "${label}: expected=${expected}, actual=${actual}"
    pass "${label}"
}

for file in "${RUNNER}" "${V011}" "${U011}" "${COMMENTS}"; do
    [ -f "${file}" ] || fail "required file missing: ${file}"
done

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/p02-v011.XXXXXX")"
trap 'rm -rf -- "${TMP_ROOT}"' EXIT INT TERM
FAKE_BIN="${TMP_ROOT}/bin"
FAKE_CALLS="${TMP_ROOT}/calls.log"
FAKE_SQL="${TMP_ROOT}/driver.sql"
BACKUP_DIR="${TMP_ROOT}/backups"
mkdir -p "${FAKE_BIN}" "${BACKUP_DIR}"
: > "${FAKE_CALLS}"
: > "${FAKE_SQL}"

cat > "${FAKE_BIN}/psql" <<'SH'
#!/usr/bin/env bash
echo "psql $*" >> "${FAKE_CALLS}"
if [[ "$*" == *"pg_control_system"* ]]; then
    printf '%s\n' "${FAKE_IDENTITY:-td006_review_contract|987654321}"
    exit "${FAKE_IDENTITY_RC:-0}"
fi
cat >> "${FAKE_SQL}"
printf '\n-- FAKE_PSQL_CALL_END --\n' >> "${FAKE_SQL}"
[ -z "${FAKE_PSQL_OUTPUT:-}" ] || printf '%s\n' "${FAKE_PSQL_OUTPUT}"
exit "${FAKE_PSQL_RC:-0}"
SH

cat > "${FAKE_BIN}/pg_dump" <<'SH'
#!/usr/bin/env bash
echo "pg_dump $*" >> "${FAKE_CALLS}"
echo "-- fake review-only backup"
SH
chmod +x "${FAKE_BIN}/psql" "${FAKE_BIN}/pg_dump"

capture() {
    set +e
    LAST_OUTPUT="$("$@" 2>&1)"
    LAST_RC=$?
    set -e
}

reset_fakes() {
    : > "${FAKE_CALLS}"
    : > "${FAKE_SQL}"
}

base_env=(env
    "PATH=${FAKE_BIN}:${PATH}"
    "FAKE_CALLS=${FAKE_CALLS}"
    "FAKE_SQL=${FAKE_SQL}"
    "PG_CONTAINER="
    "PG_PASSWORD=static-fake-not-a-secret"
    "PG_DB=td006_review_contract"
    "TD006_REVIEW_ONLY=1"
    "TD006_TEMP_DB=td006_review_contract"
    "TD006_TEMP_SYSTEM_IDENTIFIER=987654321"
    "APPROVAL=P02-M2-02A-STATIC"
    "BACKUP_DIR=${BACKUP_DIR}"
    "RETRY_MAX=1")

bash -n "${RUNNER}"
bash -n "$0"
pass "bash syntax"

DRY_RUN="$(bash "${RUNNER}" dry-run)"
assert_not_contains "${DRY_RUN}" "V011" "default dry-run excludes V011"
assert_not_contains "${DRY_RUN}" "U011" "default dry-run excludes U011"
APPLY_LINE="$(grep '^APPLY_STEPS=' "${RUNNER}")"
assert_not_contains "${APPLY_LINE}" "V011" "default APPLY_STEPS excludes V011"
assert_contains "$(grep -F 'cat "${U001_SQL}"' "${RUNNER}")" 'cat "${U001_SQL}"' "default uninstall remains U001"

reset_fakes
capture env "PATH=${FAKE_BIN}:${PATH}" "FAKE_CALLS=${FAKE_CALLS}" TD006_REVIEW_ONLY=0 \
    bash "${RUNNER}" apply --step V011
assert_eq 2 "${LAST_RC}" "missing review flag exit"
assert_contains "${LAST_OUTPUT}" "TD006_REVIEW_ONLY_REQUIRED" "missing review flag marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "missing review flag makes zero external calls"

reset_fakes
capture "${base_env[@]}" APPROVAL= bash "${RUNNER}" apply --step V011
assert_eq 2 "${LAST_RC}" "missing approval exit"
assert_contains "${LAST_OUTPUT}" "APPROVAL_MISSING" "missing approval marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "missing approval is pre-connection"

reset_fakes
capture "${base_env[@]}" BACKUP_DIR= bash "${RUNNER}" apply --step V011
assert_eq 2 "${LAST_RC}" "missing backup exit"
assert_contains "${LAST_OUTPUT}" "BACKUP_MISSING" "missing backup marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "missing backup is pre-connection"

reset_fakes
capture "${base_env[@]}" TD006_TEMP_DB= bash "${RUNNER}" apply --step V011
assert_eq 2 "${LAST_RC}" "missing temp database exit"
assert_contains "${LAST_OUTPUT}" "TD006_TEMP_DB_MISSING" "missing temp database marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "missing temp database is pre-connection"

reset_fakes
capture "${base_env[@]}" TD006_TEMP_SYSTEM_IDENTIFIER= bash "${RUNNER}" apply --step V011
assert_eq 2 "${LAST_RC}" "missing system identifier exit"
assert_contains "${LAST_OUTPUT}" "TD006_TEMP_SYSTEM_IDENTIFIER_MISSING" "missing system identifier marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "missing system identifier is pre-connection"

reset_fakes
capture "${base_env[@]}" TD006_DENY_DATABASES=td006_review_contract bash "${RUNNER}" apply --step V011
assert_eq 2 "${LAST_RC}" "deny-list database exit"
assert_contains "${LAST_OUTPUT}" "TD006_DATABASE_DENIED" "deny-list database marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "deny-list database is pre-connection"

reset_fakes
capture "${base_env[@]}" bash "${RUNNER}" uninstall --step V011
assert_eq 2 "${LAST_RC}" "wrong V011 mode exit"
assert_contains "${LAST_OUTPUT}" "TD006_REVIEW_STEP_MODE_FORBIDDEN" "wrong V011 mode marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "wrong V011 mode is pre-connection"

reset_fakes
capture "${base_env[@]}" bash "${RUNNER}" apply --step U011
assert_eq 2 "${LAST_RC}" "wrong U011 mode exit"
assert_contains "${LAST_OUTPUT}" "TD006_REVIEW_STEP_MODE_FORBIDDEN" "wrong U011 mode marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "wrong U011 mode is pre-connection"

reset_fakes
capture "${base_env[@]}" bash "${RUNNER}" apply --step V011 --skip-precheck
assert_eq 2 "${LAST_RC}" "review skip-precheck exit"
assert_contains "${LAST_OUTPUT}" "TD006_REVIEW_SKIP_PRECHECK_FORBIDDEN" "review skip-precheck marker"
assert_eq 0 "$(wc -l < "${FAKE_CALLS}" | tr -d ' ')" "review skip-precheck is pre-connection"

reset_fakes
capture "${base_env[@]}" FAKE_IDENTITY=td006_review_other\|987654321 bash "${RUNNER}" apply --step V011
assert_eq 2 "${LAST_RC}" "current_database mismatch exit"
assert_contains "${LAST_OUTPUT}" "TD006_TEMP_DB_IDENTITY_MISMATCH" "current_database mismatch marker"
assert_not_contains "$(cat "${FAKE_CALLS}")" "pg_dump" "database identity mismatch stops before backup"

reset_fakes
capture "${base_env[@]}" FAKE_IDENTITY=td006_review_contract\|111 bash "${RUNNER}" apply --step V011
assert_eq 2 "${LAST_RC}" "system identifier mismatch exit"
assert_contains "${LAST_OUTPUT}" "TD006_TEMP_SYSTEM_IDENTITY_MISMATCH" "system identifier mismatch marker"
assert_not_contains "$(cat "${FAKE_CALLS}")" "pg_dump" "system identity mismatch stops before backup"

reset_fakes
capture "${base_env[@]}" bash "${RUNNER}" apply --step V011
[ "${LAST_RC}" -eq 0 ] || fail "fake V011 review path exit=${LAST_RC}: ${LAST_OUTPUT}"
pass "fake V011 review path exit"
assert_contains "$(cat "${FAKE_CALLS}")" "pg_dump" "V011 identity precedes backup and driver"
DRIVER="$(cat "${FAKE_SQL}")"
assert_contains "${DRIVER}" "HASH_MISMATCH V011" "V011 driver hash gate"
assert_contains "${DRIVER}" "TD006_PREEXISTING_OBJECT V011" "V011 driver preexisting-object gate"
assert_contains "${DRIVER}" "SCHEMA_SIGNATURE_MISMATCH V011" "V011 driver signature gate"
assert_contains "${DRIVER}" "STEP_SKIPPED V011" "V011 driver same-hash skip"
assert_contains "${DRIVER}" "schema_signature" "V011 history stores catalog signature"

BEGIN_LINE="$(grep -n '^BEGIN;$' "${FAKE_SQL}" | head -1 | cut -d: -f1)"
CREATE_LINE="$(grep -n '^CREATE TABLE public.alarm_rule (' "${FAKE_SQL}" | head -1 | cut -d: -f1)"
HISTORY_LINE="$(grep -n '^INSERT INTO public.schema_migration_history' "${FAKE_SQL}" | head -1 | cut -d: -f1)"
COMMIT_LINE="$(grep -n '^COMMIT;$' "${FAKE_SQL}" | head -1 | cut -d: -f1)"
[ "${BEGIN_LINE}" -lt "${CREATE_LINE}" ] && [ "${CREATE_LINE}" -lt "${HISTORY_LINE}" ] && [ "${HISTORY_LINE}" -lt "${COMMIT_LINE}" ] \
    || fail "V011 DDL and SUCCEEDED history are not in one runner transaction"
pass "V011 DDL and SUCCEEDED history share one runner transaction"

reset_fakes
capture "${base_env[@]}" bash "${RUNNER}" uninstall --step U011
[ "${LAST_RC}" -eq 0 ] || fail "fake U011 review path exit=${LAST_RC}: ${LAST_OUTPUT}"
pass "fake U011 review path exit"
U_DRIVER="$(cat "${FAKE_SQL}")"
assert_contains "${U_DRIVER}" "TD006_V011_HISTORY_REQUIRED U011" "U011 requires V011 history"
assert_contains "${U_DRIVER}" "U011 refused:" "U011 embeds non-empty rejection"
assert_contains "${U_DRIVER}" "DROP TABLE IF EXISTS public.alarm_outbox" "U011 explicit unload asset"
assert_contains "${U_DRIVER}" "STEP_SKIPPED U011" "U011 same-hash skip"

reset_fakes
capture "${base_env[@]}" FAKE_PSQL_RC=1 FAKE_PSQL_OUTPUT=$'STEP_START U011\nERROR: U011 refused: alarm_record contains business facts' \
    bash "${RUNNER}" uninstall --step U011
assert_eq 1 "${LAST_RC}" "U011 non-empty failure exit"
assert_contains "${LAST_OUTPUT}" "NON_EMPTY_TABLE_REJECTED" "U011 non-empty stable error code"
assert_contains "${LAST_OUTPUT}" "FAILED record skipped" "U011 failure attempts ADR-013 FAILED history"

assert_eq 9 "$(grep -Ec '^CREATE TABLE public\.alarm_' "${V011}")" "V011 table count"
assert_eq 149 "$(grep -Ec '^COMMENT ON COLUMN public\.alarm_' "${V011}")" "V011 column comment count"
assert_eq 0 "$(grep -Ec '^(BEGIN|COMMIT);$' "${V011}" || true)" "V011 has no top-level transaction"
for table in alarm_rule alarm_rule_version alarm_maintenance_context alarm_record alarm_source_mapping alarm_action_log alarm_false_alarm_review alarm_source_inbox alarm_outbox; do
    grep -Fq "'${table}'" "${COMMENTS}" || fail "comment gate missing ${table}"
done
pass "comment gate lists all V011 tables"

echo "[p02-v011][RESULT] PASS_COUNT=${PASS_COUNT}; real_database_calls=0; real_ddl=0"
