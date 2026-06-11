FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY server server

RUN chmod +x ./gradlew && ./gradlew :server:installDist --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/server/build/install/server ./

ENV PORT=8081
ENV VIDEO_DIR=/app/video
EXPOSE 8081

ENTRYPOINT ["bin/server"]
