#!/usr/bin/env bash
set -euo pipefail

# Creates/updates the ConfigMap from worker.sh and applies the suspended Job.
# Requires: oc CLI logged in to the target cluster.

NAMESPACE="${1:-default}"
JOB_NAME="${2:-sample-batch-job}"
BATCH_APP_SERVICE_URL="${3:-http://batch-job-app.${NAMESPACE}.svc.cluster.local:8080}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

oc get namespace "$NAMESPACE" >/dev/null 2>&1 || oc create namespace "$NAMESPACE"

oc -n "$NAMESPACE" create configmap sample-batch-script \
  --from-file=worker.sh="${SCRIPT_DIR}/worker.sh" \
  --dry-run=client -o yaml | oc apply -f -

sed "s/__JOB_NAME__/${JOB_NAME}/g" "${SCRIPT_DIR}/suspended-job-openshift.yaml" | oc -n "$NAMESPACE" apply -f -
oc -n "$NAMESPACE" set env job/"$JOB_NAME" \
  JOB_NAMESPACE="$NAMESPACE" \
  JOB_NAME="$JOB_NAME" \
  BATCH_APP_BASE_URL="$BATCH_APP_SERVICE_URL"

printf 'Created suspended Job %s in namespace %s\n' "$JOB_NAME" "$NAMESPACE"
printf 'Start it via batch-job-app API: POST /api/v1/jobs/%s/%s/start\n' "$NAMESPACE" "$JOB_NAME"
