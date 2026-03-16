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
- `POST /api/jobs/{jobId}/restart` - Restarta Job (skapar ny körning)
- `GET /api/jobs` - Lista alla Jobs
- `GET /api/jobs/{jobId}` - Hämta status på Job
- `DELETE /api/jobs/{jobId}` - Stoppa/ta bort Job

## JAVABATCH Exempel

För att köra `example_javabatch.py` via inf-batch-job används en separat image och en ConfigMap
med `BATCH_TYP=JAVABATCH`.

Exempel på API-anrop (runtime-parameterisering av action och job args):

```bash
curl -X POST http://localhost:8080/api/jobs \
	-H "Content-Type: application/json" \
	-d '{
		"configMapName": "inf-batch-javabatch-config",
		"env": {
			"JOB_ACTION": "restart",
			"JOB_ARGS": "myJob=true"
		}
	}'
```

Rapporteringsregel:

- `BATCH_TYP=JAVABATCH` -> statistik/status rapporteras enligt javabatch-monitor-modellen.
- annan `BATCH_TYP` -> ingen statistikrapportering just nu.

Se även [../../inf-batch-javabatch/README.md](../../inf-batch-javabatch/README.md).

## RBAC

ServiceAccount med roles för:
- Skapa och hantera Kubernetes Jobs
- Läsa Pods och logs
