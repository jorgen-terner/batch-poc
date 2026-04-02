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

## CLI (förslag 1)

CLI:t använder samma JobControlService som API:t men kör utan HTTP-lager.

Visa hjälp:

```bash
./gradlew runCli --args="--help"
```

Exempel:

```bash
./gradlew runCli --args="--namespace default start sample-batch-job"
./gradlew runCli --args="--namespace default start sample-batch-job --timeout-seconds 900"
./gradlew runCli --args="--namespace default status sample-batch-job"
./gradlew runCli --args="--namespace default status sample-batch-job --watch --interval-seconds 5 --timeout-seconds 600"
./gradlew runCli --args="--namespace default metrics sample-batch-job"
./gradlew runCli --args="--namespace default stop sample-batch-job"
./gradlew runCli --args="--namespace default restart sample-batch-job"
./gradlew runCli --args="--namespace default restart sample-batch-job --timeout-seconds 900 --keep-failed-pods=true"
./gradlew runCli --args="--namespace default restart sample-batch-job --keep-failed-pods=false"
```

### Exit-koder (CI/CD)

- `0` = `SUCCEEDED`
- `10` = `RUNNING` eller `PENDING`
- `2` = `FAILED`
- `3` = `SUSPENDED`
- `4` = `UNKNOWN`
- `124` = timeout i `status --watch`

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

## Endpoints

- `POST /api/v1/jobs/{namespace}/{jobName}/start` (asynkront)
- `POST /api/v1/jobs/{namespace}/{jobName}/stop`
- `POST /api/v1/jobs/{namespace}/{jobName}/restart`
- `GET /api/v1/jobs/{namespace}/{jobName}/status`
- `GET /api/v1/jobs/{namespace}/{jobName}/metrics`
- `POST /api/v1/jobs/{namespace}/{jobName}/report`

### Asynkront start-anrop

`start` unsuspendar Jobbet och returnerar direkt.
Anropa `status` för att följa körningen tills `SUCCEEDED` eller `FAILED`.

Parametrar för `start` och `restart`:
- Gemensam parameter: `timeoutSeconds` (HTTP) / `--timeout-seconds` (CLI), valfri. Sätts som `spec.activeDeadlineSeconds`.
- Endast för `restart`: `keepFailedPods` (HTTP) / `--keep-failed-pods` (CLI), default `true`. Styr om terminala pods (`Failed`/`Succeeded`) behålls för felsökning.

### Exempel anrop

```bash
# Starta asynkront med timeout på 15 minuter
curl -X POST "http://localhost:8080/api/v1/jobs/default/sample-batch-job/start?timeoutSeconds=900"

# Restart med timeout och behåll terminala pods
curl -X POST "http://localhost:8080/api/v1/jobs/default/sample-batch-job/restart?timeoutSeconds=900&keepFailedPods=true"

# Restart och rensa alla pods
curl -X POST "http://localhost:8080/api/v1/jobs/default/sample-batch-job/restart?keepFailedPods=false"
```

Motsvarande CLI-anrop:

```bash
./gradlew runCli --args="--namespace default start sample-batch-job --timeout-seconds 900"
./gradlew runCli --args="--namespace default restart sample-batch-job --timeout-seconds 900 --keep-failed-pods=true"
./gradlew runCli --args="--namespace default restart sample-batch-job --keep-failed-pods=false"
```

### Pods vid felsökning

Vid `stop` rensas endast aktiva pods.
Vid `restart` styr `keepFailedPods` om terminala pods (`Failed`/`Succeeded`) ska behållas (`true`) eller rensas (`false`).

### Report payload (från Job till appen)

`report` är frivillig. Job-status och grundmetrics hämtas primärt från Kubernetes Job/Pod-status.
Skicka report endast om du vill bifoga affärsdata.

Om `report` saknas helt fungerar `status` och `metrics` ändå, och appen använder då Kubernetes-data (bland annat exit code samt termination reason/message när de finns).

```json
{
  "status": "RUNNING",
  "metrics": {
    "recordsProcessed": 1234,
    "errorCount": 0
  },
  "attributes": {
    "executionId": "abc-123"
  }
}
```

Minimal report (också giltig):

```json
{
  "status": "SUCCEEDED"
}
```

Tom report payload accepteras också och returnerar `REPORTED` utan att lagra ny rapport.

## Exempel flöde

1. Deployment innehåller ett Job med `suspend: true`.
2. Klient anropar `start`.
3. Appen patchar Job till `suspend: false`.
4. Jobbet skickar frivillig rapport till `report`-endpoint under körning.
5. Klient läser `status` och `metrics` tills terminal fas.
6. Vid behov anropas `stop` eller `restart`.
