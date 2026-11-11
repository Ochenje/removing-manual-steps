# Stage 1: Build compilation and test optimization container environment
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /build

# Pre-cache dependencies via explicit metadata isolation layers
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy codebase architectures and trigger comprehensive verify targets
COPY src ./src
RUN mvn clean package -DskipTests=false

# Stage 2: Minimalist production infrastructure wrapper runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /opt/rental-tracker

# Build standard non-root container profile paths safely
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p data && chown -R appuser:appgroup /opt/rental-tracker

USER appuser

# Mount decoupled external folder targets securely across host runtime boundaries
VOLUME ["/opt/rental-tracker/data"]

# Extract compiled fat jar file outputs safely from builder context references
COPY --from=builder /build/target/multi-user-rental-tracker-1.0.jar app.jar

# Configuration environmental parameters exposed for target runtime path adjustments
ENV DATABASE_PATH="data/app.db"

ENTRYPOINT ["sh", "-c", "java -jar app.jar ${DATABASE_PATH}"]
