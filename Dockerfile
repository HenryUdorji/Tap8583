# ==========================================
# Tap8583 Server Dockerfile (Render / Cloud)
# ==========================================

# 1. Build Stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy gradle wrapper and build configurations
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY shared shared
COPY listenerLib listenerLib
COPY serverApp serverApp

# Grant execution rights and build distribution bundle
RUN chmod +x ./gradlew
RUN ./gradlew :serverApp:installDist --no-daemon

# 2. Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy installed distribution from build stage
COPY --from=build /app/serverApp/build/install/serverApp ./

EXPOSE 8080
ENV PORT=8080
ENV HOST=0.0.0.0
ENV TAP8583_API_KEY=tap_live_secret

CMD ["./bin/serverApp"]
