# Руководство по развёртыванию платформы EasyAIoT

> **Рекомендуется новичкам**: выполните первое развёртывание через [Быстрый старт](#быстрый-старт) в этом документе. Для расширенных операций, устранения неполадок, GPU и сведений о базах данных см. [Лучшие практики развёртывания](./部署最佳实践_ru.md).

## Содержание

- [Обзор](#обзор)
- [Требования к окружению](#требования-к-окружению)
- [Быстрый старт](#быстрый-старт)
- [Профили развёртывания](#профили-развёртывания)
- [Справочник скриптов](#справочник-скриптов)
- [Обзор модулей](#обзор-модулей)
- [Порты сервисов](#порты-сервисов)
- [FAQ](#faq)
- [Управление логами](#управление-логами)
- [Процесс развёртывания](#процесс-развёртывания)

---

## Обзор

EasyAIoT — платформа интеллектуальных алгоритмов с интеграцией облака и периферии, развёртываемая с помощью **Docker-контейнеров и единого скрипта установки**.

### Компоненты платформы

| Модуль | Каталог | Описание |
|--------|-----------|-------------|
| Базовые сервисы | `.scripts/docker` | Nacos, PostgreSQL, Redis, Kafka, MinIO и др. |
| DEVICE | `DEVICE/` | Управление устройствами и API-шлюз (Java / Spring Cloud) |
| AI | `AI/` | Обучение, инференс, OCR, LLM (Python) |
| VIDEO | `VIDEO/` | Видеопоток, оповещения, запись, распознавание лиц (Python) |
| WEB | `WEB/` | Консоль управления (Vue 3) |
| APP | `APP/` | Мобильный H5 (**только профиль full**) |

### Единые точки входа скриптов

| ОС | Скрипт |
|----|--------|
| Linux | `.scripts/docker/install_linux.sh` |
| macOS | `.scripts/docker/install_mac.sh` |
| Windows | `.scripts/docker/install_win.ps1` |

> Примеры ниже для **Linux**; на macOS/Windows замените `install_linux.sh` на соответствующий скрипт.

---

## Требования к окружению

### Система и оборудование

| Параметр | Требование |
|------|-------------|
| **ОС** | **Ubuntu 24.04 LTS или новее** (**рекомендуется Ubuntu 26.04 LTS**); также macOS 10.15+, Windows 10/11 |
| **CPU** | Мин. 4 ядра, рекомендуется 8+ |
| **RAM** | Зависит от профиля; профиль full мин. 20 ГБ, рекомендуется 32 ГБ |
| **Диск** | **Мин. 300 ГБ свободно**, рекомендуется 500 ГБ+ SSD |
| **GPU** | Необязательно; для AI рекомендуется NVIDIA GPU (CUDA 12.8) |

### Программные зависимости

| ПО | Версия | Проверка |
|----------|---------|--------|
| Docker | Установлен, демон доступен | `docker --version` |
| Docker Compose | **v2.35.0+** (плагин `docker compose`) | `docker compose version` |
| curl | Проверки работоспособности | `curl --version` |

Установка на Ubuntu:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo apt install -y docker-compose-plugin
sudo usermod -aG docker $USER && newgrp docker
docker ps
```

### Права Docker (Linux)

```bash
sudo usermod -aG docker $USER
newgrp docker          # или войдите снова
docker ps              # должно выполняться без permission denied
```

Используйте `sudo` при первой установке, чтобы скрипт мог настроить зеркало и резервирование RTP-портов.

---

## Быстрый старт

### Linux: четыре шага

```bash
# ① Clone
git clone https://gitee.com/volara/easyaiot.git
cd easyaiot

# ② Самопроверка (необязательно, но рекомендуется)
.scripts/docker/install_linux.sh check
.scripts/docker/detect_system_info.sh

# ③ Установка в один клик (выбор профиля 1/2/3 при первом запуске)
sudo .scripts/docker/install_linux.sh install

# ④ Проверка и открытие браузера
.scripts/docker/install_linux.sh verify
# http://<server-ip>:8888
```

### Что происходит во время установки?

1. Выбор **профиля развёртывания** (mini / standard / full)
2. Проверка Docker, Compose и создания контейнеров
3. Определение IP хоста, создание `easyaiot-network`
4. Развёртывание по порядку: middleware → DEVICE → AI → VIDEO → WEB → APP (full)
5. Вывод URL сервисов

**Ориентировочная длительность**:

- С предсобранными образами: **~10–30 минут**
- Полная локальная сборка: **от 30 минут до нескольких часов**

Чтобы сократить установку: выполните `.scripts/docker/install_linux.sh pull` перед установкой (см. [Предсобранные образы](./部署最佳实践_ru.md#предсобранные-образы-опционально)).

### Быстрый старт macOS

```bash
git clone https://gitee.com/volara/easyaiot.git && cd easyaiot
cd .scripts/docker && chmod +x install_mac.sh
./install_mac.sh install
./install_mac.sh verify
```

### Windows

См. [Руководство по развёртыванию Windows](./平台Windows部署文档_ru.md).

---

## Профили развёртывания

При первом `install` профиль выбирается интерактивно. Результат сохраняется в `.scripts/docker/.deploy_profile`.

| Вариант | Имя | Рекомендуемая RAM | Типичное использование |
|:------:|------|-----------------|-------------|
| 1 | **mini** | ≥ 4 ГБ | Edge-узлы, PoC |
| 2 | **standard** | ≥ 16 ГБ | Обычная production (без TDengine/EMQX и т.д.) |
| 3 | **full** (по умолчанию) | ≥ 20 ГБ | Полный функционал + APP H5 |

Просмотр текущего профиля:

```bash
.scripts/docker/install_linux.sh profile
```

Неинтерактивный режим:

```bash
export EASYAIOT_DEPLOY_PROFILE=full
sudo .scripts/docker/install_linux.sh install
```

Различия профилей: [Лучшие практики — Выбор профиля развёртывания](./部署最佳实践_ru.md#выбор-профиля-развёртывания).

---

## Справочник скриптов

### Команды

| Команда | Описание | Пример |
|---------|-------------|---------|
| `install` | Первая установка и запуск | `./install_linux.sh install` |
| `start` | Запуск всех сервисов | `./install_linux.sh start` |
| `stop` | Остановка всех сервисов | `./install_linux.sh stop` |
| `restart` | Перезапуск всего | `./install_linux.sh restart` |
| `status` | Показать статус | `./install_linux.sh status` |
| `logs` | Просмотр логов | `./install_linux.sh logs` |
| `logs <module>` | Логи модуля | `./install_linux.sh logs VIDEO` |
| `build` | Локальная пересборка образов | `./install_linux.sh build` |
| `pull` | Загрузка предсобранных образов | `./install_linux.sh pull` |
| `update` | Обновление и перезапуск | `./install_linux.sh update` |
| `verify` | Проверка работоспособности | `./install_linux.sh verify` |
| `check` | Проверка окружения Docker | `./install_linux.sh check` |
| `profile` | Показать профиль развёртывания | `./install_linux.sh profile` |
| `clean` | Удаление контейнеров и образов ⚠️ | `./install_linux.sh clean` |
| `help` | Показать справку | `./install_linux.sh help` |

> Из корня проекта используйте `.scripts/docker/install_linux.sh` вместо `./install_linux.sh`.

### `install`

Первое развёртывание; устанавливает включённые модули в порядке зависимостей.

```bash
sudo .scripts/docker/install_linux.sh install
```

### `verify`

Проверяет порты и health-эндпоинты; при успехе выводит URL:

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

**Опасно**: удаляет контейнеры, образы и тома. Требует подтверждения (`y`).

### Развёртывание по модулю / только бизнес

```bash
# Только middleware
cd .scripts/docker && ./install_middleware_linux.sh install

# Только бизнес-модули
cd .scripts/docker && ./install_business_linux.sh install

# Один модуль (например, AI)
cd AI && ./install_linux.sh install
```

---

## Обзор модулей

### Базовые сервисы (`.scripts/docker`)

Middleware, управляемый `install_middleware_linux.sh`: Nacos, PostgreSQL, Redis, TDengine, Kafka, MinIO, Milvus, SRS, EMQX, ZLMediaKit, Node-RED и др. (фактический набор зависит от профиля).

### DEVICE

- **Стек**: Java 21, Spring Boot 2.7, Spring Cloud Gateway
- **Функции**: Подключение устройств, продукты, правила, GB28181, системное администрирование
- **Порт**: 48080 (Gateway)

### AI

- **Стек**: Flask, PyTorch 2.9+ (CUDA 12.8)
- **Функции**: Обучение/инференс/развёртывание моделей, OCR, речь, LLM
- **Порт**: 5000

### VIDEO

- **Стек**: Flask, OpenCV, FFmpeg
- **Функции**: Потоковое видео, алгоритмы в реальном времени/снимки, запись, оповещения, распознавание лиц
- **Порт**: 6000

### WEB

- **Стек**: Vue 3.4, TypeScript, Vite, Ant Design Vue 4
- **Порт**: 8888

### APP (только full)

- **Описание**: Мобильный H5
- **Порт**: 9010

---

## Порты сервисов

### Основные порты

| Сервис | Порт | URL |
|---------|------|-----|
| WEB | 8888 | http://localhost:8888 |
| DEVICE Gateway | 48080 | http://localhost:48080 |
| AI | 5000 | http://localhost:5000 |
| VIDEO | 6000 | http://localhost:6000 |
| Nacos | 8848 | http://localhost:8848/nacos |
| MinIO API / Console | 9000 / 9001 | http://localhost:9001 |
| APP H5 (full) | 9010 | http://localhost:9010 |

Полный список портов: [Лучшие практики — Требования к портам](./部署最佳实践_ru.md#требования-к-портам).

### Health-эндпоинты

| Модуль | Эндпоинт |
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

### 2. Слишком старая версия Docker Compose

Требуется **v2.35.0+**:

```bash
sudo apt update && sudo apt install -y docker-compose-plugin
docker compose version
```

### 3. Порт занят

```bash
ss -tlnp | grep <port>
# Остановите процесс или измените маппинг портов в docker-compose.yml
```

### 4. Сбой установки на полпути

```bash
ls -lt .scripts/docker/logs/ | head -5
tail -100 .scripts/docker/logs/install_linux_*.log
docker ps -a
.scripts/docker/install_linux.sh status
```

### 5. Сервисы запущены, но браузер не подключается

```bash
.scripts/docker/install_linux.sh verify
sudo ufw allow 8888    # если включён файрвол
.scripts/docker/install_linux.sh logs WEB
```

### 6. Недостаточно места на диске

Первая сборка использует много места — **зарезервируйте ≥ 300 ГБ**:

```bash
df -h /
docker system df
.scripts/docker/cleanup_docker_space.sh
```

### 7. WEB не работает после смены профиля

Образ WEB привязан к профилю развёртывания — пересоберите после переключения:

```bash
cd WEB && ./install_linux.sh build
```

Подробнее: [Устранение неполадок](./部署最佳实践_ru.md#устранение-неполадок).

---

## Управление логами

### Логи скриптов

`.scripts/docker/logs/`:

```
install_linux_YYYYMMDD_HHMMSS.log
install_middleware_YYYYMMDD_HHMMSS.log
```

```bash
ls -lt .scripts/docker/logs/ | head -5
tail -f .scripts/docker/logs/install_linux_*.log
```

### Логи контейнеров

```bash
.scripts/docker/install_linux.sh logs
cd DEVICE && docker compose logs -f
docker logs -f video-service
```

---

## Процесс развёртывания

### Чеклист первого развёртывания

- [ ] Ubuntu ≥ 24.04, ≥ 300 ГБ свободного места на диске
- [ ] Установлены Docker + Compose v2.35+
- [ ] `docker ps` работает для текущего пользователя
- [ ] Основные порты свободны
- [ ] Выбран профиль (mini / standard / full)
- [ ] Выполнить `install` → `verify` → открыть `:8888`

### Ежедневные операции

```bash
.scripts/docker/install_linux.sh start
.scripts/docker/install_linux.sh status
.scripts/docker/install_linux.sh logs
.scripts/docker/install_linux.sh restart
```

### Обновления

```bash
git pull
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

---

## Примечания

1. **Профили**: Соответствуйте RAM профилю; для анализа используйте `analyze_deploy_memory.sh`
2. **Диск**: Локальные сборки и тома быстро растут — **мин. 300 ГБ**, для production 500 ГБ+ SSD
3. **sudo**: Рекомендуется при первой установке для настройки зеркала и RTP
4. **Пароли**: Смените пароли middleware по умолчанию в production ([учётные данные](./部署最佳实践_ru.md#учётные-данные-по-умолчанию))
5. **clean**: Удаляет тома — сначала сделайте резервную копию
6. **Сеть**: Требуется доступ к Docker Hub или настроенному зеркалу

## Поддержка

1. [Лучшие практики развёртывания](./部署最佳实践_ru.md) — устранение неполадок
2. Логи: `.scripts/docker/install_linux.sh logs`
3. Контейнеры: `docker ps -a`
4. Создайте issue в репозитории проекта

---

**Версия документа**: 2.0  
**Последнее обновление**: 2026-07-07  
**Точка входа скрипта**: `.scripts/docker/install_linux.sh`
