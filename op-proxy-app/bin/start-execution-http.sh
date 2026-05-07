#!/bin/sh


usage() {
  cat <<'EOF'
Startar en execution via op-proxy-app HTTP-API och pollar status tills terminalt läge.

Användning:
  start-execution-http.sh --template TEMPLATE_NAME [flaggor]

Flaggor:
  --template NAME              Template-namn (krävs)
  --base-url URL               API-basurl (default: http://localhost:8080 eller OP_PROXY_BASE_URL)
  --client-request-id ID       Valfritt clientRequestId till start-anropet
  --timeout-seconds N          Valfritt timeoutSeconds till start-anropet
  --parameter KEY=VALUE        Template-parameter (kan anges flera gånger)
  --interval-seconds N         Poll-intervall för status (default: 5)
  --watch-timeout-seconds N    Max tid att vaka innan timeout (default: 3600, 0 = ingen timeout)
  --insecure                   Tillåt osäkert TLS-certifikat (curl -k)
  --help                       Visa hjälp

Exit-koder:
  0   SUCCEEDED eller STOPPED
  10  RUNNING eller PENDING (används inte i watch-läget, men finns för kompatibilitet)
  2   FAILED
  3   SUSPENDED
  4   UNKNOWN eller oväntat läge
  124 Timeout i watch-läge
  1   Fel i skript/anrop/validering
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Kommando saknas: $1"
}

is_non_negative_int() {
  case "$1" in
    ""|*[!0-9]*)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

phase_to_exit_code() {
  phase="$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')"

  case "$phase" in
    SUCCEEDED|STOPPED)
      echo 0
      ;;
    RUNNING|PENDING)
      echo 10
      ;;
    FAILED)
      echo 2
      ;;
    SUSPENDED)
      echo 3
      ;;
    UNKNOWN|"")
      echo 4
      ;;
    *)
      echo 4
      ;;
  esac
}

api_request() {
  method="$1"
  url="$2"
  body="${3-}"

  insecure_arg=""
  if [ "$INSECURE_TLS" = "true" ]; then
    insecure_arg="-k"
  fi

  if [ -n "$body" ]; then
    response="$(curl $insecure_arg --silent --show-error --request "$method" --header "Accept: application/json" --header "Content-Type: application/json" --data "$body" --write-out "\n%{http_code}" "$url")" || die "HTTP-anrop misslyckades: $method $url"
  else
    response="$(curl $insecure_arg --silent --show-error --request "$method" --header "Accept: application/json" --write-out "\n%{http_code}" "$url")" || die "HTTP-anrop misslyckades: $method $url"
  fi

  http_code=$(printf '%s' "$response" | awk 'END{print}')
  response_body=$(printf '%s' "$response" | sed '$d')

  if ! is_non_negative_int "$http_code" || [ "${#http_code}" -ne 3 ]; then
    die "Kunde inte läsa HTTP-status från svar: $method $url"
  fi

  if [ "$http_code" -lt 200 ] || [ "$http_code" -ge 300 ]; then
    echo "HTTP $http_code från $method $url" >&2
    echo "Svar: $response_body" >&2
    return 1
  fi

  printf '%s' "$response_body"
}

BASE_URL="${OP_PROXY_BASE_URL:-http://localhost:8080}"
TEMPLATE_NAME=""
CLIENT_REQUEST_ID=""
START_TIMEOUT_SECONDS=""
INTERVAL_SECONDS=5
WATCH_TIMEOUT_SECONDS=3600
INSECURE_TLS=false
PARAMETERS_JSON='[]'

while [ $# -gt 0 ]; do
  case "$1" in
    --template)
      [ $# -ge 2 ] || die "--template kräver ett värde"
      TEMPLATE_NAME="$2"
      shift 2
      ;;
    --base-url)
      [ $# -ge 2 ] || die "--base-url kräver ett värde"
      BASE_URL="$2"
      shift 2
      ;;
    --client-request-id)
      [ $# -ge 2 ] || die "--client-request-id kräver ett värde"
      CLIENT_REQUEST_ID="$2"
      shift 2
      ;;
    --timeout-seconds)
      [ $# -ge 2 ] || die "--timeout-seconds kräver ett värde"
      START_TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --parameter)
      [ $# -ge 2 ] || die "--parameter kräver KEY=VALUE"
      pair="$2"
      case "$pair" in
        *=*)
          ;;
        *)
          die "Felaktigt --parameter-värde '$pair'. Förväntat format: KEY=VALUE"
          ;;
      esac

      key="${pair%%=*}"
      value="${pair#*=}"
      [ -n "$key" ] || die "Parameternyckel får inte vara tom i '$pair'"

      PARAMETERS_JSON="$(jq -cn \
        --argjson arr "$PARAMETERS_JSON" \
        --arg k "$key" \
        --arg v "$value" \
        '$arr + [{name:$k, value:$v}]')" || die "Kunde inte bygga parameterlista med jq"
      shift 2
      ;;
    --interval-seconds)
      [ $# -ge 2 ] || die "--interval-seconds kräver ett värde"
      INTERVAL_SECONDS="$2"
      shift 2
      ;;
    --watch-timeout-seconds)
      [ $# -ge 2 ] || die "--watch-timeout-seconds kräver ett värde"
      WATCH_TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --insecure)
      INSECURE_TLS=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      die "Okänd flagga: $1"
      ;;
  esac
done

[ -n "$TEMPLATE_NAME" ] || die "--template är obligatorisk"
is_non_negative_int "$INTERVAL_SECONDS" || die "--interval-seconds måste vara ett heltal >= 0"
is_non_negative_int "$WATCH_TIMEOUT_SECONDS" || die "--watch-timeout-seconds måste vara ett heltal >= 0"
if [ -n "$START_TIMEOUT_SECONDS" ]; then
  is_non_negative_int "$START_TIMEOUT_SECONDS" || die "--timeout-seconds måste vara ett heltal >= 0"
fi

require_cmd curl
require_cmd jq

BASE_URL="${BASE_URL%/}"

start_payload="$(jq -cn \
  --arg clientRequestId "$CLIENT_REQUEST_ID" \
  --arg timeoutSeconds "$START_TIMEOUT_SECONDS" \
  --argjson parameters "$PARAMETERS_JSON" \
  '{
    clientRequestId: (if $clientRequestId == "" then null else $clientRequestId end),
    timeoutSeconds: (if $timeoutSeconds == "" then null else ($timeoutSeconds | tonumber) end),
    parameters: (if ($parameters | length) == 0 then null else $parameters end)
  } | with_entries(select(.value != null))')" || die "Kunde inte bygga start-payload med jq"

start_url="$BASE_URL/api/templates/$TEMPLATE_NAME/start"
start_response="$(api_request POST "$start_url" "$start_payload")"

execution_name="$(printf '%s' "$start_response" | jq -r '.executionName // empty')" || die "Kunde inte läsa executionName från start-svar"
[ -n "$execution_name" ] || die "start-svaret saknar executionName. Svar: $start_response"

echo "Körning startad: $execution_name"

started_epoch="$(date +%s)"
status_url="$BASE_URL/api/executions/$execution_name"

while true; do
  status_response="$(api_request GET "$status_url")"
  phase="$(printf '%s' "$status_response" | jq -r '.phase // "UNKNOWN"' | tr '[:lower:]' '[:upper:]')" || die "Kunde inte läsa fas från status-svar"
  exit_code="$(phase_to_exit_code "$phase")"

  echo "Status: fas=$phase executionName=$execution_name"

  case "$exit_code" in
    0|2|3|4)
      exit "$exit_code"
      ;;
  esac

  if [ "$WATCH_TIMEOUT_SECONDS" -gt 0 ]; then
    now_epoch="$(date +%s)"
    elapsed="$((now_epoch - started_epoch))"
    if [ "$elapsed" -ge "$WATCH_TIMEOUT_SECONDS" ]; then
      echo "Timeout efter ${WATCH_TIMEOUT_SECONDS}s medan status pollades (senaste fas=$phase)" >&2
      exit 124
    fi
  fi

  sleep "$INTERVAL_SECONDS"
done
