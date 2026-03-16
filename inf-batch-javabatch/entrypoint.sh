#!/usr/bin/env sh
set -eu

ACTION="${JOB_ACTION:-restart}"
JOB_ARGS="${JOB_ARGS:-}"
TOKEN="${FKST_TOKEN:-}"
JOB_CONFIG_PATH="${JOB_CONFIG_PATH:-/tmp/examplejob.ini}"

cat > "${JOB_CONFIG_PATH}" <<EOF
[endpoints]
springbatchpy.v2.start=${SPRINGBATCH_V2_START:-}
springbatchpy.v2.status=${SPRINGBATCH_V2_STATUS:-}
springbatchpy.v2.stop=${SPRINGBATCH_V2_STOP:-}
springbatchpy.v2.restart=${SPRINGBATCH_V2_RESTART:-}
springbatchpy.v2.summary=${SPRINGBATCH_V2_SUMMARY:-}
springbatchpy.v2.help=${SPRINGBATCH_V2_HELP:-}
EOF

set -- python /app/example_javabatch.py -j "${JOB_CONFIG_PATH}" "--${ACTION}"

if [ -n "${JOB_ARGS}" ]; then
  set -- "$@" -a "${JOB_ARGS}"
fi

if [ -n "${TOKEN}" ]; then
  set -- "$@" -t "${TOKEN}"
fi

exec "$@"
