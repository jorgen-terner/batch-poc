# Runtime-only image - expects pre-built artifacts
# Build locally first: .\gradlew :inf-batch-testapp1:build -x test
# For OpenShift: oc start-build inf-batch-testapp1 --from-dir=. --follow -n dev252
# For Docker: docker build -t inf-batch-testapp1:latest .
FROM registry.access.redhat.com/ubi8/openjdk-21-runtime:1.20

WORKDIR /deployments

ENV LANGUAGE='sv_SE:sv'

# Copy pre-built Quarkus fast-jar artifacts from build/ directory
COPY --chown=1000 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=1000 build/quarkus-app/*.jar /deployments/
COPY --chown=1000 build/quarkus-app/app/ /deployments/app/
COPY --chown=1000 build/quarkus-app/quarkus/ /deployments/quarkus/

USER 1000

ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
