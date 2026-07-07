# EasyAIoT Deployment Best Practices

> This document stays **in sync with project scripts** and applies to Linux production/test environments.  
> For a quick start, see [Platform Deployment Guide](./平台部署文档.md). For Windows, see [Windows Deployment Guide](./平台Windows部署文档.md).

---

## Table of Contents

- [5-Minute Quick Start](#5-minute-quick-start)
- [Deployment Profile Selection](#deployment-profile-selection)
- [Environment Requirements](#environment-requirements)
- [Pre-Deployment Checklist](#pre-deployment-checklist)
- [One-Click Deployment](#one-click-deployment)
- [Step-by-Step Deployment](#step-by-step-deployment)
- [Common Operations](#common-operations)
- [Pre-Built Images (Optional)](#pre-built-images-optional)
- [GPU Configuration](#gpu-configuration)
- [Special Environments](#special-environments)
- [Database Notes](#database-notes)
- [Default Credentials](#default-credentials)
- [Troubleshooting](#troubleshooting)
- [Log Locations](#log-locations)
- [Update & Uninstall](#update--uninstall)
- [Architecture Reference](#architecture-reference)

---

## 5-Minute Quick Start

```bash
# 1. Clone the repository
git clone https://gitee.com/volara/easyaiot.git
cd easyaiot

# 2. Verify Docker (see Environment Requirements below)
docker --version
docker compose version

# 3. One-click install (interactive profile selection on first run; sudo recommended for mirror & RTP setup)
sudo .scripts/docker/install_linux.sh install

# 4. Verify
.scripts/docker/install_linux.sh verify

# 5. Open the management console in a browser
# http://<server-ip>:8888
```

**First-install duration**: Without pre-built images, the script runs local `docker build` for DEVICE / AI / VIDEO / WEB, typically **30 minutes to several hours** depending on CPU, disk, and network. Run `pull` first to significantly shorten install time (see [Pre-Built Images](#pre-built-images-optional)).

---

## Deployment Profile Selection

On install, the script interactively selects a **deployment profile** (or set `EASYAIOT_DEPLOY_PROFILE`). The choice is saved to `.scripts/docker/.deploy_profile` and reused by `start` / `stop` / `update`.

| Profile | Aliases | Recommended RAM | Use Case |
|---------|---------|-----------------|----------|
| **mini** | `1` / `4g` | ≥ 4 GB | Edge nodes, PoC, resource-constrained hosts |
| **standard** | `2` / `16g` | ≥ 16 GB | Regular production without some heavy components |
| **full** | `3` (default) | ≥ 20 GB | Full features including APP mobile H5 |

View current profile and service scope:

```bash
.scripts/docker/install_linux.sh profile
```

### Services per Profile

**mini (edge minimal)**

- Business: `iot-system`, VIDEO, AI, WEB
- Middleware: PostgreSQL, Redis, SRS
- Not started: Nacos, Gateway, Kafka, iot-sink, MinIO, Milvus, ZLMediaKit, Node-RED, TDengine, EMQX, and most DEVICE sub-modules
- API routing: nginx proxies `/admin-api` and `/dev-api` directly to `iot-system:48099`

**standard**

- Not started: TDengine, EMQX, Node-RED, `iot-device`, `iot-tdengine`
- All other business modules and middleware are started

**full**

- All business modules and middleware, including **APP mobile H5** (port 9010)

Analyze whether container memory matches the profile:

```bash
.scripts/docker/analyze_deploy_memory.sh
.scripts/docker/analyze_deploy_memory.sh --all-profiles   # compare all three
```

---

## Environment Requirements

### Hardware

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 4 cores | 8+ cores |
| RAM | See [Deployment Profile Selection](#deployment-profile-selection) (full min. 20 GB) | 32 GB+ |
| Disk | **300 GB** free | 500 GB+ SSD |
| GPU | None (CPU works) | NVIDIA GPU (CUDA 12.8 for AI inference/training) |

> Disk is used for Docker image layers, build cache (`.build-cache/`), databases, and object storage volumes. First local build consumes significant space—reserve ample headroom.

### Software

| Software | Requirement | Notes |
|----------|-------------|-------|
| OS | **Ubuntu 24.04 LTS** (minimum) | **Ubuntu 26.04 LTS recommended**; Kylin and ARM64 also supported (see [Special Environments](#special-environments)) |
| Docker | Installed and daemon accessible | If missing: `curl -fsSL https://get.docker.com \| sudo sh` |
| Docker Compose | **v2.35.0+** (`docker compose` plugin) | If missing: `sudo apt install docker-compose-plugin` |
| NVIDIA Driver | 525+ | GPU scenarios only |
| NVIDIA Container Toolkit | Latest | GPU scenarios only |

### Docker Permissions (Linux)

```bash
# Add current user to docker group (recommended)
sudo usermod -aG docker $USER
newgrp docker   # or log in again

# Verify
docker ps
```

> Configuring Docker mirrors and RTP port reservation requires root—**use `sudo` for first install**.

### Port Requirements

Ensure these ports are free before deployment (some may be unused depending on profile):

| Port | Service | Notes |
|------|---------|-------|
| 1880 | Node-RED | Rule engine (full/standard) |
| 1883 | EMQX | MQTT broker (full) |
| 1935 | SRS | RTMP streaming |
| 5432 | PostgreSQL | Primary database |
| 6000 | VIDEO | Video processing |
| 6030 | TDengine | Time-series DB (full) |
| 6080 | ZLMediaKit | Media server |
| 6379 | Redis | Cache |
| 8848 | Nacos | Registry/config center |
| 8888 | WEB | Management UI |
| 9000/9001 | MinIO | Object storage API / console |
| 9010 | APP | Mobile H5 (full only) |
| 9092 | Kafka | Message queue |
| 19530 | Milvus | Vector database |
| 48080 | Gateway | API gateway |
| 5000 | AI | AI inference |
| 30000-30500 | ZLM RTP | Media ingest (script attempts reservation) |

Check port usage:

```bash
ss -tlnp | grep -E '8848|5432|6379|9092|5000|6000|8888|48080'
```

---

## Pre-Deployment Checklist

```bash
# System info and resources
.scripts/docker/detect_system_info.sh

# Docker environment
.scripts/docker/install_linux.sh check

# Disk space (root partition: ≥ 300 GB free recommended)
df -h /
docker system df
```

---

## One-Click Deployment

### Entry Script

Unified orchestrator: `.scripts/docker/install_linux.sh`

```bash
# From project root (recommended)
sudo .scripts/docker/install_linux.sh install

# Or from script directory
cd .scripts/docker
sudo ./install_linux.sh install
```

### What `install` Does Automatically

1. **Select deployment profile** — mini / standard / full, saved to `.deploy_profile`
2. **Pre-built images** — skip local build if remote registry is configured and pull is chosen
3. **Environment checks** — Docker, Compose, container creation (including `/dev/null`)
4. **Host IP detection** — for GB28181 / ZLMediaKit media URLs (set `HOST_IP=<ip>` to skip)
5. **RTP port reservation** — kernel reserves 30000-30500 (requires root)
6. **Docker mirror** — configures `docker.m.daocloud.io` acceleration (requires root)
7. **Create Docker network** — `easyaiot-network`
8. **Deploy modules in order**:
   - Middleware (`.scripts/docker/install_middleware_linux.sh`)
   - DEVICE → AI → VIDEO → WEB → APP (full)
9. **Wait for base services** — PostgreSQL / Nacos / Redis health checks
10. **Platform Agent** — ensure edge agent when needed

### Verify Deployment

```bash
.scripts/docker/install_linux.sh verify
```

Example success output:

```
Service URLs:
  Middleware (Nacos):     http://localhost:8848/nacos
  Middleware (MinIO):     http://localhost:9000 (API), http://localhost:9001 (Console)
  Middleware (Milvus):    http://localhost:9091 (Health), localhost:19530 (gRPC)
  DEVICE (Gateway):       http://localhost:48080
  AI:                     http://localhost:5000
  VIDEO:                  http://localhost:6000
  WEB:                    http://localhost:8888
  APP H5:                 http://localhost:9010    # full only
```

Open `http://<server-ip>:8888` in a browser.

---

## Step-by-Step Deployment

For fine-grained control, deploy module by module. **Set the deployment profile first** so all modules stay consistent:

```bash
export EASYAIOT_DEPLOY_PROFILE=full   # or mini / standard
```

### Step 1: Middleware

```bash
cd .scripts/docker
./install_middleware_linux.sh install
```

| Middleware | Image | Port | Purpose |
|------------|-------|------|---------|
| Nacos | nacos/nacos-server:v2.5.1 | 8848 | Service registry & config |
| PostgreSQL | postgres:18 | 5432 | Primary DB (6 business DBs) |
| Redis | redis:7.4.8 | 6379 | Cache |
| Kafka | apache/kafka:3.8.0 | 9092 | Message queue |
| MinIO | minio/minio | 9000/9001 | Object storage |
| Milvus | milvusdb/milvus:v2.6.0 | 19530/9091 | Vector DB (face recognition) |
| SRS | ossrs/srs:5 | 1935 | Streaming |
| EMQX | emqx/emqx:5.8.7 | 1883 | MQTT (full profile) |
| ZLMediaKit | zlmediakit/zlmediakit:master | 6080 | Media server |
| TDengine | tdengine/tsdb:3.3.8.4 | 6030 | Time-series DB (full profile) |
| Node-RED | nodered/node-red:latest | 1880 | Rule engine |

Readiness checks:

```bash
docker exec postgres-server pg_isready -U postgres
curl -s http://localhost:8848/nacos/actuator/health
docker exec redis-server redis-cli -a basiclab@iot975248395 ping
```

### Step 2: DEVICE

```bash
cd DEVICE
./install_linux.sh install
```

| Service | Port | Description |
|---------|------|-------------|
| iot-gateway | 48080 | API gateway |
| iot-system | 48099 | System management |
| iot-infra | 48066 | Infrastructure |
| iot-device | 48055 | Device management |
| iot-dataset | 48077 | Dataset |
| iot-message | 48033 | Messaging |
| iot-file | 48022 | File service |
| iot-sink | 48011 | Protocol adapter |
| iot-gb28181 | 5060 | GB28181 video surveillance |

### Steps 3–5: AI / VIDEO / WEB

```bash
cd AI    && ./install_linux.sh install
cd VIDEO && ./install_linux.sh install
cd WEB   && ./install_linux.sh install
cd APP   && ./install_linux.sh install   # full only
```

### Business Modules Only (No Middleware)

```bash
cd .scripts/docker
./install_business_linux.sh install              # all business modules
./install_business_linux.sh update DEVICE WEB    # update specific modules
./install_business_linux.sh verify
```

---

## Common Operations

### Unified Script

```bash
cd .scripts/docker   # or use .scripts/docker/install_linux.sh from project root

./install_linux.sh install    # first install
./install_linux.sh start      # start
./install_linux.sh stop       # stop
./install_linux.sh restart    # restart
./install_linux.sh status     # status
./install_linux.sh logs       # all logs (last 100 lines)
./install_linux.sh logs WEB   # module-specific logs
./install_linux.sh build      # rebuild images locally
./install_linux.sh update     # update & restart (optional pull/rebuild)
./install_linux.sh verify     # health check
./install_linux.sh check      # check Docker environment
./install_linux.sh profile    # show deployment profile
./install_linux.sh clean      # remove containers & images (dangerous)
./install_linux.sh pull       # pull pre-built runtime images
./install_linux.sh help       # help
```

### Per-Module Scripts

Each module directory (`DEVICE` / `AI` / `VIDEO` / `WEB` / `APP`) supports:

```bash
./install_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

Middleware only:

```bash
cd .scripts/docker
./install_middleware_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

### Common Environment Variables

| Variable | Description |
|----------|-------------|
| `EASYAIOT_DEPLOY_PROFILE` | Profile: `mini` / `standard` / `full` |
| `HOST_IP` | Force host IP, skip auto-detection |
| `PARALLEL_MODULES=true` | Parallel start/update for business modules (when RAM allows) |
| `PARALLEL_BUILD=true` | Parallel build (default serial to avoid OOM) |
| `FORCE_NETWORK_RECREATE=true` | Recreate Docker network after host IP change |
| `EASYAIOT_RUNTIME_REGISTRY` | Pre-built image registry URL |

---

## Pre-Built Images (Optional)

Pull pre-built business images from a remote registry to skip lengthy local Maven / pnpm / pip builds.

Config file: `.scripts/docker/runtime_registry.conf`

```bash
# Interactive pull (before install or during update)
.scripts/docker/install_linux.sh pull

# Build and push runtime images (CI/release)
.scripts/docker/install_linux.sh build-runtime          # all modules
.scripts/docker/install_linux.sh build-runtime DEVICE   # specific module
```

After a successful pull, subsequent `install` / `update` detects `.runtime_images_pulled` and starts containers directly.

---

## GPU Configuration

### Install & Verify

```bash
nvidia-smi

# Install NVIDIA Container Toolkit
# https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html

docker run --rm --gpus all nvidia/cuda:12.8.0-base-ubuntu24.04 nvidia-smi
```

### Auto-Detection

Install scripts auto-detect GPU:

- GPU present → enable `runtime: nvidia`, `NVIDIA_VISIBLE_DEVICES=all`
- No GPU → CPU mode

### Multi-GPU

```bash
export CUDA_VISIBLE_DEVICES=0,1
```

---

## Special Environments

### Kylin OS

```bash
sudo .scripts/docker/install_linux_kylin.sh install
```

### ARM64

```bash
sudo .scripts/docker/install_linux_arm.sh install
# AI / VIDEO automatically use ARM Dockerfiles
```

---

## Database Notes

### PostgreSQL Business Databases

Six databases are initialized on startup (scripts in `.scripts/postgresql/`):

| Database | SQL File | Purpose |
|----------|----------|---------|
| ruoyi-vue-pro20 | ruoyi-vue-pro10.sql | System management |
| iot-ai20 | iot-ai10.sql | AI service |
| iot-device10 | iot-device10.sql | Device management |
| iot-gb2818110 | iot-gb2818110.sql | Video surveillance |
| iot-message10 | iot-message10.sql | Messaging |
| iot-video10 | iot-video10.sql | Video processing |

### TDengine

SQL in `.scripts/tdengine/tdengine_super_tables.sql`; auto-initialized under full profile.

### Backup

```bash
.scripts/postgresql/backup_databases.sh
```

---

## Default Credentials

| Middleware | Username | Password | Console |
|------------|----------|----------|---------|
| Nacos | nacos | nacos | http://\<IP\>:8848/nacos |
| PostgreSQL | postgres | iot45722414822 | — |
| Redis | — | basiclab@iot975248395 | — |
| MinIO | minioadmin | basiclab@iot975248395 | http://\<IP\>:9001 |
| EMQX | admin | basiclab@iot6874125784 | http://\<IP\>:18083 |
| Milvus | — | — | http://\<IP\>:9091 |

> **Change all default passwords in production.**

---

## Troubleshooting

### Service Start Failures

```bash
docker ps -a
docker logs -f postgres-server
docker logs -f nacos-server
docker logs -f ai-service
docker logs -f video-service
.scripts/docker/install_linux.sh logs
```

### Network Issues

```bash
docker network ls | grep easyaiot
docker network inspect easyaiot-network

# After host IP change
export FORCE_NETWORK_RECREATE=true
.scripts/docker/install_linux.sh restart
```

### PostgreSQL / Redis

```bash
.scripts/docker/fix_postgresql.sh
.scripts/docker/fix_redis.sh
```

### Docker System Issues

```bash
sudo .scripts/docker/diagnose_docker_systemd.sh diagnose
sudo .scripts/docker/diagnose_docker_systemd.sh fix-all
.scripts/docker/cleanup_docker_space.sh
df -h && docker system df
```

### Kafka Consumer Group

```bash
cd VIDEO && ./fix_kafka_consumer_group.sh
```

### Port Conflicts

Edit port mappings in the module's `docker-compose.yml`, or stop the conflicting process.

### WEB Issues After Profile Change

The frontend bakes in the deploy profile at build time—rebuild WEB after switching:

```bash
cd WEB && ./install_linux.sh build
```

---

## Log Locations

| Location | Description |
|----------|-------------|
| `.scripts/docker/logs/` | Unified install / middleware script logs |
| `DEVICE/logs/` | DEVICE service logs |
| `AI/data/logs/` | AI service logs |
| `VIDEO/data/logs/` | VIDEO service logs |
| `docker logs <container>` | Live container logs |

---

## Update & Uninstall

### Update Code & Services

```bash
git pull origin main
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

Single module update:

```bash
cd AI && ./install_linux.sh update
```

### Uninstall

```bash
sudo .scripts/docker/install_linux.sh clean

# Optional: remove data volume directories
rm -rf .scripts/docker/db_data .scripts/docker/redis_data \
       .scripts/docker/minio_data .scripts/docker/mq_data \
       .scripts/docker/taos_data .scripts/docker/milvus_data
```

---

## Architecture Reference

```
┌─────────────────────────────────────────────────────────────────┐
│                    WEB Frontend (:8888)                          │
│              Vue 3 + Ant Design Vue + Vite                       │
├─────────────────────────────────────────────────────────────────┤
│                 API Gateway (:48080)                              │
│              Spring Cloud Gateway + Nacos                        │
├───────────┬───────────┬───────────┬───────────┬─────────────────┤
│ iot-system│ iot-infra │ iot-device│ iot-dataset│  iot-message   │
│ iot-file  │ iot-sink  │ iot-gb28181                        │
│           Java 21 + Spring Boot 2.7 + MyBatis-Plus              │
├───────────┴───────────┴───────────┴───────────┴─────────────────┤
│  AI (:5000)              │  VIDEO (:6000)    │  APP H5 (:9010) │
│  Flask + PyTorch         │  Flask + OpenCV   │  Mobile         │
├──────────────────────────┴───────────────────┴─────────────────┤
│                     Middleware Layer                             │
│  Nacos │ PostgreSQL │ Redis │ Kafka │ MinIO │ TDengine          │
│  Milvus │ SRS │ EMQX │ ZLMediaKit │ Node-RED                     │
└─────────────────────────────────────────────────────────────────┘
```

---

*Doc version: 2026-07-07 | Script entry: `.scripts/docker/install_linux.sh`*
