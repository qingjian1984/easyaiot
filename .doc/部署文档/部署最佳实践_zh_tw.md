# EasyAIoT 部署最佳實務

> 本文件與專案腳本 **即時同步**，適用於 Linux 生產／測試環境。  
> 快速上手請參閱 [平台部署文件](./平台部署文档_zh_tw.md)；Windows 請參閱 [平台 Windows 部署文件](./平台Windows部署文档_zh_tw.md)。

---

## 目錄

- [5 分鐘快速上手](#5-分鐘快速上手)
- [部署規格選型](#部署規格選型)
- [環境要求](#環境要求)
- [部署前檢查清單](#部署前檢查清單)
- [一鍵部署](#一鍵部署)
- [分步部署](#分步部署)
- [常用維運命令](#常用維運命令)
- [預建置映像（選用）](#預建置映像選用)
- [GPU 設定](#gpu-設定)
- [特殊環境](#特殊環境)
- [資料庫說明](#資料庫說明)
- [預設帳號密碼](#預設帳號密碼)
- [故障排除](#故障排除)
- [日誌位置](#日誌位置)
- [更新與解除安裝](#更新與解除安裝)
- [架構參考](#架構參考)

---

## 5 分鐘快速上手

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

**首次安裝耗時說明**：若未設定預建置映像，腳本會在本機 `docker build` 各業務模組（DEVICE / AI / VIDEO / WEB），通常需要 **30 分鐘～數小時**，取決於 CPU、磁碟與網路。可先執行 `pull` 拉取預建置映像以大幅縮短時間（見 [預建置映像](#預建置映像選用)）。

---

## 部署規格選型

安裝時腳本會互動選擇 **部署規格**（也可透過環境變數 `EASYAIOT_DEPLOY_PROFILE` 指定）。選擇結果儲存於 `.scripts/docker/.deploy_profile`，後續 `start` / `stop` / `update` 會自動沿用。

| 規格 | 別名 | 建議記憶體 | 適用場景 |
|------|------|----------|----------|
| **mini** | `1` / `4g` | ≥ 4 GB | 邊緣節點、概念驗證（PoC）、資源受限環境 |
| **standard** | `2` / `16g` | ≥ 16 GB | 一般生產環境，不含部分重型元件 |
| **full** | `3`（預設） | ≥ 20 GB | 完整功能，含 APP 行動端 H5 |

查看目前規格與服務範圍：

```bash
.scripts/docker/install_linux.sh profile
```

### 各規格包含的服務

**mini（邊緣精簡版）**

- 業務：`iot-system`、VIDEO、AI、WEB
- 中介軟體：PostgreSQL、Redis、SRS
- 不啟動：Nacos、Gateway、Kafka、iot-sink、MinIO、Milvus、ZLMediaKit、Node-RED、TDengine、EMQX 及多數 DEVICE 子模組
- API 路由：nginx 將 `/admin-api`、`/dev-api` 直連 `iot-system:48099`

**standard（標準版）**

- 不啟動：TDengine、EMQX、Node-RED、`iot-device`、`iot-tdengine`
- 其餘業務模組與中介軟體全部啟動

**full（完整版）**

- 啟動全部業務模組與中介軟體，含 **APP 行動端 H5**（連接埠 9010）

分析目前容器記憶體占用是否與規格相符：

```bash
.scripts/docker/analyze_deploy_memory.sh
.scripts/docker/analyze_deploy_memory.sh --all-profiles   # compare all three
```

---

## 環境要求

### 硬體要求

| 資源 | 最低配置 | 建議配置 |
|------|---------|---------|
| CPU | 4 核 | 8 核+ |
| 記憶體 | 見 [部署規格選型](#部署規格選型)（full 最低 20 GB） | 32 GB+ |
| 磁碟 | **300 GB** 可用空間 | 500 GB+ SSD |
| GPU | 無（CPU 可執行） | NVIDIA GPU（CUDA 12.8，AI 推論／訓練） |

> 磁碟主要用於：Docker 映像層、建置快取（`.build-cache/`）、資料庫與物件儲存資料卷。首次本機建置會占用大量空間，建議預留充足餘量。

### 軟體要求

| 軟體 | 要求 | 說明 |
|------|------|------|
| 作業系統 | **Ubuntu 24.04 LTS**（最低） | **建議 Ubuntu 26.04 LTS**；亦支援銀河麒麟、ARM64（見 [特殊環境](#特殊環境)） |
| Docker | 已安裝且 daemon 可存取 | 未安裝時：`curl -fsSL https://get.docker.com \| sudo sh` |
| Docker Compose | **v2.35.0+**（外掛 `docker compose`） | 未安裝時：`sudo apt install docker-compose-plugin` |
| NVIDIA Driver | 525+ | 僅 GPU 場景 |
| NVIDIA Container Toolkit | 最新版 | 僅 GPU 場景 |

### Docker 權限（Linux）

```bash
# Add current user to docker group (recommended)
sudo usermod -aG docker $USER
newgrp docker   # or log in again

# Verify
docker ps
```

> 設定 Docker 映像來源、RTP 連接埠保留等系統層操作需要 root，**建議首次安裝使用 `sudo` 執行**。

### 連接埠要求

部署前確保以下連接埠未被占用（依目前規格，部分連接埠可能未使用）：

| 連接埠 | 服務 | 說明 |
|------|------|------|
| 1880 | Node-RED | 規則引擎（full/standard） |
| 1883 | EMQX | MQTT Broker（full） |
| 1935 | SRS | 串流媒體 RTMP |
| 5432 | PostgreSQL | 主資料庫 |
| 6000 | VIDEO | 影片處理 |
| 6030 | TDengine | 時序資料庫（full） |
| 6080 | ZLMediaKit | 媒體伺服器 |
| 6379 | Redis | 快取 |
| 8848 | Nacos | 註冊／設定中心 |
| 8888 | WEB | 管理介面 |
| 9000/9001 | MinIO | 物件儲存 API／主控台 |
| 9010 | APP | 行動端 H5（僅 full） |
| 9092 | Kafka | 訊息佇列 |
| 19530 | Milvus | 向量資料庫 |
| 48080 | Gateway | 後端 API 閘道 |
| 5000 | AI | AI 推論服務 |
| 30000-30500 | ZLM RTP | 媒體收流（腳本會嘗試保留） |

檢查連接埠占用：

```bash
ss -tlnp | grep -E '8848|5432|6379|9092|5000|6000|8888|48080'
```

---

## 部署前檢查清單

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

## 一鍵部署

### 入口腳本

統一編排腳本：`.scripts/docker/install_linux.sh`

```bash
# From project root (recommended)
sudo .scripts/docker/install_linux.sh install

# Or from script directory
cd .scripts/docker
sudo ./install_linux.sh install
```

### install 自動執行的流程

1. **選擇部署規格** — 互動選擇 mini / standard / full，寫入 `.deploy_profile`
2. **預建置映像** — 若已設定遠端倉庫且選擇拉取，略過本機 build
3. **環境檢查** — Docker、Docker Compose、容器建立能力（含 `/dev/null` 檢測）
4. **主機 IP 偵測** — 注入 GB28181 / ZLMediaKit 媒體位址（可設 `HOST_IP=<ip>` 略過偵測）
5. **RTP 連接埠保留** — 設定核心保留 30000-30500（需 root）
6. **Docker 映像來源** — 自動設定 `docker.m.daocloud.io` 加速（需 root）
7. **建立 Docker 網路** — `easyaiot-network`
8. **依相依順序部署模組**：
   - 中介軟體（`.scripts/docker/install_middleware_linux.sh`）
   - DEVICE → AI → VIDEO → WEB → APP（full）
9. **等待基礎服務就緒** — PostgreSQL / Nacos / Redis 健康檢查
10. **平台 Agent** — 按需確保邊緣 Agent 可用

### 驗證部署

```bash
.scripts/docker/install_linux.sh verify
```

成功輸出範例：

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

瀏覽器開啟 `http://<server-ip>:8888` 存取管理平台。

---

## 分步部署

需要精細控制時，可依模組分步執行。**務必先確定並匯出部署規格**，確保各模組設定一致：

```bash
export EASYAIOT_DEPLOY_PROFILE=full   # or mini / standard
```

### 第一步：中介軟體

```bash
cd .scripts/docker
./install_middleware_linux.sh install
```

| 中介軟體 | 映像 | 連接埠 | 用途 |
|------------|-------|------|---------|
| Nacos | nacos/nacos-server:v2.5.1 | 8848 | 服務註冊與設定 |
| PostgreSQL | postgres:18 | 5432 | 主資料庫（6 個業務庫） |
| Redis | redis:7.4.8 | 6379 | 快取 |
| Kafka | apache/kafka:3.8.0 | 9092 | 訊息佇列 |
| MinIO | minio/minio | 9000/9001 | 物件儲存 |
| Milvus | milvusdb/milvus:v2.6.0 | 19530/9091 | 向量庫（人臉辨識） |
| SRS | ossrs/srs:5 | 1935 | 串流媒體 |
| EMQX | emqx/emqx:5.8.7 | 1883 | MQTT（full profile） |
| ZLMediaKit | zlmediakit/zlmediakit:master | 6080 | 媒體伺服器 |
| TDengine | tdengine/tsdb:3.3.8.4 | 6030 | 時序庫（full profile） |
| Node-RED | nodered/node-red:latest | 1880 | 規則引擎 |

就緒檢查：

```bash
docker exec postgres-server pg_isready -U postgres
curl -s http://localhost:8848/nacos/actuator/health
docker exec redis-server redis-cli -a basiclab@iot975248395 ping
```

### 第二步：DEVICE 服務

```bash
cd DEVICE
./install_linux.sh install
```

| 服務 | 連接埠 | 說明 |
|---------|------|-------------|
| iot-gateway | 48080 | API 閘道 |
| iot-system | 48099 | 系統管理 |
| iot-infra | 48066 | 基礎設施 |
| iot-device | 48055 | 裝置管理 |
| iot-dataset | 48077 | 資料集 |
| iot-message | 48033 | 訊息推送 |
| iot-file | 48022 | 檔案服務 |
| iot-sink | 48011 | 協定適配 |
| iot-gb28181 | 5060 | GB28181 視訊監控 |

### 第三步～第五步：AI / VIDEO / WEB

```bash
cd AI    && ./install_linux.sh install
cd VIDEO && ./install_linux.sh install
cd WEB   && ./install_linux.sh install
cd APP   && ./install_linux.sh install   # full only
```

### 僅部署業務模組（不含中介軟體）

```bash
cd .scripts/docker
./install_business_linux.sh install              # all business modules
./install_business_linux.sh update DEVICE WEB    # update specific modules
./install_business_linux.sh verify
```

---

## 常用維運命令

### 統一腳本

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

### 單模組腳本

每個模組目錄（`DEVICE` / `AI` / `VIDEO` / `WEB` / `APP`）均支援：

```bash
./install_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

中介軟體單獨管理：

```bash
cd .scripts/docker
./install_middleware_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

### 常用環境變數

| 變數 | 說明 |
|----------|-------------|
| `EASYAIOT_DEPLOY_PROFILE` | 部署規格：`mini` / `standard` / `full` |
| `HOST_IP` | 強制指定主機 IP，略過自動偵測 |
| `PARALLEL_MODULES=true` | 業務模組並行啟動／更新（記憶體充足時） |
| `PARALLEL_BUILD=true` | 並行建置（預設串行，避免 OOM） |
| `FORCE_NETWORK_RECREATE=true` | 主機 IP 變更後重建 Docker 網路 |
| `EASYAIOT_RUNTIME_REGISTRY` | 預建置映像倉庫位址 |

---

## 預建置映像（選用）

專案支援從遠端倉庫拉取預建置業務映像，略過耗時的本機 Maven / pnpm / pip 建置。

設定檔：`.scripts/docker/runtime_registry.conf`

```bash
# Interactive pull (before install or during update)
.scripts/docker/install_linux.sh pull

# Build and push runtime images (CI/release)
.scripts/docker/install_linux.sh build-runtime          # all modules
.scripts/docker/install_linux.sh build-runtime DEVICE   # specific module
```

拉取成功後，後續 `install` / `update` 會偵測到 `.runtime_images_pulled` 標記並直接啟動容器。

---

## GPU 設定

### 安裝與驗證

```bash
nvidia-smi

# Install NVIDIA Container Toolkit
# https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html

docker run --rm --gpus all nvidia/cuda:12.8.0-base-ubuntu24.04 nvidia-smi
```

### 自動偵測

安裝腳本會自動偵測 GPU：

- 有 GPU → 啟用 `runtime: nvidia`、`NVIDIA_VISIBLE_DEVICES=all`
- 無 GPU → CPU 模式執行

### 多 GPU

```bash
export CUDA_VISIBLE_DEVICES=0,1
```

---

## 特殊環境

### 銀河麒麟

```bash
sudo .scripts/docker/install_linux_kylin.sh install
```

### ARM64

```bash
sudo .scripts/docker/install_linux_arm.sh install
# AI / VIDEO automatically use ARM Dockerfiles
```

---

## 資料庫說明

### PostgreSQL 業務庫

啟動時自動初始化 6 個資料庫（腳本位於 `.scripts/postgresql/`）：

| 資料庫 | SQL 檔案 | 用途 |
|----------|----------|---------|
| ruoyi-vue-pro20 | ruoyi-vue-pro10.sql | 系統管理 |
| iot-ai20 | iot-ai10.sql | AI 服務 |
| iot-device10 | iot-device10.sql | 裝置管理 |
| iot-gb2818110 | iot-gb2818110.sql | 視訊監控 |
| iot-message10 | iot-message10.sql | 訊息推送 |
| iot-video10 | iot-video10.sql | 影片處理 |

### TDengine

SQL 位於 `.scripts/tdengine/tdengine_super_tables.sql`，full 規格下自動初始化。

### 備份

```bash
.scripts/postgresql/backup_databases.sh
```

---

## 預設帳號密碼

| 中介軟體 | 使用者名稱 | 密碼 | 主控台 |
|------------|----------|----------|---------|
| Nacos | nacos | nacos | http://\<IP\>:8848/nacos |
| PostgreSQL | postgres | iot45722414822 | — |
| Redis | — | basiclab@iot975248395 | — |
| MinIO | minioadmin | basiclab@iot975248395 | http://\<IP\>:9001 |
| EMQX | admin | basiclab@iot6874125784 | http://\<IP\>:18083 |
| Milvus | — | — | http://\<IP\>:9091 |

> **生產環境務必修改所有預設密碼。**

---

## 故障排除

### 服務啟動失敗

```bash
docker ps -a
docker logs -f postgres-server
docker logs -f nacos-server
docker logs -f ai-service
docker logs -f video-service
.scripts/docker/install_linux.sh logs
```

### 網路問題

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

### Docker 系統問題

```bash
sudo .scripts/docker/diagnose_docker_systemd.sh diagnose
sudo .scripts/docker/diagnose_docker_systemd.sh fix-all
.scripts/docker/cleanup_docker_space.sh
df -h && docker system df
```

### Kafka 消費群組

```bash
cd VIDEO && ./fix_kafka_consumer_group.sh
```

### 連接埠衝突

修改對應模組 `docker-compose.yml` 中的連接埠對應，或停止占用程序。

### 切換部署規格後 WEB 異常

前端編譯時寫入了部署規格，切換後需重建 WEB：

```bash
cd WEB && ./install_linux.sh build
```

---

## 日誌位置

| 位置 | 說明 |
|----------|-------------|
| `.scripts/docker/logs/` | 統一安裝／中介軟體腳本日誌 |
| `DEVICE/logs/` | DEVICE 服務日誌 |
| `AI/data/logs/` | AI 服務日誌 |
| `VIDEO/data/logs/` | VIDEO 服務日誌 |
| `docker logs <container>` | 容器即時日誌 |

---

## 更新與解除安裝

### 更新程式碼與服務

```bash
git pull origin main
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

單模組更新：

```bash
cd AI && ./install_linux.sh update
```

### 解除安裝

```bash
sudo .scripts/docker/install_linux.sh clean

# Optional: remove data volume directories
rm -rf .scripts/docker/db_data .scripts/docker/redis_data \
       .scripts/docker/minio_data .scripts/docker/mq_data \
       .scripts/docker/taos_data .scripts/docker/milvus_data
```

---

## 架構參考

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

*文件版本：2026-07-07 | 腳本入口：`.scripts/docker/install_linux.sh`*
