#!/bin/ksh
# 20181116 DB
# $0 start/error/stop $$
### MONITOR #######################################################
server="${MONITOR_SERVER:-${HOSTNAME:-unknown}}"
if [[ "$server" == *"prod"* ]]; then
  metricshost=fkmetrics
  exec_db=surv_executing
  history_db=surv_history
else
  metricshost=metricstest
  exec_db=davve
  history_db=davve
fi
environment="${MONITOR_ENVIRONMENT:-jbatch}"
state="${1:-start}"
PID="${2:-$$}"
starttime="${MONITOR_START_TIME:-unknown}"
elapsed="${MONITOR_ELAPSED:-00:00:00}"
object="${MONITOR_OBJECT:-${TO_JOB_NAME:-${JOB_NAME:-unknown_job}}}"
chart="${MONITOR_CHART:-${TO_ENV_NAME:-MANUELL}}"
monitor_user="${MONITOR_USER:-${LOGNAME:-${USERNAME:-unknown}}}"

monitor()       {
  case $state in
    start)
      objectstatus=Executing
      statusflag=0
      influxdb=$exec_db
      ;;
    error)
      objectstatus=Failed
      statusflag=1
      influxdb=$history_db
      ;;
    stop)
      objectstatus=Completed
      statusflag=2
      influxdb=$history_db
      ;;
    *)
      objectstatus=Unknown
      statusflag=3
      influxdb=$history_db
      ;;
  esac

  curl -s -XPOST "http://${metricshost}.sfa.se:8086/query?db=${exec_db}" \
    --data-binary "q=DROP SERIES from exec_job where JOB='${object}'" >/dev/null || true

  curl -s -XPOST "http://${metricshost}.sfa.se:8086/write?db=${influxdb}" \
    --data-ascii "exec_job,JOB=${object} Object=\"${object}\",Start_time=\"${starttime}\",Status=\"${objectstatus}\",PID=${PID},User=\"${monitor_user}\",Server=\"${server}\",Chart=\"${chart}\",Environment=\"${environment}\",Elapsed=\"${elapsed}\",Status_flag=${statusflag}" >/dev/null || true
}

monitor
