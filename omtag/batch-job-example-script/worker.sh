#!/usr/bin/env bash
set -euo pipefail

# Example batch worker for suspended Jobs managed by batch-job-app.
# It simulates processing and reports progress to /report.

JOB_NAMESPACE="${JOB_NAMESPACE:-default}"
JOB_NAME="${JOB_NAME:-sample-batch-job}"
BATCH_APP_BASE_URL="${BATCH_APP_BASE_URL:-http://batch-job-app.default.svc.cluster.local:8080}"
REPORT_URL="${REPORT_URL:-${BATCH_APP_BASE_URL}/api/v1/jobs/${JOB_NAMESPACE}/${JOB_NAME}/report}"
TOTAL_STEPS="${TOTAL_STEPS:-10}"
SLEEP_SECONDS="${SLEEP_SECONDS:-3}"

START_EPOCH="$(date +%s)"
WORKER_ID="${HOSTNAME:-worker-unknown}"
EXECUTION_ID="${WORKER_ID}-$(date +%s)"

json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/ }"
  printf '%s' "$s"
}

report() {
  local status="$1"
  local processed="$2"
  local error_count="$3"
  local message="$4"

  local now elapsed
  now="$(date +%s)"
  elapsed=$(( now - START_EPOCH ))

  local payload
  payload="$(cat <<EOF
{
  \"status\": \"$(json_escape "$status")\",
  \"metrics\": {
    \"recordsProcessed\": ${processed}.0,
    \"errorCount\": ${error_count}.0,
    \"elapsedSeconds\": ${elapsed}.0
  },
  \"attributes\": {
    \"executionId\": \"$(json_escape "$EXECUTION_ID")\",
    \"workerId\": \"$(json_escape "$WORKER_ID")\",
    \"message\": \"$(json_escape "$message")\"
  }
}
EOF
)"

  if command -v curl >/dev/null 2>&1; then
    curl -sS -X POST \
      -H "Content-Type: application/json" \
      --data "$payload" \
      "$REPORT_URL" >/dev/null || true
  fi
}

on_term() {
  report "FAILED" "${CURRENT_STEP:-0}" 1 "terminated by signal"
  exit 143
}

trap on_term TERM INT

printf 'Starting batch worker for %s/%s\n' "$JOB_NAMESPACE" "$JOB_NAME"
report "RUNNING" 0 0 "job started"

CURRENT_STEP=0
while [ "$CURRENT_STEP" -lt "$TOTAL_STEPS" ]; do
  CURRENT_STEP=$(( CURRENT_STEP + 1 ))
  printf 'Processing step %s/%s\n' "$CURRENT_STEP" "$TOTAL_STEPS"

  # Simulate useful work.
  sleep "$SLEEP_SECONDS"

  report "RUNNING" "$CURRENT_STEP" 0 "progress"
done

report "SUCCEEDED" "$CURRENT_STEP" 0 "job finished"
printf 'Job completed successfully\n'
