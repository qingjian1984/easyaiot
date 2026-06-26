# BuildKit 内置前端，支持 RUN --mount
# 构建阶段：pnpm store 使用 BuildKit cache mount（id=app-pnpm-store），
# 由 install_linux.sh 通过 --cache-from/--cache-to type=local 持久化到 .build-cache/app/pnpm-store
#
# 多架构支持：由 .scripts/docker/runtime_image.sh 的 docker wrapper 注入 --platform 实现
FROM node:22-alpine3.21 AS builder

ARG NPM_REGISTRY=https://mirrors.cloud.tencent.com/npm/

ENV CI=true

RUN npm config set registry ${NPM_REGISTRY}

RUN npm install -g pnpm@10.10.0 && \
    pnpm config set registry ${NPM_REGISTRY} && \
    pnpm config set fetch-timeout 600000 && \
    pnpm config set fetch-retries 5 && \
    pnpm config set fetch-retry-mintimeout 20000 && \
    pnpm config set fetch-retry-maxtimeout 120000

WORKDIR /app
RUN mkdir -p /tmp/app-build-logs /pnpm

COPY package.json pnpm-lock.yaml ./

RUN --mount=type=cache,target=/pnpm/store,id=app-pnpm-store \
    pnpm config set store-dir /pnpm/store && \
    pnpm fetch --frozen-lockfile 2>&1 | tee /tmp/app-build-logs/pnpm-fetch.log || true

RUN --mount=type=cache,target=/pnpm/store,id=app-pnpm-store \
    pnpm config set store-dir /pnpm/store && \
    pnpm install --frozen-lockfile --ignore-scripts 2>&1 | tee /tmp/app-build-logs/pnpm-install.log && \
    test -x node_modules/.bin/cross-env || (echo "=== pnpm install 未生成 node_modules/.bin/cross-env ===" && exit 1)

ARG CACHE_BUST=0
RUN echo "cache-bust=${CACHE_BUST}" > /tmp/.app-cache-bust

COPY . .

ARG SKIP_VITE_BUILD=0

# Docker 部署：同源 nginx 反代 /admin-api 与 /dev-api
RUN printf '\nVITE_APP_PROXY_ENABLE = true\nVITE_APP_PROXY_PREFIX = /admin-api\nVITE_APP_VIDEO_API_PREFIX = /dev-api\nVITE_SERVER_BASEURL = /admin-api\n' >> env/.env.production

RUN --mount=type=cache,target=/pnpm/store,id=app-pnpm-store \
    pnpm config set store-dir /pnpm/store && \
    set -e && \
    pnpm init-baseFiles && \
    if [ "${SKIP_VITE_BUILD}" = "1" ]; then \
        echo "=== SKIP_VITE_BUILD=1：使用预构建 dist-prebuilt（规避 QEMU 模拟 uni build）==="; \
        test -d dist-prebuilt || (echo "=== SKIP_VITE_BUILD=1 但 dist-prebuilt/ 不存在！ ===" && exit 1); \
        rm -rf dist/build/h5 && mkdir -p dist/build && mv dist-prebuilt dist/build/h5; \
    else \
        echo "=== uni build h5 开始（约 2–10 分钟）===" && \
        pnpm build:h5 2>&1 | tee /tmp/app-build-logs/pnpm-build.log || \
        (echo "=== uni build h5 failed, log: ===" && cat /tmp/app-build-logs/pnpm-build.log && exit 1); \
    fi

RUN test -d dist/build/h5 || (echo "=== dist/build/h5 missing after build ===" && cat /tmp/app-build-logs/pnpm-build.log && exit 1)

FROM nginx:1.29.2-alpine

ARG APK_MIRROR=mirrors.cloud.tencent.com

RUN if [ -n "${APK_MIRROR}" ]; then \
        sed -i "s/dl-cdn.alpinelinux.org/${APK_MIRROR}/g" /etc/apk/repositories; \
    fi

LABEL org.opencontainers.image.authors="basiclab"

WORKDIR /usr/share/nginx/html

RUN mkdir -p /usr/share/nginx/html /app/logs /opt/app-build-logs

COPY ./conf/nginx.conf /etc/nginx/nginx.conf
COPY --from=builder /app/dist/build/h5 /usr/share/nginx/html
COPY --from=builder /tmp/app-build-logs /opt/app-build-logs

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/health || exit 1

CMD ["sh", "-c", "mkdir -p /app/logs && cp -af /opt/app-build-logs/. /app/logs/ && echo \"[$(date '+%Y-%m-%d %H:%M:%S')] app-service container started\" >> /app/logs/runtime.log && nginx -g 'daemon off;'"]
