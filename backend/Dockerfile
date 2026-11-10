# ==========================================
# Stage 1: Build Spring Boot application with Maven
# ==========================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Minimal JRE Runtime (Optimized for 2GB VPS)
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner

# Install wget / curl for healthcheck
RUN apk add --no-cache curl tzdata

# Create dedicated non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy executable jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# Environment defaults optimized for low memory footprint
ENV JAVA_OPTS="-XX:+UseSerialGC -Xms256m -Xmx384m -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
