# batch-poc

Kubernetes Job Manager - En komplett REST API för att externalt managing Kubernetes Jobs.

## Arkitektur

**Trigger Job API** - En Spring Boot Web-applikation som:
- Tar emot HTTP POST-anrop med Docker-image och konfiguration
- Skapar dynamiska Kubernetes Jobs via API
- Returnerar Job-status och loggar
- Är deployad via Helm med full RBAC-support

## Struktur

```
.
├── trigger-job-api/              # Spring Boot applikation (Java)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   └── README.md
├── helm/
│   └── trigger-job-api/          # Helm Chart för deployment
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
- Maven 3.8+ (för att bygga lokalt)

### Snabb start

**1. Bygg Docker-image:**
```bash
docker build -t trigger-job-api:1.0.0 ./trigger-job-api
```

**2. Push till registry (om inte localhost):**
```bash
docker tag trigger-job-api:1.0.0 your-registry/trigger-job-api:1.0.0
docker push your-registry/trigger-job-api:1.0.0
```

**3. Installera med Helm:**
```bash
helm install trigger-api ./helm/trigger-job-api \
  --set image.repository=trigger-job-api \
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
kubectl port-forward svc/trigger-job-api 8080:8080 -n batch
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
    }
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

- [Trigger Job API applikation](trigger-job-api/README.md)
- [Helm Chart konfiguration](helm/trigger-job-api/README.md)

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
kubectl logs deployment/trigger-job-api -n batch
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
kubectl describe role trigger-job-api -n batch
kubectl describe rolebinding trigger-job-api -n batch
```

## API-status

Hälsokontroll via Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/health
```
````
