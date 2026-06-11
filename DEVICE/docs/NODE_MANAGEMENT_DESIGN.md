# 服务器节点管理模块设计文档

> 版本：v1.0 | 日期：2026-06-11

## 模块定位

`iot-node` 微服务（`node-server:48094`）提供统一的计算/媒体节点控制面，支撑 AI 模型、算法任务、流媒体集群的远程部署与调度。

## 已实现（Phase 1）

- `iot-node` 微服务：节点 CRUD、SSH 测试、Agent 注册/心跳、基础调度 API
- `NODE/agent/`：Python Agent 骨架（注册 + 心跳 + GPU/CPU 指标）
- Gateway 路由：`/admin-api/node/**`
- WEB 页面：`/node/index` 服务器节点管理
- 数据库表：`compute_node`、`node_ssh_credential`、`node_metric_snapshot`、`node_workload_binding`

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/node/create` | 创建节点（返回 agentToken） |
| PUT | `/node/update` | 更新节点 |
| DELETE | `/node/delete?id=` | 删除节点 |
| GET | `/node/page` | 分页列表 |
| GET | `/node/get?id=` | 节点详情 |
| POST | `/node/test-ssh?id=` | SSH 连通测试 |
| POST | `/node/reset-agent-token?id=` | 重置 Agent 令牌 |
| POST | `/node/maintenance?id=&enabled=` | 维护模式 |
| POST | `/node/agent/register` | Agent 注册 |
| POST | `/node/agent/heartbeat` | Agent 心跳 |
| POST | `/node/scheduler/allocate` | 分配节点 |
| POST | `/node/scheduler/release` | 释放绑定 |

## 部署步骤

### 1. 初始化数据库

节点表使用独立库 **`iot-node20`**（与 `iot-video20`、`iot-message20` 命名一致，不再放在 `iot-device20`）。

```bash
# Docker 全新初始化（含建库）
bash .scripts/docker/init-databases.sh

# 或手动建库后导入表结构
psql -h localhost -U postgres -d iot-node20 -f DEVICE/iot-node/iot-node-biz/src/main/resources/sql/node_tables.sql
```

### 2. 编译启动 iot-node

```bash
cd DEVICE && mvn package -pl iot-node/iot-node-biz -am -DskipTests
```

### 3. 安装 Agent（目标服务器）

```bash
cd NODE/agent
# 编辑 agent.env 填入 NODE_ID 和 AGENT_TOKEN
sudo bash install.sh
sudo systemctl enable --now easyaiot-node-agent
```

## Phase 2 — AI 模型远程部署（已实现）

- `AI/app/utils/node_client.py`：调用 iot-node 调度与工作负载 API
- `deploy_service.py`：支持 `target_node_id` / `auto_schedule` 远程部署
- `AIService.node_id`：记录部署节点
- Agent：`/workload/deploy|stop` HTTP 接口 + MinIO 模型下载
- WEB 部署弹窗：本机 / 自动调度 / 指定节点

### 远程部署环境变量（AI 控制面）

| 变量 | 说明 |
|------|------|
| `JAVA_BACKEND_URL` | Gateway 地址，默认 `http://localhost:48080` |
| `NODE_REMOTE_DEPLOY` | 是否启用远程部署，默认 `true` |
| `NODE_REMOTE_AI_ROOT` | 远程节点 AI 代码路径，默认 `/opt/easyaiot/AI` |
| `NODE_REMOTE_PYTHON` | 远程 Python，默认 `python3` |

## Phase 3 — 算法任务跨节点调度（已实现）

### 数据模型（`algorithm_task`）

| 字段 | 说明 |
|------|------|
| `schedule_policy` | `local`（默认）/ `auto` / `node` |
| `target_node_id` | 指定节点 ID（`schedule_policy=node`） |
| `node_id` | 实际运行节点（启动后写入） |

### 控制面改造（VIDEO）

- `app/utils/node_client.py`：调用 iot-node 调度与 workload API
- `algorithm_task_launcher_service.py`：`local` 走本机 Daemon；`auto`/`node` 经 Agent 远程拉起 `run_deploy.py`
- `run_deploy.py`：心跳 URL 支持 `VIDEO_HEARTBEAT_URL` / `VIDEO_CONTROL_URL` 环境变量

### 远程部署环境变量（VIDEO 控制面）

| 变量 | 说明 |
|------|------|
| `NODE_REMOTE_DEPLOY` | 是否启用远程部署，默认 `true` |
| `NODE_REMOTE_VIDEO_ROOT` | 远程 VIDEO 代码路径，默认 `/opt/easyaiot/VIDEO` |
| `NODE_REMOTE_PYTHON` | 远程 Python，默认 `python3` |
| `JAVA_BACKEND_URL` / `GATEWAY_URL` | Gateway 地址 |
| `VIDEO_HEARTBEAT_URL` | 经 Gateway 上报算法心跳 |

### WEB

- 算法任务表单：调度策略 + 目标节点（在线 compute/hybrid 节点）
- 任务列表：展示调度策略与实际运行节点

### 迁移 SQL

```bash
psql -d iot-video20 -f VIDEO/migrations/add_algorithm_task_node_fields.sql
```

（或依赖 `VIDEO/run.py` 启动时自动迁移）

## Phase 4 — SRS/ZLM 媒体节点池（已实现）

### 能力概览

- **设备媒体绑定**：为每个摄像头分配 SRS live/ai 与 ZLM 节点，生成 Sticky 流地址
- **调度隔离**：`srs_live` / `srs_ai` / `zlm` 仅调度 `media` / `hybrid` 角色节点
- **远程部署**：Agent `/media/deploy` 在节点上 `docker compose` 拉起 SRS/ZLM
- **VIDEO 集成**：`MEDIA_NODE_POOL_ENABLED=true` 时创建设备自动调用绑定 API

### 数据表

`device_media_binding`：设备 ↔ SRS/ZLM 节点映射及生成的流 URL

### API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/node/media/allocate` | 分配媒体节点并生成流地址 |
| GET | `/node/media/binding?deviceId=` | 查询绑定 |
| POST | `/node/media/release?deviceId=` | 释放绑定 |
| POST | `/node/media/deploy-stack` | 远程部署 SRS/ZLM 栈 |

### 媒体节点 tags（端口配置）

| Tag | 默认 | 说明 |
|-----|------|------|
| `srs_rtmp_port` | 1935 | SRS RTMP |
| `srs_http_port` | 8080 | SRS HTTP-FLV |
| `zlm_http_port` | 6080 | ZLM HTTP API |
| `zlm_rtmp_port` | 10935 | ZLM RTMP |
| `zlm_rtp_port_min/max` | 30000-30500 | GB28181 RTP 范围 |

### VIDEO 环境变量

| 变量 | 说明 |
|------|------|
| `MEDIA_NODE_POOL_ENABLED` | 启用媒体节点池，默认 `false` |
| `MEDIA_HTTP_PLAY_HOST` | 边缘播放域名（可选） |
| `MEDIA_NODE_REGION` | 调度区域偏好 |

### 部署步骤

```bash
# 1. 迁移表结构
psql -d iot-node20 -f DEVICE/iot-node/iot-node-biz/src/main/resources/sql/node_tables.sql

# 2. WEB 添加媒体节点（角色 media/hybrid），配置端口 tags

# 3. 目标机安装 Agent + Docker，同步 media-cluster 脚本
# 4. WEB「部署SRS」或 API deploy-stack 拉起容器

# 5. VIDEO 启用
export MEDIA_NODE_POOL_ENABLED=true
```

### 与 docs/streaming-cluster 关系

本阶段将媒体调度收敛到 **iot-node**，替代独立 MEDIA Scheduler（8090）的节点池职责。

## Hook / Kafka 上传流水线（已实现）

### 架构

```
SRS/ZLM on_dvr Hook → VIDEO /video/media/hook/* （<50ms）
                              ↓ MEDIA_UPLOAD_MODE=kafka
                    Kafka media.dvr.completed
                              ↓
              media_upload_worker（独立进程）
                              ↓
         MinIO 上传 + Playback + 删 GlusterFS 本地段
```

### 环境变量（VIDEO）

| 变量 | 默认 | 说明 |
|------|------|------|
| `MEDIA_UPLOAD_MODE` | `sync` | `kafka` 时 Hook 仅入队 |
| `MEDIA_KAFKA_DVR_TOPIC` | `media.dvr.completed` | DVR 完成 Topic |
| `MEDIA_KAFKA_DVR_DLQ_TOPIC` | `media.dvr.dlq` | 死信 Topic |
| `MEDIA_HOST_DATA_ROOT` | `/mnt/easyaiot-media` | GlusterFS 挂载根 |
| `MEDIA_HOOK_HOST` | `localhost` | SRS/ZLM Hook 目标（Gateway IP） |
| `MEDIA_HOOK_PORT` | `48080` | Gateway 端口 |

### 启动 Worker

```bash
# 在控制面创建 Topic（Kafka 容器 kafka-server；单机部署 replication-factor 为 1）
for t in media.dvr.completed:32 media.snap.completed:16 media.dvr.dlq:4; do
  topic="${t%%:*}"; parts="${t##*:}"
  docker exec kafka-server /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic "$topic" --partitions "$parts" --replication-factor 1
done

# VIDEO 目录下
export MEDIA_UPLOAD_MODE=kafka
python services/media_upload_worker/run_worker.py
```

### Hook 路径

| 来源 | 路径 |
|------|------|
| SRS on_dvr | `POST /video/media/hook/srs/on_dvr` |
| SRS on_publish | `POST /video/media/hook/srs/on_publish` |
| ZLM on_record_mp4 | `POST /video/media/hook/zlm/on_record_mp4` |
| 兼容旧路径 | `POST /video/camera/callback/on_dvr` |
| 抓拍完成 | `POST /video/media/hook/snap/completed` |

### 抓拍流水线（media.snap.completed）

```
算法 Worker 写本地 MEDIA_SNAP_DIR/{device_id}/*.jpg
        ↓ publish_snap_event / hook/snap/completed
Kafka media.snap.completed
        ↓ run_snap_worker.py
MinIO snap-space + SnapImage 元数据 + 删本地
```

| 变量 | 默认 | 说明 |
|------|------|------|
| `MEDIA_SNAP_UPLOAD_MODE` | 继承 `MEDIA_UPLOAD_MODE` | `kafka` 异步入队 |
| `MEDIA_SNAP_DIR` | `$MEDIA_HOST_DATA_ROOT/snaps` | 抓拍暂存目录 |
| `MEDIA_SNAP_STAGING_ENABLED` | `false` | 算法强制走暂存+流水线 |

```bash
python services/media_upload_worker/run_snap_worker.py
```

### Janitor 孤儿清理

VIDEO 内置调度（默认每 60s）或独立进程：

```bash
python services/media_janitor/run_janitor.py
```

| 变量 | 默认 | 说明 |
|------|------|------|
| `MEDIA_JANITOR_ENABLED` | `true` | 启用 Janitor |
| `JANITOR_INTERVAL_SECONDS` | `60` | 扫描间隔 |
| `JANITOR_ORPHAN_MIN_AGE_MINUTES` | `10` | 孤儿文件最小年龄 |
| `PLAYBACK_DISK_CRITICAL_PERCENT` | `90` | 磁盘紧急删除阈值 |
