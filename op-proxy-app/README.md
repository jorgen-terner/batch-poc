# op-proxy-app

Java 21-applikation för att styra förskapade Kubernetes Jobs (suspended Jobs) via REST API.

## Teknikval

- Gradle Wrapper `9.0.0`
- Java Toolchain `21`
- Logging via `SLF4J` (med Logback)
- Kubernetes integration via Fabric8 Kubernetes Client
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
- Docker/Podman installerat (för lokalt bygge)

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
3. Bygger Docker-imagen via OpenShift BuildConfig
4. Applicerar ServiceAccount och RBAC från `rbac-op-proxy-app.yaml`
5. Deployar op-proxy-app med Service och Route

### Manuell deploy

**1. Bygga jar lokalt:**

```bash
./gradlew clean build
```

**2. Bygga och pusha Docker-imagen:**

```bash
# Få registry-URL
REGISTRY=$(oc registry info)
NAMESPACE="batch-jobs"
IMAGE_TAG="latest"

# Bygg imagen
docker build -t ${REGISTRY}/${NAMESPACE}/op-proxy-app:${IMAGE_TAG} .

# Logga in i OpenShift registry (om behövs)
docker login -u $(oc whoami) -p $(oc whoami -t) ${REGISTRY}

# Pusha imagen
docker push ${REGISTRY}/${NAMESPACE}/op-proxy-app:${IMAGE_TAG}
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
- `core/pods`: `get`, `list`, `watch`
- `core/pods/log`: `get`, `list`, `watch`

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

Flödet utgår från ett existerande template-Job i klustret. När klienten skapar en run kopierar op-proxy-app template-jobbet, sätter ett nytt servergenererat `runName`, applicerar eventuella parametrar som env-variabler och skapar ett nytt Job.

Se [TEMPLATE-RUN-API-RFC.md](TEMPLATE-RUN-API-RFC.md) för bakgrund och migreringsidéer. README:n nedan beskriver den aktuella implementationen.

### HTTP-endpoints (v2)

- `POST /api/v2/templates/{templateName}/runs`
- `GET /api/v2/runs/{runName}`
- `POST /api/v2/runs/{runName}/cancel`

### Kontrakt

Create-run request:
- `clientRequestId`: valfri korrelationsnyckel från klienten
- `timeoutSeconds`: valfri timeout som sätts som `activeDeadlineSeconds`
- `parameters`: valfri lista av `name/value` som skrivs till första containerns env-variabler

Create/cancel response (`RunActionResponseVO`) innehåller `namespace`, `templateName`, `runName`, `clientRequestId`, `action`, `state`, `message` och `createdAt`.

Status response (`RunStatusResponseVO`) innehåller `namespace`, `templateName`, `runName`, `phase`, pod-räknare och tidsfält.

`runName` skickas inte in av klienten. Det genereras alltid av servern och används sedan för status- och cancel-anrop.

Validering av `parameters` i v2:
- `name` måste vara satt och får inte vara blankt
- `value` måste vara satt (`""` tom sträng är giltigt)
- Dubbel `name` i samma request avvisas

Cancel request:
- `deletePods=true`: radera både Job och tillhörande pods
- `deletePods=false` eller tom body: radera Job men behåll pods

### Flöde i v2

1. Klienten anropar `POST /api/v2/templates/{templateName}/runs`.
2. op-proxy-app läser template-jobbet i samma namespace.
3. Ett nytt `runName` genereras av servern.
4. Ett nytt Job skapas från template-jobbet med labels för template och run.
5. Klienten följer körningen via `GET /api/v2/runs/{runName}`.
6. Vid behov avbryts körningen via `POST /api/v2/runs/{runName}/cancel`.

### Telemetri för v2

op-proxy-app exponerar inte längre endpoints för att läsa metrics eller ta emot explicita report-anrop.
I stället skickar service-lagret generella Job/Run-händelser till en intern `JobMetricsReporter`.
Just nu loggas dessa händelser via `slf4j`, vilket gör att formatet kan verifieras innan en extern produkt kopplas in.

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

CLI:t innehåller nu kommandon för både v1 (legacy suspended Jobs) och v2 (template/run).

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
```

`--parameter` kan anges flera gånger och ska ha formatet `name=value`.
Dubbel parameternyckel i samma CLI-anrop avvisas.

#### v2 (template/run)

Exempel anrop:

```bash
./gradlew runCli --args="--namespace default create-run sample-batch-job --client-request-id order-4711 --timeout-seconds 900"
./gradlew runCli --args="--namespace default create-run sample-batch-job --parameter runType=FULL --parameter businessDate=2026-04-17"
./gradlew runCli --args="--namespace default run-status sample-batch-job-20260422101500-ab12cd"
./gradlew runCli --args="--namespace default run-status sample-batch-job-20260422101500-ab12cd --watch --interval-seconds 5 --timeout-seconds 900"
./gradlew runCli --args="--namespace default cancel-run sample-batch-job-20260422101500-ab12cd"
./gradlew runCli --args="--namespace default cancel-run sample-batch-job-20260422101500-ab12cd --delete-pods=true"
```

`create-run` accepterar `--client-request-id`, `--timeout-seconds` och upprepad `--parameter name=value`.
`run-status` följer samma watch-beteende som v1-status.
`cancel-run` raderar enbart Jobbet som default, och med `--delete-pods=true` raderas även pods.

Exit-koder (CI/CD):
- `0` = `SUCCEEDED`
- `10` = `RUNNING` eller `PENDING`
- `2` = `FAILED`
- `3` = `SUSPENDED`
- `4` = `UNKNOWN`
- `124` = timeout i `status --watch`

### Pods vid felsökning

Vid `stop` rensas endast aktiva pods.
Vid `restart` styr `keepFailedPods` om terminala pods (`Failed`/`Succeeded`) ska behållas (`true`) eller rensas (`false`).
