#!/bin/sh
# Skriv debug-info både till stderr och loggfil för att säkerställa synlighet
DEBUG_LOG="/tmp/inv-batch.log"

{
  echo "[inv-batch.sh] Started at $(date)"
  echo "[inv-batch.sh] JAR_PATH=${QUARKUS_RUN_JAR:-/deployments/quarkus-run.jar}"
  echo "[inv-batch.sh] JAVA_OPTS=${JAVA_OPTS:-<not set>}"
} | tee -a "${DEBUG_LOG}" >&2

JAR_PATH="${QUARKUS_RUN_JAR:-/deployments/quarkus-run.jar}"
if [ ! -r "${JAR_PATH}" ]; then
  {
    echo "ERROR: Jarfilen kunde inte lasas: ${JAR_PATH}"
    echo "Innehall i /deployments:"
    ls -la /deployments 2>&1 || true
  } | tee -a "${DEBUG_LOG}" >&2
  exit 1
fi

echo "[inv-batch.sh] Launching java..." | tee -a "${DEBUG_LOG}" >&2
exec java ${JAVA_OPTS:-} -jar "${JAR_PATH}"
