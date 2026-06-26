#!/bin/bash
# EasyAIoT 运行时镜像公共库
# 供 runtime_image.sh、install_linux.sh、install_business_linux.sh 等脚本 source 复用。
# 调用方需先设置 SCRIPT_DIR（.scripts/docker 目录）并已 source deploy_profile.sh。

# shellcheck disable=SC2034
[[ -n "${RUNTIME_IMAGE_COMMON_LOADED:-}" ]] && return 0
RUNTIME_IMAGE_COMMON_LOADED=1

: "${SCRIPT_DIR:?runtime_image_common.sh 需要 SCRIPT_DIR}"

RUNTIME_IMAGE_SCRIPT="${RUNTIME_IMAGE_SCRIPT:-${SCRIPT_DIR}/runtime_image.sh}"
RUNTIME_IMAGES_MARKER="${RUNTIME_IMAGES_MARKER:-${SCRIPT_DIR}/.runtime_images_pulled}"
RUNTIME_REGISTRY_CONFIG="${RUNTIME_REGISTRY_CONFIG:-${SCRIPT_DIR}/runtime_registry.conf}"

# 加载后由 runtime_load_registry() 填充（可被 EASYAIOT_RUNTIME_REGISTRY 环境变量覆盖）
RUNTIME_IMAGE_REGISTRY=""

# ============================================================================
# 镜像映射表（远程名 ↔ 本地名）
# ============================================================================
DEVICE_REMOTE_NAMES=(
    aiot-gateway aiot-system aiot-infra aiot-device aiot-dataset
    aiot-node aiot-tdengine aiot-file aiot-message aiot-sink aiot-gb28181
)

DEVICE_LOCAL_NAMES=(
    iot-gateway iot-module-system-biz iot-module-infra-biz iot-module-device-biz
    iot-module-dataset-biz iot-module-node-biz iot-module-tdengine-biz
    iot-module-file-biz iot-module-message-biz iot-sink-biz iot-gb28181-biz
)

INDEPENDENT_MODULES=(
    "aiot-ai|ai-service|AI"
    "aiot-video|video-service|VIDEO"
    "aiot-web|web-service|WEB"
)

# 仅 full 全量形态部署（远程名 ↔ 本地名，与 INDEPENDENT_MODULES 格式相同）
FULL_ONLY_MODULES=(
    "aiot-app|app-service|APP"
)

PROFILE_DEPENDENT_REMOTES=(aiot-web)
FULL_ONLY_REMOTES=(aiot-app)
ALL_DEPLOY_PROFILES=(mini standard full)
ALL_RUNTIME_ARCHS=(amd64 arm64)

# ============================================================================
# 架构检测
# ============================================================================
runtime_detect_arch() {
    case "$(uname -m)" in
        x86_64|amd64)   echo "amd64" ;;
        aarch64|arm64)  echo "arm64" ;;
        armv7l|armv6l)  echo "arm32" ;;
        *)              echo "amd64" ;;
    esac
}

runtime_arch_to_platform() {
    case "$1" in
        amd64) echo "linux/amd64" ;;
        arm64) echo "linux/arm64" ;;
        arm32) echo "linux/arm/v7" ;;
        *)     echo "linux/amd64" ;;
    esac
}

runtime_is_native_arch() {
    [ "$1" = "$(runtime_detect_arch)" ]
}

# ============================================================================
# 远程仓库配置（CNB）
# ============================================================================
runtime_load_registry() {
    local from_file=""
    if [ -f "$RUNTIME_REGISTRY_CONFIG" ]; then
        from_file=$(sed -n 's/^[[:space:]]*REGISTRY=//p' "$RUNTIME_REGISTRY_CONFIG" 2>/dev/null | head -1)
        from_file="${from_file//\"/}"
        from_file="${from_file//\'/}"
        from_file="${from_file%%#*}"
        from_file="${from_file// /}"
    fi
    RUNTIME_IMAGE_REGISTRY=$(runtime_normalize_registry "${EASYAIOT_RUNTIME_REGISTRY:-${from_file:-docker.cnb.cool/holmesian/easyaiot/}}")
    export RUNTIME_IMAGE_REGISTRY
}

runtime_log_registry_info() {
    runtime_load_registry
    runtime_img_msg info "========================================"
    runtime_img_msg info "  运行时镜像仓库 (CNB)"
    runtime_img_msg info "========================================"
    runtime_img_msg info "  当前地址: ${RUNTIME_IMAGE_REGISTRY}"
    runtime_img_msg info "  配置文件: ${RUNTIME_REGISTRY_CONFIG}"
    runtime_img_msg info "  更换仓库:"
    runtime_img_msg info "    1) 编辑上述配置文件，修改 REGISTRY= 一行（须以 / 结尾）"
    runtime_img_msg info "    2) 或临时指定: export EASYAIOT_RUNTIME_REGISTRY=your.cool/namespace/project/"
    runtime_img_msg info "========================================"
}

# 从 REGISTRY 地址提取主机名（如 docker.cnb.cool/holmesian/easyaiot/ → docker.cnb.cool）
runtime_registry_host() {
    local r="${1:-${REGISTRY:-$RUNTIME_IMAGE_REGISTRY}}"
    r="${r%%/*}"
    echo "$r"
}

# 检查 ~/.docker/config.json 是否已保存指定 registry 的登录凭据
runtime_docker_has_registry_auth() {
    local host="$1"
    local config="${DOCKER_CONFIG:-$HOME/.docker}/config.json"
    [ -f "$config" ] || return 1
    if ! command -v python3 >/dev/null 2>&1; then
        # 无 python3 时做简单 grep 兜底
        grep -qE "\"${host}\"|\"https://${host}\"|\"http://${host}\"" "$config" 2>/dev/null
        return $?
    fi
    python3 - "$host" "$config" <<'PY'
import json, subprocess, sys

host = sys.argv[1]
config_path = sys.argv[2]

try:
    with open(config_path, encoding="utf-8") as f:
        cfg = json.load(f)
except OSError:
    sys.exit(1)

def has_auth_entry(entry):
    return bool(entry.get("auth") or entry.get("identitytoken"))

auths = cfg.get("auths", {})
for key in (host, f"https://{host}", f"http://{host}"):
    if has_auth_entry(auths.get(key, {})):
        sys.exit(0)

def try_cred_helper(helper, server):
    if not helper:
        return False
    cmd = f"docker-credential-{helper}"
    try:
        p = subprocess.run(
            [cmd, "get"],
            input=server.encode(),
            capture_output=True,
            timeout=10,
        )
        return p.returncode == 0 and bool(p.stdout.strip())
    except (OSError, subprocess.TimeoutExpired):
        return False

cred_helpers = cfg.get("credHelpers", {})
if try_cred_helper(cred_helpers.get(host), host):
    sys.exit(0)

creds_store = cfg.get("credsStore")
if try_cred_helper(creds_store, host):
    sys.exit(0)

sys.exit(1)
PY
}

# 输出 CNB 登录/权限失败时的修复指引
runtime_print_registry_auth_help() {
    local host="$1" registry="$2" reason="${3:-not_logged_in}"
    registry=$(runtime_normalize_registry "$registry")
    runtime_img_msg error "========================================"
    case "$reason" in
        no_push_access)
            runtime_img_msg error "  无法推送到 CNB 镜像仓库（权限不足或仓库路径错误）"
            ;;
        *)
            runtime_img_msg error "  未登录 CNB 镜像仓库，无法执行 build-runtime 推送"
            ;;
    esac
    runtime_img_msg error "========================================"
    runtime_img_msg error "  目标仓库: ${registry}"
    runtime_img_msg error "  Registry: ${host}"
    runtime_img_msg error ""
    runtime_img_msg error "  请先登录 CNB Docker 制品库（需具有推送权限的访问令牌 CNB_TOKEN）："
    runtime_img_msg error "    docker login ${host} -u cnb -p \${CNB_TOKEN}"
    runtime_img_msg error ""
    runtime_img_msg error "  文档: https://docs.cnb.cool/zh/artifact/docker.html"
    runtime_img_msg error ""
    runtime_img_msg error "  若已登录仍失败，请检查："
    runtime_img_msg error "    1) CNB_TOKEN 是否有效且具有该仓库的 push 权限"
    runtime_img_msg error "    2) 仓库路径是否正确: ${RUNTIME_REGISTRY_CONFIG} 中 REGISTRY= 须以 / 结尾"
    runtime_img_msg error "       当前: ${registry}"
    runtime_img_msg error "    3) 或临时指定: export EASYAIOT_RUNTIME_REGISTRY=your.cool/namespace/project/"
    runtime_img_msg error "========================================"
}

# build-runtime 开始前校验 CNB 登录与推送权限（避免构建完成后才推送失败）
runtime_verify_registry_push_access() {
    if [ "${EASYAIOT_REGISTRY_AUTH_VERIFIED:-0}" = "1" ]; then
        return 0
    fi
    if [ "${EASYAIOT_SKIP_REGISTRY_AUTH_CHECK:-0}" = "1" ]; then
        runtime_img_msg warn "已跳过 CNB 仓库登录/推送权限检查 (EASYAIOT_SKIP_REGISTRY_AUTH_CHECK=1)"
        return 0
    fi

    local registry host probe_ref push_out rc=0
    runtime_load_registry
    registry=$(runtime_normalize_registry "${1:-${REGISTRY:-$RUNTIME_IMAGE_REGISTRY}}")
    host=$(runtime_registry_host "$registry")

    runtime_img_msg info "检查 CNB 镜像仓库登录与推送权限..."
    runtime_img_msg info "  Registry: ${host}"
    runtime_img_msg info "  仓库路径: ${registry}"

    if ! runtime_docker_has_registry_auth "$host"; then
        runtime_print_registry_auth_help "$host" "$registry" "not_logged_in"
        return 1
    fi

    runtime_img_msg info "已检测到 ${host} 登录凭据，正在探测推送权限..."

    probe_ref="${registry}easyaiot-push-auth-check:__probe__"
    if ! docker image inspect hello-world:latest >/dev/null 2>&1; then
        runtime_img_msg info "拉取 hello-world（用于推送权限探测）..."
        if ! docker pull hello-world:latest >/dev/null 2>&1; then
            runtime_img_msg warn "无法拉取 hello-world，仅验证登录凭据（未探测推送权限）"
            runtime_img_msg ok "CNB 登录凭据已就绪: ${host}"
            return 0
        fi
    fi

    docker tag hello-world:latest "$probe_ref" >/dev/null 2>&1 || {
        runtime_img_msg error "创建推送探测标签失败: ${probe_ref}"
        return 1
    }

    push_out=$(docker push "$probe_ref" 2>&1) || rc=$?
    docker rmi "$probe_ref" >/dev/null 2>&1 || true

    if [ "$rc" -ne 0 ]; then
        if echo "$push_out" | grep -qiE 'no basic auth credentials|unauthorized|authentication required|authorization failed'; then
            runtime_print_registry_auth_help "$host" "$registry" "not_logged_in"
            return 1
        fi
        if echo "$push_out" | grep -qiE 'push access denied|access denied|denied'; then
            runtime_print_registry_auth_help "$host" "$registry" "no_push_access"
            runtime_img_msg error "推送探测输出:"
            echo "$push_out" | while IFS= read -r line; do runtime_img_msg error "  $line"; done
            return 1
        fi
        runtime_img_msg error "推送权限探测失败（未知错误）:"
        echo "$push_out" | while IFS= read -r line; do runtime_img_msg error "  $line"; done
        return 1
    fi

    runtime_img_msg ok "CNB 仓库登录与推送权限验证通过"
    export EASYAIOT_REGISTRY_AUTH_VERIFIED=1
    return 0
}

# 交互选择部署形态（默认 full）；非交互时使用环境变量或 full
runtime_interactive_select_profile() {
    local purpose="${1:-pull}"
    if [ "${EASYAIOT_SKIP_PROFILE_PROMPT:-}" = "1" ]; then
        ensure_deploy_profile 2>/dev/null || export EASYAIOT_DEPLOY_PROFILE="${EASYAIOT_DEPLOY_PROFILE:-full}"
        return 0
    fi
    if [ ! -t 0 ]; then
        export EASYAIOT_DEPLOY_PROFILE="${EASYAIOT_DEPLOY_PROFILE:-full}"
        if declare -F apply_deploy_profile >/dev/null 2>&1; then
            apply_deploy_profile
            save_deploy_profile 2>/dev/null || true
        fi
        return 0
    fi

    echo ""
    if [ "$purpose" = "build" ]; then
        echo "请选择要构建的部署形态："
        echo "  1) mini     — 边缘精简版"
        echo "  2) standard — 标准版"
        echo "  3) full     — 完整版（默认）"
        echo "  4) 全部     — mini + standard + full"
        echo ""
        local choice=""
        read -r -p "请输入选项 [1-4，默认 3]: " choice
        case "${choice:-3}" in
            1) export EASYAIOT_DEPLOY_PROFILE=mini; unset EASYAIOT_RUNTIME_BUILD_ALL_PROFILES ;;
            2) export EASYAIOT_DEPLOY_PROFILE=standard; unset EASYAIOT_RUNTIME_BUILD_ALL_PROFILES ;;
            4) export EASYAIOT_RUNTIME_BUILD_ALL_PROFILES=1; unset EASYAIOT_DEPLOY_PROFILE ;;
            *) export EASYAIOT_DEPLOY_PROFILE=full; unset EASYAIOT_RUNTIME_BUILD_ALL_PROFILES ;;
        esac
    else
        echo "请选择要拉取的部署形态："
        echo "  1) mini     — 边缘精简版"
        echo "  2) standard — 标准版"
        echo "  3) full     — 完整版（默认）"
        echo ""
        local choice=""
        read -r -p "请输入选项 [1-3，默认 3]: " choice
        case "${choice:-3}" in
            1) export EASYAIOT_DEPLOY_PROFILE=mini ;;
            2) export EASYAIOT_DEPLOY_PROFILE=standard ;;
            *) export EASYAIOT_DEPLOY_PROFILE=full ;;
        esac
    fi

    if declare -F apply_deploy_profile >/dev/null 2>&1; then
        apply_deploy_profile
        save_deploy_profile 2>/dev/null || true
        sync_deploy_profile_to_modules 2>/dev/null || true
    fi
    if [ "${EASYAIOT_RUNTIME_BUILD_ALL_PROFILES:-0}" = "1" ]; then
        runtime_img_msg info "已选择: 全部形态 (mini + standard + full)"
    else
        runtime_img_msg info "已选择: $(runtime_profile_label "${EASYAIOT_DEPLOY_PROFILE}") (${EASYAIOT_DEPLOY_PROFILE})"
    fi
    echo ""
}

runtime_interactive_select_tag() {
    if [ -n "${EASYAIOT_RUNTIME_TAG:-}" ]; then
        return 0
    fi
    if [ ! -t 0 ]; then
        export EASYAIOT_RUNTIME_TAG="${TAG:-latest}"
        return 0
    fi
    local tag_input=""
    read -r -p "镜像版本标签 [默认 latest]: " tag_input
    export EASYAIOT_RUNTIME_TAG="${tag_input:-latest}"
    runtime_img_msg info "镜像标签: ${EASYAIOT_RUNTIME_TAG}"
}

runtime_interactive_confirm_push() {
    if [ -n "${EASYAIOT_RUNTIME_PUSH:-}" ]; then
        return 0
    fi
    if [ ! -t 0 ]; then
        export EASYAIOT_RUNTIME_PUSH="${EASYAIOT_RUNTIME_PUSH:-0}"
        return 0
    fi
    echo ""
    read -r -p "构建完成后推送到远程仓库？(Y/n) " push_resp
    case "${push_resp:-Y}" in
        n|N|no|NO) export EASYAIOT_RUNTIME_PUSH=0 ;;
        *) export EASYAIOT_RUNTIME_PUSH=1 ;;
    esac
}

runtime_interactive_confirm_force_rebuild() {
    if [ -n "${EASYAIOT_RUNTIME_FORCE_REBUILD:-}" ]; then
        return 0
    fi
    if [ "${FORCE_REBUILD:-}" = "true" ]; then
        export EASYAIOT_RUNTIME_FORCE_REBUILD=1
        return 0
    fi
    if [ ! -t 0 ]; then
        export EASYAIOT_RUNTIME_FORCE_REBUILD="${EASYAIOT_RUNTIME_FORCE_REBUILD:-1}"
        return 0
    fi
    echo ""
    local fr=""
    read -r -p "强制重新构建（忽略本地镜像缓存）？(Y/n) " fr
    case "${fr:-Y}" in
        n|N|no|NO) export EASYAIOT_RUNTIME_FORCE_REBUILD=0 ;;
        *) export EASYAIOT_RUNTIME_FORCE_REBUILD=1 ;;
    esac
    if [ "${EASYAIOT_RUNTIME_FORCE_REBUILD}" = "1" ]; then
        runtime_img_msg info "已选择: 强制重新构建（忽略本地镜像缓存）"
    else
        runtime_img_msg info "已选择: 复用本地镜像（已存在则跳过构建，直接推送）"
    fi
}

# pull 前交互配置（install_linux.sh pull 等无参数入口）
runtime_images_prepare_pull_interactive() {
    runtime_log_registry_info
    runtime_interactive_select_profile pull
    runtime_interactive_select_tag
    runtime_load_registry
    export EASYAIOT_RUNTIME_REGISTRY="$RUNTIME_IMAGE_REGISTRY"
    export REGISTRY="$RUNTIME_IMAGE_REGISTRY"
}

# build-runtime 前交互配置
runtime_images_prepare_build_interactive() {
    runtime_log_registry_info
    runtime_load_registry
    export EASYAIOT_RUNTIME_REGISTRY="$RUNTIME_IMAGE_REGISTRY"
    export REGISTRY="$RUNTIME_IMAGE_REGISTRY"
    runtime_verify_registry_push_access "$RUNTIME_IMAGE_REGISTRY" || exit 1
    runtime_interactive_select_profile build
    runtime_interactive_select_tag
    runtime_interactive_confirm_push
    runtime_interactive_confirm_force_rebuild
}

# 将交互/环境变量导出给 runtime_image.sh 子进程
runtime_images_export_for_invoke() {
    runtime_load_registry
    export EASYAIOT_RUNTIME_REGISTRY="${EASYAIOT_RUNTIME_REGISTRY:-$RUNTIME_IMAGE_REGISTRY}"
    export REGISTRY="${REGISTRY:-$EASYAIOT_RUNTIME_REGISTRY}"
    if [ -n "${EASYAIOT_RUNTIME_TAG:-}" ]; then
        export EASYAIOT_RUNTIME_TAG
        export TAG="$EASYAIOT_RUNTIME_TAG"
    fi
    if [ "${EASYAIOT_RUNTIME_PUSH:-0}" = "1" ] || [ "${EASYAIOT_RUNTIME_PUSH:-}" = "true" ]; then
        export EASYAIOT_RUNTIME_PUSH=1
    fi
    if [ "${EASYAIOT_RUNTIME_FORCE_REBUILD:-1}" = "1" ]; then
        export EASYAIOT_RUNTIME_FORCE_REBUILD=1
        export FORCE_REBUILD=true
    else
        export EASYAIOT_RUNTIME_FORCE_REBUILD=0
        export FORCE_REBUILD=false
    fi
    if [ -n "${EASYAIOT_DEPLOY_PROFILE:-}" ] && [ "${EASYAIOT_RUNTIME_BUILD_ALL_PROFILES:-0}" != "1" ]; then
        export EASYAIOT_RUNTIME_EXPLICIT_PROFILE="$EASYAIOT_DEPLOY_PROFILE"
    fi
    if [ "${EASYAIOT_RUNTIME_BUILD_ALL_PROFILES:-0}" = "1" ]; then
        unset EASYAIOT_RUNTIME_EXPLICIT_PROFILE
    fi
}

# ============================================================================
# 镜像引用命名（v3：形态在镜像名，架构在标签）
# ============================================================================
runtime_normalize_registry() {
    local r="${1:-$RUNTIME_IMAGE_REGISTRY}"
    [[ "$r" != */ ]] && r="${r}/"
    echo "$r"
}

runtime_remote_ref() {
    local name="$1" profile="${2:-}" arch="${3:-$(runtime_detect_arch)}"
    local registry; registry=$(runtime_normalize_registry "${REGISTRY:-$RUNTIME_IMAGE_REGISTRY}")
    local image_name="${name}"
    [ -n "$profile" ] && [ "$profile" != "full" ] && image_name="${name}-${profile}"
    echo "${registry}${image_name}:${arch}"
}

runtime_manifest_ref() {
    local name="$1" profile="${2:-}"
    local registry; registry=$(runtime_normalize_registry "${REGISTRY:-$RUNTIME_IMAGE_REGISTRY}")
    local tag="${TAG:-latest}"
    local image_name="${name}"
    [ -n "$profile" ] && [ "$profile" != "full" ] && image_name="${name}-${profile}"
    echo "${registry}${image_name}:${tag}"
}

runtime_local_ref() {
    local name="$1" profile="${2:-}"
    local tag="${TAG:-latest}"
    local ref="${name}:${tag}"
    [ -n "$profile" ] && [ "$profile" != "full" ] && ref="${ref}-${profile}"
    echo "$ref"
}

runtime_is_profile_dependent() {
    local r="$1"
    for x in "${PROFILE_DEPENDENT_REMOTES[@]}"; do
        [ "$x" = "$r" ] && return 0
    done
    return 1
}

runtime_is_full_only() {
    local r="$1"
    for x in "${FULL_ONLY_REMOTES[@]}"; do
        [ "$x" = "$r" ] && return 0
    done
    return 1
}

runtime_profile_label() {
    case "$1" in
        mini) echo "边缘精简版" ;;
        standard) echo "标准版" ;;
        full) echo "完整版" ;;
        *) echo "$1" ;;
    esac
}

# ============================================================================
# 拉取标记与本地镜像就绪检测
# ============================================================================
# 返回 0 = 已拉取且本地镜像齐全，可跳过构建
runtime_images_pulled_ready() {
    local marker="${RUNTIME_IMAGES_MARKER}"
    if [ ! -f "$marker" ]; then
        return 1
    fi

    local pull_arch="" pull_profile="" pull_tag=""
    pull_arch=$(sed -n 's/^PULL_ARCH=//p' "$marker" 2>/dev/null || true)
    pull_profile=$(sed -n 's/^PULL_PROFILE=//p' "$marker" 2>/dev/null || true)
    pull_tag=$(sed -n 's/^PULL_TAG=//p' "$marker" 2>/dev/null || true)

    local current_arch; current_arch=$(runtime_detect_arch)
    if [ "${pull_arch:-}" != "$current_arch" ]; then
        runtime_img_msg info "拉取标记架构 (${pull_arch:-?}) 与当前 (${current_arch}) 不匹配，将重新构建"
        return 1
    fi

    if declare -F ensure_deploy_profile >/dev/null 2>&1; then
        ensure_deploy_profile
    fi
    local current_profile="${EASYAIOT_DEPLOY_PROFILE:-full}"
    if [ "${pull_profile:-full}" != "$current_profile" ]; then
        runtime_img_msg info "拉取标记形态 (${pull_profile:-full}) 与当前 (${current_profile}) 不匹配，将重新构建"
        return 1
    fi

    local -a check_images=(
        "ai-service:${pull_tag:-latest}"
        "video-service:${pull_tag:-latest}"
        "iot-gateway:${pull_tag:-latest}"
    )
    case "${pull_profile:-full}" in
        mini)     check_images+=("web-service:${pull_tag:-latest}-mini") ;;
        standard) check_images+=("web-service:${pull_tag:-latest}-standard") ;;
        *)        check_images+=("web-service:${pull_tag:-latest}") ;;
    esac
    if [ "${pull_profile:-full}" = "full" ]; then
        check_images+=("app-service:${pull_tag:-latest}")
    fi

    for img in "${check_images[@]}"; do
        if ! docker image inspect "$img" >/dev/null 2>&1; then
            runtime_img_msg warn "拉取的镜像 ${img} 不在本地，将重新构建"
            return 1
        fi
    done

    runtime_img_msg ok "检测到预构建镜像已就绪 (${pull_arch}, ${pull_profile:-full})，跳过构建"
    return 0
}

# 判断 docker push 输出是否为可重试的瞬时网络/服务端错误
runtime_docker_push_is_transient_error() {
    local output="$1"
    echo "$output" | grep -qiE \
        'use of closed network connection|connection reset by peer|broken pipe|unexpected EOF|EOF|i/o timeout|temporary failure|timeout|network is unreachable|TLS handshake timeout|read: connection|write tcp.*443:|failed to copy: failed to do request|502 Bad Gateway|503 Service Unavailable|504 Gateway Timeout|server closed idle connection|transport is closing'
}

# 带重试执行镜像上传命令（docker push / docker manifest push 等；已推送层会自动复用）
# 环境变量: EASYAIOT_DOCKER_PUSH_RETRIES（默认 5）, EASYAIOT_DOCKER_PUSH_RETRY_DELAY（默认 10，秒，指数退避）
runtime_docker_upload_with_retry() {
    local label="$1"
    shift
    local max_retries="${EASYAIOT_DOCKER_PUSH_RETRIES:-5}"
    local base_delay="${EASYAIOT_DOCKER_PUSH_RETRY_DELAY:-10}"
    local attempt=1 rc=0 delay=0 log_file push_out

    log_file=$(mktemp "${TMPDIR:-/tmp}/easyaiot_docker_push.XXXXXX") || return 1

    while [ "$attempt" -le "$max_retries" ]; do
        if [ "$attempt" -gt 1 ]; then
            case $((attempt - 1)) in
                1) delay=$base_delay ;;
                2) delay=$((base_delay * 2)) ;;
                3) delay=$((base_delay * 4)) ;;
                4) delay=$((base_delay * 8)) ;;
                *) delay=120 ;;
            esac
            [ "$delay" -gt 120 ] && delay=120
            runtime_img_msg warn "${label} 失败（第 ${attempt}/${max_retries} 次），${delay}s 后重试（已推送层会自动复用）..."
            sleep "$delay"
        fi

        : > "$log_file"
        "$@" 2>&1 | tee "$log_file"
        rc=${PIPESTATUS[0]}
        if [ "$rc" -eq 0 ]; then
            rm -f "$log_file"
            return 0
        fi

        push_out=$(cat "$log_file" 2>/dev/null || true)

        # 认证/权限错误不可重试
        if echo "$push_out" | grep -qiE 'no basic auth credentials|unauthorized|authentication required|authorization failed|push access denied|access denied|denied: requested access'; then
            rm -f "$log_file"
            return "$rc"
        fi

        if [ "$attempt" -ge "$max_retries" ] || ! runtime_docker_push_is_transient_error "$push_out"; then
            rm -f "$log_file"
            return "$rc"
        fi

        attempt=$((attempt + 1))
    done

    rm -f "$log_file"
    return 1
}

# 带重试的 docker push
runtime_docker_push_with_retry() {
    runtime_docker_upload_with_retry "推送 $1" docker push "$1"
}

# 轻量消息输出（source 方有 print_* 时复用）
runtime_img_msg() {
    local level="$1" text="$2"
    case "$level" in
        info)
            if declare -F print_info >/dev/null 2>&1; then print_info "$text"; else echo "[INFO] $text"; fi ;;
        warn)
            if declare -F print_warning >/dev/null 2>&1; then print_warning "$text"; else echo "[WARN] $text"; fi ;;
        ok)
            if declare -F print_success >/dev/null 2>&1; then print_success "$text"; else echo "[OK] $text"; fi ;;
        error)
            if declare -F print_error >/dev/null 2>&1; then print_error "$text"; else echo "[ERROR] $text"; fi ;;
        *) echo "$text" ;;
    esac
}

# ============================================================================
# 统一镜像获取（install / install_business 共用）
# ============================================================================
# 交互询问拉取或本地构建；成功拉取后设置 EASYAIOT_SKIP_BUILD 与 EASYAIOT_SKIP_IMAGE_PROMPT
runtime_images_acquire() {
    local skip_prompt="${1:-0}"
    local do_local_build=0

    if [ "$skip_prompt" != "1" ] && [ "${EASYAIOT_SKIP_IMAGE_PROMPT:-0}" != "1" ]; then
        if [ -t 0 ]; then
            runtime_img_msg info "========================================"
            runtime_img_msg info "  镜像获取方式"
            runtime_img_msg info "========================================"
            runtime_img_msg info "  1) 拉取预构建镜像：从远程仓库下载（快速，默认）"
            runtime_img_msg info "  2) 本地构建：编译并制作 Docker 镜像（耗时较长）"
            echo ""
            read -r -p "是否从远程仓库下载预构建的镜像？(Y/n) " _pull_response
            case "${_pull_response:-Y}" in
                n|N|no|NO) do_local_build=1 ;;
                *) do_local_build=0 ;;
            esac
        else
            runtime_img_msg info "非交互模式，默认拉取预构建镜像"
        fi
    elif runtime_images_pulled_ready; then
        export EASYAIOT_SKIP_BUILD=1
        export EASYAIOT_SKIP_IMAGE_PROMPT=1
        return 0
    fi

    if [ "$do_local_build" -eq 0 ]; then
        runtime_img_msg info "正在拉取预构建镜像..."
        runtime_images_prepare_pull_interactive
        runtime_images_export_for_invoke
        if runtime_images_invoke pull; then
            runtime_img_msg ok "预构建镜像拉取成功"
            export EASYAIOT_SKIP_BUILD=1
        else
            runtime_img_msg warn "预构建镜像拉取失败，将尝试本地构建"
            do_local_build=1
        fi
    fi

    export EASYAIOT_SKIP_IMAGE_PROMPT=1
    [ "$do_local_build" -eq 1 ] && return 1
    return 0
}

# install 流程：拉取标记或本次拉取成功则跳过构建
runtime_images_should_skip_build() {
    if [ "${EASYAIOT_SKIP_BUILD:-0}" = "1" ]; then
        return 0
    fi
    if runtime_images_pulled_ready; then
        export EASYAIOT_SKIP_BUILD=1
        return 0
    fi
    return 1
}

# 委托 runtime_image.sh（参数原样透传，如 --profile mini --tag v1.0）
runtime_images_invoke() {
    if [ ! -f "$RUNTIME_IMAGE_SCRIPT" ]; then
        runtime_img_msg error "运行时镜像脚本不存在: ${RUNTIME_IMAGE_SCRIPT}"
        return 1
    fi
    bash "$RUNTIME_IMAGE_SCRIPT" "$@"
}

# 显示运行时镜像管理用法摘要
runtime_images_usage() {
    cat <<EOF
运行时镜像管理（业务模块 DEVICE/AI/VIDEO/WEB/APP，不含中间件；APP 仅 full）

推荐入口（交互式，无需携带参数，默认部署形态 full）:
  bash .scripts/docker/install_linux.sh pull
  bash .scripts/docker/install_linux.sh build-runtime
  bash .scripts/docker/install_business_linux.sh pull
  bash .scripts/docker/install_business_linux.sh build-runtime

远程仓库配置:
  编辑 .scripts/docker/runtime_registry.conf 中的 REGISTRY=
  或 export EASYAIOT_RUNTIME_REGISTRY=your.cool/namespace/project/

非交互（CI）可用环境变量:
  EASYAIOT_RUNTIME_REGISTRY  远程仓库地址
  EASYAIOT_DEPLOY_PROFILE    部署形态 mini|standard|full
  EASYAIOT_RUNTIME_TAG         镜像标签（默认 latest）
  EASYAIOT_RUNTIME_PUSH=1      构建后推送（仅 build-runtime）
  EASYAIOT_RUNTIME_BUILD_ALL_PROFILES=1  构建全部形态（仅 build-runtime）
  EASYAIOT_RUNTIME_FORCE_REBUILD=1       强制重建（交互式 build-runtime 默认开启）
  EASYAIOT_RUNTIME_FORCE_REBUILD=0       复用本地镜像（已存在则跳过构建，直接推送）
  EASYAIOT_SKIP_REGISTRY_AUTH_CHECK=1     跳过 build-runtime 前的 CNB 登录/推送权限检查
  EASYAIOT_DOCKER_PUSH_RETRIES=5          推送失败时的最大重试次数（默认 5，网络抖动时自动退避重试）
  EASYAIOT_DOCKER_PUSH_RETRY_DELAY=10     推送重试初始间隔秒数（默认 10，指数退避，上限 120s）

build-runtime 会在构建开始前校验 CNB 登录与推送权限；未登录请先执行:
  docker login docker.cnb.cool -u cnb -p \${CNB_TOKEN}
  详见 https://docs.cnb.cool/zh/artifact/docker.html

也可直接调用 runtime_image.sh（支持命令行参数，适合 CI）:
  bash .scripts/docker/runtime_image.sh pull
  bash .scripts/docker/runtime_image.sh build --push
EOF
}

# 初始化默认仓库地址
runtime_load_registry
