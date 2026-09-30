# syntax=docker/dockerfile:1
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx768m test bootJar

FROM eclipse-temurin:25-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 project11 \
    && useradd --uid 10001 --gid project11 --no-create-home project11 \
    && install -d -o project11 -g project11 -m 700 /app/data
WORKDIR /app
COPY --from=build --chown=project11:project11 /workspace/build/libs/project11.jar ./project11.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=5 \
    CMD curl --fail --silent --output /dev/null http://127.0.0.1:8080/signin || exit 1
ENTRYPOINT ["java", "-jar", "/app/project11.jar"]
