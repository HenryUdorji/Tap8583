# ==========================================
# Tap8583 Ingestion Server (Ktor)
# Build from the repository root:
#   docker build -f serverApp/Dockerfile -t tap8583-server .
# ==========================================

# 1. Build Stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy gradle wrapper and configurations
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY shared shared
COPY listenerLib listenerLib
COPY serverApp serverApp

# Build distribution bundle using container JDK
RUN chmod +x ./gradlew
RUN ./gradlew :serverApp:installDist --no-daemon -Dorg.gradle.java.installations.auto-download=false

# 2. Runtime Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin tap8583

# Copy installed application distribution
COPY --from=build /app/serverApp/build/install/serverApp ./

USER tap8583
EXPOSE 8080
ENV PORT=8080
ENV HOST=0.0.0.0
# Keys are runtime secrets: pass TAP8583_VIEWER_KEY / TAP8583_INGEST_KEY with
# `docker run -e` or deploy/.env. Never bake them into the image.

CMD ["./bin/serverApp"]
