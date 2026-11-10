#!/bin/sh
set -eu

APP_JAR="${APP_JAR:-/app/inv-batch-test-app.jar}"
RUNNER_DIR="${RUNNER_DIR:-/opt/inf-javabatch-runner}"
if [ -f "${RUNNER_DIR}" ]; then
  RUNNER_DIR="$(dirname "${RUNNER_DIR}")"
fi

echo "[run-inv-batch-test-app] Starting ${APP_JAR}"
echo "[run-inv-batch-test-app] Runner directory: ${RUNNER_DIR}"

if ! find "${RUNNER_DIR}" -maxdepth 1 -name '*.jar' -print -quit | grep -q .; then
  echo "[run-inv-batch-test-app] ERROR: No runner JAR found in ${RUNNER_DIR}." >&2
  echo "[run-inv-batch-test-app] Mount inf-javabatch-runner.jar into ${RUNNER_DIR} or set RUNNER_DIR." >&2
  exit 1
fi

# Kör extern runner via loader.path så att app + kontrakt + implementation
# laddas under Spring Boots launcher classloader (ingen classloader-split).
exec java ${JAVA_OPTS:-} \
  -Dloader.path="${RUNNER_DIR}" \
  -cp "${APP_JAR}" \
  org.springframework.boot.loader.launch.PropertiesLauncher
