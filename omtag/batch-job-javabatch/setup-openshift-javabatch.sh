#!/usr/bin/env bash
set -euo pipefail

# Creates ConfigMap from existing jbatch scripts and applies suspended Job.

NAMESPACE="${1:-default}"
JOB_NAME="${2:-sample-javabatch-job}"
BATCH_APP_SERVICE_URL="${3:-http://batch-job-app.${NAMESPACE}.svc.cluster.local:8080}"
SPRINGBATCH_CONFIG_FILE="${4:-springbatch_job.ini}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP_DIR"
}

trap cleanup EXIT

escape_sed_replacement() {
  printf '%s' "$1" | sed -e 's/[\\&]/\\&/g'
}

normalize_copy() {
  local source_file="$1"
  local target_file="$2"
  awk '{ sub(/\r$/, ""); print }' "$source_file" > "$target_file"
}

if [ ! -f "${SCRIPT_DIR}/${SPRINGBATCH_CONFIG_FILE}" ]; then
  printf 'Missing config file: %s\n' "${SCRIPT_DIR}/${SPRINGBATCH_CONFIG_FILE}" >&2
  exit 1
fi

oc get namespace "$NAMESPACE" >/dev/null 2>&1 || oc create namespace "$NAMESPACE"

normalize_copy "${SCRIPT_DIR}/springbatch_v2.py" "${TMP_DIR}/springbatch_v2.py"
normalize_copy "${SCRIPT_DIR}/monitor_jbatch.sh" "${TMP_DIR}/monitor_jbatch.sh"
normalize_copy "${SCRIPT_DIR}/javabatch_worker.sh" "${TMP_DIR}/javabatch_worker.sh"
normalize_copy "${SCRIPT_DIR}/${SPRINGBATCH_CONFIG_FILE}" "${TMP_DIR}/springbatch_job.ini"

oc -n "$NAMESPACE" create configmap javabatch-job-scripts \
  --from-file=springbatch_v2.py="${TMP_DIR}/springbatch_v2.py" \
  --from-file=monitor_jbatch.sh="${TMP_DIR}/monitor_jbatch.sh" \
  --from-file=javabatch_worker.sh="${TMP_DIR}/javabatch_worker.sh" \
  --from-file=springbatch_job.ini="${TMP_DIR}/springbatch_job.ini" \
  --dry-run=client -o yaml | oc apply -f -

if oc -n "$NAMESPACE" get job "$JOB_NAME" >/dev/null 2>&1; then
  printf 'Replacing existing job %s in namespace %s\n' "$JOB_NAME" "$NAMESPACE"
  oc -n "$NAMESPACE" delete job "$JOB_NAME" --wait=true
fi

JOB_NAME_ESCAPED="$(escape_sed_replacement "$JOB_NAME")"
BATCH_APP_SERVICE_URL_ESCAPED="$(escape_sed_replacement "$BATCH_APP_SERVICE_URL")"

sed \
  -e "s/__JOB_NAME__/${JOB_NAME_ESCAPED}/g" \
  -e "s|__BATCH_APP_BASE_URL__|${BATCH_APP_SERVICE_URL_ESCAPED}|g" \
  "${SCRIPT_DIR}/suspended-job-openshift.yaml" | oc -n "$NAMESPACE" apply -f -

printf 'Created suspended javabatch job %s in namespace %s\n' "$JOB_NAME" "$NAMESPACE"
printf 'Start with: ./batch-job-control.sh start %s %s <batch-job-app-url>\n' "$NAMESPACE" "$JOB_NAME"
