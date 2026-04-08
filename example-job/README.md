# example-job

Den här mappen innehåller Job-image och Kubernetes-manifest för `inf-javabatch`.

## Innehåll

- `Dockerfile` - bygger en Job-image som installerar RPM från `inf-javabatch`.
- `configmap.yaml` - exponerar `START`, `STOP` och timeout/grace-inställningar som miljövariabler.
- `suspended-job.yaml` - suspended Kubernetes Job som kan styras av `omtag/batch-job-app`.

## Byggflöde

Bygg först RPM:

```bash
./gradlew :inf-javabatch:packageRpm
```

Bygg sedan imagen från repo-roten så att Docker kan läsa RPM-filen:

```bash
docker build -f example-job/Dockerfile -t example-job:latest .
```

## Deploy

Applicera först ConfigMap och sedan Job:

```bash
kubectl apply -f example-job/configmap.yaml
kubectl apply -f example-job/suspended-job.yaml
```

När Jobbet finns i klustret kan `omtag/batch-job-app` starta det genom att unsuspenda Job-resursen.

## Stop-beteende

- `batch-job-app` suspenderar Jobbet och delete:ar aktiva pods.
- Pod-deleten skickar SIGTERM.
- `inf-javabatch` anropar då `STOP` och väntar `STOP_WAIT_SECONDS`.
- `terminationGracePeriodSeconds` i Job-manifestet måste vara större än eller lika med `STOP_WAIT_SECONDS`.