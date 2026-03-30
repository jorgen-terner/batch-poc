# batch-job-javabatch

Denna mapp innehaller anpassning for att kora befintliga javabatch-skript i modellen med suspended Kubernetes Job som styrs av batch-job-app.

## Filer

- springbatch_v2.py: Befintligt Python-skript som startar/stoppar/statusar extern Spring Batch-tjanst.
- monitor_jbatch.sh: Befintligt monitor-skript (legacy).
- javabatch_worker.sh: Container-entrypoint som kor springbatch_v2.py i Kubernetes Job och rapporterar minimal status till batch-job-app.
- springbatch_job.ini: Endpoint-konfiguration for springbatch_v2.py.
- suspended-job-openshift.yaml: Suspended Job-definition (`spec.suspend=true`) for OpenShift/Kubernetes.
- setup-openshift-javabatch.sh: Skapar ConfigMap fran skriptfilerna och ater-skapar Job med korrekta miljo-variabler direkt i manifestet.
- batch-job-control.sh: Klient mot batch-job-app API (start/stop/restart/status/metrics).

## Granssnitt mot batch-job-app

batch-job-app styr Job-resursen via Kubernetes:
- start: unsuspend (`spec.suspend=false`)
- stop: suspend + pod-delete
- restart: delete/recreate av Job

Jobbet kan skicka report till:
- `POST /api/v1/jobs/{namespace}/{jobName}/report`

Report ar frivillig. `status` och `metrics` fungerar aven utan report eftersom appen primart anvander Kubernetes Job/Pod-status.

## Snabbstart

1. Uppdatera endpointar i springbatch_job.ini.
2. Skapa suspended Job i klustret:
   ./setup-openshift-javabatch.sh <namespace> <job-name> <batch-app-base-url> [ini-file]
3. Starta via batch-job-app:
   ./batch-job-control.sh start <namespace> <job-name> <batch-job-app-url>
4. Las status/metrics:
   ./batch-job-control.sh status <namespace> <job-name> <batch-job-app-url>
   ./batch-job-control.sh metrics <namespace> <job-name> <batch-job-app-url>

## Viktiga miljo-variabler i Job

- JOB_NAMESPACE, JOB_NAME
- BATCH_APP_BASE_URL
- JOB_CONFIG
- JOB_ARGS
- FKST_TOKEN
- ENABLE_PROGRESS_REPORT: satt till `false` som default (minimal rapportering)

Satt `ENABLE_PROGRESS_REPORT=true` om du vill skicka periodiska RUNNING-rapporter under korning.
