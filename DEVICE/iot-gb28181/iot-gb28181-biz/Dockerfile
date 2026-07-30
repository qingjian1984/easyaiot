# 运行时镜像：从宿主机 target/jars 复制已编译的 Jar（由 Dockerfile.base 产出）
FROM cimg/openjdk:17.0.17

USER root

RUN userdel -r circleci 2>/dev/null || true && \
    groupadd -r appuser -g 1001 && useradd -r -u 1001 -g appuser appuser
RUN mkdir -p /app/logs /app/config /app/data /app/temp && chown -R appuser:appuser /app
WORKDIR /app

COPY target/jars/iot-gb28181-biz.jar app.jar

RUN chown appuser:appuser app.jar

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SERVER_PORT=48088 \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 48088

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

USER appuser
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
