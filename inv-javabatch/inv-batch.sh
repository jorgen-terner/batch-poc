#!/bin/sh
JAR_PATH="${QUARKUS_RUN_JAR:-/produkter/tools/inf_poc_javabatch_api/bin/quarkus-run.jar}"

# Guard against accidental empty value from environment.
if [ -z "${JAR_PATH}" ]; then
	JAR_PATH="/produkter/tools/inf_poc_javabatch_api/bin/quarkus-run.jar"
fi

# Runtime diagnostics in pod logs when startup fails quickly.
echo "[inv-batch] QUARKUS_RUN_JAR=${QUARKUS_RUN_JAR:-<not set>}"
echo "[inv-batch] JAR_PATH=${JAR_PATH}"
id || true
ls -ld /produkter /produkter/tools /produkter/tools/inf_poc_javabatch_api /produkter/tools/inf_poc_javabatch_api/bin 2>/dev/null || true
ls -la /produkter/tools/inf_poc_javabatch_api/bin 2>/dev/null || echo "[inv-batch] Missing /produkter/tools/inf_poc_javabatch_api/bin"
ls -la /opt/inf-javabatch 2>/dev/null || echo "[inv-batch] Missing /opt/inf-javabatch"

if [ ! -e "${JAR_PATH}" ]; then
	echo "[inv-batch] Jar path does not exist: ${JAR_PATH}"
	find / -maxdepth 4 -name quarkus-run.jar 2>/dev/null || true
	exit 2
fi

ls -l "${JAR_PATH}" 2>/dev/null || true
if command -v file >/dev/null 2>&1; then
	file "${JAR_PATH}" || true
fi

if [ ! -r "${JAR_PATH}" ]; then
	echo "[inv-batch] Jar exists but is not readable by current user"
	exit 2
fi

# Replace the shell with the JVM so TERM/INT from batch-wrapper reaches Quarkus directly.
exec java ${JAVA_OPTS:-} -jar "${JAR_PATH}"
