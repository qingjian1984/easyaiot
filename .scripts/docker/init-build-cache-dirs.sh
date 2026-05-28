#!/bin/bash
# EasyAIoT 统一构建缓存（项目根目录 .build-cache）
# 用法: source 本脚本后调用 init_easyaiot_build_cache_dirs "$EASYAIOT_ROOT"
#
# 各业务模块宿主机缓存互不共享：
#   ai / video  → pip-cache、pip-wheels
#   device                      → m2/repository（Maven）
#   web                         → pnpm-store、.build-stamp
#
# 环境变量:
#   BUILD_CACHE_UID  默认 1000
#   BUILD_CACHE_GID  默认 1000

: "${BUILD_CACHE_UID:=1000}"
: "${BUILD_CACHE_GID:=1000}"

EASYAIOT_PYTHON_CACHE_MODULES=(ai video)
EASYAIOT_BUILD_CACHE_MODULES=(ai video device web)

easyaiot_build_cache_base() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    echo "$(cd "$root" && pwd)/.build-cache"
}

easyaiot_normalize_module() {
    local module="${1,,}"
    case "$module" in
        ai|video|device|web) echo "$module" ;;
        *)
            echo "未知构建缓存模块: $1（支持: ${EASYAIOT_BUILD_CACHE_MODULES[*]}）" >&2
            return 1
            ;;
    esac
}

# 兼容旧名
easyaiot_normalize_python_cache_module() {
    easyaiot_normalize_module "$1"
}

module_cache_root() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local module
    module="$(easyaiot_normalize_module "$2")" || return 1
    echo "$(easyaiot_build_cache_base "$root")/${module}"
}

python_module_cache_root() {
    module_cache_root "$@"
}

pip_cache_build_context_dir_for() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local module="$2"
    local cache
    cache="$(python_module_cache_root "$root" "$module")/pip-cache"
    mkdir -p "${cache}/http"
    echo "$cache"
}

pip_wheels_build_context_dir_for() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local module="$2"
    local cache
    cache="$(python_module_cache_root "$root" "$module")/pip-wheels"
    mkdir -p "$cache"
    echo "$cache"
}

arm_pip_wheels_build_context_dir_for() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local module="$2"
    local cache
    module="$(easyaiot_normalize_module "$module")" || return 1
    cache="$(easyaiot_build_cache_base "$root")/arm/${module}/pip-wheels"
    mkdir -p "$cache"
    echo "$cache"
}

maven_repository_dir_for() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local repo
    repo="$(module_cache_root "$root" device)/m2/repository"
    mkdir -p "$repo"
    echo "$repo"
}

pnpm_store_dir_for() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local store
    store="$(module_cache_root "$root" web)/pnpm-store"
    mkdir -p "$store"
    echo "$store"
}

web_build_stamp_file() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local stamp
    stamp="$(module_cache_root "$root" web)/.build-stamp"
    mkdir -p "$(dirname "$stamp")"
    echo "$stamp"
}

# 兼容旧调用（默认 ai / device / web）
pip_cache_build_context_dir() {
    pip_cache_build_context_dir_for "${1:-${EASYAIOT_ROOT:-.}}" "ai"
}

pip_wheels_build_context_dir() {
    pip_wheels_build_context_dir_for "${1:-${EASYAIOT_ROOT:-.}}" "ai"
}

pip_wheels_dir() {
    pip_wheels_build_context_dir_for "$@"
}

arm_pip_wheels_build_context_dir() {
    arm_pip_wheels_build_context_dir_for "${1:-${EASYAIOT_ROOT:-.}}" "ai"
}

arm_pip_wheels_dir() {
    arm_pip_wheels_build_context_dir_for "$@"
}

maven_repository_dir() {
    maven_repository_dir_for "$@"
}

pnpm_store_dir() {
    pnpm_store_dir_for "$@"
}

migrate_legacy_python_cache_if_needed() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local base legacy_pip legacy_wheels module target

    base="$(easyaiot_build_cache_base "$root")"
    legacy_pip="${base}/pip-cache"
    legacy_wheels="${base}/pip-wheels"

    if [ -d "${legacy_pip}/http" ] && [ -n "$(ls -A "${legacy_pip}/http" 2>/dev/null)" ]; then
        for module in "${EASYAIOT_PYTHON_CACHE_MODULES[@]}"; do
            target="$(pip_cache_build_context_dir_for "$root" "$module")/http"
            if [ ! -d "$target" ] || [ -z "$(ls -A "$target" 2>/dev/null)" ]; then
                mkdir -p "$target"
                cp -a "${legacy_pip}/http/." "$target/" 2>/dev/null || true
            fi
        done
    fi

    if find "$legacy_wheels" -maxdepth 1 -type f 2>/dev/null | grep -q .; then
        for module in "${EASYAIOT_PYTHON_CACHE_MODULES[@]}"; do
            target="$(pip_wheels_build_context_dir_for "$root" "$module")"
            if ! find "$target" -maxdepth 1 -type f 2>/dev/null | grep -q .; then
                mkdir -p "$target"
                cp -a "${legacy_wheels}/." "$target/" 2>/dev/null || true
            fi
        done
    fi
}

migrate_legacy_device_web_cache_if_needed() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local base legacy_m2 legacy_pnpm target

    base="$(easyaiot_build_cache_base "$root")"
    legacy_m2="${base}/m2/repository"
    legacy_pnpm="${base}/pnpm-store"

    if [ -d "$legacy_m2" ] && [ -n "$(ls -A "$legacy_m2" 2>/dev/null)" ]; then
        target="$(maven_repository_dir_for "$root")"
        if [ ! -d "$target" ] || [ -z "$(ls -A "$target" 2>/dev/null)" ]; then
            mkdir -p "$target"
            cp -a "${legacy_m2}/." "$target/" 2>/dev/null || true
        fi
    fi

    if [ -d "$legacy_pnpm" ] && [ -n "$(find "$legacy_pnpm" -mindepth 1 -print -quit 2>/dev/null)" ]; then
        target="$(pnpm_store_dir_for "$root")"
        if [ ! -d "$target" ] || [ -z "$(find "$target" -mindepth 1 -print -quit 2>/dev/null)" ]; then
            mkdir -p "$target"
            cp -a "${legacy_pnpm}/." "$target/" 2>/dev/null || true
        fi
    fi
}

init_easyaiot_build_cache_dirs() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local base module
    base="$(easyaiot_build_cache_base "$root")"

    for module in "${EASYAIOT_PYTHON_CACHE_MODULES[@]}"; do
        pip_cache_build_context_dir_for "$root" "$module" >/dev/null
        pip_wheels_build_context_dir_for "$root" "$module" >/dev/null
        arm_pip_wheels_build_context_dir_for "$root" "$module" >/dev/null
    done

    maven_repository_dir_for "$root" >/dev/null
    pnpm_store_dir_for "$root" >/dev/null
    web_build_stamp_file "$root" >/dev/null

    migrate_legacy_python_cache_if_needed "$root"
    migrate_legacy_device_web_cache_if_needed "$root"

    if [ "$(id -u)" -eq 0 ]; then
        chown -R "${BUILD_CACHE_UID}:${BUILD_CACHE_GID}" "${base}" 2>/dev/null || true
    elif command -v chown >/dev/null 2>&1; then
        chown -R "${BUILD_CACHE_UID}:${BUILD_CACHE_GID}" "${base}" 2>/dev/null || \
            print_build_cache_chown_hint "${base}" 2>/dev/null || true
    fi
}

# 兼容旧函数名（参数为 EASYAIOT_ROOT，非模块目录）
init_project_build_cache_dirs() {
    init_easyaiot_build_cache_dirs "$@"
}

print_build_cache_chown_hint() {
    echo "[build-cache] 提示: 若构建报权限错误，请执行: sudo chown -R ${BUILD_CACHE_UID}:${BUILD_CACHE_GID} $1"
}

enable_docker_buildkit() {
    export DOCKER_BUILDKIT=1
    export BUILDKIT_PROGRESS="${BUILDKIT_PROGRESS:-plain}"
    # 使用 BuildKit 内置 Dockerfile 前端，不依赖 docker.io/docker/dockerfile 镜像
    # （部分 registry 镜像站缺少该镜像，会导致 # syntax=docker/dockerfile:1.4 构建失败）
}

# 可选：仍使用外部 Dockerfile 语法镜像时预拉（export DOCKERFILE_FRONTEND_IMAGE=docker/dockerfile:1.4）
ensure_dockerfile_frontend() {
    local img="${DOCKERFILE_FRONTEND_IMAGE:-}"
    [ -n "$img" ] || return 0
    if ! docker image inspect "$img" >/dev/null 2>&1; then
        docker pull "$img" 2>/dev/null || \
            echo "[build-cache] 警告: 无法预拉 Dockerfile 前端 $img，请确认 BuildKit 已启用或网络可达" >&2
    fi
}

arm_docker_images_dir() {
    local root="${1:-${EASYAIOT_ROOT:-.}}"
    local dir
    dir="$(easyaiot_build_cache_base "$root")/arm/docker-images"
    mkdir -p "$dir"
    echo "$dir"
}
