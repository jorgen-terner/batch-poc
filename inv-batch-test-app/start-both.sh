#!/bin/sh
set -eu

APP_JAR="${APP_JAR:-/app/inv-batch-test-app.jar}"
APP_URL="${APP_URL:-http://127.0.0.1:8080/inv-batch-app/batch}"
READINESS_URL="${READINESS_URL:-${APP_URL}/actuator/health/readiness}"
APP_START_TIMEOUT_SECONDS="${APP_START_TIMEOUT_SECONDS:-180}"

BATCH_RUNNER="${BATCH_RUNNER:-/produkter/fk/tools/fk-inf_batch_api/bin/batch-api-script/batch-api-runner.sh}"
RUN_BATCH_SCRIPT="${RUN_BATCH_SCRIPT:-/produkter/fk/tools/fk-inf_poc_javabatch_api/scripts/run-batch.sh}"

if [ ! -x "${BATCH_RUNNER}" ]; then
  ALT_RUNNER="/produkter/tools/inf_poc_batch_api/bin/batch-api-runner.sh"
  if [ -x "${ALT_RUNNER}" ]; then
    BATCH_RUNNER="${ALT_RUNNER}"
  fi
fi

if [ ! -x "${RUN_BATCH_SCRIPT}" ]; then
  ALT_SCRIPT="/usr/local/bin/inv-batch.sh"
  if [ -x "${ALT_SCRIPT}" ]; then
    RUN_BATCH_SCRIPT="${ALT_SCRIPT}"
  fi
fi

export START="${START:-${APP_URL}/myapp/start}"
export STATUS="${STATUS:-${APP_URL}/myapp/status}"
export STOP="${STOP:-${APP_URL}/myapp/stop}"
export EXEC_ID_PARAM_NAME="${EXEC_ID_PARAM_NAME:-execId}"

echo "[start-both] Starting Spring Boot app: ${APP_JAR}"
java ${JAVA_OPTS:-} -jar "${APP_JAR}" &
APP_PID=$!

cleanup() {
  if kill -0 "${APP_PID}" 2>/dev/null; then
    echo "[start-both] Stopping Spring Boot app (pid=${APP_PID})"
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "[start-both] Waiting for readiness: ${READINESS_URL}"
elapsed=0
while [ "${elapsed}" -lt "${APP_START_TIMEOUT_SECONDS}" ]; do
  if command -v curl >/dev/null 2>&1; then
    if curl -fsS "${READINESS_URL}" >/dev/null 2>&1; then
      break
    fi
  elif command -v wget >/dev/null 2>&1; then
    if wget -q -O - "${READINESS_URL}" >/dev/null 2>&1; then
      break
    fi
  fi

  sleep 2
  elapsed=$((elapsed + 2))
done

if [ "${elapsed}" -ge "${APP_START_TIMEOUT_SECONDS}" ]; then
  echo "[start-both] App did not become ready within ${APP_START_TIMEOUT_SECONDS}s"
  exit 1
fi

if [ ! -x "${BATCH_RUNNER}" ]; then
  echo "[start-both] batch-api runner not executable: ${BATCH_RUNNER}"
  exit 2
fi

if [ ! -x "${RUN_BATCH_SCRIPT}" ]; then
  echo "[start-both] run-batch script not executable: ${RUN_BATCH_SCRIPT}"
  exit 2
fi

echo "[start-both] Running wrapper: ${BATCH_RUNNER} ${RUN_BATCH_SCRIPT}"
"${BATCH_RUNNER}" "${RUN_BATCH_SCRIPT}"
runner_rc=$?

echo "[start-both] Wrapper finished with exit code ${runner_rc}"
exit "${runner_rc}"
