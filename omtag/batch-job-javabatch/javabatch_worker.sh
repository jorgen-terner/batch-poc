#!/usr/bin/env bash
set -euo pipefail

# Container entrypoint used by Kubernetes Job managed by batch-job-app.
# It runs springbatch_v2.py and reports progress/results to /report.

JOB_NAMESPACE="${JOB_NAMESPACE:-default}"
JOB_NAME="${JOB_NAME:-sample-javabatch-job}"
BATCH_APP_BASE_URL="${BATCH_APP_BASE_URL:-http://batch-job-app.default.svc.cluster.local:8080}"
REPORT_URL="${REPORT_URL:-${BATCH_APP_BASE_URL}/api/v1/jobs/${JOB_NAMESPACE}/${JOB_NAME}/report}"
REPORT_INTERVAL_SECONDS="${REPORT_INTERVAL_SECONDS:-10}"
ENABLE_PROGRESS_REPORT="${ENABLE_PROGRESS_REPORT:-false}"
JOB_CONFIG="${JOB_CONFIG:-/opt/jbatch/springbatch_job.ini}"
JOB_ARGS="${JOB_ARGS:-}"
FKST_TOKEN="${FKST_TOKEN:-}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

START_EPOCH="$(date +%s)"
EXECUTION_ID="${HOSTNAME:-worker}-$(date +%s)"
CHILD_PID=""

json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/ }"
  printf '%s' "$s"
}

report() {
  local status="$1"
  local error_count="$2"
  local message="$3"

  local now elapsed
  now="$(date +%s)"
  elapsed=$(( now - START_EPOCH ))

  local payload
  payload="$(cat <<EOF
{
  \"status\": \"$(json_escape "$status")\",
  \"metrics\": {
    \"errorCount\": ${error_count}.0,
    \"elapsedSeconds\": ${elapsed}.0
  },
  \"attributes\": {
    \"executionId\": \"$(json_escape "$EXECUTION_ID")\",
    \"jobArgs\": \"$(json_escape "$JOB_ARGS")\",
    \"message\": \"$(json_escape "$message")\"
  }
}
EOF
)"

  curl -sS -X POST \
    -H "Content-Type: application/json" \
    --data "$payload" \
    "$REPORT_URL" >/dev/null || true
}

attempt_stop_remote_job() {
  set +e
  if [ -n "${CHILD_PID}" ] && kill -0 "${CHILD_PID}" >/dev/null 2>&1; then
    "${PYTHON_BIN}" /opt/jbatch/springbatch_v2.py \
      -j "${JOB_CONFIG}" \
      --stop \
      -a "${JOB_ARGS}" \
      -t "${FKST_TOKEN}" >/dev/null 2>&1
  fi
  set -e
}

on_term() {
  report "FAILED" 1 "terminated by signal"
  attempt_stop_remote_job

  if [ -n "${CHILD_PID}" ] && kill -0 "${CHILD_PID}" >/dev/null 2>&1; then
    kill "${CHILD_PID}" >/dev/null 2>&1 || true
    wait "${CHILD_PID}" >/dev/null 2>&1 || true
  fi

  exit 143
}

trap on_term TERM INT

report "RUNNING" 0 "jbatch worker started"

"${PYTHON_BIN}" /opt/jbatch/springbatch_v2.py \
  -j "${JOB_CONFIG}" \
  --start \
  -a "${JOB_ARGS}" \
  -t "${FKST_TOKEN}" &
CHILD_PID="$!"

while kill -0 "${CHILD_PID}" >/dev/null 2>&1; do
  sleep "${REPORT_INTERVAL_SECONDS}"
  if [ "${ENABLE_PROGRESS_REPORT}" = "true" ]; then
    report "RUNNING" 0 "springbatch_v2 still running"
  fi
done

set +e
wait "${CHILD_PID}"
RC="$?"
set -e

if [ "$RC" -eq 0 ]; then
  report "SUCCEEDED" 0 "springbatch_v2 completed"
  exit 0
fi

report "FAILED" 1 "springbatch_v2 failed with rc=${RC}"
exit "$RC"
