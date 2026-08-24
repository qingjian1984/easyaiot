#!/usr/bin/env bash
# LC02-09-R1 §23.6: isolated real EMQX 5.8.7 authentication/ACL contract.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
IMAGE="emqx/emqx:5.8.7"
PREFIX="lc02-09-emqx"
ENABLED="${LC02_09_EMQX_ENABLED:-false}"

fail() {
    echo "[lc02-09][FAIL] $*" >&2
    exit 1
}

pass() {
    echo "[lc02-09][PASS] $*"
}

require_tool() {
    command -v "$1" >/dev/null 2>&1 || fail "ENVIRONMENT_UNAVAILABLE required tool missing: $1"
}

[ "${ENABLED}" = "true" ] || fail "LC02_09_EMQX_ENABLED=true is required"
for tool in docker mktemp openssl awk grep sed mvn; do
    require_tool "${tool}"
done

docker info >/dev/null 2>&1 || fail "ENVIRONMENT_UNAVAILABLE Docker daemon is unavailable"
[ "$(docker info --format '{{.OSType}}' 2>/dev/null)" = "linux" ] \
    || fail "ENVIRONMENT_UNAVAILABLE Docker must use Linux containers"
docker image inspect "${IMAGE}" >/dev/null 2>&1 \
    || fail "ENVIRONMENT_UNAVAILABLE local ${IMAGE} image is required; image pull is forbidden"

TOKEN="$(openssl rand -hex 8)"
CONTAINER="${PREFIX}-${TOKEN}"
NETWORK="${PREFIX}-net-${TOKEN}"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/lc02-09-emqx.XXXXXX")"
BOOTSTRAP="${TMP_ROOT}/lc02-auth-bootstrap.csv"
MAVEN_LOG="${TMP_ROOT}/maven.log"
REPORT_BASE="${REPO_ROOT}/DEVICE/iot-sink/iot-sink-biz/target/surefire-reports/com.basiclab.iot.sink.telemetry.inbox.mqtt.EmqxTelemetryAclIntegrationTest"

PASSWORD_A="$(openssl rand -hex 24)"
PASSWORD_B="$(openssl rand -hex 24)"
PASSWORD_CENTER="$(openssl rand -hex 24)"
[ "${PASSWORD_A}" != "${PASSWORD_B}" ] && [ "${PASSWORD_A}" != "${PASSWORD_CENTER}" ] \
    && [ "${PASSWORD_B}" != "${PASSWORD_CENTER}" ] || fail "random credential collision"
printf '%s\n' 'user_id,password,is_superuser' \
    "lc02-collector-a,${PASSWORD_A},false" \
    "lc02-collector-b,${PASSWORD_B},false" \
    "lc02-center-inbox,${PASSWORD_CENTER},false" >"${BOOTSTRAP}"
chmod 600 "${BOOTSTRAP}" 2>/dev/null || true

host_path() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$1"
    else
        printf '%s' "$1"
    fi
}

cleanup() {
    local original_rc=$?
    set +e
    docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
    docker network rm "${NETWORK}" >/dev/null 2>&1 || true
    rm -rf -- "${TMP_ROOT}" 2>/dev/null || true
    local containers networks
    containers="$(docker ps -a --filter "name=^/${CONTAINER}$" --format '{{.Names}}' 2>/dev/null | wc -l | tr -d ' ')"
    networks="$(docker network ls --filter "name=^${NETWORK}$" --format '{{.Name}}' 2>/dev/null | wc -l | tr -d ' ')"
    if [ "${containers}" = "0" ] && [ "${networks}" = "0" ] && [ ! -e "${TMP_ROOT}" ]; then
        echo "[lc02-09][PASS] isolated container/network/credential residue=0"
    else
        echo "[lc02-09][FAIL] isolated residue is not zero" >&2
        original_rc=1
    fi
    exit "${original_rc}"
}
trap cleanup EXIT INT TERM

[ "$(docker ps -a --filter "name=^/${CONTAINER}$" --format '{{.Names}}' | wc -l | tr -d ' ')" = "0" ] \
    || fail "isolated container name already exists"
[ "$(docker network ls --filter "name=^${NETWORK}$" --format '{{.Name}}' | wc -l | tr -d ' ')" = "0" ] \
    || fail "isolated network name already exists"

grep -Fq '{deny, all}.' "${SCRIPT_DIR}/acl.conf" || fail "ACL final deny is missing"
grep -Fq '{eq, "/iot/+/+/property/upstream/report"}' "${SCRIPT_DIR}/acl.conf" \
    || fail "center exact real Topic ACL is missing"
if grep -Fq '$share' "${SCRIPT_DIR}/acl.conf"; then
    fail "shared-subscription prefix must not appear in center file ACL"
fi
if grep -Eq '\{allow,[[:space:]]*all\}|ipaddr|is_superuser[[:space:]]*=[[:space:]]*true' \
        "${SCRIPT_DIR}/acl.conf" "${SCRIPT_DIR}/emqx.conf"; then
    fail "permissive ACL, IP bypass or superuser found"
fi
pass "static deny-by-default and non-superuser contract verified"

docker network create "${NETWORK}" >/dev/null
MSYS_NO_PATHCONV=1 docker run -d \
    --name "${CONTAINER}" \
    --network "${NETWORK}" \
    --publish 127.0.0.1::1883 \
    --mount "type=bind,source=$(host_path "${SCRIPT_DIR}/emqx.conf"),target=/opt/emqx/etc/emqx.conf,readonly" \
    --mount "type=bind,source=$(host_path "${SCRIPT_DIR}/acl.conf"),target=/opt/emqx/etc/lc02-acl.conf,readonly" \
    --mount "type=bind,source=$(host_path "${BOOTSTRAP}"),target=/opt/emqx/etc/lc02-auth-bootstrap.csv,readonly" \
    "${IMAGE}" >/dev/null

ready=false
for _ in $(seq 1 60); do
    if docker exec "${CONTAINER}" emqx ctl status 2>/dev/null | grep -Fq 'is started'; then
        ready=true
        break
    fi
    sleep 1
done
[ "${ready}" = "true" ] || {
    docker logs --tail 80 "${CONTAINER}" 2>&1 | sed -E 's/[0-9a-f]{32,}/[REDACTED]/g' >&2 || true
    fail "isolated EMQX did not become ready"
}

STATUS="$(docker exec "${CONTAINER}" emqx ctl status)"
grep -Fq '5.8.7' <<<"${STATUS}" || fail "running broker version is not 5.8.7"
AUTH_CONF="$(docker exec "${CONTAINER}" emqx ctl conf show authentication 2>/dev/null)"
AUTHZ_CONF="$(docker exec "${CONTAINER}" emqx ctl conf show authorization 2>/dev/null)"
grep -Fq 'built_in_database' <<<"${AUTH_CONF}" || fail "built-in authentication is not loaded"
grep -Fq 'no_match => deny' <<<"${AUTHZ_CONF}" || grep -Fq 'no_match = deny' <<<"${AUTHZ_CONF}" \
    || fail "authorization.no_match is not deny"
grep -Fq 'deny_action => disconnect' <<<"${AUTHZ_CONF}" \
    || grep -Fq 'deny_action = disconnect' <<<"${AUTHZ_CONF}" \
    || fail "authorization.deny_action is not disconnect"

HOST_PORT="$(docker port "${CONTAINER}" 1883/tcp | awk -F: 'NR==1 {print $NF}')"
[[ "${HOST_PORT}" =~ ^[0-9]+$ ]] || fail "isolated MQTT host port was not resolved"
IMAGE_ID="$(docker image inspect --format '{{.Id}}' "${IMAGE}")"
echo "[lc02-09][INFO] EMQX version=5.8.7 imageId=${IMAGE_ID} hostPort=${HOST_PORT}"

rm -f -- "${REPORT_BASE}.txt" "${REPORT_BASE}.xml"
set +e
MAVEN_ARGS=(
    -pl iot-sink/iot-sink-biz -am test
    -Dtest=EmqxTelemetryAclIntegrationTest
    -DfailIfNoTests=false -Dmaven.test.skip=false -DskipTests=false
    -Dsurefire.failIfNoSpecifiedTests=false -Dsurefire.printSummary=true
    -DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true
    -DforkCount=1 -DreuseForks=false -Dsurefire.useManifestOnlyJar=false
)
CONTRACT_ENV=(
    LC02_09_EMQX_ENABLED=true
    LC02_09_EMQX_HOST=127.0.0.1
    LC02_09_EMQX_PORT="${HOST_PORT}"
    LC02_09_COLLECTOR_A_PASSWORD="${PASSWORD_A}"
    LC02_09_COLLECTOR_B_PASSWORD="${PASSWORD_B}"
    LC02_09_CENTER_PASSWORD="${PASSWORD_CENTER}"
)
if mvn -version >/dev/null 2>&1; then
    env "${CONTRACT_ENV[@]}" mvn -f "${REPO_ROOT}/DEVICE/pom.xml" \
        "${MAVEN_ARGS[@]}" >"${MAVEN_LOG}" 2>&1
elif command -v powershell.exe >/dev/null 2>&1 \
        && { command -v wslpath >/dev/null 2>&1 || command -v cygpath >/dev/null 2>&1; }; then
    if command -v wslpath >/dev/null 2>&1; then
        WINDOWS_POM="$(wslpath -w "${REPO_ROOT}/DEVICE/pom.xml")"
    else
        WINDOWS_POM="$(cygpath -w "${REPO_ROOT}/DEVICE/pom.xml")"
    fi
    WINDOWS_CONTRACT_ENV="LC02_09_EMQX_ENABLED:LC02_09_EMQX_HOST:LC02_09_EMQX_PORT:LC02_09_COLLECTOR_A_PASSWORD:LC02_09_COLLECTOR_B_PASSWORD:LC02_09_CENTER_PASSWORD"
    if [ -n "${WSLENV:-}" ]; then
        WINDOWS_CONTRACT_ENV="${WSLENV}:${WINDOWS_CONTRACT_ENV}"
    fi
    env "${CONTRACT_ENV[@]}" WSLENV="${WINDOWS_CONTRACT_ENV}" \
        powershell.exe -NoProfile -NonInteractive -Command \
        "[Console]::OutputEncoding=[Text.UTF8Encoding]::new(); \$mavenArgs=@('-pl','iot-sink/iot-sink-biz','-am','test','-Dtest=EmqxTelemetryAclIntegrationTest','-DfailIfNoTests=false','-Dmaven.test.skip=false','-DskipTests=false','-Dsurefire.failIfNoSpecifiedTests=false','-Dsurefire.printSummary=true','-DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true','-DforkCount=1','-DreuseForks=false','-Dsurefire.useManifestOnlyJar=false'); & mvn.cmd -f '${WINDOWS_POM}' \$mavenArgs; exit \$LASTEXITCODE" \
        >"${MAVEN_LOG}" 2>&1
else
    echo "ENVIRONMENT_UNAVAILABLE neither native Maven/JDK nor Windows Maven bridge is usable" >"${MAVEN_LOG}"
    false
fi
MAVEN_RC=$?
set -e
if [ "${MAVEN_RC}" -ne 0 ]; then
    tail -n 120 "${MAVEN_LOG}" \
        | sed -e "s/${PASSWORD_A}/[REDACTED]/g" \
              -e "s/${PASSWORD_B}/[REDACTED]/g" \
              -e "s/${PASSWORD_CENTER}/[REDACTED]/g" \
        | sed -E 's/[0-9a-f]{32,}/[REDACTED]/g' >&2 || true
    docker logs --tail 120 "${CONTAINER}" 2>&1 \
        | sed -e "s/${PASSWORD_A}/[REDACTED]/g" \
              -e "s/${PASSWORD_B}/[REDACTED]/g" \
              -e "s/${PASSWORD_CENTER}/[REDACTED]/g" \
        | sed -E 's/[0-9a-f]{32,}/[REDACTED]/g' >&2 || true
    fail "Maven real EMQX contract failed (rc=${MAVEN_RC})"
fi

if [ ! -f "${REPORT_BASE}.txt" ]; then
    tail -n 120 "${MAVEN_LOG}" \
        | sed -e "s/${PASSWORD_A}/[REDACTED]/g" \
              -e "s/${PASSWORD_B}/[REDACTED]/g" \
              -e "s/${PASSWORD_CENTER}/[REDACTED]/g" \
        | sed -E 's/[0-9a-f]{32,}/[REDACTED]/g' >&2 || true
    fail "Surefire report for EMQX contract is missing"
fi
SUMMARY="$(grep -E 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' \
    "${REPORT_BASE}.txt" | tail -1)"
grep -Eq 'Tests run: 12, Failures: 0, Errors: 0, Skipped: 0' <<<"${SUMMARY}" \
    || fail "unexpected Surefire summary"
pass "real EMQX ACL matrix ${SUMMARY}"

if grep -Fq "${PASSWORD_A}" "${MAVEN_LOG}" "${REPORT_BASE}.txt" "${REPORT_BASE}.xml" 2>/dev/null \
        || grep -Fq "${PASSWORD_B}" "${MAVEN_LOG}" "${REPORT_BASE}.txt" "${REPORT_BASE}.xml" 2>/dev/null \
        || grep -Fq "${PASSWORD_CENTER}" "${MAVEN_LOG}" "${REPORT_BASE}.txt" "${REPORT_BASE}.xml" 2>/dev/null; then
    fail "temporary credential leaked into Maven/Surefire output"
fi

git -C "${REPO_ROOT}" diff --check -- \
    DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/TelemetryUpstreamTopicParser.java \
    DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/TelemetryMqttProperties.java \
    DEVICE/iot-sink/iot-sink-biz/src/main/resources/application.yaml \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/route/TelemetryUpstreamTopicParserContractTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxAutoConfigurationTest.java \
    DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/EmqxTelemetryAclIntegrationTest.java \
    .scripts/emqx/lc02-09/emqx.conf .scripts/emqx/lc02-09/acl.conf \
    .scripts/emqx/lc02-09/lc02_09_emqx_acl_contract.sh
pass "scoped diff check passed"
pass "LC02-09 isolated contract complete"
