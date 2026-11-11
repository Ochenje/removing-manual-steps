# Stage 1: Build and verify the application
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /build

# Pre-cache dependencies via explicit metadata isolation layers
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy sources and run the complete test suite before packaging
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean verify

# Stage 2: Run the application as a non-root user
FROM eclipse-temurin:25-jre-alpine
WORKDIR /opt/rental-tracker

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p data logs && chown -R appuser:appgroup /opt/rental-tracker

USER appuser

COPY --from=builder /build/target/removing-manual-steps-1.0.jar app.jar

ENV DATABASE_PATH="/opt/rental-tracker/data/app.db" \
    LOG_PATH="/opt/rental-tracker/logs/app.log"

ENTRYPOINT ["java", "-jar", "app.jar"]
