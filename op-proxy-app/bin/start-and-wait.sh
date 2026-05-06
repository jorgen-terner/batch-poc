#!/usr/bin/env bash
# start-and-wait.sh
#
# Startar ett batch-jobb via op-proxy och pollrar status tills det är klart.
#
# Användning:
#   ./start-and-wait.sh [OPTIONS] <templateName>
#
# Options:
#   -u, --url <url>            Base-URL till op-proxy (default: http://localhost:8080)
#   -n, --namespace <ns>       Namespace (skickas ej till HTTP-API, visas bara i log)
#   -p, --parameter <n=v>      Template-parameter, kan anges flera gånger
#   -r, --client-request-id <id>  Korrelationsnyckel
#   -t, --timeout-seconds <n>  Job-timeout som sätts i Kubernetes (activeDeadlineSeconds)
#   -i, --poll-interval <n>    Sekunder mellan status-anrop (default: 5)
#   -m, --max-wait <n>         Max sekunder att vänta innan skriptet ger upp (default: 3600)
#   --no-wait                  Returnera direkt efter start utan att vänta på klart
#   -h, --help                 Visa denna hjälp
#
# Returkod:
#   0  SUCCEEDED
#   1  Internt skriptfel
#   2  FAILED
#   3  STOPPED
#   4  CANCELLED eller okänd slutfas
#   5  Timeout – max-wait överskreds

set -euo pipefail

# ---------------------------------------------------------------------------
# Standardvärden
# ---------------------------------------------------------------------------
OP_PROXY_URL="http://localhost:8080"
POLL_INTERVAL=5
MAX_WAIT=3600
NO_WAIT=false
TEMPLATE_NAME=""
CLIENT_REQUEST_ID=""
TIMEOUT_SECONDS=""
declare -a PARAMETERS=()

# ---------------------------------------------------------------------------
# Hjälpfunktioner
# ---------------------------------------------------------------------------
log()  { echo "[$(date -Iseconds)] $*" >&2; }
die()  { log "ERROR: $*"; exit 1; }

usage() {
    grep '^#' "$0" | sed 's/^# \{0,1\}//' | tail -n +2
    exit 0
}

require_cmd() {
    command -v "$1" > /dev/null 2>&1 || die "Kommandot '$1' saknas. Installera det och försök igen."
}

# ---------------------------------------------------------------------------
# Argument-parsning
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        -u|--url)               OP_PROXY_URL="${2:?'--url kräver ett värde'}"; shift 2 ;;
        -n|--namespace)         shift 2 ;;  # accepteras men används ej i HTTP-anropen
        -p|--parameter)         PARAMETERS+=("${2:?'--parameter kräver ett värde'}"); shift 2 ;;
        -r|--client-request-id) CLIENT_REQUEST_ID="${2:?'--client-request-id kräver ett värde'}"; shift 2 ;;
        -t|--timeout-seconds)   TIMEOUT_SECONDS="${2:?'--timeout-seconds kräver ett värde'}"; shift 2 ;;
        -i|--poll-interval)     POLL_INTERVAL="${2:?'--poll-interval kräver ett värde'}"; shift 2 ;;
        -m|--max-wait)          MAX_WAIT="${2:?'--max-wait kräver ett värde'}"; shift 2 ;;
        --no-wait)              NO_WAIT=true; shift ;;
        -h|--help)              usage ;;
        -*)                     die "Okänd flagga: $1" ;;
        *)
            [[ -z "$TEMPLATE_NAME" ]] || die "Oväntat argument: $1"
            TEMPLATE_NAME="$1"
            shift
            ;;
    esac
done

[[ -n "$TEMPLATE_NAME" ]] || die "templateName måste anges."

require_cmd curl
require_cmd jq

# ---------------------------------------------------------------------------
# Bygg JSON-body för start
# ---------------------------------------------------------------------------
build_start_body() {
    local body
    body=$(jq -n '{}')

    if [[ -n "$CLIENT_REQUEST_ID" ]]; then
        body=$(jq --arg v "$CLIENT_REQUEST_ID" '. + {clientRequestId: $v}' <<< "$body")
    fi

    if [[ -n "$TIMEOUT_SECONDS" ]]; then
        body=$(jq --argjson v "$TIMEOUT_SECONDS" '. + {timeoutSeconds: $v}' <<< "$body")
    fi

    if [[ ${#PARAMETERS[@]} -gt 0 ]]; then
        local params_json="[]"
        for param in "${PARAMETERS[@]}"; do
            local name="${param%%=*}"
            local value="${param#*=}"
            [[ "$name" != "$param" ]] || die "Parameter måste ha formatet name=value: '$param'"
            params_json=$(jq --arg n "$name" --arg v "$value" '. + [{name: $n, value: $v}]' <<< "$params_json")
        done
        body=$(jq --argjson p "$params_json" '. + {parameters: $p}' <<< "$body")
    fi

    echo "$body"
}

# ---------------------------------------------------------------------------
# START
# ---------------------------------------------------------------------------
START_URL="${OP_PROXY_URL}/api/templates/${TEMPLATE_NAME}/start"
START_BODY=$(build_start_body)

log "Startar execution för template '${TEMPLATE_NAME}'..."
log "  URL:  $START_URL"
[[ -z "$START_BODY" || "$START_BODY" == "{}" ]] || log "  Body: $START_BODY"

START_RESPONSE=$(curl -sS -w '\n%{http_code}' \
    -X POST \
    -H "Content-Type: application/json" \
    --data "$START_BODY" \
    "$START_URL")

HTTP_BODY=$(sed '$d' <<< "$START_RESPONSE")
HTTP_CODE=$(tail -1  <<< "$START_RESPONSE")

if [[ "$HTTP_CODE" -lt 200 || "$HTTP_CODE" -ge 300 ]]; then
    die "Start misslyckades (HTTP $HTTP_CODE): $HTTP_BODY"
fi

EXECUTION_NAME=$(jq -r '.executionName // empty' <<< "$HTTP_BODY")
[[ -n "$EXECUTION_NAME" ]] || die "Start-svaret saknar executionName. Svar: $HTTP_BODY"

log "Execution startad: $EXECUTION_NAME"
echo "$HTTP_BODY" | jq .

# ---------------------------------------------------------------------------
# Returnera direkt om --no-wait
# ---------------------------------------------------------------------------
if [[ "$NO_WAIT" == "true" ]]; then
    log "--no-wait angiven, avslutar utan att vänta."
    exit 0
fi

# ---------------------------------------------------------------------------
# POLLA STATUS
# ---------------------------------------------------------------------------
STATUS_URL="${OP_PROXY_URL}/api/executions/${EXECUTION_NAME}"
DEADLINE=$(( $(date +%s) + MAX_WAIT ))

log "Pollrar status var ${POLL_INTERVAL}s (max ${MAX_WAIT}s)..."

while true; do
    NOW=$(date +%s)
    if [[ "$NOW" -ge "$DEADLINE" ]]; then
        log "Timeout: max-wait på ${MAX_WAIT}s överskreds för execution '${EXECUTION_NAME}'."
        exit 5
    fi

    STATUS_RESPONSE=$(curl -sS -w '\n%{http_code}' "$STATUS_URL")
    HTTP_BODY=$(sed '$d' <<< "$STATUS_RESPONSE")
    HTTP_CODE=$(tail -1  <<< "$STATUS_RESPONSE")

    if [[ "$HTTP_CODE" -lt 200 || "$HTTP_CODE" -ge 300 ]]; then
        log "Varning: status-anrop returnerade HTTP $HTTP_CODE. Försöker igen om ${POLL_INTERVAL}s."
        sleep "$POLL_INTERVAL"
        continue
    fi

    PHASE=$(jq -r '.phase // "UNKNOWN"' <<< "$HTTP_BODY")
    ELAPSED=$(jq -r '.elapsedSeconds // "?"' <<< "$HTTP_BODY")
    log "  phase=$PHASE  elapsed=${ELAPSED}s"

    case "${PHASE^^}" in
        SUCCEEDED)
            log "Execution '${EXECUTION_NAME}' avslutad med SUCCEEDED."
            echo "$HTTP_BODY" | jq .
            exit 0
            ;;
        FAILED)
            log "Execution '${EXECUTION_NAME}' avslutad med FAILED."
            echo "$HTTP_BODY" | jq .
            exit 2
            ;;
        STOPPED)
            log "Execution '${EXECUTION_NAME}' avslutad med STOPPED."
            echo "$HTTP_BODY" | jq .
            exit 3
            ;;
        CANCELLED)
            log "Execution '${EXECUTION_NAME}' avslutad med CANCELLED."
            echo "$HTTP_BODY" | jq .
            exit 4
            ;;
        RUNNING|PENDING|SUSPENDED)
            ;;  # fortsätt pollra
        *)
            log "Okänd fas '${PHASE}'. Forsätter pollra."
            ;;
    esac

    sleep "$POLL_INTERVAL"
done
