# k8s-jobs-example

Exempel på Helm chart som skapar Kubernetes-resurser för batchkörning med `Job`.

Målet med exemplet:
- Ett jobb kan startas externt vid behov.
- Jobbets status kan läsas ut i Kubernetes.
- Jobbet kan vara antingen ett skript eller en Spring Batch-applikation.

## Hur det fungerar

Chartet skapar:
- `ServiceAccount` för jobbkörning.
- `Role` + `RoleBinding` med rättigheter att skapa/läsa `Job` och läsa pod-loggar.
- Två `CronJob`-resurser i läget `suspend: true` som används som mallar:
  - Script-mall
  - Spring Batch-mall
- Ett internt HTTP-API (Deployment + Service) för att trigga jobb och läsa jobbstatus.

När en extern klient vill starta ett jobb skapas ett nytt `Job` från vald CronJob-mall.

## HTTP API för extern trigger/status

API:t körs som en Service i klustret:

- Service-namn: `batch-demo-k8s-jobs-example-trigger-api`
- Port: `8080`

Exempel med port-forward:

```bash
kubectl port-forward svc/batch-demo-k8s-jobs-example-trigger-api 8080:8080 -n batch
```

### Trigger via API (script-mall)

```bash
curl -X POST http://localhost:8080/trigger/batch-demo-k8s-jobs-example-script-template
```

### Trigger via API (spring-mall)

```bash
curl -X POST http://localhost:8080/trigger/batch-demo-k8s-jobs-example-spring-template
```

Svaret innehåller skapat `jobName`.

### Status via API

```bash
curl http://localhost:8080/jobs/<job-namn>
```

Exempel på response-fält:
- `phase` (`Pending|Running|Complete|Failed`)
- `active`, `succeeded`, `failed`
- `conditions`

## Installera

```bash
helm install batch-demo ./helm/k8s-jobs-example -n batch --create-namespace
```

## Extern start av jobb

### Script-jobb

```bash
kubectl create job --from=cronjob/batch-demo-k8s-jobs-example-script-template script-run-$(date +%s) -n batch
```

### Spring Batch-jobb

```bash
kubectl create job --from=cronjob/batch-demo-k8s-jobs-example-spring-template spring-run-$(date +%s) -n batch
```

## Statusrapportering

### Lista alla jobb

```bash
kubectl get jobs -l app.kubernetes.io/instance=batch-demo -n batch
```

### Hämta status för ett visst jobb

```bash
kubectl get job <job-namn> -n batch -o jsonpath='{.status}'
```

### Hämta loggar

```bash
kubectl logs job/<job-namn> -n batch
```

## Anpassa jobb-typer

Konfiguration ligger i `values.yaml`.

### Trigger API
- `triggerApi.enabled`
- `triggerApi.image`
- `triggerApi.port`
- `triggerApi.service.type`
- `triggerApi.service.port`

### Script
- `scriptJob.image`
- `scriptJob.command`
- `scriptJob.env`

### Spring Batch
- `springBatchJob.image`
- `springBatchJob.command`
- `springBatchJob.env`

Exempel: stäng av en jobbtyp

```yaml
scriptJob:
  enabled: false
```

## Exempelvärden för Spring Batch-parametrar

Lägg till argument i `springBatchJob.command`, till exempel:

```yaml
springBatchJob:
  command:
    - java
    - -jar
    - app.jar
    - --spring.batch.job.name=importCustomers
    - --input.file=/data/customers.csv
```
