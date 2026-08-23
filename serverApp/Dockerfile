# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY shared shared
COPY listenerLib listenerLib
COPY serverApp serverApp

RUN chmod +x ./gradlew
RUN ./gradlew :serverApp:installDist --no-daemon

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/serverApp/build/install/serverApp ./

EXPOSE 8080
ENV PORT=8080
ENV HOST=0.0.0.0
ENV TAP8583_API_KEY=tap_live_secret

CMD ["./bin/serverApp"]
