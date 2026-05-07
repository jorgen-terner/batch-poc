# op-proxy-app

Java 21-applikation för att styra batch-körningar i Kubernetes/OpenShift via REST API.

Appen stödjer template/run-flöde, där nya Jobs skapas från ett template-Job.

## Teknikval

- Kubernetes integration via Fabric8 Kubernetes Client
- OpenShift Template-processing via Fabric8 OpenShift Client
- HTTP API via Quarkus (REST + CDI)

## Koncept

Här används ett befintligt template-Job som källa. Vid varje ny körning kopierar appen template-jobbet, genererar ett nytt `runName`, applicerar parametrar och skapar ett nytt Job i klustret.

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

op-proxy-app behöver namespaced RBAC för att kunna styra template/run-körningar:

- `batch/jobs`: `get`, `list`, `watch`, `create`, `update`, `patch`, `delete`
- `core/pods`: `get`, `list`, `watch`, `delete`
- `core/pods/log`: `get`, `list`, `watch`
- `template.openshift.io/templates`: `get`, `list`, `watch` (template-run)
- `template.openshift.io/processedtemplates`: `create` (server-side template processing)


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

CLI:t använder samma service som HTTP-API:t, men utan HTTP-lager.

## Loggnivå via miljövariabel

Du kan styra loggnivån med miljövariabeln `LOG_LEVEL`.

Tillåtna vanliga värden är t.ex. `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.

Exempel i OpenShift (deployment):

```bash
oc -n batch-jobs set env deployment/op-proxy-app LOG_LEVEL=DEBUG
```

Exempel för lokal start:

```bash
LOG_LEVEL=DEBUG ./gradlew quarkusDev
```

## Template-baserat API

API:t används för template-baserade körningar i OpenShift/Kubernetes.

Flödet utgår från en OpenShift Template-resurs i klustret. När klienten startar en execution processar op-proxy-app templaten, använder `metadata.name` från processad template som `executionName`, applicerar eventuella parametrar som template-parametrar och skapar ett nytt Job.

Notis: implementationen använder båda API-varianterna. Jobs/pods hanteras via Kubernetes API, medan template-processing görs via OpenShift Template API (server-side processing med lokal fallback).

Se [TEMPLATE-EXECUTION-API-RFC.md](TEMPLATE-EXECUTION-API-RFC.md) för bakgrund och migreringsidéer. README:n nedan beskriver den aktuella implementationen.

### HTTP-endpoints

- `POST /api/templates/{templateName}/start`
- `GET /api/executions/{executionName}`
- `POST /api/executions/{executionName}/stop`

### curl-exempel

```bash
# 1) Start execution
curl -sS -X POST "http://localhost:8080/api/templates/$TEMPLATE_NAME/start" -H "Content-Type: application/json"
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
curl -sS -X GET "http://localhost:8080/api/executions/$EXECUTION_NAME"

# 3) Stop execution
curl -sS -X POST "http://localhost:8080/api/executions/$EXECUTION_NAME/stop"
```

### Kontrakt

Start request:
- `clientRequestId`: valfri korrelationsnyckel från klienten
- `timeoutSeconds`: valfri timeout som sätts som `activeDeadlineSeconds`
- `parameters`: valfri lista av `name/value` som skickas som OpenShift template-parametrar vid processering av templaten

Start/stop response (`ExecutionActionResponseVO`) innehåller `namespace`, `templateName`, `executionName`, `clientRequestId`, `action`, `state`, `message` och `createdAt`.

Status response (`ExecutionStatusResponseVO`) innehåller `namespace`, `templateName`, `executionName`, `phase`, pod-räknare och tidsfält.

`executionName` skickas inte in av klienten. Det sätts av processad template (`metadata.name`) och returneras i start-responsen för status- och stop-anrop.

Validering av `parameters`:
- `name` måste vara satt och får inte vara blankt
- `value` måste vara satt (`""` tom sträng är giltigt)
- Dubbel `name` i samma request avvisas

Notis: `parameters` används enbart som template-parametrar vid processering av OpenShift Template. De injiceras inte som separata env-overrides efter att templaten processats.

Stop request:
- Ingen request body
- Stop utför alltid graceful stop (suspend), väntar en kort stund på att aktiva pods ska stanna och raderar sedan execution-Jobbet

### Flöde

1. Klienten anropar `POST /api/templates/{templateName}/start`.
2. op-proxy-app processar templaten i samma namespace.
3. `executionName` läses från `metadata.name` i processad template.
4. Ett nytt Job skapas från processad template med labels för template och execution.
5. Klienten följer körningen via `GET /api/executions/{executionName}`.
6. Vid behov stoppar körningen via `POST /api/executions/{executionName}/stop`.

### Metrik

op-proxy-app exponerar inte längre endpoints för att läsa metrics eller ta emot explicita report-anrop.
I stället skickar service-lagret generella Job/Execution-händelser till en intern `JobMetricsReporter`.
Just nu loggas dessa händelser via `slf4j` innan en extern produkt kopplas in.

### CLI-API

CLI:t innehåller kommandon för template/execution.

Visa hjälp:

```bash
./gradlew runCli --args="--help"
```

Exempel anrop:

```bash
./gradlew op-proxy-app:runCli --args="--namespace default start-execution inv-javabatch-template --timeout-seconds 1800 --parameter businessDate=2026-04-24"
./gradlew op-proxy-app:runCli --args="--namespace default execution-status exec-name-123"
./gradlew op-proxy-app:runCli --args="--namespace default stop-execution exec-name-123"
```

`--parameter` kan anges flera gånger och ska ha formatet `name=value`.
Dubbel parameternyckel i samma CLI-anrop avvisas.

#### Template/execution

Exempel anrop:

```bash
./gradlew op-proxy-app:runCli --args="--namespace default start-execution sample-batch-job --client-request-id order-4711 --timeout-seconds 900"
./gradlew op-proxy-app:runCli --args="--namespace default start-execution sample-batch-job --parameter runType=FULL --parameter businessDate=2026-04-17"
./gradlew op-proxy-app:runCli --args="--namespace default execution-status sample-batch-job-20260422101500-ab12cd"
./gradlew op-proxy-app:runCli --args="--namespace default execution-status sample-batch-job-20260422101500-ab12cd --watch --interval-seconds 5 --timeout-seconds 900"
./gradlew op-proxy-app:runCli --args="--namespace default stop-execution sample-batch-job-20260422101500-ab12cd"
```

`start-execution` accepterar `--client-request-id`, `--timeout-seconds` och upprepad `--parameter name=value`.
För `start-execution` betyder `--parameter` template-parameter till OpenShift Template.
`execution-status` följer watch-beteende med samma flaggor som i exemplen (`--watch`, `--interval-seconds`, `--timeout-seconds`).
`stop-execution` gör alltid graceful stop och raderar execution-Jobbet.

#### Från terminal i en pod utan Gradle

I en vanlig runtime-pod finns normalt inte Gradle-wrappern. Kör då CLI via wrapper-skriptet i imagen.

`java -jar /deployments/quarkus-run.jar` är inte samma sak som CLI:t. Det kommandot startar Quarkus-applikationen och exponerar HTTP-API:t på port 8080.

För enklare CLI-körning finns wrapper-skriptet `/deployments/bin/run-cli.sh` i imagen:

```bash
sh /deployments/bin/run-cli.sh --help
sh /deployments/bin/run-cli.sh --namespace default start-execution inv-javabatch-template --timeout-seconds 900
```

Skriptet hittar rätt fast-jar-layout automatiskt och använder projektets Java 21 lokalt när det behövs.

Vanliga CLI-exempel i pod:

```bash
sh /deployments/bin/run-cli.sh --namespace default start-execution inv-javabatch-template --timeout-seconds 900
sh /deployments/bin/run-cli.sh --namespace default execution-status <execution-name>
sh /deployments/bin/run-cli.sh --namespace default execution-status <execution-name> --watch --interval-seconds 5 --timeout-seconds 900
sh /deployments/bin/run-cli.sh --namespace default stop-execution <execution-name>
```

För ett rent HTTP-flöde (start + polling till terminalt läge) finns även:

```bash
sh /deployments/bin/start-batch.sh --template inv-javabatch-template
sh /deployments/bin/start-batch.sh --template inv-javabatch-template --parameter businessDate=2026-05-07 --parameter runType=FULL
sh /deployments/bin/start-batch.sh --template inv-javabatch-template --base-url https://op-proxy-app.<namespace>.apps.example.com --insecure
```

Skriptet anropar `POST /api/templates/{templateName}/start`, läser ut `executionName`, pollar `GET /api/executions/{executionName}` tills terminal status och returnerar exit-kod enligt tabellen nedan.

Om du behöver HTTP-anrop direkt, se API-sektionen ovan.

Exit-koder (CI/CD):
- `0` = `SUCCEEDED` eller `STOPPED`
- `10` = `RUNNING` eller `PENDING`
- `2` = `FAILED`
- `3` = `SUSPENDED`
- `4` = `UNKNOWN`
- `124` = timeout i `status --watch`

### Pods vid felsökning

För executions kan poddar efter `FAILED` behållas för felsökning tills cluster-ttl städar dem (styrt av `ttlSecondsAfterFinished` i Job/template).




















