# Runtime image for inf-javabatch Quarkus fast-jar artifacts.
# Build command (run from repository root c:/repos/batch-poc):
#   docker build -f inv-javabatch/Dockerfile -t inv-javabatch:latest .
FROM registry.access.redhat.com/ubi8/openjdk-21-runtime:1.20

WORKDIR /deployments

# Source path is relative to the Docker build context.
ARG QUARKUS_APP_DIR=inf-javabatch/build/quarkus-app

COPY --chown=1000 ${QUARKUS_APP_DIR}/lib/ /deployments/lib/
COPY --chown=1000 ${QUARKUS_APP_DIR}/*.jar /deployments/
COPY --chown=1000 ${QUARKUS_APP_DIR}/app/ /deployments/app/
COPY --chown=1000 ${QUARKUS_APP_DIR}/quarkus/ /deployments/quarkus/

USER 1000

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
