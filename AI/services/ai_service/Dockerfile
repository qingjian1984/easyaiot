# 使用PyTorch官方镜像（包含CUDA支持，Python 3.10）
# 注意：本镜像仅支持 x86_64 架构，ARM架构不支持容器化部署
# 升级到PyTorch 2.9.0以支持最新功能
ARG BASE_IMAGE=pytorch/pytorch:2.9.0-cuda12.8-cudnn9-runtime
FROM ${BASE_IMAGE}

# 设置非交互式环境，避免构建时卡住
ARG DEBIAN_FRONTEND=noninteractive
ENV DEBIAN_FRONTEND=noninteractive

# 镜像源配置（通过 --build-arg 传入，默认腾讯云）
ARG APT_MIRROR_URL=https://mirrors.cloud.tencent.com
ARG PIP_INDEX_URL=https://mirrors.cloud.tencent.com/pypi/simple

# 设置工作目录
WORKDIR /app

# 设置环境变量
ENV PYTHONUNBUFFERED=1
ENV CUDA_VISIBLE_DEVICES=0
ENV TZ=Asia/Shanghai

# 设置时区，避免时区配置交互式提示
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 配置镜像源（支持Ubuntu和Debian）
RUN if [ -n "${APT_MIRROR_URL}" ]; then \
        OS_ID=$(cat /etc/os-release 2>/dev/null | grep "^ID=" | cut -d'=' -f2 | tr -d '"' || echo "ubuntu") && \
        OS_VERSION=$(cat /etc/os-release 2>/dev/null | grep "^VERSION_CODENAME=" | cut -d'=' -f2 | tr -d '"' || cat /etc/os-release 2>/dev/null | grep "^VERSION_ID=" | cut -d'=' -f2 | tr -d '"' | cut -d'.' -f1 || echo "jammy") && \
        if [ "$OS_ID" = "ubuntu" ]; then \
            sed -i "s|http://.*archive.ubuntu.com|${APT_MIRROR_URL}|g" /etc/apt/sources.list 2>/dev/null || true; \
            sed -i "s|http://.*security.ubuntu.com|${APT_MIRROR_URL}|g" /etc/apt/sources.list 2>/dev/null || true; \
            if [ ! -f /etc/apt/sources.list ] || ! grep -q "${APT_MIRROR_URL}" /etc/apt/sources.list 2>/dev/null; then \
                echo "deb ${APT_MIRROR_URL}/ubuntu/ ${OS_VERSION} main restricted universe multiverse" > /etc/apt/sources.list && \
                echo "deb ${APT_MIRROR_URL}/ubuntu/ ${OS_VERSION}-updates main restricted universe multiverse" >> /etc/apt/sources.list && \
                echo "deb ${APT_MIRROR_URL}/ubuntu/ ${OS_VERSION}-security main restricted universe multiverse" >> /etc/apt/sources.list && \
                echo "deb ${APT_MIRROR_URL}/ubuntu/ ${OS_VERSION}-backports main restricted universe multiverse" >> /etc/apt/sources.list; \
            fi; \
        elif [ "$OS_ID" = "debian" ]; then \
            sed -i "s|deb.debian.org|${APT_MIRROR_URL##https://}|g" /etc/apt/sources.list.d/debian.sources 2>/dev/null || true; \
            sed -i "s|deb.debian.org|${APT_MIRROR_URL##https://}|g" /etc/apt/sources.list 2>/dev/null || true; \
            if [ ! -f /etc/apt/sources.list ] || ! grep -q "${APT_MIRROR_URL}" /etc/apt/sources.list 2>/dev/null; then \
                echo "deb ${APT_MIRROR_URL}/debian/ ${OS_VERSION} main contrib non-free non-free-firmware" > /etc/apt/sources.list && \
                echo "deb ${APT_MIRROR_URL}/debian/ ${OS_VERSION}-updates main contrib non-free non-free-firmware" >> /etc/apt/sources.list && \
                echo "deb ${APT_MIRROR_URL}/debian-security/ ${OS_VERSION}-security main contrib non-free non-free-firmware" >> /etc/apt/sources.list; \
            fi; \
        fi; \
    fi || true

# 安装基本工具（根据不同的宿主机架构修改）
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc git zip unzip wget curl vim htop libgl1 libglib2.0-0 libpython3-dev gnupg g++ libusb-1.0-0 libsm6 \
    libgomp1 libgtk-3-0 libavcodec-dev libavformat-dev libswscale-dev \
    && rm -rf /var/lib/apt/lists/*

# 安装 X11 依赖（可视化，如果需要）
# 注意：镜像源已在上面配置，这里直接使用
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    x11-apps \
    libgl1-mesa-glx \
    libxcb-xinerama0 \
    && rm -rf /var/lib/apt/lists/*

# 设置环境变量（可视化）
ENV QT_QPA_PLATFORM=xcb
ENV QT_X11_NO_MITSHM=1

# 安装 pip 并更新到最新版本
RUN pip config set global.index-url ${PIP_INDEX_URL} \
    && pip install --upgrade pip --timeout 30 --retries 3 -i ${PIP_INDEX_URL}

# 复制requirements文件
COPY requirements.txt requirements.txt

# 安装Python依赖（PyTorch已预装，无需重复安装）
RUN pip install -r requirements.txt --timeout 30 --retries 3 -i ${PIP_INDEX_URL}

# 复制应用代码
COPY . .

# 创建必要的目录
RUN mkdir -p data/uploads data/datasets data/models data/inference_results temp_uploads static/models

# 设置权限（如果存在 run_deploy.py）
RUN if [ -f run_deploy.py ]; then chmod +x run_deploy.py; fi

# 创建非root用户（-m 参数自动创建主目录）
RUN groupadd -r appuser && useradd -r -m -g appuser appuser

# 创建必要的用户目录并设置权限
RUN mkdir -p /home/appuser/.paddlex/temp /home/appuser/.config/Ultralytics && \
    chown -R appuser:appuser /home/appuser

# 设置目录权限
RUN chown -R appuser:appuser /app

# 切换到非root用户
USER appuser

# 暴露端口（默认5000，可通过环境变量FLASK_RUN_PORT修改）
EXPOSE 5000

# 健康检查
# 注意：start-period设置为90秒，因为应用启动时会等待Nacos服务（最多30秒）
# 加上应用初始化时间，总共可能需要60-90秒
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:5000/actuator/health || exit 1

# 启动命令
CMD ["python", "run_deploy.py"]
