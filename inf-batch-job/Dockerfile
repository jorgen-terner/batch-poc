# Build stage
FROM gradle:8.11.1-jdk17 AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradlew gradlew.bat ./
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew build -x test --no-daemon

# Runtime stage
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.18

WORKDIR /app

ENV LANGUAGE='en_US:en'

# Quarkus fast-jar runtime
COPY --from=builder --chown=1000 /app/build/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=1000 /app/build/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=1000 /app/build/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=1000 /app/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 1000

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
