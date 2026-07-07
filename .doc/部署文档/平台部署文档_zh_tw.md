# EasyAIoT 平台部署文件

> **新手推薦路徑**：先讀本文「快速開始」完成首次部署；進階運維、故障排查、GPU 與資料庫細節請參閱 [部署最佳實踐.md](./部署最佳实践_zh_tw.md)。

## 目錄

- [概述](#概述)
- [環境要求](#環境要求)
- [快速開始](#快速開始)
- [部署規格說明](#部署規格說明)
- [腳本使用說明](#腳本使用說明)
- [模組說明](#模組說明)
- [服務連接埠](#服務連接埠)
- [常見問題](#常見問題)
- [日誌管理](#日誌管理)
- [部署流程建議](#部署流程建議)

---

## 概述

EasyAIoT 是雲邊一體化智慧演算法應用平台，採用 **Docker 容器化 + 統一安裝腳本** 一鍵部署。

### 平台組成

| 模組 | 目錄 | 說明 |
|------|------|------|
| 基礎服務 | `.scripts/docker` | Nacos、PostgreSQL、Redis、Kafka、MinIO 等中介軟體 |
| DEVICE | `DEVICE/` | 裝置管理與 API 閘道（Java / Spring Cloud） |
| AI | `AI/` | 模型訓練、推理、OCR、LLM 等（Python） |
| VIDEO | `VIDEO/` | 視訊串流處理、告警、錄影、人臉辨識（Python） |
| WEB | `WEB/` | 管理主控台（Vue 3） |
| APP | `APP/` | 行動端 H5（僅 **full** 全量規格） |

### 統一入口腳本

| 系統 | 腳本路徑 |
|------|----------|
| Linux | `.scripts/docker/install_linux.sh` |
| macOS | `.scripts/docker/install_mac.sh` |
| Windows | `.scripts/docker/install_win.ps1` |

> 下文以 **Linux** 為主；macOS / Windows 指令將 `install_linux.sh` 替換為對應腳本即可。

---

## 環境要求

### 系統與硬體

| 項目 | 要求 |
|------|------|
| **作業系統** | **Ubuntu 24.04 LTS 及以上**（**建議 Ubuntu 26.04 LTS**）；亦支援 macOS 10.15+、Windows 10/11 |
| **CPU** | 最低 4 核，建議 8 核+ |
| **記憶體** | 取決於部署規格（見下表）；full 規格最低 20 GB，建議 32 GB |
| **磁碟** | **最低 300 GB 可用空間**，建議 500 GB+ SSD |
| **GPU** | 可選；AI 訓練/推理建議 NVIDIA GPU（CUDA 12.8） |

### 軟體相依

| 軟體 | 版本要求 | 驗證指令 |
|------|----------|----------|
| Docker | 已安裝且 daemon 可存取 | `docker --version` |
| Docker Compose | **v2.35.0+**（`docker compose` 外掛程式） | `docker compose version` |
| curl | 健康檢查用 | `curl --version` |

安裝參考：

```bash
# Docker（Ubuntu）
curl -fsSL https://get.docker.com | sudo sh
sudo apt install -y docker-compose-plugin

# 權限
sudo usermod -aG docker $USER && newgrp docker
docker ps
```

### Docker 權限（Linux）

```bash
sudo usermod -aG docker $USER
newgrp docker          # 或重新登入
docker ps              # 應無 permission denied
```

首次安裝建議使用 `sudo`，以便腳本設定映像檔加速與 RTP 連接埠保留。

---

## 快速開始

### Linux 四步部署

```bash
# ① 複製程式碼
git clone https://gitee.com/volara/easyaiot.git
cd easyaiot

# ② 環境自我檢查（可選但建議）
.scripts/docker/install_linux.sh check
.scripts/docker/detect_system_info.sh

# ③ 一鍵安裝（首次會詢問部署規格 1/2/3）
sudo .scripts/docker/install_linux.sh install

# ④ 驗證並存取
.scripts/docker/install_linux.sh verify
# 瀏覽器開啟 http://<伺服器IP>:8888
```

### 安裝過程中會發生什麼？

1. 選擇 **部署規格**（mini / standard / full）
2. 檢查 Docker、Compose、容器建立能力
3. 偵測宿主機 IP，建立 `easyaiot-network`
4. 依序部署：中介軟體 → DEVICE → AI → VIDEO → WEB → APP（full）
5. 輸出各服務存取位址

**預計耗時**：

- 已拉取預建置映像檔：**約 10～30 分鐘**
- 本機完整建置：**30 分鐘～數小時**（視硬體而定）

縮短安裝時間：安裝前執行 `.scripts/docker/install_linux.sh pull` 拉取預建置映像檔（詳見 [部署最佳實踐 - 預建置映像](./部署最佳实践_zh_tw.md#預建置映像選用)）。

### macOS 快速開始

```bash
git clone https://gitee.com/volara/easyaiot.git && cd easyaiot
cd .scripts/docker && chmod +x install_mac.sh
./install_mac.sh install
./install_mac.sh verify
```

### Windows

請參閱 [平台Windows部署文件_zh_tw.md](./平台Windows部署文档_zh_tw.md)。

---

## 部署規格說明

首次 `install` 時會互動選擇規格，選擇結果儲存在 `.scripts/docker/.deploy_profile`。

| 選項 | 名稱 | 建議記憶體 | 典型場景 |
|:----:|------|----------|----------|
| 1 | **mini** | ≥ 4 GB | 邊緣節點、PoC 驗證 |
| 2 | **standard** | ≥ 16 GB | 常規生產（不含 TDengine/EMQX 等） |
| 3 | **full**（預設） | ≥ 20 GB | 完整功能 + APP H5 |

查看目前規格：

```bash
.scripts/docker/install_linux.sh profile
```

非互動指定規格：

```bash
export EASYAIOT_DEPLOY_PROFILE=full
sudo .scripts/docker/install_linux.sh install
```

各規格服務差異詳見 [部署最佳實踐 - 部署規格選型](./部署最佳实践_zh_tw.md#部署規格選型)。

---

## 腳本使用說明

### 指令一覽

| 指令 | 說明 | 範例 |
|------|------|------|
| `install` | 首次安裝並啟動 | `./install_linux.sh install` |
| `start` | 啟動全部服務 | `./install_linux.sh start` |
| `stop` | 停止全部服務 | `./install_linux.sh stop` |
| `restart` | 重新啟動全部服務 | `./install_linux.sh restart` |
| `status` | 查看執行狀態 | `./install_linux.sh status` |
| `logs` | 查看日誌 | `./install_linux.sh logs` |
| `logs <模組>` | 指定模組日誌 | `./install_linux.sh logs VIDEO` |
| `build` | 本機重新建置映像檔 | `./install_linux.sh build` |
| `pull` | 拉取預建置映像檔 | `./install_linux.sh pull` |
| `update` | 更新並重新啟動 | `./install_linux.sh update` |
| `verify` | 健康檢查 | `./install_linux.sh verify` |
| `check` | 檢查 Docker 環境 | `./install_linux.sh check` |
| `profile` | 查看部署規格 | `./install_linux.sh profile` |
| `clean` | 清理容器與映像檔 ⚠️ | `./install_linux.sh clean` |
| `help` | 顯示說明 | `./install_linux.sh help` |

> 在專案根目錄可將 `./install_linux.sh` 替換為 `.scripts/docker/install_linux.sh`。

### install 指令

首次部署使用。會自動依相依順序安裝所有已啟用模組，並在中介軟體就緒後繼續後續模組。

```bash
sudo .scripts/docker/install_linux.sh install
```

### verify 指令

檢查各模組連接埠與健康端點，全部通過時列印存取位址：

```
[SUCCESS] 所有服務運行正常！

服務存取位址:
  基礎服務 (Nacos):     http://localhost:8848/nacos
  基礎服務 (MinIO):     http://localhost:9000 (API), http://localhost:9001 (Console)
  Device服務 (Gateway): http://localhost:48080
  AI服務:               http://localhost:5000
  Video服務:            http://localhost:6000
  Web前端:              http://localhost:8888
```

### clean 指令 ⚠️

**危險操作**：刪除容器、映像檔及資料磁碟區。執行前會要求確認（輸入 `y`）。

### 分模組 / 僅業務部署

```bash
# 僅中介軟體
cd .scripts/docker && ./install_middleware_linux.sh install

# 僅業務模組（不含中介軟體）
cd .scripts/docker && ./install_business_linux.sh install

# 單一模組（例：AI）
cd AI && ./install_linux.sh install
```

---

## 模組說明

### 基礎服務（`.scripts/docker`）

平台執行所需的中介軟體，由 `install_middleware_linux.sh` 管理。

包含：Nacos、PostgreSQL、Redis、TDengine、Kafka、MinIO、Milvus、SRS、EMQX、ZLMediaKit、Node-RED 等（具體啟用的服務取決於部署規格）。

### DEVICE 服務

- **技術棧**：Java 21、Spring Boot 2.7、Spring Cloud Gateway
- **核心能力**：裝置接入、產品管理、規則引擎、GB28181、系統管理
- **入口連接埠**：48080（Gateway）

### AI 服務

- **技術棧**：Flask、PyTorch 2.9+（CUDA 12.8）
- **核心能力**：模型訓練/推理/部署、OCR、語音、LLM
- **連接埠**：5000

### VIDEO 服務

- **技術棧**：Flask、OpenCV、FFmpeg
- **核心能力**：視訊串流處理、即時/快照演算法、錄影、告警、人臉辨識
- **連接埠**：6000

### WEB 服務

- **技術棧**：Vue 3.4、TypeScript、Vite、Ant Design Vue 4
- **連接埠**：8888

### APP 服務（僅 full）

- **說明**：行動端 H5
- **連接埠**：9010

---

## 服務連接埠

### 核心連接埠

| 服務 | 連接埠 | 存取位址 |
|------|------|----------|
| WEB 前端 | 8888 | http://localhost:8888 |
| DEVICE Gateway | 48080 | http://localhost:48080 |
| AI 服務 | 5000 | http://localhost:5000 |
| VIDEO 服務 | 6000 | http://localhost:6000 |
| Nacos | 8848 | http://localhost:8848/nacos |
| MinIO API / Console | 9000 / 9001 | http://localhost:9001 |
| APP H5（full） | 9010 | http://localhost:9010 |

完整連接埠清單見 [部署最佳實踐 - 連接埠要求](./部署最佳实践_zh_tw.md#連接埠要求)。

### 健康檢查端點

| 模組 | 端點 |
|------|------|
| 基礎服務 (Nacos) | `/nacos/actuator/health` |
| DEVICE | `/actuator/health` |
| AI | `/actuator/health` |
| VIDEO | `/actuator/health` |
| WEB | `/health` |
| APP | `/health` |

---

## 常見問題

### 1. Docker 權限不足

```
permission denied while trying to connect to the Docker daemon socket
```

```bash
sudo usermod -aG docker $USER
newgrp docker
docker ps
```

### 2. Docker Compose 版本過低

腳本要求 **v2.35.0+**：

```bash
sudo apt update && sudo apt install -y docker-compose-plugin
docker compose version
```

### 3. 連接埠被佔用

```bash
ss -tlnp | grep <端口号>
# 停止佔用程序，或修改對應 docker-compose.yml 連接埠對應
```

### 4. 安裝中途失敗

```bash
# 查看腳本日誌
ls -lt .scripts/docker/logs/ | head -5
tail -100 .scripts/docker/logs/install_linux_*.log

# 查看容器狀態
docker ps -a
.scripts/docker/install_linux.sh status
```

### 5. 服務已啟動但瀏覽器無法存取

```bash
.scripts/docker/install_linux.sh verify
sudo ufw allow 8888    # 如啟用了防火牆
.scripts/docker/install_linux.sh logs WEB
```

### 6. 磁碟空間不足

首次建置會佔用大量磁碟，**建議保留 ≥ 300 GB**：

```bash
df -h /
docker system df
.scripts/docker/cleanup_docker_space.sh
```

### 7. 切換部署規格後前端異常

WEB 映像檔與部署規格綁定，切換後需重建：

```bash
cd WEB && ./install_linux.sh build
```

更多排查方案見 [部署最佳實踐 - 故障排查](./部署最佳实践_zh_tw.md#故障排查)。

---

## 日誌管理

### 腳本日誌

儲存在 `.scripts/docker/logs/`：

```
install_linux_YYYYMMDD_HHMMSS.log
install_middleware_YYYYMMDD_HHMMSS.log
```

```bash
ls -lt .scripts/docker/logs/ | head -5
tail -f .scripts/docker/logs/install_linux_*.log
```

### 容器日誌

```bash
.scripts/docker/install_linux.sh logs           # 全部模組摘要
cd DEVICE && docker compose logs -f            # 單一模組詳細日誌
docker logs -f video-service                   # 單一容器
```

---

## 部署流程建議

### 首次部署檢查清單

- [ ] Ubuntu ≥ 24.04，磁碟可用 ≥ 300 GB
- [ ] Docker + Compose v2.35+ 已安裝
- [ ] 目前使用者可執行 `docker ps`
- [ ] 核心連接埠未被佔用
- [ ] 已選定部署規格（mini / standard / full）
- [ ] 執行 `install` → `verify` → 瀏覽器存取 `:8888`

### 日常運維

```bash
.scripts/docker/install_linux.sh start      # 開機後啟動
.scripts/docker/install_linux.sh status       # 查看狀態
.scripts/docker/install_linux.sh logs         # 查看日誌
.scripts/docker/install_linux.sh restart      # 重新啟動
```

### 版本更新

```bash
git pull
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

---

## 注意事項

1. **部署規格**：安裝前確認記憶體與規格相符；可用 `analyze_deploy_memory.sh` 分析
2. **磁碟**：本機建置 + 資料磁碟區增長快，**最低 300 GB**，生產建議 500 GB+ SSD
3. **sudo**：首次安裝建議 sudo，以設定映像檔來源與 RTP 連接埠
4. **密碼**：生產環境務必修改中介軟體預設密碼（見 [部署最佳實踐](./部署最佳实践_zh_tw.md#預設帳號密碼)）
5. **clean**：會刪除資料磁碟區，執行前務必備份
6. **網路**：需能存取 Docker Hub 或已設定的映像檔加速來源

## 技術支援

1. 查閱 [部署最佳實踐.md](./部署最佳实践_zh_tw.md) 故障排查章節
2. 查看日誌：`.scripts/docker/install_linux.sh logs`
3. 檢查容器：`docker ps -a`
4. 向專案儲存庫提交 Issue

---

**文件版本**：2.0  
**最後更新**：2026-07-07  
**腳本入口**：`.scripts/docker/install_linux.sh`
