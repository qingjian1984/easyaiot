# EasyAIoT 平台部署文档

> **新手推荐路径**：先读本文「快速开始」完成首次部署；进阶运维、故障排查、GPU 与数据库细节请参阅 [部署最佳实践.md](./部署最佳实践.md)。

## 目录

- [概述](#概述)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [部署规格说明](#部署规格说明)
- [脚本使用说明](#脚本使用说明)
- [模块说明](#模块说明)
- [服务端口](#服务端口)
- [常见问题](#常见问题)
- [日志管理](#日志管理)
- [部署流程建议](#部署流程建议)

---

## 概述

EasyAIoT 是云边一体化智能算法应用平台，采用 **Docker 容器化 + 统一安装脚本** 一键部署。

### 平台组成

| 模块 | 目录 | 说明 |
|------|------|------|
| 基础服务 | `.scripts/docker` | Nacos、PostgreSQL、Redis、Kafka、MinIO 等中间件 |
| DEVICE | `DEVICE/` | 设备管理与 API 网关（Java / Spring Cloud） |
| AI | `AI/` | 模型训练、推理、OCR、LLM 等（Python） |
| VIDEO | `VIDEO/` | 视频流处理、告警、录像、人脸识别（Python） |
| WEB | `WEB/` | 管理控制台（Vue 3） |
| APP | `APP/` | 移动端 H5（仅 **full** 全量规格） |

### 统一入口脚本

| 系统 | 脚本路径 |
|------|----------|
| Linux | `.scripts/docker/install_linux.sh` |
| macOS | `.scripts/docker/install_mac.sh` |
| Windows | `.scripts/docker/install_win.ps1` |

> 下文以 **Linux** 为主；macOS / Windows 命令将 `install_linux.sh` 替换为对应脚本即可。

---

## 环境要求

### 系统与硬件

| 项目 | 要求 |
|------|------|
| **操作系统** | **Ubuntu 24.04 LTS 及以上**（**建议 Ubuntu 26.04 LTS**）；亦支持 macOS 10.15+、Windows 10/11 |
| **CPU** | 最低 4 核，推荐 8 核+ |
| **内存** | 取决于部署规格（见下表）；full 规格最低 20 GB，推荐 32 GB |
| **磁盘** | **最低 300 GB 可用空间**，推荐 500 GB+ SSD |
| **GPU** | 可选；AI 训练/推理建议 NVIDIA GPU（CUDA 12.8） |

### 软件依赖

| 软件 | 版本要求 | 验证命令 |
|------|----------|----------|
| Docker | 已安装且 daemon 可访问 | `docker --version` |
| Docker Compose | **v2.35.0+**（`docker compose` 插件） | `docker compose version` |
| curl | 健康检查用 | `curl --version` |

安装参考：

```bash
# Docker（Ubuntu）
curl -fsSL https://get.docker.com | sudo sh
sudo apt install -y docker-compose-plugin

# 权限
sudo usermod -aG docker $USER && newgrp docker
docker ps
```

### Docker 权限（Linux）

```bash
sudo usermod -aG docker $USER
newgrp docker          # 或重新登录
docker ps              # 应无 permission denied
```

首次安装建议使用 `sudo`，以便脚本配置镜像加速与 RTP 端口预留。

---

## 快速开始

### Linux 四步部署

```bash
# ① 克隆代码
git clone https://gitee.com/volara/easyaiot.git
cd easyaiot

# ② 环境自检（可选但推荐）
.scripts/docker/install_linux.sh check
.scripts/docker/detect_system_info.sh

# ③ 一键安装（首次会询问部署规格 1/2/3）
sudo .scripts/docker/install_linux.sh install

# ④ 验证并访问
.scripts/docker/install_linux.sh verify
# 浏览器打开 http://<服务器IP>:8888
```

### 安装过程中会发生什么？

1. 选择 **部署规格**（mini / standard / full）
2. 检查 Docker、Compose、容器创建能力
3. 检测宿主机 IP，创建 `easyaiot-network`
4. 按顺序部署：中间件 → DEVICE → AI → VIDEO → WEB → APP（full）
5. 输出各服务访问地址

**预计耗时**：

- 已拉取预构建镜像：**约 10～30 分钟**
- 本地完整构建：**30 分钟～数小时**（视硬件而定）

缩短安装时间：安装前执行 `.scripts/docker/install_linux.sh pull` 拉取预构建镜像（详见 [部署最佳实践 - 预构建镜像](./部署最佳实践.md#预构建镜像可选)）。

### macOS 快速开始

```bash
git clone https://gitee.com/volara/easyaiot.git && cd easyaiot
cd .scripts/docker && chmod +x install_mac.sh
./install_mac.sh install
./install_mac.sh verify
```

### Windows

请参阅 [平台Windows部署文档_zh.md](./平台Windows部署文档_zh.md)。

---

## 部署规格说明

首次 `install` 时会交互选择规格，选择结果保存在 `.scripts/docker/.deploy_profile`。

| 选项 | 名称 | 推荐内存 | 典型场景 |
|:----:|------|----------|----------|
| 1 | **mini** | ≥ 4 GB | 边缘节点、PoC 验证 |
| 2 | **standard** | ≥ 16 GB | 常规生产（不含 TDengine/EMQX 等） |
| 3 | **full**（默认） | ≥ 20 GB | 完整功能 + APP H5 |

查看当前规格：

```bash
.scripts/docker/install_linux.sh profile
```

非交互指定规格：

```bash
export EASYAIOT_DEPLOY_PROFILE=full
sudo .scripts/docker/install_linux.sh install
```

各规格服务差异详见 [部署最佳实践 - 部署规格选型](./部署最佳实践.md#部署规格选型)。

---

## 脚本使用说明

### 命令一览

| 命令 | 说明 | 示例 |
|------|------|------|
| `install` | 首次安装并启动 | `./install_linux.sh install` |
| `start` | 启动全部服务 | `./install_linux.sh start` |
| `stop` | 停止全部服务 | `./install_linux.sh stop` |
| `restart` | 重启全部服务 | `./install_linux.sh restart` |
| `status` | 查看运行状态 | `./install_linux.sh status` |
| `logs` | 查看日志 | `./install_linux.sh logs` |
| `logs <模块>` | 指定模块日志 | `./install_linux.sh logs VIDEO` |
| `build` | 本地重新构建镜像 | `./install_linux.sh build` |
| `pull` | 拉取预构建镜像 | `./install_linux.sh pull` |
| `update` | 更新并重启 | `./install_linux.sh update` |
| `verify` | 健康检查 | `./install_linux.sh verify` |
| `check` | 检查 Docker 环境 | `./install_linux.sh check` |
| `profile` | 查看部署规格 | `./install_linux.sh profile` |
| `clean` | 清理容器与镜像 ⚠️ | `./install_linux.sh clean` |
| `help` | 显示帮助 | `./install_linux.sh help` |

> 在项目根目录可将 `./install_linux.sh` 替换为 `.scripts/docker/install_linux.sh`。

### install 命令

首次部署使用。会自动按依赖顺序安装所有已启用模块，并在中间件就绪后继续后续模块。

```bash
sudo .scripts/docker/install_linux.sh install
```

### verify 命令

检查各模块端口与健康端点，全部通过时打印访问地址：

```
[SUCCESS] 所有服务运行正常！

服务访问地址:
  基础服务 (Nacos):     http://localhost:8848/nacos
  基础服务 (MinIO):     http://localhost:9000 (API), http://localhost:9001 (Console)
  Device服务 (Gateway): http://localhost:48080
  AI服务:               http://localhost:5000
  Video服务:            http://localhost:6000
  Web前端:              http://localhost:8888
```

### clean 命令 ⚠️

**危险操作**：删除容器、镜像及数据卷。执行前会要求确认（输入 `y`）。

### 分模块 / 仅业务部署

```bash
# 仅中间件
cd .scripts/docker && ./install_middleware_linux.sh install

# 仅业务模块（不含中间件）
cd .scripts/docker && ./install_business_linux.sh install

# 单模块（例：AI）
cd AI && ./install_linux.sh install
```

---

## 模块说明

### 基础服务（`.scripts/docker`）

平台运行所需的中间件，由 `install_middleware_linux.sh` 管理。

包含：Nacos、PostgreSQL、Redis、TDengine、Kafka、MinIO、Milvus、SRS、EMQX、ZLMediaKit、Node-RED 等（具体启用的服务取决于部署规格）。

### DEVICE 服务

- **技术栈**：Java 21、Spring Boot 2.7、Spring Cloud Gateway
- **核心能力**：设备接入、产品管理、规则引擎、GB28181、系统管理
- **入口端口**：48080（Gateway）

### AI 服务

- **技术栈**：Flask、PyTorch 2.9+（CUDA 12.8）
- **核心能力**：模型训练/推理/部署、OCR、语音、LLM
- **端口**：5000

### VIDEO 服务

- **技术栈**：Flask、OpenCV、FFmpeg
- **核心能力**：视频流处理、实时/快照算法、录像、告警、人脸识别
- **端口**：6000

### WEB 服务

- **技术栈**：Vue 3.4、TypeScript、Vite、Ant Design Vue 4
- **端口**：8888

### APP 服务（仅 full）

- **说明**：移动端 H5
- **端口**：9010

---

## 服务端口

### 核心端口

| 服务 | 端口 | 访问地址 |
|------|------|----------|
| WEB 前端 | 8888 | http://localhost:8888 |
| DEVICE Gateway | 48080 | http://localhost:48080 |
| AI 服务 | 5000 | http://localhost:5000 |
| VIDEO 服务 | 6000 | http://localhost:6000 |
| Nacos | 8848 | http://localhost:8848/nacos |
| MinIO API / Console | 9000 / 9001 | http://localhost:9001 |
| APP H5（full） | 9010 | http://localhost:9010 |

完整端口列表见 [部署最佳实践 - 端口要求](./部署最佳实践.md#端口要求)。

### 健康检查端点

| 模块 | 端点 |
|------|------|
| 基础服务 (Nacos) | `/nacos/actuator/health` |
| DEVICE | `/actuator/health` |
| AI | `/actuator/health` |
| VIDEO | `/actuator/health` |
| WEB | `/health` |
| APP | `/health` |

---

## 常见问题

### 1. Docker 权限不足

```
permission denied while trying to connect to the Docker daemon socket
```

```bash
sudo usermod -aG docker $USER
newgrp docker
docker ps
```

### 2. Docker Compose 版本过低

脚本要求 **v2.35.0+**：

```bash
sudo apt update && sudo apt install -y docker-compose-plugin
docker compose version
```

### 3. 端口被占用

```bash
ss -tlnp | grep <端口号>
# 停止占用进程，或修改对应 docker-compose.yml 端口映射
```

### 4. 安装中途失败

```bash
# 查看脚本日志
ls -lt .scripts/docker/logs/ | head -5
tail -100 .scripts/docker/logs/install_linux_*.log

# 查看容器状态
docker ps -a
.scripts/docker/install_linux.sh status
```

### 5. 服务已启动但浏览器无法访问

```bash
.scripts/docker/install_linux.sh verify
sudo ufw allow 8888    # 如启用了防火墙
.scripts/docker/install_linux.sh logs WEB
```

### 6. 磁盘空间不足

首次构建会占用大量磁盘，**建议预留 ≥ 300 GB**：

```bash
df -h /
docker system df
.scripts/docker/cleanup_docker_space.sh
```

### 7. 切换部署规格后前端异常

WEB 镜像与部署规格绑定，切换后需重建：

```bash
cd WEB && ./install_linux.sh build
```

更多排查方案见 [部署最佳实践 - 故障排查](./部署最佳实践.md#故障排查)。

---

## 日志管理

### 脚本日志

保存在 `.scripts/docker/logs/`：

```
install_linux_YYYYMMDD_HHMMSS.log
install_middleware_YYYYMMDD_HHMMSS.log
```

```bash
ls -lt .scripts/docker/logs/ | head -5
tail -f .scripts/docker/logs/install_linux_*.log
```

### 容器日志

```bash
.scripts/docker/install_linux.sh logs           # 全部模块摘要
cd DEVICE && docker compose logs -f            # 单模块详细日志
docker logs -f video-service                   # 单容器
```

---

## 部署流程建议

### 首次部署检查表

- [ ] Ubuntu ≥ 24.04，磁盘可用 ≥ 300 GB
- [ ] Docker + Compose v2.35+ 已安装
- [ ] 当前用户可执行 `docker ps`
- [ ] 核心端口未被占用
- [ ] 已选定部署规格（mini / standard / full）
- [ ] 执行 `install` → `verify` → 浏览器访问 `:8888`

### 日常运维

```bash
.scripts/docker/install_linux.sh start      # 开机后启动
.scripts/docker/install_linux.sh status       # 查看状态
.scripts/docker/install_linux.sh logs         # 查看日志
.scripts/docker/install_linux.sh restart      # 重启
```

### 版本更新

```bash
git pull
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

---

## 注意事项

1. **部署规格**：安装前确认内存与规格匹配；可用 `analyze_deploy_memory.sh` 分析
2. **磁盘**：本地构建 + 数据卷增长快，**最低 300 GB**，生产建议 500 GB+ SSD
3. **sudo**：首次安装建议 sudo，以配置镜像源与 RTP 端口
4. **密码**：生产环境务必修改中间件默认密码（见 [部署最佳实践](./部署最佳实践.md#默认账号密码)）
5. **clean**：会删除数据卷，执行前务必备份
6. **网络**：需能访问 Docker Hub 或已配置的镜像加速源

## 技术支持

1. 查阅 [部署最佳实践.md](./部署最佳实践.md) 故障排查章节
2. 查看日志：`.scripts/docker/install_linux.sh logs`
3. 检查容器：`docker ps -a`
4. 向项目仓库提交 Issue

---

**文档版本**：2.0  
**最后更新**：2026-07-07  
**脚本入口**：`.scripts/docker/install_linux.sh`
