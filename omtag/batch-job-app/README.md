# batch-job-app

Java 21-applikation for att styra forskapade Kubernetes Jobs (suspended Jobs) via REST API.

## Teknikval

- Gradle Wrapper `9.0.0`
- Java Toolchain `21`
- Logging via `SLF4J` (med Logback)
- Kubernetes integration via Fabric8 Kubernetes Client
- HTTP API via Quarkus (REST + CDI)

## Koncept

I stallet for att skapa nya Jobs med image/version vid varje korning, utgar appen fran att Job redan finns i Kubernetes med:

```yaml
spec:
  suspend: true
```

API:et andrar sedan Job-state genom att patcha `spec.suspend` och hantera pods.

## Starta lokalt (Quarkus)

```bash
./gradlew quarkusDev
```

Bygg jar:

```bash
./gradlew build
```

## CLI (forslag 1)

CLI:t anvander samma JobControlService som API:t men kor utan HTTP-lager.

Visa hjalp:

```bash
./gradlew runCli --args="--help"
```

Exempel:

```bash
./gradlew runCli --args="--namespace default start sample-batch-job"
./gradlew runCli --args="--namespace default status sample-batch-job"
./gradlew runCli --args="--namespace default status sample-batch-job --watch --interval-seconds 5 --timeout-seconds 600"
./gradlew runCli --args="--namespace default metrics sample-batch-job"
./gradlew runCli --args="--namespace default stop sample-batch-job"
./gradlew runCli --args="--namespace default restart sample-batch-job"
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
4. Applicerar ServiceAccount och RBAC från `rbac-batch-job-app.yaml`
5. Deployar batch-job-app med Service och Route

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
docker build -t ${REGISTRY}/${NAMESPACE}/batch-job-app:${IMAGE_TAG} .

# Logga in i OpenShift registry (om behövs)
docker login -u $(oc whoami) -p $(oc whoami -t) ${REGISTRY}

# Pusha imagen
docker push ${REGISTRY}/${NAMESPACE}/batch-job-app:${IMAGE_TAG}
```

**3. Applicera RBAC (ServiceAccount + Role + RoleBinding):**

```bash
oc -n batch-jobs apply -f rbac-batch-job-app.yaml
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

batch-job-app behöver namespaced RBAC för att kunna styra suspended Jobs:

- `batch/jobs`: `get`, `list`, `watch`, `create`, `update`, `patch`, `delete`
- `core/pods`: `get`, `list`, `watch`
- `core/pods/log`: `get`, `list`, `watch`

RBAC är utbrutet i separat fil: `rbac-batch-job-app.yaml`.

### Verifiera deployment

```bash
# Kontrollera status
oc -n batch-jobs get deployment,pods

# Visa logs
oc -n batch-jobs logs -l app=batch-job-app -f

# Få API-endpoint
oc -n batch-jobs get route batch-job-app -o jsonpath='{.spec.host}'

# Test hälsostatus
curl https://$(oc -n batch-jobs get route batch-job-app -o jsonpath='{.spec.host}')/q/health/ready
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
oc -n production scale deployment/batch-job-app --replicas=2
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

- `POST /api/v1/jobs/{namespace}/{jobName}/start` (synkront; väntar till terminal status)
- `POST /api/v1/jobs/{namespace}/{jobName}/stop`
- `POST /api/v1/jobs/{namespace}/{jobName}/restart`
- `GET /api/v1/jobs/{namespace}/{jobName}/status`
- `GET /api/v1/jobs/{namespace}/{jobName}/metrics`
- `POST /api/v1/jobs/{namespace}/{jobName}/report`

### Synkront start-anrop

`start` unsuspendar Jobbet och pollar sedan status tills `SUCCEEDED` eller `FAILED`.

Query-parametrar:
- `intervalSeconds` (default `5`) - pollingintervall
- `timeoutSeconds` (valfri) - timeout for att avbryta väntan

### Report payload (fran Job till appen)

`report` ar frivillig. Job-status och grundmetrics hamtas primart fran Kubernetes Job/Pod-status.
Skicka report endast om du vill bifoga affarsdata.

Om `report` saknas helt fungerar `status` och `metrics` anda, och appen anvander da Kubernetes-data (bland annat exit code samt termination reason/message nar de finns).

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

Tom report payload accepteras ocksa och returnerar `REPORTED` utan att lagra ny rapport.

## Exempel flode

1. Deployment innehaller ett Job med `suspend: true`.
2. Klient anropar `start`.
3. Appen patchar Job till `suspend: false`.
4. Jobbet skickar frivillig rapport till `report`-endpoint under korning.
5. Klient laser `status` och `metrics`.
6. Vid behov anropas `stop` eller `restart`.
