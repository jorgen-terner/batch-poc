# inf-batch-javabatch

Konfiguration for att kora `javabatch.py`-imagen som ett Kubernetes Job via `inf-batch-job`.

## 0. Bygg och pusha imagen

Kallkod for imagen finns i `c:\repos\javabatch`.

```powershell
cd c:\repos\javabatch
docker build -t inf-batch-javabatch:latest .

# Exempel: tagga till OpenShift internal registry
docker tag inf-batch-javabatch:latest image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-javabatch:latest
docker push image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-javabatch:latest
```

Alternativ med OpenShift BuildConfig:

```powershell
oc new-build --binary --name=inf-batch-javabatch --strategy=docker --to=inf-batch-javabatch:latest -n dev252
cd c:\repos\javabatch
oc start-build inf-batch-javabatch --from-dir=. --follow -n dev252
```

## 0.1 End-to-end i OpenShift

```powershell
# 1. Verifiera namespace och login
oc project dev252
oc whoami

# 2. Deploya/uppdatera RBAC för inf-batch-job
oc apply -f c:\repos\batch-poc\rbac-inf-batch-job.yaml -n dev252

# 3. Bygg eller pusha javabatch-imagen
cd c:\repos\javabatch
oc start-build inf-batch-javabatch --from-dir=. --follow -n dev252

# 4. Uppdatera och deploya ConfigMap för javabatch
oc apply -f c:\repos\batch-poc\inf-batch-javabatch\configmap.yaml -n dev252

# 5. Säkerställ att inf-batch-job kör
oc get deployment inf-batch-job -n dev252

# 6. Port-forward API:t lokalt
oc port-forward deployment/inf-batch-job 8080:8080 -n dev252
```

## 1. Deploya ConfigMap

```powershell
oc apply -f configmap.yaml -n dev252
```

Uppdatera `JAVABATCH_*`-URL:erna i `configmap.yaml` innan deploy till era faktiska endpointadresser.

## 2. Starta jobb via inf-batch-job API

Minimalt anrop:

```powershell
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "configMapName": "inf-batch-javabatch-config"
  }'
```

## 3. Runtime-overrides (action, args, token)

```powershell
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "configMapName": "inf-batch-javabatch-config",
    "env": {
      "JOB_ACTION": "restart",
      "JOB_ARGS": "myJob=true",
      "FKST": "<token>"
    }
  }'
```

`JOB_ACTION` styr vilket flaggalternativ som skickas till `javabatch.py` (`start|status|stop|restart|summary|help`).

## 4. Viktiga ConfigMap-nycklar

- `image`: image med `javabatch.py`.
- `BATCH_TYP=JAVABATCH`: aktiverar monitoreringsrapportering i `inf-batch-job`.
- `JAVABATCH_*`: endpoint-URL:er som `inf-javabatch-app` anropar.
- `TO_JOB_NAME` och `TO_ENV_NAME`: metadata till metrics-rapportering.

## 5. Verifiering

```powershell
# Job skapad
oc get jobs -n dev252

# Pod-loggar
oc logs -f job/<job-name> -n dev252

# Status via API
curl http://localhost:8080/api/jobs/<job-id>

# Inspectera den skapade job-definitionen
oc get job <job-id> -n dev252 -o yaml

# Verifiera att ConfigMap-värden kommit med i podden
oc get pod -n dev252 -l job-name=<job-id>
oc describe pod <pod-name> -n dev252
```

## 6. Exakt API-sekvens

Starta:

```powershell
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "configMapName": "inf-batch-javabatch-config"
  }'
```

Status:

```powershell
curl http://localhost:8080/api/jobs/<job-id>
```

Restart:

```powershell
curl -X POST http://localhost:8080/api/jobs/<job-id>/restart
```

Delete/stoppa Kubernetes-jobbet:

```powershell
curl -X DELETE http://localhost:8080/api/jobs/<job-id>
```

## 7. Verifiera metrics mot monitor_jbatch

Följande ska vara lika i rapporteringen:

- `BATCH_TYP=JAVABATCH` måste finnas i ConfigMap.
- start skickar `Executing` med `Status_flag=0`.
- stop skickar `Completed` med `Status_flag=2`.
- error skickar `Failed` med `Status_flag=1`.
- `prod`-server skickar till `fkmetrics`, övriga till `metricstest`.
- `TO_JOB_NAME` och `TO_ENV_NAME` styr `Object` respektive `Chart`.

Skillnad som fortfarande ar accepterad i nuvarande implementation:

- `Start_time` genereras i Java och ar inte byte-for-byte identisk med `ps -o start=` i shellscriptet.
