# inf-batch-javabatch

Detta ar en image med example_javabatch.py som batch-poc kan starta som Kubernetes Job.

## Bygg image

```powershell
cd c:\repos\batch-poc\inf-batch-javabatch
docker build -t inf-batch-javabatch:latest .
```

## Build och push i OpenShift

```powershell
oc new-build --binary --name=inf-batch-javabatch --strategy=docker --to=inf-batch-javabatch:latest -n dev252
oc start-build inf-batch-javabatch --from-dir=. --follow -n dev252
```

## Deploya configmap

```powershell
oc apply -f configmap.yaml -n dev252
```

## Starta som separat Job via inf-batch-job

```powershell
curl -X POST http://localhost:8080/api/jobs ^
  -H "Content-Type: application/json" ^
  -d "{\"configMapName\":\"inf-batch-javabatch-config\",\"env\":{\"JOB_ACTION\":\"restart\",\"JOB_ARGS\":\"myJob=true\"}}"
```

Detta skapar ett separat Kubernetes Job-objekt for script-imagen.

## Trigger via Kubernetes-manifest

Du kan aven trigga inf-batch-job via ett eget Kubernetes Job:

oc apply -f trigger-job-via-api.yaml

Justera env-falten JOB_ACTION och JOB_ARGS i manifestet for onskad parametrisering.
