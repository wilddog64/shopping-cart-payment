# Payment Service Dockerfile
# Multi-stage build for optimized image size

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Install Maven (wrapper incompatible with Alpine)
RUN apk add --no-cache maven

# Copy build descriptors and resolve dependencies (cached layer)
COPY pom.xml checkstyle.xml ./
RUN --mount=type=secret,id=GH_TOKEN \
    mkdir -p /root/.m2 && \
    printf '<settings>\n  <servers>\n    <server>\n      <id>github-rabbitmq-client</id>\n      <username>x-token-auth</username>\n      <password>%s</password>\n    </server>\n  </servers>\n</settings>\n' "$(cat /run/secrets/GH_TOKEN)" > /root/.m2/settings.xml && \
    mvn dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application
RUN --mount=type=secret,id=GH_TOKEN mvn package -DskipTests -B

# Extract layers for optimized Docker layering
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: Run as non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy extracted layers (better caching)
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8084

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health/liveness || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=default"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
