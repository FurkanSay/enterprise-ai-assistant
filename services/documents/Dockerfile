# syntax=docker/dockerfile:1.7
# Build context = repo root.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY services/documents/pom.xml  services/documents/
COPY services/documents/src      services/documents/src
COPY libs/generated/java         libs/generated/java

WORKDIR /build/services/documents
# -U forces a refresh of the Maven metadata so cached "not found" markers
# from the previous layer do not stick.
RUN mvn -B -U -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN groupadd -r documents && useradd -r -g documents -d /app -s /sbin/nologin documents

COPY --from=build --chown=documents:documents /build/services/documents/target/*.jar /app/app.jar

EXPOSE 8082
USER documents

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8082/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
