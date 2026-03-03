# spring-batch-example

Helm-chart som skapar en suspendad `Job`-mall för Spring Batch.

## Installera

```bash
helm install sb-example ./spring-batch-example -n batch
```

Detta skapar en Job-mall med namn:

- `sb-example-spring-batch-example-template`

Starta mallen direkt utan API:

```bash
kubectl patch job sb-example-spring-batch-example-template -n batch --type merge -p '{"spec":{"suspend":false}}'
```

## Använd med trigger-API från k8s-jobs-example

Om du installerat trigger-API:t i samma namespace:

```bash
kubectl port-forward svc/batch-demo-k8s-jobs-example-trigger-api 8080:8080 -n batch
curl -X POST http://localhost:8080/trigger/sb-example-spring-batch-example-template
```

Status:

```bash
curl http://localhost:8080/jobs/<job-namn>
```

## Anpassa image

Sätt image med Helm-override:

```bash
helm upgrade --install sb-example ./spring-batch-example -n batch \
  --set image.repository=ghcr.io/acme/my-spring-batch \
  --set image.tag=1.0.0
```
