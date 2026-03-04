# batch-poc

Kubernetes Job Manager - En komplett REST API för att externalt managing Kubernetes Jobs.

## Arkitektur

**INF Batch Job** - En Quarkus Web-applikation som:
- Tar emot HTTP POST-anrop med Docker-image och konfiguration
- Skapar dynamiska Kubernetes Jobs via API
- Returnerar Job-status och loggar
- Är deployad via Helm med full RBAC-support
- Snabb start-tid och låg minnesförbrukning

## Struktur

```
.
├── inf-batch-job/              # Quarkus applikation (Java)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── README.md
├── helm/
│   └── inf-batch-job/          # Helm Chart för deployment
│       ├── Chart.yaml
│       ├── values.yaml
│       ├── templates/
│       │   ├── deployment.yaml
│       │   ├── service.yaml
│       │   ├── rbac.yaml
│       │   ├── configmap.yaml
│       │   ├── ingress.yaml
│       │   └── _helpers.tpl
│       └── README.md
├── README.md
└── run-demo.ps1
```

## Installation & Deployment

### Förutsättningar
- Kubernetes cluster (Minikube, Docker Desktop, eller cloud)
- Helm 3+
- Gradle 8.11+ (wrapper included in project)

### Snabb start

**1. Bygg Docker-image:**
```bash
docker build -t inf-batch-job:1.0.0 ./inf-batch-job
```

**2. Push till registry (om inte localhost):**
```bash
docker tag inf-batch-job:1.0.0 your-registry/inf-batch-job:1.0.0
docker push your-registry/inf-batch-job:1.0.0
```

**3. Installera med Helm:**
```bash
helm install batch-api ./helm/inf-batch-job \
  --set image.repository=inf-batch-job \
  --set image.tag=1.0.0 \
  -n batch --create-namespace
```

**4. Verifiera:**
```bash
kubectl get pods -n batch
kubectl get svc -n batch
```

## API-användning

### Exponera API lokalt:
```bash
kubectl port-forward svc/inf-batch-job 8080:8080 -n batch
```

### Starta ett Job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "my-image-processor",
    "image": "busybox:latest",
    "command": ["echo", "Hello from Kubernetes!"],
    "env": {
      "MY_VAR": "value"
    },
    "parallelism": 2,
    "completions": 5
  }'
```

**Respons:**
```json
{
  "jobId": "my-image-processor-1234",
  "jobName": "my-image-processor",
  "message": "Job startad framgångsrikt",
  "success": true
}
```

### Hämta Job-status
```bash
curl http://localhost:8080/api/jobs/my-image-processor-1234
```

**Respons:**
```json
{
  "jobId": "my-image-processor-1234",
  "jobName": "my-image-processor", 
  "image": "busybox:latest",
  "status": "RUNNING",
  "completions": null,
  "parallelism": 1,
  "createdAt": "2026-03-04T10:30:00",
  "startTime": "2026-03-04T10:30:05",
  "completionTime": null,
  "message": null
}
```

### Lista alla Jobs
```bash
curl http://localhost:8080/api/jobs
```

### Stoppa ett Job
```bash
curl -X DELETE http://localhost:8080/api/jobs/my-image-processor-1234
```

## Detaljerad dokumentation

- [INF Batch Job applikation](inf-batch-job/README.md)
- [Helm Chart konfiguration](helm/inf-batch-job/README.md)

## Test-skript

PowerShell-script för att automatisera installation och test:

```powershell
./run-demo.ps1
```

## Snabb felsökning

**Kontrollera Pod-status:**
```bash
kubectl get pods -n batch
kubectl describe pod <pod-namn> -n batch
```

**Läs logs från API:**
```bash
kubectl logs deployment/inf-batch-job -n batch
```

**Verifiera Jobs:**
```bash
kubectl get jobs -n batch
```

**Läs Job-loggar:**
```bash
kubectl logs job/<job-namn> -n batch
```

**Kontrollera RBAC-permissions:**
```bash
kubectl describe role inf-batch-job -n batch
kubectl describe rolebinding inf-batch-job -n batch
```

## API-status

Hälsokontroll via Quarkus SmallRye Health:

```bash
curl http://localhost:8080/actuator/health
```

Obs: Health-endpointen är konfigurerad på samma path som tidigare för kompatibilitet.
````
