#!/bin/sh
set -eu

APP_JAR="${APP_JAR:-/app/inv-batch-test-app.jar}"
RUNNER_DIR="${RUNNER_DIR:-/opt/inf-javabatch-runner}"

echo "[run-inv-batch-test-app] Starting ${APP_JAR}"
echo "[run-inv-batch-test-app] Runner classpath: ${RUNNER_DIR}"
exec java ${JAVA_OPTS:-} -cp "${APP_JAR}:${RUNNER_DIR}/*" org.springframework.boot.loader.launch.JarLauncher
