# batch-poc

Detta repo visar ett komplett exempel för externt triggbara Kubernetes Jobs med Helm.

## Struktur

- helm/k8s-jobs-example
  - Baschart med:
    - RBAC och ServiceAccount
    - suspendade CronJob-mallar
    - trigger-API (HTTP) för att starta Job och läsa status
- spring-batch-example
  - Exempelchart som skapar en Spring Batch CronJob-mall
- script-example
  - Exempelchart som skapar en script CronJob-mall

## End-to-end flöde

Automatiserat alternativ (PowerShell):

```powershell
./run-demo.ps1
```

Scriptet installerar charts, triggar ett Spring- och ett Script-jobb via API och skriver ut status.

Om du redan installerat allt kan du hoppa installationen:

```powershell
./run-demo.ps1 -SkipInstall
```

Manuellt alternativ:

1. Installera baschartet med trigger-API:

```bash
helm install batch-demo ./helm/k8s-jobs-example -n batch --create-namespace
```

2. Installera en eller båda jobbmallarna:

```bash
helm install sb-example ./spring-batch-example -n batch
helm install script-example ./script-example -n batch
```

3. Exponera trigger-API lokalt:

```bash
kubectl port-forward svc/batch-demo-k8s-jobs-example-trigger-api 8080:8080 -n batch
```

4. Starta jobb via API:

Spring Batch:

```bash
curl -X POST http://localhost:8080/trigger/sb-example-spring-batch-example-template
```

Script:

```bash
curl -X POST http://localhost:8080/trigger/script-example-script-example-template
```

5. Läs status för ett skapat jobb:

```bash
curl http://localhost:8080/jobs/<job-namn>
```

## Alternativ: starta med kubectl direkt

Du kan även skapa Job från CronJob-mall utan API:

```bash
kubectl create job --from=cronjob/sb-example-spring-batch-example-template sb-run-$(date +%s) -n batch
kubectl create job --from=cronjob/script-example-script-example-template script-run-$(date +%s) -n batch
```

## Snabb felsökning

- Kontrollera att CronJob-mallar finns:

```bash
kubectl get cronjobs -n batch
```

- Kontrollera skapade jobb:

```bash
kubectl get jobs -n batch
```

- Hämta loggar:

```bash
kubectl logs job/<job-namn> -n batch
```
