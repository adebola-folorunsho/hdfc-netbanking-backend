# ─── STAGE 1: BUILD ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY . .

RUN ./mvnw -pl transaction-service --also-make \
    -DskipTests \
    clean package

# ─── STAGE 2: RUNTIME ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY --from=builder /build/transaction-service/target/transaction-service-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]