#!/bin/bash
# ============================================
# EasyAIoT ARM Python 离线 wheel（按模块隔离）
# 目录: .build-cache/arm/{ai,video}/pip-wheels
# ============================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_command() { command -v "$1" >/dev/null 2>&1; }

image_to_tar_name() {
    echo "$1" | sed 's#[/:]#_#g'
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EASYAIOT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=init-build-cache-dirs.sh
source "${SCRIPT_DIR}/init-build-cache-dirs.sh"

AI_DIR="${EASYAIOT_ROOT}/AI"
VIDEO_DIR="${EASYAIOT_ROOT}/VIDEO"

ARM_BASE_IMAGE="${ARM_BASE_IMAGE:-pytorch/manylinuxaarch64-builder:cuda12.9}"
DOCKER_IMAGES_DIR="$(arm_docker_images_dir "$EASYAIOT_ROOT")"

# 参数: AI | VIDEO（模块名，映射到 ai / video 缓存目录）
MODULE_ARG="${1:-AI}"
case "${MODULE_ARG^^}" in
    AI) CACHE_MODULE="ai"; REQ_SOURCE="${AI_DIR}/requirements.txt" ;;
    VIDEO) CACHE_MODULE="video"; REQ_SOURCE="${VIDEO_DIR}/requirements.txt" ;;
    *)
        print_error "未知模块: $MODULE_ARG（支持 AI、VIDEO）"
        exit 1
        ;;
esac

PIP_WHEELS_DIR="$(arm_pip_wheels_build_context_dir_for "$EASYAIOT_ROOT" "$CACHE_MODULE")"

init_easyaiot_build_cache_dirs "$EASYAIOT_ROOT"

if ! check_command docker; then
    print_error "未检测到 docker，请先安装 Docker"
    exit 1
fi

download_docker_image() {
    local image="$1"
    local tar_file="${DOCKER_IMAGES_DIR}/$(image_to_tar_name "$image").tar"

    print_info "拉取镜像: $image"
    docker pull "$image"
    print_info "保存镜像到: $tar_file"
    docker save -o "$tar_file" "$image"
    print_success "镜像已保存: $image"
}

if [ "${CACHE_DOCKER_IMAGE:-0}" = "1" ]; then
    print_info "下载并保存 Docker 基础镜像..."
    download_docker_image "$ARM_BASE_IMAGE"
else
    print_info "跳过 Docker 镜像缓存（启用: CACHE_DOCKER_IMAGE=1 $0）"
fi

# 展开 -r includes，避免合并文件中出现无法解析的相对引用（与 x86 版本逻辑一致）
append_requirements_file() {
    local req_file="$1"
    local out_file="$2"
    local req_dir line include_path

    [ -f "$req_file" ] || return 0
    req_dir="$(cd "$(dirname "$req_file")" && pwd)"

    while IFS= read -r line || [ -n "$line" ]; do
        line="${line%%#*}"
        line="$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
        [ -z "$line" ] && continue
        case "$line" in
            --index-url*) ;;
            -r*)
                include_path="${line#-r }"
                include_path="${line#-r}"
                include_path="$(echo "$include_path" | sed 's/^[[:space:]]*//')"
                if [[ "$include_path" != /* ]]; then
                    include_path="${req_dir}/${include_path}"
                fi
                append_requirements_file "$include_path" "$out_file"
                ;;
            *)
                echo "$line" >> "$out_file"
                ;;
        esac
    done < "$req_file"
}

prepare_flattened_requirements_arm() {
    local req_file="$1"  # requirements.txt (ARM 版含 torch)
    local out_file out_dir
    # ★ 在项目 .build-cache 下创建临时文件，确保 Docker 挂载时路径可达
    out_dir="$(easyaiot_build_cache_base "$EASYAIOT_ROOT")/tmp"
    mkdir -p "$out_dir"
    out_file="$(mktemp "${out_dir}/flat-requirements-arm-XXXXXX")"
    : > "$out_file"
    echo "--index-url https://mirrors.cloud.tencent.com/pypi/simple" >> "$out_file"
    if [ ! -f "$req_file" ]; then
        print_error "requirements 不存在: $req_file"
        rm -f "$out_file"
        return 1
    fi
    # 先做 onnxruntime-gpu → onnxruntime 替换，再展开 -r 引用
    local tmp_req
    tmp_req="$(mktemp "${out_dir}/tmp-req-arm-XXXXXX")"
    sed 's/onnxruntime-gpu>=/onnxruntime>=/g' "$req_file" > "$tmp_req"
    append_requirements_file "$tmp_req" "$out_file"
    rm -f "$tmp_req"
    echo "$out_file"
}

if [ "${CLEAR_PIP_WHEELS:-0}" = "1" ]; then
    print_info "[${CACHE_MODULE}] 清理旧 ARM pip wheel..."
    find "$PIP_WHEELS_DIR" -maxdepth 1 -type f -delete 2>/dev/null || true
fi

download_pip_packages() {
    local flat_req
    flat_req="$(prepare_flattened_requirements_arm "$REQ_SOURCE")"
    trap 'rm -f "$flat_req"' RETURN

    print_info "[${CACHE_MODULE}] ARM pip wheel → ${PIP_WHEELS_DIR}"
    print_info "[${CACHE_MODULE}] requirements 扁平化: $(wc -l < "$flat_req") 行"

    # ★ 挂载前校验：确保 flat_req 是普通文件（非目录），防止 Docker 创建空目录
    if [ ! -f "$flat_req" ]; then
        print_error "[${CACHE_MODULE}] 扁平化 requirements 文件无效或为目录: ${flat_req}"
        return 1
    fi

    set +e
    docker run --rm \
        -v "${flat_req}:/tmp/requirements-arm.flat:ro" \
        -v "${PIP_WHEELS_DIR}:/wheels" \
        "$ARM_BASE_IMAGE" \
        /bin/bash -lc '
set -e
if [ -x /opt/python/cp311-cp311/bin/pip3.11 ]; then
    PIP_BIN=/opt/python/cp311-cp311/bin/pip3.11
elif [ -x /opt/python/cp310-cp310/bin/pip3.10 ]; then
    PIP_BIN=/opt/python/cp310-cp310/bin/pip3.10
elif command -v pip3 >/dev/null 2>&1; then
    PIP_BIN=$(command -v pip3)
else
    echo "未找到可用 pip3"
    exit 1
fi
"$PIP_BIN" --version
"$PIP_BIN" download -r /tmp/requirements-arm.flat -d /wheels --timeout 120 --retries 3 -i https://mirrors.cloud.tencent.com/pypi/simple
'
    local docker_download_status=$?
    set -e

    if [ $docker_download_status -eq 0 ]; then
        print_success "[${CACHE_MODULE}] pip wheel 下载完成（与目标容器 ABI 一致）"
        return 0
    fi

    if [ "${ALLOW_HOST_PIP_FALLBACK:-0}" != "1" ]; then
        print_error "[${CACHE_MODULE}] 容器内下载失败（默认禁用本机回退，避免 ABI 不匹配）"
        print_info "如确需回退: ALLOW_HOST_PIP_FALLBACK=1 $0 $MODULE_ARG"
        return 1
    fi

    print_warning "[${CACHE_MODULE}] 容器内下载失败，使用本机 python3 回退..."
    python3 -m pip download -r "$flat_req" -d "$PIP_WHEELS_DIR" --timeout 120 --retries 3 \
        -i https://mirrors.cloud.tencent.com/pypi/simple
    print_warning "已使用本机环境回退下载，可能与目标容器 ABI 不一致"
    return 0
}

download_pip_packages

du -sh "$(easyaiot_build_cache_base "$EASYAIOT_ROOT")/arm" 2>/dev/null || true
print_success "ARM 预下载完成: ${PIP_WHEELS_DIR}"
