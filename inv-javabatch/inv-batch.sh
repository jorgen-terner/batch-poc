#!/bin/sh
JAR_PATH="${QUARKUS_RUN_JAR:-/produkter/tools/inf_poc_javabatch_api/bin/quarkus-run.jar}"

# Replace the shell with the JVM so TERM/INT from batch-wrapper reaches Quarkus directly.
exec java ${JAVA_OPTS:-} -jar "${JAR_PATH}"
