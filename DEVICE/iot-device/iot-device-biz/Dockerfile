# ======================= 运行时阶段 =======================
# 此 Dockerfile 从宿主机挂载的 Jar 包构建运行时镜像
# Jar 包应该已经通过 base-builder 服务编译并挂载到 ./target/jars/ 目录

FROM cimg/openjdk:17.0.17

USER root

# 设置非root用户以增强安全性
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 创建工作目录并设置权限
RUN mkdir -p /app && chown -R appuser:appuser /app
WORKDIR /app

# 从宿主机挂载的 Jar 包目录复制构建产物
COPY target/jars/iot-device-biz.jar app.jar

RUN chown appuser:appuser app.jar

# 环境变量配置
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SERVER_PORT=48083 \
    JAVA_OPTS="-Xms512m -Xmx512m -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Asia/Shanghai"

# 暴露应用端口
EXPOSE 48083

# 动态健康检查（针对Spring Boot应用）
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health > /dev/null 2>&1 || exit 1

# 切换到非root用户
USER appuser

# 启动应用（使用exec形式确保正确信号处理）
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
