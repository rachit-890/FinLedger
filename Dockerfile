# ============ STAGE 1: BUILD ============
# This stage installs Maven, downloads dependencies, and builds the JAR
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper & pom.xml first (for dependency caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/

# Build the JAR (tests are skipped here — they are pure unit tests and run in CI)
RUN ./mvnw clean package -DskipTests -B


# ============ STAGE 2: RUN ============
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/FinLedger-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
