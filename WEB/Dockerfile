# BuildKit 内置前端，支持 RUN --mount
# 构建阶段：pnpm store 使用 BuildKit cache mount（id=web-pnpm-store），
# 由 install_linux.sh 通过 --cache-from/--cache-to type=local 持久化到 .build-cache/web/pnpm-store
#
# 多架构支持：由 .scripts/docker/runtime_image.sh 的 docker wrapper 注入 --platform 实现，
# Docker BuildKit 会根据 --platform 自动拉取对应架构的基础镜像
FROM node:22-alpine3.21 AS builder

# 镜像源配置（通过 --build-arg 传入，默认腾讯云）
ARG NPM_REGISTRY=https://mirrors.cloud.tencent.com/npm/

ENV CI=true

RUN npm config set registry ${NPM_REGISTRY}

RUN npm install -g pnpm@11.3.0 && \
    pnpm config set registry ${NPM_REGISTRY} && \
    pnpm config set fetch-timeout 600000 && \
    pnpm config set fetch-retries 5 && \
    pnpm config set fetch-retry-mintimeout 20000 && \
    pnpm config set fetch-retry-maxtimeout 120000

WORKDIR /app
RUN mkdir -p /tmp/web-build-logs /pnpm

# 先复制依赖清单（利用层缓存）；registry 与 fetch 配置已通过上方 pnpm config set 设置
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# 预拉依赖到 store（持久化见 install_linux.sh --cache-from/--cache-to type=local）
RUN --mount=type=cache,target=/pnpm/store,id=web-pnpm-store \
    pnpm config set store-dir /pnpm/store && \
    pnpm fetch --frozen-lockfile 2>&1 | tee /tmp/web-build-logs/pnpm-fetch.log || true

# 依赖安装只依赖 package.json / pnpm-lock.yaml / pnpm-workspace.yaml（已在上方 COPY）。
# 放在 COPY 源码之前 → 仅改业务代码时此层命中缓存，不再重复 pnpm install（省下每次更新的安装耗时）。
# 本仓库无 workspace 子包、无依赖源码的 postinstall，故先于源码安装是安全的；
# 安装后用 cross-env 探针确认 node_modules 完整，不完整则立即失败。
RUN --mount=type=cache,target=/pnpm/store,id=web-pnpm-store \
    pnpm config set store-dir /pnpm/store && \
    pnpm install --frozen-lockfile 2>&1 | tee /tmp/web-build-logs/pnpm-install.log && \
    test -x node_modules/.bin/cross-env || (echo "=== pnpm install 未生成 node_modules/.bin/cross-env ===" && exit 1)

# 源码变更或 install_linux.sh clean 后通过 CACHE_BUST 使后续层失效（避免命中 CACHED 跳过 pnpm build）
ARG CACHE_BUST=0
ARG VITE_GLOB_DEPLOY_PROFILE=full
RUN echo "cache-bust=${CACHE_BUST}" > /tmp/.web-cache-bust

COPY . .

# 跨架构构建时，runtime_image.sh 会先在本机架构下执行 docker build --target builder，
# 提取 dist/ 放回 build context（dist-prebuilt/），再设 SKIP_VITE_BUILD=1 跳过 QEMU 模拟 vite build。
ARG SKIP_VITE_BUILD=0

# 部署形态与上传前缀写入生产环境（与 nginx /dev-api 路由一致；mini 无 Gateway 48080）
RUN printf '\nVITE_GLOB_DEPLOY_PROFILE = %s\nVITE_GLOB_UPLOAD_URL = /dev-api\n' "${VITE_GLOB_DEPLOY_PROFILE}" >> .env.production

# vite 前端构建（约 2–10 分钟）；跨架构时通过 SKIP_VITE_BUILD=1 跳过，直接用预构建 dist-prebuilt
RUN --mount=type=cache,target=/pnpm/store,id=web-pnpm-store \
    pnpm config set store-dir /pnpm/store && \
    set -e && \
    if [ "${SKIP_VITE_BUILD}" = "1" ]; then \
        echo "=== SKIP_VITE_BUILD=1：使用预构建 dist-prebuilt（规避 QEMU 模拟 vite build）==="; \
        test -d dist-prebuilt || (echo "=== SKIP_VITE_BUILD=1 但 dist-prebuilt/ 不存在！ ===" && exit 1); \
        mv dist-prebuilt dist; \
    else \
        echo "=== vite build 开始（约 2–10 分钟）===" && \
        npx cross-env NODE_ENV=production vite build 2>&1 | tee /tmp/web-build-logs/pnpm-build.log || \
        (echo "=== vite build failed, log: ===" && cat /tmp/web-build-logs/pnpm-build.log && exit 1) && \
        echo "=== postBuild 开始 ===" && \
        npx esno ./build/script/postBuild.ts 2>&1 | tee -a /tmp/web-build-logs/pnpm-build.log || \
        (echo "=== postBuild failed, log: ===" && cat /tmp/web-build-logs/pnpm-build.log && exit 1); \
    fi

RUN test -d dist || (echo "=== dist missing after build ===" && cat /tmp/web-build-logs/pnpm-build.log && exit 1)

FROM nginx:1.29.2-alpine

# 镜像源配置（通过 --build-arg 传入，默认腾讯云）
ARG APK_MIRROR=mirrors.cloud.tencent.com

RUN if [ -n "${APK_MIRROR}" ]; then \
        sed -i "s/dl-cdn.alpinelinux.org/${APK_MIRROR}/g" /etc/apk/repositories; \
    fi

LABEL org.opencontainers.image.authors="basiclab"

WORKDIR /usr/share/nginx/html

RUN mkdir -p /usr/share/nginx/html /app/logs /opt/web-build-logs

COPY ./conf/nginx.conf /etc/nginx/nginx.conf
COPY --from=builder /app/dist /usr/share/nginx/html
COPY --from=builder /tmp/web-build-logs /opt/web-build-logs

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

CMD ["sh", "-c", "mkdir -p /app/logs && cp -af /opt/web-build-logs/. /app/logs/ && echo \"[$(date '+%Y-%m-%d %H:%M:%S')] web-service container started\" >> /app/logs/runtime.log && nginx -g 'daemon off;'"]
