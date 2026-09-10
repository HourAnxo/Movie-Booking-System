# syntax=docker/dockerfile:1

# ---------- build ----------
# Pinned to JDK 21. Lombok's annotation processor does not run on newer
# JDKs — it fails silently and every @Getter-generated method comes back
# as "cannot find symbol" — and all twelve modules target 21 regardless.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
COPY src ./src

# The cache mount is what makes twelve images affordable: one shared ~/.m2
# across every module's build instead of downloading Spring twelve times.
# Tests are skipped because they are @SpringBootTest context loads that
# need MySQL and Eureka running — neither exists at image build time.
RUN --mount=type=cache,target=/root/.m2     mvn -B -q clean package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# curl is here only for HEALTHCHECK. The base image ships neither curl nor
# wget, and a bare TCP probe would defeat the point: the port is open long
# before the service can serve a request, and stays open after its database
# has gone.
RUN apt-get update     && apt-get install -y --no-install-recommends curl     && rm -rf /var/lib/apt/lists/*     && useradd --system --create-home --uid 1001 spring

COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

USER spring

EXPOSE 8089

# start-period covers JVM boot and Flyway; a failure before it elapses does
# not count against the retries.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5     CMD curl -fsS http://localhost:8089/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
