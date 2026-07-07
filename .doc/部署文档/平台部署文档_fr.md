# Guide de déploiement de la plateforme EasyAIoT

> **Recommandé pour les débutants** : effectuez votre premier déploiement via [Démarrage rapide](#démarrage-rapide) dans ce document. Pour les opérations avancées, le dépannage, le GPU et les détails sur les bases de données, consultez [Bonnes pratiques de déploiement](./部署最佳实践_fr.md).

## Table des matières

- [Aperçu](#aperçu)
- [Exigences d'environnement](#exigences-denvironnement)
- [Démarrage rapide](#démarrage-rapide)
- [Profils de déploiement](#profils-de-déploiement)
- [Référence des scripts](#référence-des-scripts)
- [Vue d'ensemble des modules](#vue-densemble-des-modules)
- [Ports des services](#ports-des-services)
- [FAQ](#faq)
- [Gestion des journaux](#gestion-des-journaux)
- [Flux de déploiement](#flux-de-déploiement)

---

## Aperçu

EasyAIoT est une plateforme d'algorithmes intelligents intégrant cloud et périphérie, déployée avec **des conteneurs Docker et un script d'installation unifié**.

### Composants de la plateforme

| Module | Répertoire | Description |
|--------|-----------|-------------|
| Services de base | `.scripts/docker` | Nacos, PostgreSQL, Redis, Kafka, MinIO, etc. |
| DEVICE | `DEVICE/` | Gestion des appareils et passerelle API (Java / Spring Cloud) |
| AI | `AI/` | Entraînement, inférence, OCR, LLM (Python) |
| VIDEO | `VIDEO/` | Streaming vidéo, alertes, enregistrement, reconnaissance faciale (Python) |
| WEB | `WEB/` | Console de gestion (Vue 3) |
| APP | `APP/` | H5 mobile (**profil full** uniquement) |

### Scripts d'entrée unifiés

| OS | Script |
|----|--------|
| Linux | `.scripts/docker/install_linux.sh` |
| macOS | `.scripts/docker/install_mac.sh` |
| Windows | `.scripts/docker/install_win.ps1` |

> Les exemples ci-dessous utilisent **Linux** ; remplacez `install_linux.sh` par le script correspondant sur macOS/Windows.

---

## Exigences d'environnement

### Système et matériel

| Élément | Exigence |
|------|-------------|
| **OS** | **Ubuntu 24.04 LTS ou plus récent** (**Ubuntu 26.04 LTS recommandé**) ; également macOS 10.15+, Windows 10/11 |
| **CPU** | Min. 4 cœurs, 8+ recommandés |
| **RAM** | Dépend du profil ; profil full min. 20 Go, 32 Go recommandés |
| **Disque** | **Min. 300 Go libres**, 500 Go+ SSD recommandé |
| **GPU** | Optionnel ; GPU NVIDIA (CUDA 12.8) recommandé pour l'IA |

### Dépendances logicielles

| Logiciel | Version | Vérification |
|----------|---------|--------|
| Docker | Installé, démon accessible | `docker --version` |
| Docker Compose | **v2.35.0+** (plugin `docker compose`) | `docker compose version` |
| curl | Contrôles de santé | `curl --version` |

Installation sur Ubuntu :

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo apt install -y docker-compose-plugin
sudo usermod -aG docker $USER && newgrp docker
docker ps
```

### Permissions Docker (Linux)

```bash
sudo usermod -aG docker $USER
newgrp docker          # ou reconnectez-vous
docker ps              # doit réussir sans permission denied
```

Utilisez `sudo` pour la première installation afin que le script puisse configurer l'accélération par miroir et la réservation des ports RTP.

---

## Démarrage rapide

### Linux : quatre étapes

```bash
# ① Clone
git clone https://gitee.com/volara/easyaiot.git
cd easyaiot

# ② Auto-vérification (optionnelle mais recommandée)
.scripts/docker/install_linux.sh check
.scripts/docker/detect_system_info.sh

# ③ Installation en un clic (sélection du profil 1/2/3 au premier lancement)
sudo .scripts/docker/install_linux.sh install

# ④ Vérification et ouverture du navigateur
.scripts/docker/install_linux.sh verify
# http://<server-ip>:8888
```

### Que se passe-t-il pendant l'installation ?

1. Sélection du **profil de déploiement** (mini / standard / full)
2. Vérification de Docker, Compose et de la création de conteneurs
3. Détection de l'IP hôte, création de `easyaiot-network`
4. Déploiement dans l'ordre : middleware → DEVICE → AI → VIDEO → WEB → APP (full)
5. Affichage des URL des services

**Durée estimée** :

- Avec images pré-construites : **~10–30 minutes**
- Construction locale complète : **30 minutes à plusieurs heures**

Pour raccourcir l'installation : exécutez `.scripts/docker/install_linux.sh pull` avant l'installation (voir [Images préconstruites](./部署最佳实践_fr.md#images-préconstruites-optionnel)).

### Démarrage rapide macOS

```bash
git clone https://gitee.com/volara/easyaiot.git && cd easyaiot
cd .scripts/docker && chmod +x install_mac.sh
./install_mac.sh install
./install_mac.sh verify
```

### Windows

Consultez le [Guide de déploiement Windows](./平台Windows部署文档_fr.md).

---

## Profils de déploiement

Lors du premier `install`, vous choisissez un profil de manière interactive. Il est enregistré dans `.scripts/docker/.deploy_profile`.

| Option | Nom | RAM recommandée | Usage typique |
|:------:|------|-----------------|-------------|
| 1 | **mini** | ≥ 4 Go | Nœuds edge, PoC |
| 2 | **standard** | ≥ 16 Go | Production courante (sans TDengine/EMQX, etc.) |
| 3 | **full** (par défaut) | ≥ 20 Go | Fonctionnalités complètes + APP H5 |

Afficher le profil actuel :

```bash
.scripts/docker/install_linux.sh profile
```

Mode non interactif :

```bash
export EASYAIOT_DEPLOY_PROFILE=full
sudo .scripts/docker/install_linux.sh install
```

Différences entre profils : [Bonnes pratiques — Sélection du profil de déploiement](./部署最佳实践_fr.md#sélection-du-profil-de-déploiement).

---

## Référence des scripts

### Commandes

| Commande | Description | Exemple |
|---------|-------------|---------|
| `install` | Première installation et démarrage | `./install_linux.sh install` |
| `start` | Démarrer tous les services | `./install_linux.sh start` |
| `stop` | Arrêter tous les services | `./install_linux.sh stop` |
| `restart` | Redémarrer tout | `./install_linux.sh restart` |
| `status` | Afficher l'état | `./install_linux.sh status` |
| `logs` | Voir les journaux | `./install_linux.sh logs` |
| `logs <module>` | Journaux d'un module | `./install_linux.sh logs VIDEO` |
| `build` | Reconstruire les images localement | `./install_linux.sh build` |
| `pull` | Télécharger les images pré-construites | `./install_linux.sh pull` |
| `update` | Mettre à jour et redémarrer | `./install_linux.sh update` |
| `verify` | Contrôle de santé | `./install_linux.sh verify` |
| `check` | Vérifier l'environnement Docker | `./install_linux.sh check` |
| `profile` | Afficher le profil de déploiement | `./install_linux.sh profile` |
| `clean` | Supprimer conteneurs et images ⚠️ | `./install_linux.sh clean` |
| `help` | Afficher l'aide | `./install_linux.sh help` |

> Depuis la racine du projet, utilisez `.scripts/docker/install_linux.sh` au lieu de `./install_linux.sh`.

### `install`

Premier déploiement ; installe les modules activés dans l'ordre des dépendances.

```bash
sudo .scripts/docker/install_linux.sh install
```

### `verify`

Vérifie les ports et les points de contrôle de santé ; affiche les URL en cas de succès :

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

**Dangereux** : supprime les conteneurs, images et volumes. Nécessite une confirmation (`y`).

### Déploiement par module / métier uniquement

```bash
# Middleware uniquement
cd .scripts/docker && ./install_middleware_linux.sh install

# Modules métier uniquement
cd .scripts/docker && ./install_business_linux.sh install

# Module unique (ex. AI)
cd AI && ./install_linux.sh install
```

---

## Vue d'ensemble des modules

### Services de base (`.scripts/docker`)

Middleware géré par `install_middleware_linux.sh` : Nacos, PostgreSQL, Redis, TDengine, Kafka, MinIO, Milvus, SRS, EMQX, ZLMediaKit, Node-RED, etc. (ensemble réel selon le profil).

### DEVICE

- **Stack** : Java 21, Spring Boot 2.7, Spring Cloud Gateway
- **Fonctionnalités** : Accès appareils, produits, règles, GB28181, administration système
- **Port** : 48080 (Gateway)

### AI

- **Stack** : Flask, PyTorch 2.9+ (CUDA 12.8)
- **Fonctionnalités** : Entraîner/inférer/déployer des modèles, OCR, parole, LLM
- **Port** : 5000

### VIDEO

- **Stack** : Flask, OpenCV, FFmpeg
- **Fonctionnalités** : Streaming, algorithmes temps réel/instantané, enregistrement, alertes, reconnaissance faciale
- **Port** : 6000

### WEB

- **Stack** : Vue 3.4, TypeScript, Vite, Ant Design Vue 4
- **Port** : 8888

### APP (full uniquement)

- **Description** : H5 mobile
- **Port** : 9010

---

## Ports des services

### Ports principaux

| Service | Port | URL |
|---------|------|-----|
| WEB | 8888 | http://localhost:8888 |
| DEVICE Gateway | 48080 | http://localhost:48080 |
| AI | 5000 | http://localhost:5000 |
| VIDEO | 6000 | http://localhost:6000 |
| Nacos | 8848 | http://localhost:8848/nacos |
| MinIO API / Console | 9000 / 9001 | http://localhost:9001 |
| APP H5 (full) | 9010 | http://localhost:9010 |

Liste complète des ports : [Bonnes pratiques — Exigences de ports](./部署最佳实践_fr.md#exigences-de-ports).

### Points de contrôle de santé

| Module | Point de terminaison |
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

### 2. Docker Compose trop ancien

Nécessite **v2.35.0+** :

```bash
sudo apt update && sudo apt install -y docker-compose-plugin
docker compose version
```

### 3. Port déjà utilisé

```bash
ss -tlnp | grep <port>
# Arrêtez le processus ou modifiez le mappage de ports dans docker-compose.yml
```

### 4. Échec de l'installation en cours de route

```bash
ls -lt .scripts/docker/logs/ | head -5
tail -100 .scripts/docker/logs/install_linux_*.log
docker ps -a
.scripts/docker/install_linux.sh status
```

### 5. Services démarrés mais le navigateur ne se connecte pas

```bash
.scripts/docker/install_linux.sh verify
sudo ufw allow 8888    # si le pare-feu est activé
.scripts/docker/install_linux.sh logs WEB
```

### 6. Espace disque insuffisant

La première construction utilise beaucoup d'espace — **réservez ≥ 300 Go** :

```bash
df -h /
docker system df
.scripts/docker/cleanup_docker_space.sh
```

### 7. WEB cassé après changement de profil

L'image WEB est liée au profil de déploiement — reconstruisez après le changement :

```bash
cd WEB && ./install_linux.sh build
```

Plus d'informations : [Dépannage](./部署最佳实践_fr.md#dépannage).

---

## Gestion des journaux

### Journaux des scripts

`.scripts/docker/logs/` :

```
install_linux_YYYYMMDD_HHMMSS.log
install_middleware_YYYYMMDD_HHMMSS.log
```

```bash
ls -lt .scripts/docker/logs/ | head -5
tail -f .scripts/docker/logs/install_linux_*.log
```

### Journaux des conteneurs

```bash
.scripts/docker/install_linux.sh logs
cd DEVICE && docker compose logs -f
docker logs -f video-service
```

---

## Flux de déploiement

### Liste de contrôle pour le premier déploiement

- [ ] Ubuntu ≥ 24.04, ≥ 300 Go d'espace disque libre
- [ ] Docker + Compose v2.35+ installés
- [ ] `docker ps` fonctionne pour l'utilisateur actuel
- [ ] Ports principaux libres
- [ ] Profil choisi (mini / standard / full)
- [ ] Exécuter `install` → `verify` → ouvrir `:8888`

### Opérations quotidiennes

```bash
.scripts/docker/install_linux.sh start
.scripts/docker/install_linux.sh status
.scripts/docker/install_linux.sh logs
.scripts/docker/install_linux.sh restart
```

### Mises à jour

```bash
git pull
sudo .scripts/docker/install_linux.sh update
.scripts/docker/install_linux.sh verify
```

---

## Remarques

1. **Profils** : Adaptez la RAM au profil ; utilisez `analyze_deploy_memory.sh` pour analyser
2. **Disque** : Les constructions locales et les volumes grossissent vite — **min. 300 Go**, 500 Go+ SSD pour la production
3. **sudo** : Recommandé lors de la première installation pour le miroir et la configuration RTP
4. **Mots de passe** : Changez les mots de passe middleware par défaut en production ([identifiants](./部署最佳实践_fr.md#identifiants-par-défaut))
5. **clean** : Supprime les volumes — sauvegardez d'abord
6. **Réseau** : Accès à Docker Hub ou miroir configuré requis

## Assistance

1. [Bonnes pratiques de déploiement](./部署最佳实践_fr.md) — dépannage
2. Journaux : `.scripts/docker/install_linux.sh logs`
3. Conteneurs : `docker ps -a`
4. Ouvrir une issue dans le dépôt du projet

---

**Version du document** : 2.0  
**Dernière mise à jour** : 2026-07-07  
**Point d'entrée du script** : `.scripts/docker/install_linux.sh`
