# op-proxy-app

Java 21-applikation för att styra batch-körningar i Kubernetes/OpenShift via REST API.

Appen stödjer två modeller:
- Legacy v1: styrning av förskapade suspended Jobs
- Nuvarande v2: template/run-flöde där nya Jobs skapas från ett template-Job

## Teknikval

- Kubernetes integration via Fabric8 Kubernetes Client
- OpenShift Template-processing via Fabric8 OpenShift Client
- HTTP API via Quarkus (REST + CDI)

## Koncept

op-proxy-app innehåller både ett äldre suspended-job-flöde och ett nyare template/run-flöde.

I legacy-v1 utgår appen från att Job redan finns i Kubernetes med:

```yaml
spec:
  suspend: true
```

API:et ändrar sedan Job-state genom att patcha `spec.suspend` och hantera pods.

I v2 används i stället ett befintligt template-Job som källa. Vid varje ny körning kopierar appen template-jobbet, genererar ett nytt `runName`, applicerar parametrar och skapar ett nytt Job i klustret.

## Starta lokalt (Quarkus)

```bash
./gradlew quarkusDev
```

Bygg jar:

```bash
./gradlew build
```

## Deploy till OpenShift

### Manuell deploy

```bash
# Skapa BuildConfig (en gång) - viktigt med --to för att tagga automatiskt!
oc new-build --binary --name=op-proxy --strategy=docker --to=op-proxy:latest -n dev252

# Bygg från modulkatalogen med färdigbyggda artifacts
cd op-proxy-app
oc start-build op-proxy --from-dir=. --follow -n dev252

# Deploya med template (inkluderar Deployment + Service)
cd ..
oc process -f deployment-template.yaml -p NAMESPACE=dev252 | oc apply -f -
```

**3. Applicera RBAC (Role + RoleBinding):**

```bash
oc apply -f rbac-op-proxy-app.yaml
```

### RBAC-rättigheter som behövs

op-proxy-app behöver namespaced RBAC för att kunna styra både legacy suspended Jobs och v2 template/run-körningar:

- `batch/jobs`: `get`, `list`, `watch`, `create`, `update`, `patch`, `delete`
- `core/pods`: `get`, `list`, `watch`, `delete`
- `core/pods/log`: `get`, `list`, `watch`
- `template.openshift.io/templates`: `get`, `list`, `watch` (v2 template-run)
- `template.openshift.io/processedtemplates`: `create` (v2 server-side template processing)


RBAC är utbrutet i separat fil: `rbac-op-proxy-app.yaml`.

### Verifiera deployment

```bash
# Kontrollera status
oc -n batch-jobs get deployment,pods

# Visa logs
oc -n batch-jobs logs -l app=op-proxy-app -f

# Få API-endpoint
oc -n batch-jobs get route op-proxy-app -o jsonpath='{.spec.host}'

# Test hälsostatus
curl https://$(oc -n batch-jobs get route op-proxy-app -o jsonpath='{.spec.host}')/q/health/ready
```

## API (HTTP och CLI)

CLI:t använder samma JobControlService som HTTP-API:t, men utan HTTP-lager.

## Template-baserat API (v2)

Utöver legacy-API:t för suspended Jobs finns nu ett separat v2-API för template-baserade körningar i OpenShift/Kubernetes.

Flödet utgår från en OpenShift Template-resurs i klustret. När klienten startar en execution processar op-proxy-app templaten, använder `metadata.name` från processad template som `executionName`, applicerar eventuella parametrar som env-variabler och skapar ett nytt Job.

Notis: implementationen använder båda API-varianterna. Jobs/pods hanteras via Kubernetes API, medan template-processing i v2 görs via OpenShift Template API (server-side processing med lokal fallback).

Se [TEMPLATE-EXECUTION-API-RFC.md](TEMPLATE-EXECUTION-API-RFC.md) för bakgrund och migreringsidéer. README:n nedan beskriver den aktuella implementationen.

### HTTP-endpoints (v2)

- `POST /api/v2/templates/{templateName}/start`
- `GET /api/v2/executions/{executionName}`
- `POST /api/v2/executions/{executionName}/stop`

### curl-exempel (v2)

```bash
# 1) Start execution
curl -sS -X POST "http://localhost:8080/api/v2/templates/$TEMPLATE_NAME/start" -H "Content-Type: application/json"
  --data-raw '{
    "clientRequestId": "manual-2026-04-23-001",
    "timeoutSeconds": 1800,
    "parameters": [
      { "name": "runType", "value": "FULL" },
      { "name": "businessDate", "value": "2026-04-23" }
    ]
  }'


Läs ut executionName från start-svar

# 2) Execution status
curl -sS -X GET "http://localhost:8080/api/v2/executions/$EXECUTION_NAME"

# 3) Stop execution
curl -sS -X POST "http://localhost:8080/api/v2/executions/$EXECUTION_NAME/stop"
```

### Kontrakt

Start request:
- `clientRequestId`: valfri korrelationsnyckel från klienten
- `timeoutSeconds`: valfri timeout som sätts som `activeDeadlineSeconds`
- `parameters`: valfri lista av `name/value` som skrivs till första containerns env-variabler

Start/stop response (`ExecutionActionResponseVO`) innehåller `namespace`, `templateName`, `executionName`, `clientRequestId`, `action`, `state`, `message` och `createdAt`.

Status response (`ExecutionStatusResponseVO`) innehåller `namespace`, `templateName`, `executionName`, `phase`, pod-räknare och tidsfält.

`executionName` skickas inte in av klienten. Det sätts av processad template (`metadata.name`) och returneras i start-responsen för status- och stop-anrop.

Validering av `parameters` i v2:
- `name` måste vara satt och får inte vara blankt
- `value` måste vara satt (`""` tom sträng är giltigt)
- Dubbel `name` i samma request avvisas

Stop request:
- Ingen request body
- Stop utför alltid graceful stop (suspend), väntar en kort stund på att aktiva pods ska stanna och raderar sedan execution-Jobbet

### Flöde i v2

1. Klienten anropar `POST /api/v2/templates/{templateName}/start`.
2. op-proxy-app processar templaten i samma namespace.
3. `executionName` läses från `metadata.name` i processad template.
4. Ett nytt Job skapas från processad template med labels för template och execution.
5. Klienten följer körningen via `GET /api/v2/executions/{executionName}`.
6. Vid behov stoppar körningen via `POST /api/v2/executions/{executionName}/stop`.

### Metrik för v2

op-proxy-app exponerar inte längre endpoints för att läsa metrics eller ta emot explicita report-anrop.
I stället skickar service-lagret generella Job/Execution-händelser till en intern `JobMetricsReporter`.
Just nu loggas dessa händelser via `slf4j` innan en extern produkt kopplas in.

## Legacy API (v1 suspended Jobs)

### HTTP-endpoints

- `POST /api/v1/jobs/{jobName}/start` (asynkront)
- `POST /api/v1/jobs/{jobName}/stop`
- `POST /api/v1/jobs/{jobName}/restart`
- `GET /api/v1/jobs/{jobName}/status`

REST-API:t är namespace-bundet per appinstans. Namespace läses från `BATCH_JOB_NAMESPACE` och sätts automatiskt från poddens eget namespace i OpenShift-deploymenten.

### Kort om v1

`start` unsuspendar Jobbet och returnerar direkt. Klienten följer sedan körningen via `status` tills `SUCCEEDED` eller `FAILED`.

Parametrar för `start` och `restart`:
- Gemensamma body-fält: `timeoutSeconds` (valfri) och `parameters` (valfri lista med `{ "name": "...", "value": "..." }`).
- Endast för `restart`: `keepFailedPods` i body, default `true`. Styr om terminala pods (`Failed`/`Succeeded`) behålls för felsökning.

Validering av `parameters`:
- `name` måste vara satt och får inte vara blankt.
- `value` måste vara satt (`""` tom sträng är giltigt).
- Dubbel `name` i samma request avvisas.

### CLI-API

CLI:t innehåller nu kommandon för både v1 (legacy suspended Jobs) och v2 (template/execution).

Visa hjälp:

```bash
./gradlew runCli --args="--help"
```

Exempel anrop:

```bash
./gradlew op-proxy-app:runCli --args="--namespace default start sample-batch-job --timeout-seconds 900"
./gradlew op-proxy-app:runCli --args="--namespace default start sample-batch-job --timeout-seconds 900 --parameter runType=FULL --parameter businessDate=2026-04-17"
./gradlew op-proxy-app:runCli --args="--namespace default restart sample-batch-job --timeout-seconds 900 --keep-failed-pods=true"
./gradlew op-proxy-app:runCli --args="--namespace default restart sample-batch-job --timeout-seconds 900 --keep-failed-pods=true --parameter runType=REPROCESS"
./gradlew op-proxy-app:runCli --args="--namespace default restart sample-batch-job --keep-failed-pods=false"
./gradlew op-proxy-app:runCli --args="--namespace default start-execution inv-javabatch-template --timeout-seconds 1800 --parameter businessDate=2026-04-24"
./gradlew op-proxy-app:runCli --args="--namespace default execution-status exec-name-123"
./gradlew op-proxy-app:runCli --args="--namespace default stop-execution exec-name-123"
```

`--parameter` kan anges flera gånger och ska ha formatet `name=value`.
Dubbel parameternyckel i samma CLI-anrop avvisas.

#### v2 (template/execution)

Exempel anrop:

```bash
./gradlew op-proxy-app:runCli --args="--namespace default start-execution sample-batch-job --client-request-id order-4711 --timeout-seconds 900"
./gradlew op-proxy-app:runCli --args="--namespace default start-execution sample-batch-job --parameter runType=FULL --parameter businessDate=2026-04-17"
./gradlew op-proxy-app:runCli --args="--namespace default execution-status sample-batch-job-20260422101500-ab12cd"
./gradlew op-proxy-app:runCli --args="--namespace default execution-status sample-batch-job-20260422101500-ab12cd --watch --interval-seconds 5 --timeout-seconds 900"
./gradlew op-proxy-app:runCli --args="--namespace default stop-execution sample-batch-job-20260422101500-ab12cd"
```

`start-execution` accepterar `--client-request-id`, `--timeout-seconds` och upprepad `--parameter name=value`.
`execution-status` följer samma watch-beteende som v1-status.
`stop-execution` gör alltid graceful stop och raderar execution-Jobbet.

Exit-koder (CI/CD):
- `0` = `SUCCEEDED` eller `STOPPED`
- `10` = `RUNNING` eller `PENDING`
- `2` = `FAILED`
- `3` = `SUSPENDED`
- `4` = `UNKNOWN`
- `124` = timeout i `status --watch`

### Pods vid felsökning

Vid `restart` styr `keepFailedPods` om terminala pods (`Failed`/`Succeeded`) ska behållas (`true`) eller rensas (`false`).
För v2-executions kan poddar efter `FAILED` behållas för felsökning tills cluster-ttl städar dem (styrt av `ttlSecondsAfterFinished` i Job/template).
