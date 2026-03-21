# ─── STAGE 1: BUILD ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY . .

RUN ./mvnw -pl eureka-server --also-make \
    -DskipTests \
    clean package

# ─── STAGE 2: RUNTIME ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY --from=builder /build/eureka-server/target/eureka-server-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]