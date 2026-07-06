# shellcheck shell=bash
# 共享 Docker registry-mirrors 配置（供 install_business_linux.sh 等轻量脚本 source）
# 调用方需提供: check_command, print_info, print_success, print_warning, print_error

DOCKER_MIRROR="${DOCKER_MIRROR:-https://docker.m.daocloud.io/}"

restart_docker_if_active() {
    if systemctl is-active --quiet docker; then
        print_info "正在重启 Docker 服务以使配置生效..."
        systemctl daemon-reload
        systemctl restart docker
        print_success "Docker 服务已重启"
    fi
}

# 配置 Docker 镜像源
# 优先 jq；无 jq 用 python3；两者皆无且 daemon.json 已存在则跳过（不盲改未知 JSON）
configure_docker_mirror() {
    print_info "配置 Docker 镜像源..."

    local config_file="/etc/docker/daemon.json"

    if [ "$EUID" -ne 0 ]; then
        print_warning "配置 Docker 镜像源需要 root 权限，跳过此步骤"
        return 0
    fi
    mkdir -p /etc/docker

    if [ ! -f "$config_file" ]; then
        printf '{\n  "registry-mirrors": ["%s"]\n}\n' "$DOCKER_MIRROR" > "$config_file"
        print_success "已写入 Docker 镜像源配置: $DOCKER_MIRROR"
        restart_docker_if_active
        return 0
    fi

    if check_command jq; then
        if jq -e --arg m "${DOCKER_MIRROR%/}" \
            '(.["registry-mirrors"] // []) | map(rtrimstr("/")) == [$m]' \
            "$config_file" > /dev/null 2>&1; then
            print_success "Docker 镜像源配置已就绪（$DOCKER_MIRROR）"
            return 0
        fi
        local tmp_json
        tmp_json=$(mktemp)
        if jq --arg m "$DOCKER_MIRROR" '.["registry-mirrors"] = [$m]' "$config_file" > "$tmp_json" 2>/dev/null; then
            mv "$tmp_json" "$config_file"
            print_success "Docker 镜像源已更新为 $DOCKER_MIRROR"
            restart_docker_if_active
            return 0
        fi
        rm -f "$tmp_json"
        print_error "解析 $config_file 失败（非法 JSON？），请手动检查"
        return 1
    fi

    if check_command python3; then
        local rc=0
        python3 - "$config_file" "$DOCKER_MIRROR" <<'PYEOF' || rc=$?
import json, sys
path, mirror = sys.argv[1], sys.argv[2]
cfg = json.load(open(path))
cur = [m.rstrip('/') for m in cfg.get('registry-mirrors', []) if isinstance(m, str)]
if cur == [mirror.rstrip('/')]:
    sys.exit(0)
cfg['registry-mirrors'] = [mirror]
json.dump(cfg, open(path, 'w'), indent=2, ensure_ascii=False)
sys.exit(3)
PYEOF
        case $rc in
            0) print_success "Docker 镜像源配置已就绪（$DOCKER_MIRROR）" ;;
            3) print_success "Docker 镜像源已更新为 $DOCKER_MIRROR"; restart_docker_if_active ;;
            *) print_error "解析 $config_file 失败（非法 JSON？），请手动检查"; return 1 ;;
        esac
        return 0
    fi

    print_warning "未安装 jq/python3 且 $config_file 已存在，跳过自动配置（请手动确认 registry-mirrors 含 $DOCKER_MIRROR）"
}
