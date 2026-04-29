#!/bin/bash
set -e

if [ $# -lt 1 ]; then
   echo "Usage: $0 <batch-job-script> [args...]"
   exit 1
fi


echo "========== Job Info =========="

# Job name 
if [ -n "$JOB_NAME" ]; then
    echo "Kubernetes Job Name: $JOB_NAME"
else
    echo "Kubernetes Job Name: (not set, expected in \$JOB_NAME)"
    exit 1
fi

# Job UID
if [ -n "$JOB_UID" ]; then
    echo "Kubernetes Job UID: $JOB_UID"
else
    echo "Kubernetes Job UID: (not set, expected in \$JOB_UID)"
    exit 1
fi

echo "========================================="

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

BATCH_SCRIPT="$1"
shift

echo "Starting metrics-app in background..."
"$SCRIPT_DIR/batch-metrics-app" &
METRICS_PID=$!


echo "Waiting for metrics-app to be ready on port 8080..."
MAX_RETRIES=30
COUNT=0

while ! curl -s http://localhost:8080/ > /dev/null; do
    sleep 1
    COUNT=$((COUNT + 1))
    if [ $COUNT -ge $MAX_RETRIES ]; then
        echo "Error: metrics-app timed out and never started!"
        exit 1
    fi
done

echo "Metrics-app server is up! Curling /start endpoint..."
curl -s -X POST -H "Content-Type: application/json" \
     -d "{\"job_name\": \"$JOB_NAME\", \"job_uid\": \"$JOB_UID\"}" \
     http://localhost:8080/start || echo "Warning: Failed to reach /start endpoint!"


# ... (tidigare kod för start av server osv) ...

# Variabler för att hålla koll på status
FAIL_MESSAGE=""
EXIT_STATUS=0
ALREADY_DONE=false
BATCH_PID=""

function forward_signal {
    local signal_name="$1"
    echo "Received ${signal_name}, forwarding to batch job..."

    # När wrappern kör som PID 1 i en container måste signalen skickas vidare manuellt.
    if [ -n "$BATCH_PID" ] && kill -0 "$BATCH_PID" 2>/dev/null; then
        kill -s "$signal_name" "$BATCH_PID" 2>/dev/null || true
        set +e
        wait "$BATCH_PID"
        EXIT_STATUS=$?
        set -e
    else
        EXIT_STATUS=143
    fi

    FAIL_MESSAGE="Batch job terminated by signal ${signal_name}"

    # Låt on_exit hantera /stop och cleanup.
    exit "$EXIT_STATUS"
}

function on_exit {
    # Förhindra att funktionen körs flera gånger (t.ex. vid både fel och slut)
    if [ "$ALREADY_DONE" = "true" ]; then
        return
    fi
    ALREADY_DONE=true

    local stopped_time
    stopped_time=$(date -Iseconds)

    local batch_status="ok"
    if [ "$EXIT_STATUS" -ne 0 ]; then
        batch_status="failed"
        echo "Batch script failed with exit code $EXIT_STATUS"
    else
        echo "Batch script succeeded."
    fi

    # Skapa JSON
    local json_payload
    json_payload=$(cat <<EOF
{
  "batch_stopped": "$stopped_time",
  "batch_stop_status": "$batch_status",
  "batch_stop_fail_message": "$FAIL_MESSAGE"
}
EOF
)

    echo "Curling /stop endpoint..."
    curl -s -X POST -H "Content-Type: application/json" --data "$json_payload" http://localhost:8080/stop || echo "Failed to reach /stop endpoint!"

    echo "Stopping metrics-app gracefully..."
    kill -15 $METRICS_PID 2>/dev/null || true

    # Vänta max 5 sek på att den dör
    local count=0
    while kill -0 $METRICS_PID 2>/dev/null && [ $count -lt 5 ]; do
        sleep 1
        count=$((count + 1))
    done

    # Force kill om den fortfarande lever
    kill -9 $METRICS_PID 2>/dev/null || true
    echo "Cleanup complete."
}

# Registrera trap
trap 'on_exit' EXIT
trap 'forward_signal TERM' TERM
trap 'forward_signal INT' INT

echo "Starting your batch job: $BATCH_SCRIPT $@"

# Inaktivera 'set -e' tillfälligt för att manuellt fånga exit-koden
# och kunna vänta på child-processen även när signaler avbryter wait.
set +e
bash "$BATCH_SCRIPT" "$@" &
BATCH_PID=$!

wait "$BATCH_PID"
EXIT_STATUS=$?
set -e

if [ $EXIT_STATUS -ne 0 ] && [ -z "$FAIL_MESSAGE" ]; then
    FAIL_MESSAGE="Batch job failed with exit code $EXIT_STATUS"
fi

# Detta triggar nu on_exit EN gång med rätt EXIT_STATUS
exit $EXIT_STATUS
