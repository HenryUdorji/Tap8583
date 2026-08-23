# ==========================================
# Tap8583 Server Dockerfile (Render / Cloud)
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

# Copy installed application distribution
COPY --from=build /app/serverApp/build/install/serverApp ./

EXPOSE 8080
ENV PORT=8080
ENV HOST=0.0.0.0
ENV TAP8583_API_KEY=tap_live_secret

CMD ["./bin/serverApp"]
