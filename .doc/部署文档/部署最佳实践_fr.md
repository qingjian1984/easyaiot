# Bonnes pratiques de déploiement EasyAIoT

> Ce document reste **synchronisé avec les scripts du projet** et s'applique aux environnements Linux de production/test.  
> Pour un démarrage rapide, consultez [Guide de déploiement de la plateforme](./平台部署文档_fr.md). Pour Windows, consultez [Guide de déploiement Windows](./平台Windows部署文档_fr.md).

---

## Table des matières

- [Démarrage rapide en 5 minutes](#démarrage-rapide-en-5-minutes)
- [Sélection du profil de déploiement](#sélection-du-profil-de-déploiement)
- [Exigences d'environnement](#exigences-denvironnement)
- [Liste de contrôle pré-déploiement](#liste-de-contrôle-pré-déploiement)
- [Déploiement en un clic](#déploiement-en-un-clic)
- [Déploiement étape par étape](#déploiement-étape-par-étape)
- [Opérations courantes](#opérations-courantes)
- [Images préconstruites (optionnel)](#images-préconstruites-optionnel)
- [Configuration GPU](#configuration-gpu)
- [Environnements spéciaux](#environnements-spéciaux)
- [Notes sur les bases de données](#notes-sur-les-bases-de-données)
- [Identifiants par défaut](#identifiants-par-défaut)
- [Dépannage](#dépannage)
- [Emplacements des journaux](#emplacements-des-journaux)
- [Mise à jour et désinstallation](#mise-à-jour-et-désinstallation)
- [Référence d'architecture](#référence-darchitecture)

---

## Démarrage rapide en 5 minutes

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

**Durée de la première installation** : Sans images préconstruites, le script exécute un `docker build` local pour DEVICE / AI / VIDEO / WEB, généralement **30 minutes à plusieurs heures** selon le CPU, le disque et le réseau. Exécutez d'abord `pull` pour réduire considérablement le temps d'installation (voir [Images préconstruites](#images-préconstruites-optionnel)).

---

## Sélection du profil de déploiement

Lors de l'installation, le script sélectionne interactivement un **profil de déploiement** (ou définissez `EASYAIOT_DEPLOY_PROFILE`). Le choix est enregistré dans `.scripts/docker/.deploy_profile` et réutilisé par `start` / `stop` / `update`.

| Profil | Alias | RAM recommandée | Cas d'usage |
|---------|---------|-----------------|----------|
| **mini** | `1` / `4g` | ≥ 4 GB | Nœuds edge, PoC, hôtes à ressources limitées |
| **standard** | `2` / `16g` | ≥ 16 GB | Production standard sans certains composants lourds |
| **full** | `3` (par défaut) | ≥ 20 GB | Fonctionnalités complètes, y compris APP mobile H5 |

Afficher le profil actuel et la portée des services :

```bash
.scripts/docker/install_linux.sh profile
```

### Services par profil

**mini (edge minimal)**

- Métier : `iot-system`, VIDEO, AI, WEB
- Middleware : PostgreSQL, Redis, SRS
- Non démarrés : Nacos, Gateway, Kafka, iot-sink, MinIO, Milvus, ZLMediaKit, Node-RED, TDengine, EMQX, et la plupart des sous-modules DEVICE
- Routage API : nginx proxy `/admin-api` et `/dev-api` directement vers `iot-system:48099`

**standard**

- Non démarrés : TDengine, EMQX, Node-RED, `iot-device`, `iot-tdengine`
- Tous les autres modules métier et middleware sont démarrés

**full**

- Tous les modules métier et middleware, y compris **APP mobile H5** (port 9010)

Analyser si la mémoire des conteneurs correspond au profil :

```bash
.scripts/docker/analyze_deploy_memory.sh
.scripts/docker/analyze_deploy_memory.sh --all-profiles   # compare all three
```

---

## Exigences d'environnement

### Matériel

| Ressource | Minimum | Recommandé |
|----------|---------|-------------|
| CPU | 4 cœurs | 8+ cœurs |
| RAM | Voir [Sélection du profil de déploiement](#sélection-du-profil-de-déploiement) (full min. 20 GB) | 32 GB+ |
| Disque | **300 GB** libres | 500 GB+ SSD |
| GPU | Aucun (CPU fonctionne) | GPU NVIDIA (CUDA 12.8 pour l'inférence/entraînement AI) |

> Le disque est utilisé pour les couches d'images Docker, le cache de build (`.build-cache/`), les bases de données et les volumes de stockage d'objets. La première construction locale consomme beaucoup d'espace — réservez une marge suffisante.

### Logiciels

| Logiciel | Exigence | Notes |
|----------|-------------|-------|
| OS | **Ubuntu 24.04 LTS** (minimum) | **Ubuntu 26.04 LTS recommandé** ; Kylin et ARM64 également pris en charge (voir [Environnements spéciaux](#environnements-spéciaux)) |
| Docker | Installé et daemon accessible | Si absent : `curl -fsSL https://get.docker.com \| sudo sh` |
| Docker Compose | **v2.35.0+** (plugin `docker compose`) | Si absent : `sudo apt install docker-compose-plugin` |
| NVIDIA Driver | 525+ | Scénarios GPU uniquement |
| NVIDIA Container Toolkit | Dernière version | Scénarios GPU uniquement |

### Permissions Docker (Linux)

```bash
# Add current user to docker group (recommended)
sudo usermod -aG docker $USER
newgrp docker   # or log in again

# Verify
docker ps
```

> La configuration des miroirs Docker et la réservation des ports RTP nécessitent root — **utilisez `sudo` pour la première installation**.

### Exigences de ports

Assurez-vous que ces ports sont libres avant le déploiement (certains peuvent être inutilisés selon le profil) :

| Port | Service | Notes |
|------|---------|-------|
| 1880 | Node-RED | Moteur de règles (full/standard) |
| 1883 | EMQX | Broker MQTT (full) |
| 1935 | SRS | Streaming RTMP |
| 5432 | PostgreSQL | Base de données principale |
| 6000 | VIDEO | Traitement vidéo |
| 6030 | TDengine | Base de données time-series (full) |
| 6080 | ZLMediaKit | Serveur média |
| 6379 | Redis | Cache |
| 8848 | Nacos | Registre/centre de configuration |
| 8888 | WEB | Interface de gestion |
| 9000/9001 | MinIO | API stockage d'objets / console |
| 9010 | APP | Mobile H5 (full uniquement) |
| 9092 | Kafka | File de messages |
| 19530 | Milvus | Base de données vectorielle |
| 48080 | Gateway | Passerelle API |
| 5000 | AI | Inférence AI |
| 30000-30500 | ZLM RTP | Ingestion média (le script tente la réservation) |

Vérifier l'utilisation des ports :

```bash
ss -tlnp | grep -E '8848|5432|6379|9092|5000|6000|8888|48080'
```

---

## Liste de contrôle pré-déploiement

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

## Déploiement en un clic

### Script d'entrée

Orchestrateur unifié : `.scripts/docker/install_linux.sh`

```bash
# From project root (recommended)
sudo .scripts/docker/install_linux.sh install

# Or from script directory
cd .scripts/docker
sudo ./install_linux.sh install
```

### Ce que `install` fait automatiquement

1. **Sélectionner le profil de déploiement** — mini / standard / full, enregistré dans `.deploy_profile`
2. **Images préconstruites** — ignorer la construction locale si un registre distant est configuré et que pull est choisi
3. **Vérifications d'environnement** — Docker, Compose, création de conteneurs (y compris `/dev/null`)
4. **Détection de l'IP hôte** — pour les URL média GB28181 / ZLMediaKit (définir `HOST_IP=<ip>` pour ignorer)
5. **Réservation des ports RTP** — le noyau réserve 30000-30500 (nécessite root)
6. **Miroir Docker** — configure l'accélération `docker.m.daocloud.io` (nécessite root)
7. **Créer le réseau Docker** — `easyaiot-network`
8. **Déployer les modules dans l'ordre** :
   - Middleware (`.scripts/docker/install_middleware_linux.sh`)
   - DEVICE → AI → VIDEO → WEB → APP (full)
9. **Attendre les services de base** — vérifications de santé PostgreSQL / Nacos / Redis
10. **Platform Agent** — assurer l'agent edge si nécessaire

### Vérifier le déploiement

```bash
.scripts/docker/install_linux.sh verify
```

Exemple de sortie en cas de succès :

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

Ouvrir `http://<server-ip>:8888` dans un navigateur.

---

## Déploiement étape par étape

Pour un contrôle fin, déployez module par module. **Définissez d'abord le profil de déploiement** pour que tous les modules restent cohérents :

```bash
export EASYAIOT_DEPLOY_PROFILE=full   # or mini / standard
```

### Étape 1 : Middleware

```bash
cd .scripts/docker
./install_middleware_linux.sh install
```

| Middleware | Image | Port | Rôle |
|------------|-------|------|---------|
| Nacos | nacos/nacos-server:v2.5.1 | 8848 | Registre de services et configuration |
| PostgreSQL | postgres:18 | 5432 | Base principale (6 bases métier) |
| Redis | redis:7.4.8 | 6379 | Cache |
| Kafka | apache/kafka:3.8.0 | 9092 | File de messages |
| MinIO | minio/minio | 9000/9001 | Stockage d'objets |
| Milvus | milvusdb/milvus:v2.6.0 | 19530/9091 | Base vectorielle (reconnaissance faciale) |
| SRS | ossrs/srs:5 | 1935 | Streaming |
| EMQX | emqx/emqx:5.8.7 | 1883 | MQTT (profil full) |
| ZLMediaKit | zlmediakit/zlmediakit:master | 6080 | Serveur média |
| TDengine | tdengine/tsdb:3.3.8.4 | 6030 | Base time-series (profil full) |
| Node-RED | nodered/node-red:latest | 1880 | Moteur de règles |

Vérifications de disponibilité :

```bash
docker exec postgres-server pg_isready -U postgres
curl -s http://localhost:8848/nacos/actuator/health
docker exec redis-server redis-cli -a basiclab@iot975248395 ping
```

### Étape 2 : DEVICE

```bash
cd DEVICE
./install_linux.sh install
```

| Service | Port | Description |
|---------|------|-------------|
| iot-gateway | 48080 | Passerelle API |
| iot-system | 48099 | Gestion système |
| iot-infra | 48066 | Infrastructure |
| iot-device | 48055 | Gestion des appareils |
| iot-dataset | 48077 | Jeu de données |
| iot-message | 48033 | Messagerie |
| iot-file | 48022 | Service de fichiers |
| iot-sink | 48011 | Adaptateur de protocole |
| iot-gb28181 | 5060 | Vidéosurveillance GB28181 |

### Étapes 3–5 : AI / VIDEO / WEB

```bash
cd AI    && ./install_linux.sh install
cd VIDEO && ./install_linux.sh install
cd WEB   && ./install_linux.sh install
cd APP   && ./install_linux.sh install   # full only
```

### Modules métier uniquement (sans middleware)

```bash
cd .scripts/docker
./install_business_linux.sh install              # all business modules
./install_business_linux.sh update DEVICE WEB    # update specific modules
./install_business_linux.sh verify
```

---

## Opérations courantes

### Script unifié

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

### Scripts par module

Chaque répertoire de module (`DEVICE` / `AI` / `VIDEO` / `WEB` / `APP`) prend en charge :

```bash
./install_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

Middleware uniquement :

```bash
cd .scripts/docker
./install_middleware_linux.sh install | start | stop | restart | status | logs | build | clean | update
```

### Variables d'environnement courantes

| Variable | Description |
|----------|-------------|
| `EASYAIOT_DEPLOY_PROFILE` | Profil : `mini` / `standard` / `full` |
| `HOST_IP` | Forcer l'IP hôte, ignorer la détection automatique |
| `PARALLEL_MODULES=true` | Démarrage/mise à jour parallèle des modules métier (si la RAM le permet) |
| `PARALLEL_BUILD=true` | Construction parallèle (sériel par défaut pour éviter OOM) |
| `FORCE_NETWORK_RECREATE=true` | Recréer le réseau Docker après changement d'IP hôte |
| `EASYAIOT_RUNTIME_REGISTRY` | URL du registre d'images préconstruites |

---

## Images préconstruites (optionnel)

Tirez les images métier préconstruites depuis un registre distant pour éviter les longues constructions locales Maven / pnpm / pip.

Fichier de configuration : `.scripts/docker/runtime_registry.conf`

```bash
# Interactive pull (before install or during update)
.scripts/docker/install_linux.sh pull

# Build and push runtime images (CI/release)
.scripts/docker/install_linux.sh build-runtime          # all modules
.scripts/docker/install_linux.sh build-runtime DEVICE   # specific module
```

Après un pull réussi, les `install` / `update` suivants détectent `.runtime_images_pulled` et démarrent les conteneurs directement.

---

## Configuration GPU

### Installation et vérification

```bash
nvidia-smi

# Install NVIDIA Container Toolkit
# https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html

docker run --rm --gpus all nvidia/cuda:12.8.0-base-ubuntu24.04 nvidia-smi
```

### Détection automatique

Les scripts d'installation détectent automatiquement le GPU :

- GPU présent → activer `runtime: nvidia`, `NVIDIA_VISIBLE_DEVICES=all`
- Pas de GPU → mode CPU

### Multi-GPU

```bash
export CUDA_VISIBLE_DEVICES=0,1
```

---

## Environnements spéciaux

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

## Notes sur les bases de données

### Bases de données métier PostgreSQL

Six bases de données sont initialisées au démarrage (scripts dans `.scripts/postgresql/`) :

| Base de données | Fichier SQL | Rôle |
|----------|----------|---------|
| ruoyi-vue-pro20 | ruoyi-vue-pro10.sql | Gestion système |
| iot-ai20 | iot-ai10.sql | Service AI |
| iot-device10 | iot-device10.sql | Gestion des appareils |
| iot-gb2818110 | iot-gb2818110.sql | Vidéosurveillance |
| iot-message10 | iot-message10.sql | Messagerie |
| iot-video10 | iot-video10.sql | Traitement vidéo |

### TDengine

SQL dans `.scripts/tdengine/tdengine_super_tables.sql` ; initialisation automatique sous le profil full.

### Sauvegarde

```bash
.scripts/postgresql/backup_databases.sh
```

---

## Identifiants par défaut

| Middleware | Nom d'utilisateur | Mot de passe | Console |
|------------|----------|----------|---------|
| Nacos | nacos | nacos | http://\<IP\>:8848/nacos |
| PostgreSQL | postgres | iot45722414822 | — |
| Redis | — | basiclab@iot975248395 | — |
| MinIO | minioadmin | basiclab@iot975248395 | http://\<IP\>:9001 |
| EMQX | admin | basiclab@iot6874125784 | http://\<IP\>:18083 |
| Milvus | — | — | http://\<IP\>:9091 |

> **Modifiez tous les mots de passe par défaut en production.**

---

## Dépannage

### Échecs de démarrage de service

```bash
docker ps -a
docker logs -f postgres-server
docker logs -f nacos-server
docker logs -f ai-service
docker logs -f video-service
.scripts/docker/install_linux.sh logs
```

### Problèmes réseau

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

### Problèmes système Docker

```bash
sudo .scripts/docker/diagnose_docker_systemd.sh diagnose
sudo .scripts/docker/diagnose_docker_systemd.sh fix-all
.scripts/docker/cleanup_docker_space.sh
df -h && docker system df
```

### Groupe de consommateurs Kafka

```bash
cd VIDEO && ./fix_kafka_consumer_group.sh
```

### Conflits de ports

Modifiez les mappages de ports dans le `docker-compose.yml` du module, ou arrêtez le processus en conflit.

### Problèmes WEB après changement de profil

Le frontend intègre le profil de déploiement au moment du build — reconstruisez WEB après le changement :

```bash
cd WEB && ./install_linux.sh build
```

---

## Emplacements des journaux

| Emplacement | Description |
|----------|-------------|
| `.scripts/docker/logs/` | Journaux unifiés d'installation / scripts middleware |
| `DEVICE/logs/` | Journaux des services DEVICE |
| `AI/data/logs/` | Journaux du service AI |
| `VIDEO/data/logs/` | Journaux du service VIDEO |
| `docker logs <container>` | Journaux en direct des conteneurs |

---

## Mise à jour et désinstallation

### Mettre à jour le code et les services

```bash
git pull origin main
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

Mise à jour d'un module unique :

```bash
cd AI && ./install_linux.sh update
```

### Désinstallation

```bash
sudo .scripts/docker/install_linux.sh clean

# Optional: remove data volume directories
rm -rf .scripts/docker/db_data .scripts/docker/redis_data \
       .scripts/docker/minio_data .scripts/docker/mq_data \
       .scripts/docker/taos_data .scripts/docker/milvus_data
```

---

## Référence d'architecture

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

*Version du document : 2026-07-07 | Point d'entrée du script : `.scripts/docker/install_linux.sh`*
