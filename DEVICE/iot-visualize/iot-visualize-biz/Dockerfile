FROM cimg/openjdk:17.0.17

USER root

RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN mkdir -p /app && chown -R appuser:appuser /app
WORKDIR /app

COPY target/jars/iot-visualize-biz.jar app.jar
RUN chown appuser:appuser app.jar

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SERVER_PORT=48095 \
    JAVA_OPTS="-Xms256m -Xmx320m -Djava.security.egd=file:/dev/./urandom -Duser.timezone=Asia/Shanghai"

EXPOSE 48095

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

USER appuser
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
