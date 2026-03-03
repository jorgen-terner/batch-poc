# script-example

Helm-chart som skapar en suspendad `Job`-mall för skriptkörning.

## Installera

```bash
helm install script-example ./script-example -n batch
```

Detta skapar en Job-mall med namn:

- `script-example-script-example-template`

Starta mallen direkt utan API:

```bash
kubectl patch job script-example-script-example-template -n batch --type merge -p '{"spec":{"suspend":false}}'
```

## Använd med trigger-API från k8s-jobs-example

Om du installerat trigger-API:t i samma namespace:

```bash
kubectl port-forward svc/batch-demo-k8s-jobs-example-trigger-api 8080:8080 -n batch
curl -X POST http://localhost:8080/trigger/script-example-script-example-template
```

Status:

```bash
curl http://localhost:8080/jobs/<job-namn>
```

## Anpassa script

Uppdatera `command` i `values.yaml` eller med `--set` vid install/upgrade.
