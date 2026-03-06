# INF Batch Job Helm Chart

K8s Job Manager API - REST API för att hantera Kubernetes Jobs.

## Hur man kör

### Förkrav

- Kubernetes/OpenShift-kluster
- `kubectl` (eller `oc` för OpenShift)
- Helm 3+

### Kubernetes

Kör från mappen `helm/inf-batch-job`:

```bash
helm upgrade --install inf-batch-job .
```

Verifiera release:

```bash
helm list
kubectl get pods,svc
```

### OpenShift

Kör från mappen `helm/inf-batch-job`:

```bash
helm upgrade --install inf-batch-job . -f values-openshift.yaml
```

Verifiera release och route:

```bash
helm list
oc get pods,svc,route
```

### Uppgradera / avinstallera

```bash
helm upgrade inf-batch-job .
helm uninstall inf-batch-job
```

### Lokal testning (port-forward)

Kubernetes:

```bash
kubectl port-forward svc/inf-batch-job 8080:8080
```

OpenShift:

```bash
oc port-forward svc/inf-batch-job 8080:8080
```

Testa API lokalt:

```bash
curl http://localhost:8080/q/health
curl http://localhost:8080/api/jobs
```

## Installation

```bash
helm install inf-batch-job ./inf-batch-job
```

### OpenShift

För OpenShift, aktivera Route istället för Ingress:

```bash
helm install inf-batch-job ./inf-batch-job \
	--set openshift.route.enabled=true \
	--set ingress.enabled=false
```

Alternativt med fördefinierad override-fil:

```bash
helm upgrade --install inf-batch-job ./inf-batch-job -f values-openshift.yaml
```

## Configuration

Se `values.yaml` för alla konfigurerbara parametrar.

### Viktiga inställningar:

- `image.repository` - Docker image för applikationen
- `image.tag` - Image tag/version
- `replicaCount` - Antal replicas
- `kubernetesApi.namespace` - Kubernetes namespace för Jobs
- `openshift.route.enabled` - Aktivera OpenShift Route
- `podSecurityContext.fsGroup` - Sätt endast om er SCC kräver det

## Endpoints

- `POST /api/jobs` - Starta nytt Job
- `GET /api/jobs` - Lista alla Jobs
- `GET /api/jobs/{jobId}` - Hämta status på Job
- `DELETE /api/jobs/{jobId}` - Stoppa/ta bort Job

## RBAC

ServiceAccount med roles för:
- Skapa och hantera Kubernetes Jobs
- Läsa Pods och logs
