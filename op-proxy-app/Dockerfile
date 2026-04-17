# Runtime stage - build artifacts are expected to exist locally from ./gradlew build
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

WORKDIR /deployments

ENV LANG=sv_SE.UTF-8 \
	LANGUAGE=sv_SE:sv \
	LC_ALL=sv_SE.UTF-8

# Copy pre-built Quarkus fast-jar artifacts (minimal image)
COPY --chown=1000 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=1000 build/quarkus-app/*.jar /deployments/
COPY --chown=1000 build/quarkus-app/app/ /deployments/app/
COPY --chown=1000 build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 1000

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
