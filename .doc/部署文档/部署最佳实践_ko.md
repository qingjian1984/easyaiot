# EasyAIoT 배포 모범 사례

> 본 문서는 **프로젝트 스크립트와 실시간 동기화**되며 Linux 프로덕션/테스트 환경에 적용됩니다.  
> 빠른 시작은 [플랫폼 배포 가이드](./平台部署文档_ko.md)를, Windows는 [Windows 배포 가이드](./平台Windows部署文档_ko.md)를 참조하세요.

---

## 목차

- [5분 빠른 시작](#5분-빠른-시작)
- [배포 프로필 선택](#배포-프로필-선택)
- [환경 요구사항](#환경-요구사항)
- [배포 전 점검 목록](#배포-전-점검-목록)
- [원클릭 배포](#원클릭-배포)
- [단계별 배포](#단계별-배포)
- [일반 운영](#일반-운영)
- [사전 빌드 이미지(선택)](#사전-빌드-이미지선택)
- [GPU 구성](#gpu-구성)
- [특수 환경](#특수-환경)
- [데이터베이스 참고사항](#데이터베이스-참고사항)
- [기본 자격 증명](#기본-자격-증명)
- [문제 해결](#문제-해결)
- [로그 위치](#로그-위치)
- [업데이트 및 제거](#업데이트-및-제거)
- [아키텍처 참고](#아키텍처-참고)

---

## 5분 빠른 시작

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

**최초 설치 소요 시간**: 사전 빌드 이미지 없이 스크립트가 DEVICE / AI / VIDEO / WEB에 대해 로컬 `docker build`를 실행하며, CPU·디스크·네트워크에 따라 일반적으로 **30분에서 수 시간** 소요됩니다. 설치 시간을 크게 단축하려면 먼저 `pull`을 실행하세요([사전 빌드 이미지](#사전-빌드-이미지선택) 참조).

---

## 배포 프로필 선택

설치 시 스크립트가 **배포 프로필**을 대화형으로 선택합니다(`EASYAIOT_DEPLOY_PROFILE` 설정 가능). 선택 내용은 `.scripts/docker/.deploy_profile`에 저장되며 `start` / `stop` / `update`에서 재사용됩니다.

| 프로필 | 별칭 | 권장 RAM | 사용 사례 |
|---------|---------|-----------------|----------|
| **mini** | `1` / `4g` | ≥ 4 GB | 엣지 노드, PoC, 리소스 제한 호스트 |
| **standard** | `2` / `16g` | ≥ 16 GB | 일부 무거운 구성 요소 없는 일반 프로덕션 |
| **full** | `3` (기본값) | ≥ 20 GB | APP 모바일 H5 포함 전체 기능 |

현재 프로필 및 서비스 범위 확인:

```bash
.scripts/docker/install_linux.sh profile
```

### 프로필별 서비스

**mini (엣지 최소)**

- 비즈니스: `iot-system`, VIDEO, AI, WEB
- 미들웨어: PostgreSQL, Redis, SRS
- 미시작: Nacos, Gateway, Kafka, iot-sink, MinIO, Milvus, ZLMediaKit, Node-RED, TDengine, EMQX 및 대부분의 DEVICE 하위 모듈
- API 라우팅: nginx가 `/admin-api` 및 `/dev-api`를 `iot-system:48099`로 직접 프록시

**standard**

- 미시작: TDengine, EMQX, Node-RED, `iot-device`, `iot-tdengine`
- 그 외 모든 비즈니스 모듈 및 미들웨어 시작

**full**

- **APP 모바일 H5** 포함 모든 비즈니스 모듈 및 미들웨어(포트 9010)

컨테이너 메모리가 프로필과 일치하는지 분석:

```bash
.scripts/docker/analyze_deploy_memory.sh
.scripts/docker/analyze_deploy_memory.sh --all-profiles   # compare all three
```

---

## 환경 요구사항

### 하드웨어

| 리소스 | 최소 | 권장 |
|----------|---------|-------------|
| CPU | 4코어 | 8코어+ |
| RAM | [배포 프로필 선택](#배포-프로필-선택) 참조(full 최소 20 GB) | 32 GB+ |
| 디스크 | **300 GB** 여유 | 500 GB+ SSD |
| GPU | 없음(CPU 가능) | NVIDIA GPU(AI 추론/학습용 CUDA 12.8) |

> 디스크는 Docker 이미지 레이어, 빌드 캐시(`.build-cache/`), 데이터베이스 및 객체 스토리지 볼륨에 사용됩니다. 최초 로컬 빌드는 상당한 공간을 소비하므로 충분한 여유를 확보하세요.

### 소프트웨어

| 소프트웨어 | 요구사항 | 참고 |
|----------|-------------|-------|
| OS | **Ubuntu 24.04 LTS**(최소) | **Ubuntu 26.04 LTS 권장**; Kylin 및 ARM64도 지원([특수 환경](#특수-환경) 참조) |
| Docker | 설치 및 데몬 접근 가능 | 없을 경우: `curl -fsSL https://get.docker.com \| sudo sh` |
| Docker Compose | **v2.35.0+**(`docker compose` 플러그인) | 없을 경우: `sudo apt install docker-compose-plugin` |
| NVIDIA Driver | 525+ | GPU 시나리오만 |
| NVIDIA Container Toolkit | 최신 | GPU 시나리오만 |

### Docker 권한(Linux)

```bash
# Add current user to docker group (recommended)
sudo usermod -aG docker $USER
newgrp docker   # or log in again

# Verify
docker ps
```

> Docker 미러 구성 및 RTP 포트 예약에는 root가 필요합니다 — **최초 설치 시 `sudo` 사용을 권장합니다**.

### 포트 요구사항

배포 전 다음 포트가 비어 있는지 확인하세요(프로필에 따라 일부는 미사용):

| Port | Service | Notes |
|------|---------|-------|
| 1880 | Node-RED | 규칙 엔진(full/standard) |
| 1883 | EMQX | MQTT 브로커(full) |
| 1935 | SRS | RTMP 스트리밍 |
| 5432 | PostgreSQL | 주 데이터베이스 |
| 6000 | VIDEO | 비디오 처리 |
| 6030 | TDengine | 시계열 DB(full) |
| 6080 | ZLMediaKit | 미디어 서버 |
| 6379 | Redis | 캐시 |
| 8848 | Nacos | 레지스트리/설정 센터 |
| 8888 | WEB | 관리 UI |
| 9000/9001 | MinIO | 객체 스토리지 API / 콘솔 |
| 9010 | APP | 모바일 H5(full만) |
| 9092 | Kafka | 메시지 큐 |
| 19530 | Milvus | 벡터 데이터베이스 |
| 48080 | Gateway | API 게이트웨이 |
| 5000 | AI | AI 추론 |
| 30000-30500 | ZLM RTP | 미디어 수집(스크립트가 예약 시도) |

포트 사용 확인:

```bash
ss -tlnp | grep -E '8848|5432|6379|9092|5000|6000|8888|48080'
```

---

## 배포 전 점검 목록

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

## 원클릭 배포

### 진입 스크립트

통합 오케스트레이터: `.scripts/docker/install_linux.sh`

```bash
# From project root (recommended)
sudo .scripts/docker/install_linux.sh install

# Or from script directory
cd .scripts/docker
sudo ./install_linux.sh install
```

### `install`이 자동으로 수행하는 작업

1. **배포 프로필 선택** — mini / standard / full, `.deploy_profile`에 저장
2. **사전 빌드 이미지** — 원격 레지스트리가 구성되고 pull이 선택된 경우 로컬 빌드 건너뜀
3. **환경 점검** — Docker, Compose, 컨테이너 생성(`/dev/null` 포함)
4. **호스트 IP 감지** — GB28181 / ZLMediaKit 미디어 URL용(`HOST_IP=<ip>` 설정 시 건너뜀)
5. **RTP 포트 예약** — 커널이 30000-30500 예약(root 필요)
6. **Docker 미러** — `docker.m.daocloud.io` 가속 구성(root 필요)
7. **Docker 네트워크 생성** — `easyaiot-network`
8. **순서대로 모듈 배포**:
   - 미들웨어(`.scripts/docker/install_middleware_linux.sh`)
   - DEVICE → AI → VIDEO → WEB → APP(full)
9. **기본 서비스 대기** — PostgreSQL / Nacos / Redis 상태 점검
10. **Platform Agent** — 필요 시 엣지 에이전트 확보

### 배포 확인

```bash
.scripts/docker/install_linux.sh verify
```

성공 출력 예시:

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

브라우저에서 `http://<server-ip>:8888`을 엽니다.

---

## 단계별 배포

세밀한 제어를 위해 모듈별로 배포합니다. **먼저 배포 프로필을 설정**하여 모든 모듈이 일관되게 유지되도록 합니다:

```bash
export EASYAIOT_DEPLOY_PROFILE=full   # or mini / standard
```

### 1단계: 미들웨어

```bash
cd .scripts/docker
./install_middleware_linux.sh install
```

| 미들웨어 | Image | Port | 용도 |
|------------|-------|------|---------|
| Nacos | nacos/nacos-server:v2.5.1 | 8848 | 서비스 레지스트리 및 설정 |
| PostgreSQL | postgres:18 | 5432 | 주 DB(6개 비즈니스 DB) |
| Redis | redis:7.4.8 | 6379 | Cache |
| Kafka | apache/kafka:3.8.0 | 9092 | Message queue |
| MinIO | minio/minio | 9000/9001 | Object storage |
| Milvus | milvusdb/milvus:v2.6.0 | 19530/9091 | Vector DB(얼굴 인식) |
| SRS | ossrs/srs:5 | 1935 | Streaming |
| EMQX | emqx/emqx:5.8.7 | 1883 | MQTT(full 프로필) |
| ZLMediaKit | zlmediakit/zlmediakit:master | 6080 | Media server |
| TDengine | tdengine/tsdb:3.3.8.4 | 6030 | Time-series DB(full 프로필) |
| Node-RED | nodered/node-red:latest | 1880 | Rule engine |

준비 상태 점검:

```bash
docker exec postgres-server pg_isready -U postgres
curl -s http://localhost:8848/nacos/actuator/health
docker exec redis-server redis-cli -a basiclab@iot975248395 ping
```

### 2단계: DEVICE

```bash
cd DEVICE
./install_linux.sh install
```

| 서비스 | Port | 설명 |
|---------|------|-------------|
| iot-gateway | 48080 | API Gateway |
| iot-system | 48099 | 시스템 관리 |
| iot-infra | 48066 | 인프라 |
| iot-device | 48055 | 디바이스 관리 |
| iot-dataset | 48077 | 데이터셋 |
| iot-message | 48033 | 메시징 |
| iot-file | 48022 | 파일 서비스 |
| iot-sink | 48011 | 프로토콜 어댑터 |
| iot-gb28181 | 5060 | GB28181 비디오 감시 |

### 3–5단계: AI / VIDEO / WEB

```bash
cd AI    && ./install_linux.sh install
cd VIDEO && ./install_linux.sh install
cd WEB   && ./install_linux.sh install
cd APP   && ./install_linux.sh install   # full only
```

### 비즈니스 모듈만(미들웨어 없음)

```bash
cd .scripts/docker
./install_business_linux.sh install              # all business modules
./install_business_linux.sh update DEVICE WEB    # update specific modules
./install_business_linux.sh verify
```

---

## 일반 운영

### 통합 스크립트

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

### 모듈별 스크립트

각 모듈 디렉터리(`DEVICE` / `AI` / `VIDEO` / `WEB` / `APP`)는 다음을 지원합니다:

```bash
./install_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

미들웨어만:

```bash
cd .scripts/docker
./install_middleware_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

### 일반 환경 변수

| 변수 | 설명 |
|----------|-------------|
| `EASYAIOT_DEPLOY_PROFILE` | Profile: `mini` / `standard` / `full` |
| `HOST_IP` | 호스트 IP 강제 지정, 자동 감지 건너뜀 |
| `PARALLEL_MODULES=true` | 비즈니스 모듈 병렬 시작/업데이트(RAM 허용 시) |
| `PARALLEL_BUILD=true` | 병렬 빌드(기본값은 OOM 방지를 위해 순차) |
| `FORCE_NETWORK_RECREATE=true` | 호스트 IP 변경 후 Docker 네트워크 재생성 |
| `EASYAIOT_RUNTIME_REGISTRY` | 사전 빌드 이미지 레지스트리 URL |

---

## 사전 빌드 이미지(선택)

원격 레지스트리에서 사전 빌드된 비즈니스 이미지를 pull하여 긴 로컬 Maven / pnpm / pip 빌드를 건너뜁니다.

구성 파일: `.scripts/docker/runtime_registry.conf`

```bash
# Interactive pull (before install or during update)
.scripts/docker/install_linux.sh pull

# Build and push runtime images (CI/release)
.scripts/docker/install_linux.sh build-runtime          # all modules
.scripts/docker/install_linux.sh build-runtime DEVICE   # specific module
```

pull 성공 후 후속 `install` / `update`는 `.runtime_images_pulled`를 감지하고 컨테이너를 직접 시작합니다.

---

## GPU 구성

### 설치 및 확인

```bash
nvidia-smi

# Install NVIDIA Container Toolkit
# https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html

docker run --rm --gpus all nvidia/cuda:12.8.0-base-ubuntu24.04 nvidia-smi
```

### 자동 감지

설치 스크립트가 GPU를 자동 감지합니다:

- GPU 있음 → `runtime: nvidia`, `NVIDIA_VISIBLE_DEVICES=all` 활성화
- GPU 없음 → CPU 모드

### 다중 GPU

```bash
export CUDA_VISIBLE_DEVICES=0,1
```

---

## 특수 환경

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

## 데이터베이스 참고사항

### PostgreSQL 비즈니스 데이터베이스

시작 시 6개 데이터베이스가 초기화됩니다(스크립트는 `.scripts/postgresql/`):

| Database | SQL File | 용도 |
|----------|----------|---------|
| ruoyi-vue-pro20 | ruoyi-vue-pro10.sql | 시스템 관리 |
| iot-ai20 | iot-ai10.sql | AI 서비스 |
| iot-device10 | iot-device10.sql | 디바이스 관리 |
| iot-gb2818110 | iot-gb2818110.sql | 비디오 감시 |
| iot-message10 | iot-message10.sql | 메시징 |
| iot-video10 | iot-video10.sql | 비디오 처리 |

### TDengine

SQL은 `.scripts/tdengine/tdengine_super_tables.sql`에 있으며 full 프로필에서 자동 초기화됩니다.

### 백업

```bash
.scripts/postgresql/backup_databases.sh
```

---

## 기본 자격 증명

| 미들웨어 | Username | Password | Console |
|------------|----------|----------|---------|
| Nacos | nacos | nacos | http://\<IP\>:8848/nacos |
| PostgreSQL | postgres | iot45722414822 | — |
| Redis | — | basiclab@iot975248395 | — |
| MinIO | minioadmin | basiclab@iot975248395 | http://\<IP\>:9001 |
| EMQX | admin | basiclab@iot6874125784 | http://\<IP\>:18083 |
| Milvus | — | — | http://\<IP\>:9091 |

> **프로덕션에서는 모든 기본 비밀번호를 변경하세요.**

---

## 문제 해결

### 서비스 시작 실패

```bash
docker ps -a
docker logs -f postgres-server
docker logs -f nacos-server
docker logs -f ai-service
docker logs -f video-service
.scripts/docker/install_linux.sh logs
```

### 네트워크 문제

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

### Docker 시스템 문제

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

### 포트 충돌

모듈의 `docker-compose.yml`에서 포트 매핑을 수정하거나 충돌하는 프로세스를 중지하세요.

### 프로필 변경 후 WEB 문제

프론트엔드는 빌드 시 배포 프로필을 내장하므로 전환 후 WEB을 재빌드하세요:

```bash
cd WEB && ./install_linux.sh build
```

---

## 로그 위치

| 위치 | 설명 |
|----------|-------------|
| `.scripts/docker/logs/` | 통합 설치 / 미들웨어 스크립트 로그 |
| `DEVICE/logs/` | DEVICE 서비스 로그 |
| `AI/data/logs/` | AI 서비스 로그 |
| `VIDEO/data/logs/` | VIDEO 서비스 로그 |
| `docker logs <container>` | 컨테이너 실시간 로그 |

---

## 업데이트 및 제거

### 코드 및 서비스 업데이트

```bash
git pull origin main
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

단일 모듈 업데이트:

```bash
cd AI && ./install_linux.sh update
```

### 제거

```bash
sudo .scripts/docker/install_linux.sh clean

# Optional: remove data volume directories
rm -rf .scripts/docker/db_data .scripts/docker/redis_data \
       .scripts/docker/minio_data .scripts/docker/mq_data \
       .scripts/docker/taos_data .scripts/docker/milvus_data
```

---

## 아키텍처 참고

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

*문서 버전: 2026-07-07 | 스크립트 진입점: `.scripts/docker/install_linux.sh`*
