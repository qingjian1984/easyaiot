# EasyAIoT 安装脚本说明

本目录（`.scripts/docker/`）下共 14 个 `install*.sh` 脚本，按 **"装什么"**（全栈 / 只中间件 / 只业务）和 **"什么 OS / 架构"** 两个维度组织。

## ① 全栈总入口（中间件 + 业务，按 OS / 架构）

| 脚本 | 作用 | 适用环境 |
|---|---|---|
| [install_linux.sh](./install_linux.sh) | **核心总入口**：install / start / stop / restart / build / pull / verify / profile / diagnose … 含中间件 + 所有业务模块 | 标准 x86_64 Linux（WSL2 用这个） |
| [install_linux_arm.sh](./install_linux_arm.sh) | 同上，ARM 架构版 | ARM Linux（飞腾 / 鲲鹏） |
| [install_linux_centos.sh](./install_linux_centos.sh) | **系统适配壳**：检测发行版 / SELinux / firewalld，装或升级 Docker CE，配镜像源，放行端口；然后转交 install_linux.sh（命令菜单一致） | CentOS / Rocky / Alma / RHEL (x86) |
| [install_linux_centos_arm.sh](./install_linux_centos_arm.sh) | 同上 ARM 版，转交 install_linux_arm.sh | CentOS 系 ARM |
| [install_linux_kylin.sh](./install_linux_kylin.sh) | 麒麟系统适配入口 | 银河麒麟等国产 Linux |
| [install_linux_openeuler.sh](./install_linux_openeuler.sh) | openEuler 24.x 适配：卸旧 docker-engine、装 Docker CE、修 `$releasever` 仓库 404、配 DNS / 镜像源；转交 install_linux.sh | openEuler |
| [install_mac.sh](./install_mac.sh) | macOS **仅镜像部署**（bootstrap / check / install / pull，不支持本地 build） | macOS（需 Docker Desktop + bash4） |
| [install_windows.sh](./install_windows.sh) | Windows **仅镜像部署**（Git Bash 运行） | Windows（Docker Desktop / WSL2） |

## ② 桌面端共用逻辑（内部，不直接调）

| 脚本 | 作用 |
|---|---|
| [install_desktop_common.sh](./install_desktop_common.sh) | mac / win 镜像部署共用逻辑，被 `install_mac.sh` / `install_windows.sh` source 后调 `desktop_main`；只拉预构建镜像，禁止本地 build |
| [install_middleware_mac.sh](./install_middleware_mac.sh) | **已废弃** —— 兼容旧入口，直接 `exec install_middleware_desktop.sh` |

## ③ 只装中间件（Nacos / PostgreSQL / Redis / MinIO / Kafka 等基础服务）

| 脚本 | 作用 |
|---|---|
| [install_middleware_linux.sh](./install_middleware_linux.sh) | Linux 中间件部署 / 管理（被 `install_linux.sh` 的基础服务阶段调用） |
| [install_middleware_desktop.sh](./install_middleware_desktop.sh) | 桌面端中间件（仅拉镜像 compose up，不本地 build） |

## ④ 只装业务模块（DEVICE / AI / RTC / VIDEO / WEB / APP / VISUALIZE / TRANSFORM / PANEL，不含中间件）

| 脚本 | 作用 |
|---|---|
| [install_business_linux.sh](./install_business_linux.sh) | 业务模块统一管理，委托各模块自己的 `install_linux.sh`；不含中间件 |
| [install_business_desktop.sh](./install_business_desktop.sh) | 桌面端业务一键（仅镜像，过滤 `install_desktop_common`） |

## 调用关系

```
Linux 全栈:  install_linux.sh ──┬─► install_middleware_linux.sh   (中间件)
                                └─► 各业务模块/install_linux.sh    (业务)

OS 适配壳:   centos / openeuler / kylin / arm
                  └─ 处理系统层(Docker 安装 / 镜像源 / 防火墙) ─► install_linux.sh
             （命令菜单与 install_linux.sh 完全一致，只是多了 OS 预处理）

桌面端:      install_mac.sh / install_windows.sh ──source─► install_desktop_common.sh
                  ├─► install_middleware_desktop.sh  (中间件, 仅镜像)
                  └─► install_business_desktop.sh    (业务, 仅镜像)

拆分版:      只想重装一半 ─► install_business_linux.sh    (业务)
                              install_middleware_linux.sh (中间件)
```

## 选用建议

| 场景 | 用哪个 |
|---|---|
| WSL2 / 标准 x86 Linux，首次全栈部署 | `install_linux.sh install` |
| CentOS / openEuler / 麒麟 等国产系统 | 对应的 `install_linux_*.sh`（先做 OS 适配再转交） |
| macOS / Windows | `install_mac.sh` / `install_windows.sh`（仅镜像，不支持本地 build） |
| 中间件已跑起来，只想管业务模块 | `install_business_linux.sh` |
| 只想重装 / 管理中间件 | `install_middleware_linux.sh` |
| 查看所有命令 | `install_linux.sh help` |

## 常用命令速查

```bash
# Linux 全栈（WSL2）
bash .scripts/docker/install_linux.sh install      # 首次安装并启动
bash .scripts/docker/install_linux.sh start        # 启动全部
bash .scripts/docker/install_linux.sh status       # 查看状态
bash .scripts/docker/install_linux.sh logs RTC     # 看某模块日志
bash .scripts/docker/install_linux.sh verify       # 健康检查
bash .scripts/docker/install_linux.sh help         # 完整命令列表

# 只管业务（中间件不动）
bash .scripts/docker/install_business_linux.sh install
bash .scripts/docker/install_business_linux.sh status RTC

# 指定部署形态（mini / standard / full）
EASYAIOT_DEPLOY_PROFILE=mini bash .scripts/docker/install_linux.sh install
```

> 详见各脚本头部注释，或 `install_linux.sh help`。
