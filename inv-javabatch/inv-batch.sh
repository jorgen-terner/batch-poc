#!/bin/sh

JAR_PATH="${QUARKUS_RUN_JAR:-/produkter/fk/tools/fk-inf_poc_javabatch_api/bin/quarkus-run.jar}"

# Quarkus requires JBoss LogManager. Force console logs to stdout.
DEFAULT_JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dquarkus.log.console.stderr=false -Dquarkus.http.host=0.0.0.0 -Duser.language=sv -Duser.country=SE"

# shellcheck disable=SC2086
exec java ${DEFAULT_JAVA_OPTS} -jar "${JAR_PATH}"