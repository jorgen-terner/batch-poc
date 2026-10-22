#!/bin/sh
set -eu

APP_JAR="${APP_JAR:-/app/inv-batch-test-app.jar}"

echo "[run-inv-batch-test-app] Starting ${APP_JAR}"
exec java ${JAVA_OPTS:-} -jar "${APP_JAR}"
