# INF Batch Job

En REST API för att hantera Kubernetes Jobs. Applikationen gör det möjligt att starta, stoppa och övervaka Kubernetes Jobs via HTTP-requests.

## Features

- **ConfigMap-driven konfiguration**: All job-konfiguration lagras i ConfigMaps
- Starta Kubernetes Jobs från HTTP POST-anrop
- Starta om existerande Job via restart-endpoint
- Override specifika värden vid körning (image, env, parallelism, etc)
- Kolla status på pågående och slutförda Jobs
- Stoppa/radera Jobs
- Lista alla Jobs i namespace
- ConfigMap cache för snabbare körning
- Admin endpoints för cache-management
- Health checks för Kubernetes liveness/readiness probes
- Typstyrd rapportering via ConfigMap-faltet `BATCH_TYP`

Se [ConfigMap Guide](CONFIGMAP-GUIDE.md) för detaljer om konfiguration.

## Endpoints

### Starta ett Job från ConfigMap
```bash
POST /api/jobs
Content-Type: application/json

{
  "configMapName": "inf-batch-testapp1-config"
}
```

ConfigMap:en innehåller all konfiguration (image, env, parallelism, etc). Se [ConfigMap Guide](CONFIGMAP-GUIDE.md).

**Valfria overrides** - för att tillfälligt ändra värden från ConfigMap:
```bash
POST /api/jobs
Content-Type: application/json

{
  "configMapName": "inf-batch-testapp1-config",
  "jobName": "my-custom-job-name",
  "env": {
    "SIMULATION_STEPS": "10",
    "LOG_LEVEL": "DEBUG"
  },
  "image": "image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:v2.0"
}
```

**Fält:**
- `configMapName` (required): Namnet på ConfigMap med job-konfiguration
- `jobName` (optional): Override jobName från ConfigMap
- `image` (optional): Override image från ConfigMap
- `command` (optional): Override command från ConfigMap
- `env` (optional): Lägg till eller override miljövariabler från ConfigMap
- `imagePullPolicy` (optional): Override imagePullPolicy från ConfigMap
- `ttlSecondsAfterFinished` (optional): Override TTL från ConfigMap
- `parallelism` (optional): Override parallelism från ConfigMap
- `completions` (optional): Override completions från ConfigMap

**Response (201 Created):**
```json
{
  "jobId": "testapp1-20260313-143052",
  "jobName": "testapp1",
  "message": "Job startad framgångsrikt",
  "success": true
}
```

### Hämta Job-status
```bash
GET /api/jobs/{jobId}
```

### Restarta ett Job
```bash
POST /api/jobs/{jobId}/restart
```

Skapar en ny jobbkörning med samma grundkonfiguration som ursprungsjobbet (configMap/image/env overrides).

**Response:**
```json
{
  "jobId": "testapp1-20260313-143052",
  "jobName": "testapp1",
  "image": "image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:latest",
  "status": "RUNNING",
  "completions": 1,
  "parallelism": 1,
  "createdAt": "2026-03-13T14:30:52",
  "startTime": "2026-03-13T14:30:55",
  "completionTime": null,
  "message": null
}
```

**Status värden:** `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`
```

### BATCH_TYP och statistikrapportering

- Om `BATCH_TYP=JAVABATCH` i ConfigMap som anges i `configMapName`:
  - inf-batch-job rapporterar start/stop/error till samma Influx-endpoints som `monitor_jbatch`-scriptet (tidigare `monitor.py`).
- Annan eller saknad `BATCH_TYP`:
  - ingen statistik rapporteras just nu (Noop).

Se exempel i [inf-batch-javabatch/README.md](inf-batch-javabatch/README.md).

### Lista alla Jobs
```bash
GET /api/jobs
```

### Stoppa/radera Job
```bash
DELETE /api/jobs/{jobId}
```

### Admin endpoints

#### Cache-status
```bash
GET /api/admin/cache/status
```

**Response:**
```json
{
  "cacheSize": 3,
  "message": "ConfigMap cache innehåller 3 entries"
}
```

#### Rensa cache
```bash
# Rensa hela cachen
DELETE /api/admin/cache

# Rensa cache för specifik ConfigMap
DELETE /api/admin/cache/{configMapName}
```

ConfigMap-data cachas för att minska belastning på Kubernetes API. Använd dessa endpoints för att tvinga en refresh efter ConfigMap-uppdateringar.

## Kör lokalt (utan Kubernetes)

För lokal utveckling med Minikube/Docker Desktop Kubernetes:

```bash
# Bygg applikationen
.\gradlew.bat build

# Kör Quarkus i dev-läge (med hot reload)
.\gradlew.bat quarkusDev

# Eller kör den färdigbyggda applikationen
java -jar build\quarkus-app\quarkus-run.jar
```

API är då tillgänglig på `http://localhost:8080`

## Installera med Helm

```bash
helm install inf-batch-job ./helm/inf-batch-job
```

## Bygga och deploya till OpenShift

### Förberedelser

Bygg projektet lokalt först:

```powershell
# Bygg alla moduler
.\gradlew build -x test

# Eller bygg specifika moduler
.\gradlew :inf-batch-job:build -x test
.\gradlew :inf-batch-testapp1:build -x test
```

### Alternativ 1: OpenShift BuildConfig (Rekommenderat)

Använd OpenShift BuildConfig för att bygga direkt från lokala filer:

#### inf-batch-job (REST API)
```powershell
# Skapa BuildConfig (en gång) - viktigt med --to för att tagga automatiskt!
oc new-build --binary --name=inf-batch-job --strategy=docker --to=inf-batch-job:latest -n dev252

# Bygg från modulkatalogen med färdigbyggda artifacts
cd inf-batch-job
oc start-build inf-batch-job --from-dir=. --follow -n dev252

# Deploya med template (inkluderar Deployment + Service)
cd ..
oc process -f example-deployment.yaml -p NAMESPACE=dev252 | oc apply -f -

# Eller med custom parametrar
oc process -f example-deployment.yaml \
  -p NAMESPACE=dev252 \
  -p IMAGE_TAG=latest \
  -p CPU_LIMIT=2 \
  -p MEMORY_LIMIT=1Gi \
  | oc apply -f -

# Verifiera deployment
oc get deployment inf-batch-job -n dev252
oc get service inf-batch-job -n dev252
oc logs -f deployment/inf-batch-job -n dev252
```

**Template parametrar:**
- `NAMESPACE` (required) - OpenShift namespace
- `IMAGE_REGISTRY` - Registry URL (default: `image-registry.openshift-image-registry.svc:5000`)
- `IMAGE_TAG` - Image tag (default: `latest`)
- `SERVICE_ACCOUNT_NAME` - ServiceAccount (default: `duplosa`)
- `CPU_LIMIT` - Max CPU (default: `1`)
- `MEMORY_LIMIT` - Max memory (default: `512Mi`)
- `CPU_REQUEST` - CPU request (default: `100m`)
- `MEMORY_REQUEST` - Memory request (default: `256Mi`)

**OBS:** Glöm inte att först applya RBAC-konfiguration (se [RBAC-konfiguration](#rbac-konfiguration)).

#### inf-batch-testapp1 (Batch Application)
```powershell
# Skapa BuildConfig (en gång) - viktigt med --to för att tagga automatiskt!
oc new-build --binary --name=inf-batch-testapp1 --strategy=docker --to=inf-batch-testapp1:latest -n dev252

# Bygg från modulkatalogen med färdigbyggda artifacts
cd inf-batch-testapp1
oc start-build inf-batch-testapp1 --from-dir=. --follow -n dev252

# Kör som Kubernetes Job
oc create job testapp1-run --image=image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:latest -n dev252
```

### Alternativ 2: Lokal Docker build och push

```powershell
# Bygg Docker image lokalt
cd inf-batch-job
docker build -t inf-batch-job:latest .

# Logga in på OpenShift registry
$token = oc whoami -t
docker login -u $(oc whoami) -p $token default-route-openshift-image-registry.apps.your-cluster.com

# Tagga och pusha
docker tag inf-batch-job:latest default-route-openshift-image-registry.apps.your-cluster.com/dev252/inf-batch-job:latest
docker push default-route-openshift-image-registry.apps.your-cluster.com/dev252/inf-batch-job:latest
```

### RBAC-konfiguration

För att applikationen ska kunna skapa Kubernetes Jobs och läsa ConfigMaps behövs RBAC-konfiguration:

```powershell
# Applicera RBAC (en gång, med cluster-admin rättigheter)
oc apply -f rbac-inf-batch-job.yaml -n dev252
```

Detta skapar:
- **Role**: `inf-batch-job-role` med behörighet att:
  - Hantera Jobs (create, get, list, watch, update, patch, delete)
  - Läsa ConfigMaps (get, list)
- **RoleBinding**: Kopplar rollen till `duplosa` serviceaccount

**Viktigt:** Om du får fel som `Misslyckades att läsa ConfigMap`, kontrollera att RBAC innehåller ConfigMap-rättigheter:
```powershell
oc describe role inf-batch-job-role -n dev252
# Ska visa både "batch/jobs" och "configmaps" under Resources
```

### Verifiera deployment

```powershell
# Kontrollera ImageStream
oc get imagestream -n dev252

# Kontrollera Deployment
oc get deployment inf-batch-job -n dev252

# Kontrollera logs
oc logs -f deployment/inf-batch-job -n dev252

# Testa API
oc port-forward deployment/inf-batch-job 8080:8080 -n dev252
curl http://localhost:8080/q/health
```

## Konfiguration

Se [Helm Chart README](../helm/inf-batch-job/README.md) för Kubernetes-specifika inställningar.

### Environment-variabler
- `K8S_NAMESPACE` - Kubernetes namespace för Jobs (default: `default`)
- `JOB_TTL_SECONDS` - Sekunder innan Job raderas automatiskt (default: `86400`)
- `LOG_LEVEL` - Logging-nivå (default: `INFO`)

## Loggning

Applikationen loggar till stdout i JSON-format för enkel integration med Kubernetes logging-system.

```bash
kubectl logs -f deployment/inf-batch-job
```

### Memory/CPU issues
Justera resource limits i `values.yaml`.
