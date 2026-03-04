# INF Batch Job Helm Chart

K8s Job Manager API - REST API för att hantera Kubernetes Jobs.

## Installation

```bash
helm install inf-batch-job ./inf-batch-job
```

## Configuration

Se `values.yaml` för alla konfigurerbara parametrar.

### Viktiga inställningar:

- `image.repository` - Docker image för applikationen
- `image.tag` - Image tag/version
- `replicaCount` - Antal replicas
- `kubernetesApi.namespace` - Kubernetes namespace för Jobs

## Endpoints

- `POST /api/jobs` - Starta nytt Job
- `GET /api/jobs` - Lista alla Jobs
- `GET /api/jobs/{jobId}` - Hämta status på Job
- `DELETE /api/jobs/{jobId}` - Stoppa/ta bort Job

## RBAC

ServiceAccount med roles för:
- Skapa och hantera Kubernetes Jobs
- Läsa Pods och logs
