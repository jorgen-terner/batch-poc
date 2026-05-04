#!/bin/sh

die() {
  echo "$1" >&2
  exit 1
}

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || die "Could not resolve script directory"
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd) || PROJECT_DIR="$SCRIPT_DIR/.."

APP_HOME=${OP_PROXY_CLI_HOME:-}
if [ -z "$APP_HOME" ] && [ -d "/deployments/app" ] && [ -d "/deployments/lib" ]; then
  APP_HOME=/deployments
fi
if [ -z "$APP_HOME" ]; then
  APP_HOME=$(CDPATH= cd -- "$SCRIPT_DIR/../build/quarkus-app" 2>/dev/null && pwd || true)
fi

if [ -z "$APP_HOME" ] || [ ! -d "$APP_HOME/app" ] || [ ! -d "$APP_HOME/lib" ] || [ ! -d "$APP_HOME/quarkus" ]; then
  die "Could not find Quarkus fast-jar layout. Set OP_PROXY_CLI_HOME to the directory containing app/, lib/, quarkus/ and quarkus-run.jar."
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD=java
  if [ -f "$PROJECT_DIR/gradle.properties" ]; then
    GRADLE_JAVA_HOME=$(sed -n 's/^org\.gradle\.java\.home=//p' "$PROJECT_DIR/gradle.properties" | head -n 1)
    if [ -n "${GRADLE_JAVA_HOME:-}" ] && [ -x "$GRADLE_JAVA_HOME/bin/java" ]; then
      JAVA_CMD="$GRADLE_JAVA_HOME/bin/java"
    fi
  fi
fi

CLASSPATH="$APP_HOME/app/*:$APP_HOME/quarkus/*:$APP_HOME/lib/main/*:$APP_HOME/lib/boot/*"

exec "$JAVA_CMD" \
  ${JAVA_OPTS:-} \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -cp "$CLASSPATH" \
  infrastruktur.batch.cli.BatchJobCli \
  "$@"