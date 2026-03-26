#!/usr/bin/env bash
set -euo pipefail

# Client script that matches batch-job-app API operations:
# start, stop, restart, status, metrics

usage() {
  cat <<'EOF'
Usage:
  ./batch-job-control.sh <action> <namespace> <job-name> [base-url]

Actions:
  start | stop | restart | status | metrics

Examples:
  ./batch-job-control.sh start default sample-batch-job
  ./batch-job-control.sh status default sample-batch-job http://localhost:8080
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

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
    URL="${BASE_URL}/api/v1/jobs/${NAMESPACE}/${JOB_NAME}/${ACTION}"
    ;;
  status|metrics)
    METHOD="GET"
    URL="${BASE_URL}/api/v1/jobs/${NAMESPACE}/${JOB_NAME}/${ACTION}"
    ;;
  *)
    printf 'Unknown action: %s\n' "$ACTION" >&2
    usage
    exit 1
    ;;
esac

curl -sS -X "$METHOD" "$URL"
