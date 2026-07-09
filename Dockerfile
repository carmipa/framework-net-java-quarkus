# syntax=docker/dockerfile:1
# Build multi-stage: compila com Gradle e executa em UBI OpenJDK 25 (Quarkus fast-jar).
# Uso: docker compose build && docker compose up -d

FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src src
RUN ./gradlew build -x test --no-daemon -Dquarkus.package.jar.type=fast-jar -Dquarkus.profile=prod

FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24

ENV LANGUAGE='pt_BR:pt' \
    HOME=/deployments/data \
    QUARKUS_PROFILE=prod \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Duser.home=/deployments/data" \
    JAVA_APP_JAR="/deployments/quarkus-run.jar"

USER root
RUN mkdir -p /deployments/data/logs /deployments/data/geo /deployments/data/.framework-net \
    && chown -R 185:0 /deployments/data \
    && chmod -R g+rwX /deployments/data

WORKDIR /deployments

COPY --from=build --chown=185:0 /build/build/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185:0 /build/build/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185:0 /build/build/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185:0 /build/build/quarkus-app/quarkus/ /deployments/quarkus/

VOLUME ["/deployments/data"]

EXPOSE 8080

USER 185

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/ > /dev/null || exit 1

ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
