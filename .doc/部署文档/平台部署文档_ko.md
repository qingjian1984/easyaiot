# EasyAIoT 플랫폼 배포 가이드

> **초보자 권장 경로**: 이 문서의 [빠른 시작](#빠른-시작)으로 첫 배포를 완료하세요. 고급 운영, 문제 해결, GPU 및 데이터베이스 세부 사항은 [배포 모범 사례](./部署最佳实践_ko.md)를 참조하세요.

## 목차

- [개요](#개요)
- [환경 요구사항](#환경-요구사항)
- [빠른 시작](#빠른-시작)
- [배포 프로필](#배포-프로필)
- [스크립트 참조](#스크립트-참조)
- [모듈 개요](#모듈-개요)
- [서비스 포트](#서비스-포트)
- [FAQ](#faq)
- [로그 관리](#로그-관리)
- [배포 워크플로](#배포-워크플로)

---

## 개요

EasyAIoT는 **Docker 컨테이너와 통합 설치 스크립트**로 배포되는 클라우드-엣지 통합 지능형 알고리즘 플랫폼입니다.

### 플랫폼 구성 요소

| 모듈 | 디렉터리 | 설명 |
|--------|-----------|-------------|
| 기본 서비스 | `.scripts/docker` | Nacos, PostgreSQL, Redis, Kafka, MinIO 등 |
| DEVICE | `DEVICE/` | 장치 관리 및 API 게이트웨이 (Java / Spring Cloud) |
| AI | `AI/` | 모델 학습, 추론, OCR, LLM (Python) |
| VIDEO | `VIDEO/` | 비디오 스트리밍, 알림, 녹화, 얼굴 인식 (Python) |
| WEB | `WEB/` | 관리 콘솔 (Vue 3) |
| APP | `APP/` | 모바일 H5 (**full** 프로필만) |

### 통합 진입 스크립트

| OS | 스크립트 |
|----|--------|
| Linux | `.scripts/docker/install_linux.sh` |
| macOS | `.scripts/docker/install_mac.sh` |
| Windows | `.scripts/docker/install_win.ps1` |

> 아래 예시는 **Linux** 기준입니다. macOS/Windows에서는 `install_linux.sh`를 해당 스크립트로 바꾸세요.

---

## 환경 요구사항

### 시스템 및 하드웨어

| 항목 | 요구사항 |
|------|-------------|
| **OS** | **Ubuntu 24.04 LTS 이상** (**Ubuntu 26.04 LTS 권장**); macOS 10.15+, Windows 10/11도 지원 |
| **CPU** | 최소 4코어, 8코어 이상 권장 |
| **RAM** | 프로필에 따라 다름; full 프로필 최소 20 GB, 32 GB 권장 |
| **디스크** | **최소 300 GB 여유 공간**, 500 GB+ SSD 권장 |
| **GPU** | 선택 사항; AI용 NVIDIA GPU (CUDA 12.8) 권장 |

### 소프트웨어 의존성

| 소프트웨어 | 버전 | 확인 |
|----------|---------|--------|
| Docker | 설치됨, 데몬 접근 가능 | `docker --version` |
| Docker Compose | **v2.35.0+** (`docker compose` 플러그인) | `docker compose version` |
| curl | 헬스 체크 | `curl --version` |

Ubuntu 설치:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo apt install -y docker-compose-plugin
sudo usermod -aG docker $USER && newgrp docker
docker ps
```

### Docker 권한 (Linux)

```bash
sudo usermod -aG docker $USER
newgrp docker          # 또는 다시 로그인
docker ps              # permission denied 없이 성공해야 함
```

첫 설치 시 스크립트가 미러 가속 및 RTP 포트 예약을 구성할 수 있도록 `sudo`를 사용하세요.

---

## 빠른 시작

### Linux: 네 단계

```bash
# ① Clone
git clone https://gitee.com/volara/easyaiot.git
cd easyaiot

# ② 자가 점검 (선택 사항이지만 권장)
.scripts/docker/install_linux.sh check
.scripts/docker/detect_system_info.sh

# ③ 원클릭 설치 (첫 실행 시 프로필 1/2/3 선택)
sudo .scripts/docker/install_linux.sh install

# ④ 검증 및 브라우저 열기
.scripts/docker/install_linux.sh verify
# http://<server-ip>:8888
```

### 설치 중 무슨 일이 일어나나요?

1. **배포 프로필** 선택 (mini / standard / full)
2. Docker, Compose, 컨테이너 생성 확인
3. 호스트 IP 감지, `easyaiot-network` 생성
4. 순서대로 배포: 미들웨어 → DEVICE → AI → VIDEO → WEB → APP (full)
5. 서비스 URL 출력

**예상 소요 시간**:

- 사전 빌드 이미지 사용 시: **약 10–30분**
- 로컬 전체 빌드: **30분~수시간**

설치 시간 단축: 설치 전 `.scripts/docker/install_linux.sh pull` 실행 (자세한 내용은 [사전 빌드 이미지](./部署最佳实践_ko.md#사전-빌드-이미지선택) 참조).

### macOS 빠른 시작

```bash
git clone https://gitee.com/volara/easyaiot.git && cd easyaiot
cd .scripts/docker && chmod +x install_mac.sh
./install_mac.sh install
./install_mac.sh verify
```

### Windows

[Windows 배포 가이드](./平台Windows部署文档_ko.md)를 참조하세요.

---

## 배포 프로필

첫 `install` 시 대화형으로 프로필을 선택합니다. 선택 결과는 `.scripts/docker/.deploy_profile`에 저장됩니다.

| 옵션 | 이름 | 권장 RAM | 일반적인 용도 |
|:------:|------|-----------------|-------------|
| 1 | **mini** | ≥ 4 GB | 엣지 노드, PoC |
| 2 | **standard** | ≥ 16 GB | 일반 프로덕션 (TDengine/EMQX 등 미포함) |
| 3 | **full** (기본값) | ≥ 20 GB | 전체 기능 + APP H5 |

현재 프로필 보기:

```bash
.scripts/docker/install_linux.sh profile
```

비대화형 지정:

```bash
export EASYAIOT_DEPLOY_PROFILE=full
sudo .scripts/docker/install_linux.sh install
```

프로필별 차이: [배포 모범 사례 — 배포 프로필 선택](./部署最佳实践_ko.md#배포-프로필-선택).

---

## 스크립트 참조

### 명령

| 명령 | 설명 | 예시 |
|---------|-------------|---------|
| `install` | 첫 설치 및 시작 | `./install_linux.sh install` |
| `start` | 모든 서비스 시작 | `./install_linux.sh start` |
| `stop` | 모든 서비스 중지 | `./install_linux.sh stop` |
| `restart` | 전체 재시작 | `./install_linux.sh restart` |
| `status` | 상태 표시 | `./install_linux.sh status` |
| `logs` | 로그 보기 | `./install_linux.sh logs` |
| `logs <module>` | 모듈 로그 | `./install_linux.sh logs VIDEO` |
| `build` | 로컬에서 이미지 재빌드 | `./install_linux.sh build` |
| `pull` | 사전 빌드 이미지 가져오기 | `./install_linux.sh pull` |
| `update` | 업데이트 및 재시작 | `./install_linux.sh update` |
| `verify` | 헬스 체크 | `./install_linux.sh verify` |
| `check` | Docker 환경 확인 | `./install_linux.sh check` |
| `profile` | 배포 프로필 표시 | `./install_linux.sh profile` |
| `clean` | 컨테이너 및 이미지 제거 ⚠️ | `./install_linux.sh clean` |
| `help` | 도움말 표시 | `./install_linux.sh help` |

> 프로젝트 루트에서는 `./install_linux.sh` 대신 `.scripts/docker/install_linux.sh`를 사용하세요.

### `install`

첫 배포; 의존성 순서대로 활성화된 모듈을 설치합니다.

```bash
sudo .scripts/docker/install_linux.sh install
```

### `verify`

포트 및 헬스 엔드포인트를 확인하고, 성공 시 URL을 출력합니다:

```
[SUCCESS] All services are running!

Service URLs:
  Middleware (Nacos):     http://localhost:8848/nacos
  Middleware (MinIO):     http://localhost:9000 (API), http://localhost:9001 (Console)
  DEVICE (Gateway):       http://localhost:48080
  AI:                     http://localhost:5000
  VIDEO:                  http://localhost:6000
  WEB:                    http://localhost:8888
```

### `clean` ⚠️

**위험**: 컨테이너, 이미지, 볼륨을 제거합니다. 확인(`y`)이 필요합니다.

### 모듈별 / 비즈니스 전용 배포

```bash
# 미들웨어만
cd .scripts/docker && ./install_middleware_linux.sh install

# 비즈니스 모듈만
cd .scripts/docker && ./install_business_linux.sh install

# 단일 모듈 (예: AI)
cd AI && ./install_linux.sh install
```

---

## 모듈 개요

### 기본 서비스 (`.scripts/docker`)

`install_middleware_linux.sh`로 관리되는 미들웨어: Nacos, PostgreSQL, Redis, TDengine, Kafka, MinIO, Milvus, SRS, EMQX, ZLMediaKit, Node-RED 등 (실제 구성은 프로필에 따라 다름).

### DEVICE

- **스택**: Java 21, Spring Boot 2.7, Spring Cloud Gateway
- **기능**: 장치 접속, 제품, 규칙, GB28181, 시스템 관리
- **포트**: 48080 (Gateway)

### AI

- **스택**: Flask, PyTorch 2.9+ (CUDA 12.8)
- **기능**: 모델 학습/추론/배포, OCR, 음성, LLM
- **포트**: 5000

### VIDEO

- **스택**: Flask, OpenCV, FFmpeg
- **기능**: 스트리밍, 실시간/스냅샷 알고리즘, 녹화, 알림, 얼굴 인식
- **포트**: 6000

### WEB

- **스택**: Vue 3.4, TypeScript, Vite, Ant Design Vue 4
- **포트**: 8888

### APP (full만)

- **설명**: 모바일 H5
- **포트**: 9010

---

## 서비스 포트

### 핵심 포트

| 서비스 | 포트 | URL |
|---------|------|-----|
| WEB | 8888 | http://localhost:8888 |
| DEVICE Gateway | 48080 | http://localhost:48080 |
| AI | 5000 | http://localhost:5000 |
| VIDEO | 6000 | http://localhost:6000 |
| Nacos | 8848 | http://localhost:8848/nacos |
| MinIO API / Console | 9000 / 9001 | http://localhost:9001 |
| APP H5 (full) | 9010 | http://localhost:9010 |

전체 포트 목록: [모범 사례 — 포트 요구사항](./部署最佳实践_ko.md#포트-요구사항).

### 헬스 엔드포인트

| 모듈 | 엔드포인트 |
|--------|----------|
| Base (Nacos) | `/nacos/actuator/health` |
| DEVICE | `/actuator/health` |
| AI | `/actuator/health` |
| VIDEO | `/actuator/health` |
| WEB | `/health` |
| APP | `/health` |

---

## FAQ

### 1. Docker Permission Denied

```bash
sudo usermod -aG docker $USER
newgrp docker
docker ps
```

### 2. Docker Compose 버전이 너무 낮음

**v2.35.0+** 필요:

```bash
sudo apt update && sudo apt install -y docker-compose-plugin
docker compose version
```

### 3. 포트 사용 중

```bash
ss -tlnp | grep <port>
# 프로세스를 중지하거나 docker-compose.yml 포트 매핑 수정
```

### 4. 설치 중간에 실패

```bash
ls -lt .scripts/docker/logs/ | head -5
tail -100 .scripts/docker/logs/install_linux_*.log
docker ps -a
.scripts/docker/install_linux.sh status
```

### 5. 서비스는 실행 중이지만 브라우저 연결 불가

```bash
.scripts/docker/install_linux.sh verify
sudo ufw allow 8888    # 방화벽이 활성화된 경우
.scripts/docker/install_linux.sh logs WEB
```

### 6. 디스크 공간 부족

첫 빌드는 많은 디스크를 사용합니다 — **≥ 300 GB 확보**:

```bash
df -h /
docker system df
.scripts/docker/cleanup_docker_space.sh
```

### 7. 프로필 변경 후 WEB 오류

WEB 이미지는 배포 프로필에 연결됩니다 — 변경 후 재빌드:

```bash
cd WEB && ./install_linux.sh build
```

더 보기: [문제 해결](./部署最佳实践_ko.md#문제-해결).

---

## 로그 관리

### 스크립트 로그

`.scripts/docker/logs/`:

```
install_linux_YYYYMMDD_HHMMSS.log
install_middleware_YYYYMMDD_HHMMSS.log
```

```bash
ls -lt .scripts/docker/logs/ | head -5
tail -f .scripts/docker/logs/install_linux_*.log
```

### 컨테이너 로그

```bash
.scripts/docker/install_linux.sh logs
cd DEVICE && docker compose logs -f
docker logs -f video-service
```

---

## 배포 워크플로

### 첫 배포 체크리스트

- [ ] Ubuntu ≥ 24.04, ≥ 300 GB 디스크 여유 공간
- [ ] Docker + Compose v2.35+ 설치됨
- [ ] 현재 사용자에 대해 `docker ps` 동작
- [ ] 핵심 포트 비어 있음
- [ ] 프로필 선택 (mini / standard / full)
- [ ] `install` → `verify` 실행 → `:8888` 열기

### 일상 운영

```bash
.scripts/docker/install_linux.sh start
.scripts/docker/install_linux.sh status
.scripts/docker/install_linux.sh logs
.scripts/docker/install_linux.sh restart
```

### 업데이트

```bash
git pull
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

---

## 참고 사항

1. **프로필**: RAM을 프로필에 맞추세요; `analyze_deploy_memory.sh`로 분석
2. **디스크**: 로컬 빌드와 볼륨이 빠르게 증가 — **최소 300 GB**, 프로덕션은 500 GB+ SSD
3. **sudo**: 미러 및 RTP 설정을 위해 첫 설치 시 권장
4. **비밀번호**: 프로덕션에서 기본 미들웨어 비밀번호 변경 ([자격 증명](./部署最佳实践_ko.md#기본-자격-증명))
5. **clean**: 볼륨 삭제 — 먼저 백업
6. **네트워크**: Docker Hub 또는 구성된 미러 접근 필요

## 지원

1. [배포 모범 사례](./部署最佳实践_ko.md) — 문제 해결
2. 로그: `.scripts/docker/install_linux.sh logs`
3. 컨테이너: `docker ps -a`
4. 프로젝트 저장소에 이슈 등록

---

**문서 버전**: 2.0  
**최종 업데이트**: 2026-07-07  
**스크립트 진입점**: `.scripts/docker/install_linux.sh`
