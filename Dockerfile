# syntax=docker/dockerfile:1

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy the Maven wrapper and backend project descriptor first so dependency
# resolution is cached in its own layer and only re-runs when pom.xml changes.
COPY backend/mvnw backend/mvnw
COPY backend/.mvn backend/.mvn
COPY backend/pom.xml backend/pom.xml
RUN chmod +x backend/mvnw && ./backend/mvnw -f backend/pom.xml dependency:go-offline -q

# Copy source and build the fat JAR (skip tests — they run in CI separately).
COPY backend/src backend/src
RUN ./backend/mvnw -f backend/pom.xml clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for least-privilege runtime.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/backend/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
