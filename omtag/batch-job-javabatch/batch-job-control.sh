#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./batch-job-control.sh <action> <namespace> <job-name> [base-url]

Actions:
  start | stop | restart | status | metrics
EOF
}

ACTION="${1:-}"
NAMESPACE="${2:-}"
JOB_NAME="${3:-}"
BASE_URL="${4:-http://localhost:8080}"

if [ -z "$ACTION" ] || [ -z "$NAMESPACE" ] || [ -z "$JOB_NAME" ]; then
  usage
  exit 1
fi

case "$ACTION" in
  start|stop|restart)
    METHOD="POST"
    ;;
  status|metrics)
    METHOD="GET"
    ;;
  *)
    usage
    exit 1
    ;;
esac

curl -sS -X "$METHOD" "${BASE_URL}/api/v1/jobs/${NAMESPACE}/${JOB_NAME}/${ACTION}"
