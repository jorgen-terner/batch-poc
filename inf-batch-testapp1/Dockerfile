# Build stage
FROM gradle:8.11.1-jdk21 AS builder

WORKDIR /build

# Copy entire project for build context
COPY . .

# Build only the testapp1 module
WORKDIR /build
RUN gradle :inf-batch-testapp1:build -x test --no-daemon

# Runtime stage
FROM registry.access.redhat.com/ubi8/openjdk-21-runtime:1.20

WORKDIR /deployments

ENV LANGUAGE='en_US:en'

# Copy Quarkus fast-jar artifacts
COPY --from=builder --chown=1000 /build/inf-batch-testapp1/build/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=1000 /build/inf-batch-testapp1/build/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=1000 /build/inf-batch-testapp1/build/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=1000 /build/inf-batch-testapp1/build/quarkus-app/quarkus/ /deployments/quarkus/

USER 1000

ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
