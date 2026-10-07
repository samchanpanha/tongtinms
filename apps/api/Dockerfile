# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache the dependency tree first so source changes don't re-download Maven artifacts
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/tongtin-api-*.jar app.jar

USER spring:spring
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
