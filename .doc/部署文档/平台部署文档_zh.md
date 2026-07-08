# EasyAIoT 平台部署文档

> 首次部署请参阅 [快速开始](#快速开始)；进阶运维、GPU、数据库与故障排查见 [部署最佳实践.md](./部署最佳实践.md)。

---

## 目录

- [概述](#概述)
- [两种使用模式](#两种使用模式)
- [快速开始](#快速开始)
- [部署规格](#部署规格)
- [脚本命令参考](#脚本命令参考)
- [服务访问与端口](#服务访问与端口)
- [常见问题](#常见问题)
- [环境要求](#环境要求)

---

## 概述

EasyAIoT 采用 **Docker 容器化 + 统一安装脚本** 部署，平台由基础中间件与 DEVICE / AI / VIDEO / WEB / APP 等业务模块组成。

| 模块 | 目录 | 说明 |
|------|------|------|
| 基础服务 | `.scripts/docker` | Nacos、PostgreSQL、Redis、Kafka、MinIO 等 |
| DEVICE | `DEVICE/` | 设备管理与 API 网关（Java / Spring Cloud） |
| AI | `AI/` | 模型训练、推理（Python） |
| VIDEO | `VIDEO/` | 视频流处理、告警、录像（Python） |
| WEB | `WEB/` | 管理控制台（Vue 3） |
| APP | `APP/` | 移动端 H5（仅 **full** 规格） |

**统一入口脚本**（下文以 Linux x86 为例）：

| 系统 | 脚本 |
|------|------|
| Linux x86 | `.scripts/docker/install_linux.sh` |
| Linux ARM | `.scripts/docker/install_linux_arm.sh` |
| 银河麒麟 | `.scripts/docker/install_linux_kylin.sh` |
| macOS | `.scripts/docker/install_mac.sh` |
| Windows | `.scripts/docker/install_win.ps1` |

---

## 两种使用模式

统一入口脚本支持 **交互引导** 与 **指定命令** 两种用法，底层能力一致，可按场景选择：

| | 交互引导 | 指定命令 |
|---|---|---|
| **入口** | 无参数 / `menu` / `interactive` | `<命令> [参数]` |
| **适用场景** | 首次部署、现场运维、问题排查 | 开发调试、脚本化运维、CI/CD |
| **操作方式** | 中文菜单，数字选择 | 直接执行子命令 |
| **执行后** | 自动回到当前菜单层 | 执行完毕即退出 |

```bash
# 交互引导
sudo .scripts/docker/install_linux.sh

# 指定命令
sudo .scripts/docker/install_linux.sh install
.scripts/docker/install_linux.sh status
```

**选型建议：**

- 日常手动运维、不熟悉命令参数 → 交互引导
- 已知目标操作、需写入脚本或定时任务 → 指定命令（**禁止**在 Cron/CI 中无参数调用，否则会阻塞等待输入）

### 交互引导：菜单结构

**根菜单**

```
  1) 部署 — 安装、启停、更新、状态、日志
  2) 分析 — 日志合并、磁盘占用、健康检查
  0) 退出
```

**【部署】子菜单**

| # | 操作 | 等价命令 |
|:-:|------|----------|
| 1 | 首次安装并启动 | `install` |
| 2 | 启动所有服务 | `start` |
| 3 | 停止所有服务 | `stop` |
| 4 | 重启所有服务 | `restart` |
| 5 | 查看运行状态 | `status` |
| 6 | 查看服务日志 | `logs` |
| 7 | 验证服务健康 | `verify` |
| 8 | 更新镜像并重启 | `update` |
| 9 | 检查 Docker 环境 | `check` |
| 10 | 查看部署规格 | `profile` |
| 11 | 完整命令行帮助 | `help` |

**【分析】子菜单**

| # | 操作 | 等价命令 |
|:-:|------|----------|
| 1 | 多模块日志合并（各源约 500 行） | `analyze-logs` |
| 2 | 磁盘占用分析 | `analyze-disk` |
| 3 | 服务状态 + 健康验证 | `status` + `verify` |
| 4 | Docker 环境检查 | `check` |

**典型操作路径：**

| 场景 | 交互路径 |
|------|----------|
| 首次部署 | 1 → 1 → 7 |
| 重启后拉起服务 | 1 → 2 → 7 |
| 故障信息采集 | 2 → 3 → 1 → 2 |

---

## 快速开始

### 环境前提

- 操作系统：**Ubuntu 24.04+**（建议 26.04）
- Docker + Docker Compose **v2.35+**
- 磁盘可用空间 **≥ 300 GB**

```bash
docker --version && docker compose version && docker ps
```

### 方式一：交互引导

```bash
git clone https://gitee.com/volara/easyaiot.git
cd easyaiot

sudo .scripts/docker/install_linux.sh
# 1 部署 → 1 首次安装 → 7 健康验证
```

首次安装会交互选择部署规格，完成后浏览器访问 `http://<服务器IP>:8888`。

### 方式二：指定命令

```bash
git clone https://gitee.com/volara/easyaiot.git
cd easyaiot

# 可选：拉取预构建镜像，缩短 install 耗时
sudo .scripts/docker/install_linux.sh pull

sudo .scripts/docker/install_linux.sh install
.scripts/docker/install_linux.sh verify
```

### 安装耗时

| 情况 | 预计耗时 |
|------|----------|
| 已拉取预构建镜像 | 10～30 分钟 |
| 本地完整构建 | 30 分钟～数小时 |

`install` 执行流程：选择部署规格 → 环境检查 → 创建网络 → 按序部署中间件与业务模块 → 健康等待。详见 [部署最佳实践 - 一键部署](./部署最佳实践.md#一键部署与分步部署)。

---

## 部署规格

首次 `install` 时交互选择，结果保存在 `.scripts/docker/.deploy_profile`，后续 `start` / `stop` / `update` 自动沿用。

| 选项 | 名称 | 推荐内存 | 适用场景 |
|:----:|------|----------|----------|
| 1 | **mini** | ≥ 4 GB | 边缘节点、PoC 验证 |
| 2 | **standard** | ≥ 16 GB | 常规生产 |
| 3 | **full**（默认） | ≥ 20 GB | 完整功能，含 APP H5 |

```bash
.scripts/docker/install_linux.sh profile                              # 查看当前规格
export EASYAIOT_DEPLOY_PROFILE=full && sudo .../install_linux.sh install  # 非交互指定
```

各规格服务差异见 [部署最佳实践 - 部署规格选型](./部署最佳实践.md#部署规格选型)。

---

## 脚本命令参考

### 命令一览

| 命令 | 说明 |
|------|------|
| `install` | 首次安装并启动 |
| `start` / `stop` / `restart` | 启停控制 |
| `status` | 查看运行状态 |
| `logs [模块]` | 查看日志，如 `logs VIDEO` |
| `verify` | 健康检查 |
| `check` | Docker 环境检查 |
| `update` | 更新镜像并重启 |
| `pull` | 拉取预构建镜像 |
| `build` | 本地重新构建镜像 |
| `profile` | 查看部署规格 |
| `analyze-logs` | 多模块日志合并 |
| `analyze-disk` | 磁盘占用分析 |
| `diagnose` | 进入【分析】子菜单 |
| `clean` | 清理容器与镜像 ⚠️（含数据卷） |
| `help` | 显示帮助 |
| `menu` | 打开交互引导 |

### 非交互日志采集

```bash
cd .scripts/docker

./analyze_merge_logs.sh --non-interactive \
  --modules dev-iot-sink,dev-iot-message,biz-video --lines 500 --save

./analyze_merge_logs.sh --non-interactive --modules DEVICE --save
./analyze_disk_usage.sh --save --top 15
```

### 模式对照

| 操作 | 交互引导 | 指定命令 |
|------|----------|----------|
| 首次安装 | 1 → 1 | `install` |
| 启动服务 | 1 → 2 | `start` |
| 健康检查 | 1 → 7 | `verify` |
| 日志合并 | 2 → 1 | `analyze-logs` |
| 磁盘分析 | 2 → 2 | `analyze-disk` |

### 分模块部署

```bash
cd .scripts/docker && ./install_middleware_linux.sh install   # 仅中间件
cd .scripts/docker && ./install_business_linux.sh install     # 仅业务模块
cd AI && ./install_linux.sh install                           # 单模块
```

---

## 服务访问与端口

`verify` 通过后主要访问地址：

| 服务 | 地址 |
|------|------|
| WEB 管理平台 | http://\<服务器IP\>:8888 |
| API Gateway | http://\<服务器IP\>:48080 |
| Nacos | http://\<服务器IP\>:8848/nacos |
| MinIO Console | http://\<服务器IP\>:9001 |
| AI | http://\<服务器IP\>:5000 |
| VIDEO | http://\<服务器IP\>:6000 |
| APP H5（full） | http://\<服务器IP\>:9010 |

| 端口 | 服务 |
|------|------|
| 8888 | WEB |
| 48080 | Gateway |
| 8848 | Nacos |
| 9000/9001 | MinIO |
| 5000 | AI |
| 6000 | VIDEO |
| 9010 | APP（full） |

完整端口列表见 [部署最佳实践 - 端口要求](./部署最佳实践.md#环境要求与部署前检查)。

---

## 常见问题

| 现象 | 处理 |
|------|------|
| Docker `permission denied` | `sudo usermod -aG docker $USER && newgrp docker` |
| Compose 版本过低 | `sudo apt install -y docker-compose-plugin` |
| 端口被占用 | `ss -tlnp \| grep <端口>` |
| 安装失败 | `tail .scripts/docker/logs/install_linux_*.log` |
| 服务正常但无法访问 | `verify` + 检查防火墙 |
| 磁盘不足 | `df -h /`，建议预留 ≥ 300 GB |

**故障信息采集：**

```bash
# 交互：2 分析 → 1 日志 + 2 磁盘
# 命令行：
.scripts/docker/install_linux.sh check
.scripts/docker/install_linux.sh status
.scripts/docker/install_linux.sh verify
cd .scripts/docker && ./analyze_merge_logs.sh --non-interactive --modules all --save
./analyze_disk_usage.sh --save
```

更多排查见 [部署最佳实践 - 故障排查](./部署最佳实践.md#故障排查)。

---

## 环境要求

| 项目 | 要求 |
|------|------|
| 操作系统 | Ubuntu 24.04+（建议 26.04）；亦支持 macOS、Windows、ARM、银河麒麟 |
| CPU | 最低 4 核，推荐 8 核+ |
| 内存 | 取决于部署规格（full ≥ 20 GB，推荐 32 GB） |
| 磁盘 | 最低 300 GB 可用，推荐 500 GB+ SSD |
| GPU | 可选；AI 训练/推理建议 NVIDIA GPU（CUDA 12.8） |
| Docker Compose | v2.35.0+ |

```bash
# Docker 安装（Ubuntu）
curl -fsSL https://get.docker.com | sudo sh
sudo apt install -y docker-compose-plugin
sudo usermod -aG docker $USER && newgrp docker
```

**注意事项：**

1. 首次安装建议使用 `sudo`（配置镜像加速与 RTP 端口预留）
2. 生产环境修改中间件默认密码（见 [部署最佳实践](./部署最佳实践.md#默认账号密码)）
3. `clean` 会删除数据卷，执行前务必备份
4. 切换部署规格后需重建 WEB：`cd WEB && ./install_linux.sh build`

---

**文档版本**：3.1  
**最后更新**：2026-07-08  
**脚本入口**：`.scripts/docker/install_linux.sh`（无参数 = 交互引导；`<命令>` = 直接执行）
