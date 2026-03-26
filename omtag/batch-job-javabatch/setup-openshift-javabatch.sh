#!/usr/bin/env bash
set -euo pipefail

# Creates ConfigMap from existing jbatch scripts and applies suspended Job.

NAMESPACE="${1:-default}"
JOB_NAME="${2:-sample-javabatch-job}"
BATCH_APP_SERVICE_URL="${3:-http://batch-job-app.${NAMESPACE}.svc.cluster.local:8080}"
SPRINGBATCH_CONFIG_FILE="${4:-springbatch_job.ini}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -f "${SCRIPT_DIR}/${SPRINGBATCH_CONFIG_FILE}" ]; then
  printf 'Missing config file: %s\n' "${SCRIPT_DIR}/${SPRINGBATCH_CONFIG_FILE}" >&2
  exit 1
fi

oc get namespace "$NAMESPACE" >/dev/null 2>&1 || oc create namespace "$NAMESPACE"

oc -n "$NAMESPACE" create configmap javabatch-job-scripts \
  --from-file=springbatch_v2.py="${SCRIPT_DIR}/springbatch_v2.py" \
  --from-file=monitor_jbatch.sh="${SCRIPT_DIR}/monitor_jbatch.sh" \
  --from-file=javabatch_worker.sh="${SCRIPT_DIR}/javabatch_worker.sh" \
  --from-file=springbatch_job.ini="${SCRIPT_DIR}/${SPRINGBATCH_CONFIG_FILE}" \
  --dry-run=client -o yaml | oc apply -f -

sed "s/__JOB_NAME__/${JOB_NAME}/g" "${SCRIPT_DIR}/suspended-job-openshift.yaml" | oc -n "$NAMESPACE" apply -f -

oc -n "$NAMESPACE" set env job/"$JOB_NAME" \
  JOB_NAMESPACE="$NAMESPACE" \
  JOB_NAME="$JOB_NAME" \
  BATCH_APP_BASE_URL="$BATCH_APP_SERVICE_URL"

printf 'Created suspended javabatch job %s in namespace %s\n' "$JOB_NAME" "$NAMESPACE"
printf 'Start with: ./batch-job-control.sh start %s %s <batch-job-app-url>\n' "$NAMESPACE" "$JOB_NAME"
