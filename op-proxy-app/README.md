# op-proxy-app

Java 21-applikation för att styra förskapade Kubernetes Jobs (suspended Jobs) via REST API.

## Teknikval

- Gradle Wrapper `9.0.0`
- Java Toolchain `21`
- Logging via `SLF4J` (med Logback)
- Kubernetes integration via Fabric8 Kubernetes Client
- OpenShift Template-processing via Fabric8 OpenShift Client
- HTTP API via Quarkus (REST + CDI)

## Koncept

I stället för att skapa nya Jobs med image/version vid varje körning, utgår appen från att Job redan finns i Kubernetes med:

```yaml
spec:
  suspend: true
```

API:et ändrar sedan Job-state genom att patcha `spec.suspend` och hantera pods.

## Starta lokalt (Quarkus)

```bash
./gradlew quarkusDev
```

Bygg jar:

```bash
./gradlew build
```

## Deploy till OpenShift

### Förutsättningar

- OpenShift CLI (`oc`) installerad och konfigurerad
- Inloggad i OpenShift-klustret: `oc login <cluster-url>`

### Automatisk deploy (rekommenderat)

Använd deploy-scriptet för att bygga och deploya appen:

```bash
chmod +x ./setup-openshift.sh
./setup-openshift.sh [NAMESPACE] [IMAGE_TAG]
```

**Exempel:**

```bash
# Deploy till standardnamespace med tag 'latest'
./setup-openshift.sh batch-jobs latest

# Deploy till namespace 'production' med version 'v0.1.0'
./setup-openshift.sh production v0.1.0
```

Scriptet gör följande:
1. Bygger appen lokalt (`./gradlew build`)
2. Skapar namespace (om det inte finns)
3. Bygger containerimagen via OpenShift BuildConfig
4. Applicerar ServiceAccount och RBAC från `rbac-op-proxy-app.yaml`
5. Deployar op-proxy-app med Service och Route

### Manuell deploy

**1. Bygga jar lokalt:**

```bash
./gradlew clean build
```

**2. Bygg image via OpenShift BuildConfig:**

```bash
# Skapa BuildConfig (en gång)
oc -n batch-jobs new-build --binary --name=op-proxy-app --strategy=docker --to=op-proxy-app:latest

# Säkerställ att builden använder Dockerfile i denna modul
oc -n batch-jobs patch bc/op-proxy-app -p '{"spec":{"strategy":{"dockerStrategy":{"dockerfilePath":"op-proxy-app/Dockerfile"}}}}'

# Starta build från repo-roten
oc -n batch-jobs start-build op-proxy-app --from-dir=. --follow
```

**3. Applicera RBAC (ServiceAccount + Role + RoleBinding):**

```bash
oc -n batch-jobs apply -f rbac-op-proxy-app.yaml
```

**4. Deploy via template:**

```bash
oc new-project batch-jobs 2>/dev/null || oc project batch-jobs

oc process -f deployment-template.yaml \
  -p NAMESPACE=batch-jobs \
  -p SERVICE_ACCOUNT_NAME=duplosa \
  -p IMAGE_TAG=latest | oc apply -f -
```

### RBAC-rättigheter som behövs

op-proxy-app behöver namespaced RBAC för att kunna styra suspended Jobs:

- `batch/jobs`: `get`, `list`, `watch`, `create`, `update`, `patch`, `delete`
- `core/pods`: `get`, `list`, `watch`, `delete`
- `core/pods/log`: `get`, `list`, `watch`
- `template.openshift.io/templates`: `get`, `list`, `watch` (v2 template-execution)
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

### Använda deploy-mallen manuellt

Deployment-mallen `deployment-template.yaml` kan användas för att deploya med egna parametrar:

```bash
oc process -f deployment-template.yaml \
  -p NAMESPACE=production \
  -p SERVICE_ACCOUNT_NAME=duplosa \
  -p IMAGE_TAG=v0.1.0 \
  -p CPU_LIMIT=1000m \
  -p MEMORY_LIMIT=1Gi | oc apply -f -

# Skala upp vid behov
oc -n production scale deployment/op-proxy-app --replicas=2
```

**Tillgängliga parametrar:**
- `NAMESPACE` - Target namespace (default: batch-jobs)
- `IMAGE_TAG` - Bildversion (default: latest)
- `SERVICE_ACCOUNT_NAME` - ServiceAccount som deploymenten kör som (default: duplosa)
- `CPU_REQUEST` - CPU request (default: 100m)
- `CPU_LIMIT` - CPU limit (default: 500m)
- `MEMORY_REQUEST` - Memory request (default: 256Mi)
- `MEMORY_LIMIT` - Memory limit (default: 512Mi)

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
# Sätt endpoint och resurser
NAMESPACE=dev252
BASE_URL="https://$(oc -n ${NAMESPACE} get route op-proxy-app -o jsonpath='{.spec.host}')"
TEMPLATE_NAME="inv-javabatch-template"

# 1) Start execution
START_RESPONSE=$(curl -sS -X POST "$BASE_URL/api/v2/templates/$TEMPLATE_NAME/start" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data-raw '{
    "clientRequestId": "manual-2026-04-23-001",
    "timeoutSeconds": 1800,
    "parameters": [
      { "name": "runType", "value": "FULL" },
      { "name": "businessDate", "value": "2026-04-23" }
    ]
  }')

echo "$START_RESPONSE"

# Läs ut executionName från start-svar (kräver jq)
EXECUTION_NAME=$(echo "$START_RESPONSE" | jq -r '.executionName')

# 2) Execution status
curl -sS -X GET "$BASE_URL/api/v2/executions/$EXECUTION_NAME" \
  -H "Accept: application/json"

# 3) Stop execution
curl -sS -X POST "$BASE_URL/api/v2/executions/$EXECUTION_NAME/stop" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data-raw '{
    "deletePods": false
  }'
```

Om du inte vill använda `jq` kan du kopiera `executionName` manuellt från start-responsen och använda den i status- och stop-anropen.

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
- `deletePods=true`: graceful stop (suspend + terminera aktiva pods), radera sedan Job och kvarvarande pods
- `deletePods=false` eller tom body: graceful stop, radera Job och behåll aktiva och terminala pods

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
./gradlew runCli --args="--namespace default start sample-batch-job --timeout-seconds 900"
./gradlew runCli --args="--namespace default start sample-batch-job --timeout-seconds 900 --parameter runType=FULL --parameter businessDate=2026-04-17"
./gradlew runCli --args="--namespace default restart sample-batch-job --timeout-seconds 900 --keep-failed-pods=true"
./gradlew runCli --args="--namespace default restart sample-batch-job --timeout-seconds 900 --keep-failed-pods=true --parameter runType=REPROCESS"
./gradlew runCli --args="--namespace default restart sample-batch-job --keep-failed-pods=false"
./gradlew runCli --args="--namespace default start-execution inv-javabatch-template --timeout-seconds 1800 --parameter businessDate=2026-04-24"
./gradlew runCli --args="--namespace default execution-status exec-name-123"
./gradlew runCli --args="--namespace default stop-execution exec-name-123 --delete-pods=false"
```

`--parameter` kan anges flera gånger och ska ha formatet `name=value`.
Dubbel parameternyckel i samma CLI-anrop avvisas.

#### v2 (template/execution)

Exempel anrop:

```bash
./gradlew runCli --args="--namespace default start-execution sample-batch-job --client-request-id order-4711 --timeout-seconds 900"
./gradlew runCli --args="--namespace default start-execution sample-batch-job --parameter runType=FULL --parameter businessDate=2026-04-17"
./gradlew runCli --args="--namespace default execution-status sample-batch-job-20260422101500-ab12cd"
./gradlew runCli --args="--namespace default execution-status sample-batch-job-20260422101500-ab12cd --watch --interval-seconds 5 --timeout-seconds 900"
./gradlew runCli --args="--namespace default stop-execution sample-batch-job-20260422101500-ab12cd"
./gradlew runCli --args="--namespace default stop-execution sample-batch-job-20260422101500-ab12cd --delete-pods=true"
```

`start-execution` accepterar `--client-request-id`, `--timeout-seconds` och upprepad `--parameter name=value`.
`execution-status` följer samma watch-beteende som v1-status.
`stop-execution` gör graceful stop som default och bevarar aktiva och terminala pods. Med `--delete-pods=true` raderas även kvarvarande pods.

Exit-koder (CI/CD):
- `0` = `SUCCEEDED` eller `STOPPED`
- `10` = `RUNNING` eller `PENDING`
- `2` = `FAILED`
- `3` = `SUSPENDED`
- `4` = `UNKNOWN`
- `124` = timeout i `status --watch`

### Pods vid felsökning

Vid `stop` bevaras pods som default.
Vid `stop --delete-pods=true` rensas aktiva och terminala pods.
Vid `restart` styr `keepFailedPods` om terminala pods (`Failed`/`Succeeded`) ska behållas (`true`) eller rensas (`false`).
