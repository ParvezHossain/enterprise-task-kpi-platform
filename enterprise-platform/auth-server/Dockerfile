FROM maven:3.9.16-eclipse-temurin-25-alpine@sha256:21e373f51c0b14c9c76d415033f4eb145d1c0b59cd227a3ec5cc1c78002c6da8 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Runs Surefire unit tests. Run clean verify with Docker on the host/CI for Failsafe.
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:25.0.4_7-jre-alpine-3.24@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682 AS runtime
RUN addgroup -S -g 10001 auth && adduser -S -D -H -u 10001 -G auth auth
WORKDIR /app
COPY --from=build --chmod=0444 /build/target/auth-server-0.0.1-SNAPSHOT.jar /app/auth-server.jar
ENV SPRING_PROFILES_ACTIVE=docker
USER 10001:10001
EXPOSE 9000
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -T 4 -O /dev/null "http://127.0.0.1:${AUTH_SERVER_PORT:-9000}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/auth-server.jar"]
