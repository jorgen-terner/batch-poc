#!/bin/sh

set -eu

err() {
  echo "ERROR: $*" >&2
  exit 1
}

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || err "Kunde inte läsa script-katalog"
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd) || err "Kunde inte läsa projektkatalog"

resolve_java() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    printf '%s' "$JAVA_HOME/bin/java"
    return
  fi

  if command -v java >/dev/null 2>&1; then
    command -v java
    return
  fi

  err "java hittades inte. Sätt JAVA_HOME eller lägg java i PATH"
}

resolve_jar() {
  if [ -n "${OP_PROXY_CLIENT_JAR:-}" ]; then
    [ -f "$OP_PROXY_CLIENT_JAR" ] || err "OP_PROXY_CLIENT_JAR pekar på fil som inte finns: $OP_PROXY_CLIENT_JAR"
    printf '%s' "$OP_PROXY_CLIENT_JAR"
    return
  fi

  # Välj senaste byggda jar och undvik *-plain.jar.
  latest_jar=$(ls -1t "$PROJECT_DIR"/build/libs/op-proxy-client-*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -n 1 || true)
  [ -n "$latest_jar" ] || err "Ingen klient-jar hittades. Bygg först: ./gradlew :op-proxy-client:jar"
  printf '%s' "$latest_jar"
}

JAVA_CMD=$(resolve_java)
JAR_FILE=$(resolve_jar)

exec "$JAVA_CMD" ${JAVA_OPTS:-} -jar "$JAR_FILE" "$@"
