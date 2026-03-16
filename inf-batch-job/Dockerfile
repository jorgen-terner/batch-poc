# Runtime stage only - build artifacts are expected to exist locally
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.18

WORKDIR /deployments

ENV LANG=sv_SE.UTF-8
ENV LANGUAGE=sv_SE:sv
ENV LC_ALL=sv_SE.UTF-8

# Copy pre-built Quarkus fast-jar artifacts
COPY --chown=1000 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=1000 build/quarkus-app/*.jar /deployments/
COPY --chown=1000 build/quarkus-app/app/ /deployments/app/
COPY --chown=1000 build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 1000

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Duser.language=sv -Duser.country=SE"

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
