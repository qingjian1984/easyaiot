# Лучшие практики развёртывания EasyAIoT

> Этот документ **синхронизирован со скриптами проекта** и применим к производственным и тестовым средам Linux.  
> Для быстрого старта см. [Руководство по развёртыванию платформы](./平台部署文档_ru.md). Для Windows см. [Руководство по развёртыванию на Windows](./平台Windows部署文档_ru.md).

---

## Содержание

- [Быстрый старт за 5 минут](#быстрый-старт-за-5-минут)
- [Выбор профиля развёртывания](#выбор-профиля-развёртывания)
- [Требования к окружению](#требования-к-окружению)
- [Контрольный список перед развёртыванием](#контрольный-список-перед-развёртыванием)
- [Развёртывание в один клик](#развёртывание-в-один-клик)
- [Пошаговое развёртывание](#пошаговое-развёртывание)
- [Типовые операции](#типовые-операции)
- [Предсобранные образы (опционально)](#предсобранные-образы-опционально)
- [Настройка GPU](#настройка-gpu)
- [Особые среды](#особые-среды)
- [Примечания по базам данных](#примечания-по-базам-данных)
- [Учётные данные по умолчанию](#учётные-данные-по-умолчанию)
- [Устранение неполадок](#устранение-неполадок)
- [Расположение логов](#расположение-логов)
- [Обновление и удаление](#обновление-и-удаление)
- [Справочник по архитектуре](#справочник-по-архитектуре)

---

## Быстрый старт за 5 минут

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

**Длительность первой установки**: без предсобранных образов скрипт выполняет локальный `docker build` для DEVICE / AI / VIDEO / WEB, обычно **от 30 минут до нескольких часов** в зависимости от CPU, диска и сети. Сначала выполните `pull`, чтобы значительно сократить время установки (см. [Предсобранные образы](#предсобранные-образы-опционально)).

---

## Выбор профиля развёртывания

При установке скрипт интерактивно выбирает **профиль развёртывания** (или задайте `EASYAIOT_DEPLOY_PROFILE`). Выбор сохраняется в `.scripts/docker/.deploy_profile` и повторно используется командами `start` / `stop` / `update`.

| Профиль | Псевдонимы | Рекомендуемая RAM | Сценарий использования |
|---------|---------|-----------------|----------|
| **mini** | `1` / `4g` | ≥ 4 GB | Периферийные узлы, PoC, хосты с ограниченными ресурсами |
| **standard** | `2` / `16g` | ≥ 16 GB | Обычное production без некоторых тяжёлых компонентов |
| **full** | `3` (по умолчанию) | ≥ 20 GB | Полный функционал, включая мобильный H5 APP |

Просмотр текущего профиля и области сервисов:

```bash
.scripts/docker/install_linux.sh profile
```

### Сервисы по профилям

**mini (минимальный периферийный)**

- Бизнес: `iot-system`, VIDEO, AI, WEB
- Middleware: PostgreSQL, Redis, SRS
- Не запускаются: Nacos, Gateway, Kafka, iot-sink, MinIO, Milvus, ZLMediaKit, Node-RED, TDengine, EMQX и большинство подмодулей DEVICE
- Маршрутизация API: nginx проксирует `/admin-api` и `/dev-api` напрямую на `iot-system:48099`

**standard**

- Не запускаются: TDengine, EMQX, Node-RED, `iot-device`, `iot-tdengine`
- Все остальные бизнес-модули и middleware запускаются

**full**

- Все бизнес-модули и middleware, включая **мобильный H5 APP** (порт 9010)

Анализ соответствия памяти контейнеров выбранному профилю:

```bash
.scripts/docker/analyze_deploy_memory.sh
.scripts/docker/analyze_deploy_memory.sh --all-profiles   # compare all three
```

---

## Требования к окружению

### Аппаратное обеспечение

| Ресурс | Минимум | Рекомендуется |
|----------|---------|-------------|
| CPU | 4 ядра | 8+ ядер |
| RAM | См. [Выбор профиля развёртывания](#выбор-профиля-развёртывания) (full мин. 20 GB) | 32 GB+ |
| Диск | **300 GB** свободно | 500 GB+ SSD |
| GPU | Нет (работает на CPU) | NVIDIA GPU (CUDA 12.8 для AI inference/training) |

> Диск используется для слоёв Docker-образов, кэша сборки (`.build-cache/`), баз данных и томов объектного хранилища. Первая локальная сборка потребляет значительное место — оставьте достаточный запас.

### Программное обеспечение

| ПО | Требование | Примечания |
|----------|-------------|-------|
| ОС | **Ubuntu 24.04 LTS** (минимум) | **Рекомендуется Ubuntu 26.04 LTS**; также поддерживаются Kylin и ARM64 (см. [Особые среды](#особые-среды)) |
| Docker | Установлен и daemon доступен | Если отсутствует: `curl -fsSL https://get.docker.com \| sudo sh` |
| Docker Compose | **v2.35.0+** (плагин `docker compose`) | Если отсутствует: `sudo apt install docker-compose-plugin` |
| NVIDIA Driver | 525+ | Только для сценариев с GPU |
| NVIDIA Container Toolkit | Последняя версия | Только для сценариев с GPU |

### Права Docker (Linux)

```bash
# Add current user to docker group (recommended)
sudo usermod -aG docker $USER
newgrp docker   # or log in again

# Verify
docker ps
```

> Настройка зеркал Docker и резервирование RTP-портов требует root — **используйте `sudo` при первой установке**.

### Требования к портам

Перед развёртыванием убедитесь, что эти порты свободны (некоторые могут не использоваться в зависимости от профиля):

| Порт | Сервис | Примечания |
|------|---------|-------|
| 1880 | Node-RED | Движок правил (full/standard) |
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

Проверка использования портов:

```bash
ss -tlnp | grep -E '8848|5432|6379|9092|5000|6000|8888|48080'
```

---

## Контрольный список перед развёртыванием

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

## Развёртывание в один клик

### Входной скрипт

Единый оркестратор: `.scripts/docker/install_linux.sh`

```bash
# From project root (recommended)
sudo .scripts/docker/install_linux.sh install

# Or from script directory
cd .scripts/docker
sudo ./install_linux.sh install
```

### Что автоматически выполняет `install`

1. **Выбор профиля развёртывания** — mini / standard / full, сохраняется в `.deploy_profile`
2. **Предсобранные образы** — пропуск локальной сборки, если настроен удалённый registry и выбран pull
3. **Проверка окружения** — Docker, Compose, создание контейнеров (включая `/dev/null`)
4. **Определение IP хоста** — для URL медиа GB28181 / ZLMediaKit (задайте `HOST_IP=<ip>`, чтобы пропустить)
5. **Резервирование RTP-портов** — ядро резервирует 30000-30500 (требуется root)
6. **Зеркало Docker** — настраивает ускорение `docker.m.daocloud.io` (требуется root)
7. **Создание Docker-сети** — `easyaiot-network`
8. **Развёртывание модулей по порядку**:
   - Middleware (`.scripts/docker/install_middleware_linux.sh`)
   - DEVICE → AI → VIDEO → WEB → APP (full)
9. **Ожидание базовых сервисов** — проверки здоровья PostgreSQL / Nacos / Redis
10. **Platform Agent** — обеспечение edge agent при необходимости

### Проверка развёртывания

```bash
.scripts/docker/install_linux.sh verify
```

Пример успешного вывода:

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

Откройте `http://<server-ip>:8888` в браузере.

---

## Пошаговое развёртывание

Для детального контроля развёртывайте модуль за модулем. **Сначала задайте профиль развёртывания**, чтобы все модули оставались согласованными:

```bash
export EASYAIOT_DEPLOY_PROFILE=full   # or mini / standard
```

### Шаг 1: Middleware

```bash
cd .scripts/docker
./install_middleware_linux.sh install
```

| Middleware | Образ | Порт | Назначение |
|------------|-------|------|---------|
| Nacos | nacos/nacos-server:v2.5.1 | 8848 | Реестр сервисов и конфигурация |
| PostgreSQL | postgres:18 | 5432 | Основная БД (6 бизнес-БД) |
| Redis | redis:7.4.8 | 6379 | Кэш |
| Kafka | apache/kafka:3.8.0 | 9092 | Очередь сообщений |
| MinIO | minio/minio | 9000/9001 | Объектное хранилище |
| Milvus | milvusdb/milvus:v2.6.0 | 19530/9091 | Векторная БД (распознавание лиц) |
| SRS | ossrs/srs:5 | 1935 | Стриминг |
| EMQX | emqx/emqx:5.8.7 | 1883 | MQTT (профиль full) |
| ZLMediaKit | zlmediakit/zlmediakit:master | 6080 | Медиасервер |
| TDengine | tdengine/tsdb:3.3.8.4 | 6030 | Временная БД (профиль full) |
| Node-RED | nodered/node-red:latest | 1880 | Движок правил |

Проверки готовности:

```bash
docker exec postgres-server pg_isready -U postgres
curl -s http://localhost:8848/nacos/actuator/health
docker exec redis-server redis-cli -a basiclab@iot975248395 ping
```

### Шаг 2: DEVICE

```bash
cd DEVICE
./install_linux.sh install
```

| Сервис | Порт | Описание |
|---------|------|-------------|
| iot-gateway | 48080 | API gateway |
| iot-system | 48099 | Управление системой |
| iot-infra | 48066 | Инфраструктура |
| iot-device | 48055 | Управление устройствами |
| iot-dataset | 48077 | Наборы данных |
| iot-message | 48033 | Обмен сообщениями |
| iot-file | 48022 | Файловый сервис |
| iot-sink | 48011 | Протокольный адаптер |
| iot-gb28181 | 5060 | Видеонаблюдение GB28181 |

### Шаги 3–5: AI / VIDEO / WEB

```bash
cd AI    && ./install_linux.sh install
cd VIDEO && ./install_linux.sh install
cd WEB   && ./install_linux.sh install
cd APP   && ./install_linux.sh install   # full only
```

### Только бизнес-модули (без middleware)

```bash
cd .scripts/docker
./install_business_linux.sh install              # all business modules
./install_business_linux.sh update DEVICE WEB    # update specific modules
./install_business_linux.sh verify
```

---

## Типовые операции

### Единый скрипт

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

### Скрипты по модулям

Каждый каталог модуля (`DEVICE` / `AI` / `VIDEO` / `WEB` / `APP`) поддерживает:

```bash
./install_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

Только middleware:

```bash
cd .scripts/docker
./install_middleware_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

### Распространённые переменные окружения

| Переменная | Описание |
|----------|-------------|
| `EASYAIOT_DEPLOY_PROFILE` | Профиль: `mini` / `standard` / `full` |
| `HOST_IP` | Принудительно задать IP хоста, пропустить автоопределение |
| `PARALLEL_MODULES=true` | Параллельный start/update бизнес-модулей (при достаточной RAM) |
| `PARALLEL_BUILD=true` | Параллельная сборка (по умолчанию последовательно, чтобы избежать OOM) |
| `FORCE_NETWORK_RECREATE=true` | Пересоздать Docker-сеть после смены IP хоста |
| `EASYAIOT_RUNTIME_REGISTRY` | URL registry предсобранных образов |

---

## Предсобранные образы (опционально)

Загрузите предсобранные бизнес-образы из удалённого registry, чтобы пропустить длительную локальную сборку Maven / pnpm / pip.

Файл конфигурации: `.scripts/docker/runtime_registry.conf`

```bash
# Interactive pull (before install or during update)
.scripts/docker/install_linux.sh pull

# Build and push runtime images (CI/release)
.scripts/docker/install_linux.sh build-runtime          # all modules
.scripts/docker/install_linux.sh build-runtime DEVICE   # specific module
```

После успешного pull последующие `install` / `update` обнаружат `.runtime_images_pulled` и сразу запустят контейнеры.

---

## Настройка GPU

### Установка и проверка

```bash
nvidia-smi

# Install NVIDIA Container Toolkit
# https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html

docker run --rm --gpus all nvidia/cuda:12.8.0-base-ubuntu24.04 nvidia-smi
```

### Автоопределение

Скрипты установки автоматически определяют GPU:

- GPU присутствует → включают `runtime: nvidia`, `NVIDIA_VISIBLE_DEVICES=all`
- GPU отсутствует → режим CPU

### Несколько GPU

```bash
export CUDA_VISIBLE_DEVICES=0,1
```

---

## Особые среды

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

## Примечания по базам данных

### Бизнес-базы PostgreSQL

При запуске инициализируются шесть баз данных (скрипты в `.scripts/postgresql/`):

| База данных | SQL-файл | Назначение |
|----------|----------|---------|
| ruoyi-vue-pro20 | ruoyi-vue-pro10.sql | Управление системой |
| iot-ai20 | iot-ai10.sql | AI-сервис |
| iot-device10 | iot-device10.sql | Управление устройствами |
| iot-gb2818110 | iot-gb2818110.sql | Видеонаблюдение |
| iot-message10 | iot-message10.sql | Обмен сообщениями |
| iot-video10 | iot-video10.sql | Обработка видео |

### TDengine

SQL в `.scripts/tdengine/tdengine_super_tables.sql`; автоматическая инициализация при профиле full.

### Резервное копирование

```bash
.scripts/postgresql/backup_databases.sh
```

---

## Учётные данные по умолчанию

| Middleware | Имя пользователя | Пароль | Консоль |
|------------|----------|----------|---------|
| Nacos | nacos | nacos | http://\<IP\>:8848/nacos |
| PostgreSQL | postgres | iot45722414822 | — |
| Redis | — | basiclab@iot975248395 | — |
| MinIO | minioadmin | basiclab@iot975248395 | http://\<IP\>:9001 |
| EMQX | admin | basiclab@iot6874125784 | http://\<IP\>:18083 |
| Milvus | — | — | http://\<IP\>:9091 |

> **Измените все пароли по умолчанию в production.**

---

## Устранение неполадок

### Сбои запуска сервисов

```bash
docker ps -a
docker logs -f postgres-server
docker logs -f nacos-server
docker logs -f ai-service
docker logs -f video-service
.scripts/docker/install_linux.sh logs
```

### Проблемы с сетью

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

### Проблемы системы Docker

```bash
sudo .scripts/docker/diagnose_docker_systemd.sh diagnose
sudo .scripts/docker/diagnose_docker_systemd.sh fix-all
.scripts/docker/cleanup_docker_space.sh
df -h && docker system df
```

### Consumer group Kafka

```bash
cd VIDEO && ./fix_kafka_consumer_group.sh
```

### Конфликты портов

Измените сопоставление портов в `docker-compose.yml` модуля или остановите конфликтующий процесс.

### Проблемы WEB после смены профиля

Frontend встраивает профиль развёртывания при сборке — пересоберите WEB после переключения:

```bash
cd WEB && ./install_linux.sh build
```

---

## Расположение логов

| Расположение | Описание |
|----------|-------------|
| `.scripts/docker/logs/` | Единые логи скриптов установки / middleware |
| `DEVICE/logs/` | Логи сервисов DEVICE |
| `AI/data/logs/` | Логи AI-сервиса |
| `VIDEO/data/logs/` | Логи VIDEO-сервиса |
| `docker logs <container>` | Логи контейнера в реальном времени |

---

## Обновление и удаление

### Обновление кода и сервисов

```bash
git pull origin main
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

Обновление одного модуля:

```bash
cd AI && ./install_linux.sh update
```

### Удаление

```bash
sudo .scripts/docker/install_linux.sh clean

# Optional: remove data volume directories
rm -rf .scripts/docker/db_data .scripts/docker/redis_data \
       .scripts/docker/minio_data .scripts/docker/mq_data \
       .scripts/docker/taos_data .scripts/docker/milvus_data
```

---

## Справочник по архитектуре

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

*Версия документа: 2026-07-07 | Точка входа скрипта: `.scripts/docker/install_linux.sh`*
