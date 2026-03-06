# INF Batch Job

En REST API för att hantera Kubernetes Jobs. Applikationen gör det möjligt att starta, stoppa och övervaka Kubernetes Jobs via HTTP-requests.

## Features

- Starta nya Kubernetes Jobs från HTTP POST-anrop
- Kolla status på pågående och slutförda Jobs
- Stoppa/radera Jobs
- Lista alla Jobs i en namespace
- Health checks för Kubernetes liveness/readiness probes

## Endpoints

### Starta ett Job
```bash
POST /api/jobs
Content-Type: application/json

{
  "jobName": "image-processor",
  "image": "myregistry/image:tag",
  "command": ["--verbose"],
  "env": {
    "CONFIG_URL": "http://config-service"
  },
  "imagePullPolicy": "IfNotPresent",
  "ttlSecondsAfterFinished": 86400,
  "parallelism": 3,
  "completions": 5
}
```

**Fält:**
- `jobName` (required): Namn på jobbet
- `image` (required): Docker-image att köra
- `command` (optional): Kommando att exekvera
- `env` (optional): Miljövariabler
- `imagePullPolicy` (optional): IfNotPresent, Always, eller Never
- `ttlSecondsAfterFinished` (optional): Sekunder innan Job raderas efter avslut
- `parallelism` (optional): Antal pods som körs parallellt
- `completions` (optional): Antal framgångsrika kompletteringar som krävs

**Response (201 Created):**
```json
{
  "jobId": "image-processor-5234",
  "jobName": "image-processor",
  "message": "Job startad framgångsrikt",
  "success": true
}
```

### Hämta Job-status
```bash
GET /api/jobs/{jobId}
```

**Response:**
```json
{
  "jobId": "image-processor-5234",
  "jobName": "image-processor",
  "image": "myregistry/image:tag",
  "status": "RUNNING",
  "completions": 1,
  "parallelism": 1,
  "createdAt": "2026-03-04T10:30:00",
  "startTime": "2026-03-04T10:30:05",
  "completionTime": null,
  "message": null
}
```

### Lista alla Jobs
```bash
GET /api/jobs
```

### Stoppa/radera Job
```bash
DELETE /api/jobs/{jobId}
```

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
