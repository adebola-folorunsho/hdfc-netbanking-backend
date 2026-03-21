# ─── STAGE 1: BUILD ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY . .

RUN ./mvnw -pl currency-service --also-make \
    -DskipTests \
    clean package

# ─── STAGE 2: RUNTIME ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY --from=builder /build/currency-service/target/currency-service-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "app.jar"]