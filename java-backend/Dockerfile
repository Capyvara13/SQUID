FROM maven:3.8.8-eclipse-temurin-11 AS builder

# Build the project and create an executable Spring Boot jar
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:11-jre

# Install curl for healthcheck and utilities
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy the repackaged Spring Boot jar from the builder stage
# Use a wildcard to match the built artifact (the spring-boot plugin repackages to the same artifact name)
COPY --from=builder /build/target/squid-core-*.jar app.jar

# Create test-vectors directory
RUN mkdir -p /app/test-vectors

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/api/v1/health || exit 1

# Run the application
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]
