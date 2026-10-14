# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first so code changes do not re-download them.
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin legalsuite \
    && mkdir -p /app/uploads && chown -R legalsuite:legalsuite /app
WORKDIR /app
COPY --from=build --chown=legalsuite:legalsuite /src/target/owlaw-api-1.0.0.jar app.jar
USER legalsuite
ENV PORT=18081 \
    LEGALSUITE_UPLOAD_DIR=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Uploaded documents and payment proofs. Mount a volume here and back it up with the database.
VOLUME ["/app/uploads"]
EXPOSE 18081
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/api/v1/health" > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
