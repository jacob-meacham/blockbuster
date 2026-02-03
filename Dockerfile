FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY detekt.yml .editorconfig ./
RUN ./gradlew --version
COPY src/ src/
COPY --from=frontend /app/frontend/dist/ src/main/resources/frontend/
RUN ./gradlew shadowJar --no-daemon -x buildFrontend -x copyFrontendDist

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r blockbuster && useradd -r -g blockbuster -m blockbuster
RUN mkdir -p /app/data && chown -R blockbuster:blockbuster /app

COPY --from=build --chown=blockbuster:blockbuster /app/build/libs/blockbuster-1.0.0-SNAPSHOT.jar app.jar

USER blockbuster

EXPOSE 8585

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8585/health || exit 1

LABEL org.opencontainers.image.title="Blockbuster" \
      org.opencontainers.image.description="NFC-powered media library system" \
      org.opencontainers.image.source="https://github.com/blockbuster"

ENTRYPOINT ["java", "-jar", "app.jar", "server", "/config/config.yml"]
