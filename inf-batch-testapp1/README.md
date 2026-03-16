# inf-batch-testapp1

En enkel Quarkus testapplikation för att demonstrera batch job execution via inf-batch-job.

## Beskrivning

Denna applikation är en Quarkus command-mode applikation som:
- Loggar start och slut av batch job
- Simulerar 5 steg av arbete med 2 sekunders delay mellan varje
- Returnerar exit code 0 vid lyckad körning

## Konfiguration via ConfigMap

ConfigMap innehåller all konfiguration - både job-konfiguration (image, parallelism) och miljövariabler för applikationen.

### ConfigMap struktur

Filen [configmap.yaml](configmap.yaml) innehåller:

1. **Job Configuration** (läses av inf-batch-job):
   - `image` - Container image att köra
   - `parallelism`, `completions` - Job execution settings
   - `ttlSecondsAfterFinished` - Cleanup settings

2. **Application Configuration** (miljövariabler för batch-appen):
   - `SIMULATION_STEPS` - Antal steg att köra
   - `BATCH_SIZE` - Batch storlek
   - `LOG_LEVEL` - Logging-nivå
   - ... och alla andra app-specifika settings

### Deploya ConfigMap

```powershell
# Deploya ConfigMap till OpenShift
oc apply -f configmap.yaml -n dev252

# Verifiera
oc get configmap inf-batch-testapp1-config -n dev252

# Visa innehåll
oc describe configmap inf-batch-testapp1-config -n dev252
```

## Bygga applikationen

```powershell
# Bygg lokalt med Gradle (från root-katalogen)
.\gradlew :inf-batch-testapp1:build -x test

## Testa lokalt

```bash
# Kör direkt med Java
java -jar build/quarkus-app/quarkus-run.jar

```

## Exempel output

```
2026-03-09 10:00:00,123 INFO  [com.exa.tes.TestBatchApp] ============================================================
2026-03-09 10:00:00,125 INFO  [com.exa.tes.TestBatchApp] TEST BATCH APPLICATION STARTED
2026-03-09 10:00:00,126 INFO  [com.exa.tes.TestBatchApp] Timestamp: 2026-03-09T10:00:00
2026-03-09 10:00:00,127 INFO  [com.exa.tes.TestBatchApp] ============================================================
2026-03-09 10:00:00,128 INFO  [com.exa.tes.TestBatchApp] Processing batch job...
2026-03-09 10:00:00,129 INFO  [com.exa.tes.TestBatchApp] Step 1 of 5 - Processing...
2026-03-09 10:00:02,131 INFO  [com.exa.tes.TestBatchApp] Step 2 of 5 - Processing...
...
2026-03-09 10:00:10,145 INFO  [com.exa.tes.TestBatchApp] ============================================================
2026-03-09 10:00:10,146 INFO  [com.exa.tes.TestBatchApp] TEST BATCH APPLICATION COMPLETED SUCCESSFULLY
2026-03-09 10:00:10,147 INFO  [com.exa.tes.TestBatchApp] ============================================================
```

## Skicka in job till inf-batch-job REST API


```powershell
# Port-forward till inf-batch-job
oc port-forward deployment/inf-batch-job 8080:8080 -n dev252

# Starta batch job
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "configMapName": "inf-batch-testapp1-config"
  }'

# Starta med custom jobName
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "configMapName": "inf-batch-testapp1-config",
    "jobName": "my-custom-run"
  }'

# Override specifika värden från ConfigMap
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "configMapName": "inf-batch-testapp1-config",
    "env": {
      "SIMULATION_STEPS": "10",
      "RUN_ID": "special-batch-20260312"
    }
  }'

# Override image (t.ex. för test med annan version)
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "configMapName": "inf-batch-testapp1-config",
    "image": "image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:v2.0"
  }'

# Kontrollera job status
curl -X GET http://localhost:8080/api/jobs/{jobId}

# Lista alla jobs
curl -X GET http://localhost:8080/api/jobs
```

## Deployment

### Steg 1: Deploya ConfigMap (Endast en gång)

```powershell
cd c:\repos\batch-poc\inf-batch-testapp1
oc apply -f configmap.yaml -n dev252
```

### Steg 2: Bygg och deploya Container Image

#### OpenShift BuildConfig

Bygg och deploya direkt till OpenShift:

```powershell
# Bygg projektet lokalt först
cd c:\repos\batch-poc
.\gradlew :inf-batch-testapp1:build -x test

# Skapa BuildConfig (en gång) - viktigt med --to för att tagga automatiskt!
oc new-build --binary --name=inf-batch-testapp1 --strategy=docker --to=inf-batch-testapp1:latest -n dev252

# Bygg från modulkatalogen med färdigbyggda artifacts
cd inf-batch-testapp1
oc start-build inf-batch-testapp1 --from-dir=. --follow -n dev252

# Verifiera att imagen finns
oc get imagestream inf-batch-testapp1 -n dev252
```

**Troubleshooting: "Back-off pulling image"**

Om du får felet `Back-off pulling image "image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:latest"`, betyder det att ImageStream saknar tags. Kontrollera med:

```powershell
oc get imagestream inf-batch-testapp1 -n dev252
# Om TAGS kolumnen är tom, saknas taggen
```

**Lösning:** Bygg och tagga imagen:

```powershell
# 1. Bygg applikationen lokalt
cd c:\repos\batch-poc
.\gradlew :inf-batch-testapp1:build -x test

# 2. Om BuildConfig inte finns, skapa den (viktigt med --to!)
oc new-build --name=inf-batch-testapp1 --binary --strategy=docker --to=inf-batch-testapp1:latest -n dev252

# 3. Bygg och pusha till ImageStream (skapar automatiskt 'latest' tag)
cd inf-batch-testapp1
oc start-build inf-batch-testapp1 --from-dir=. --follow -n dev252

# 4. Verifiera att taggen finns
oc get imagestream inf-batch-testapp1 -n dev252
# TAGS kolumnen ska nu visa: latest
```

Efter detta kommer jobbet kunna pulla imagen utan problem.

### Steg 3: Kör som Kubernetes Job (direkt)
oc create job testapp1-run-$(Get-Date -Format "yyyyMMddHHmmss") `
  --image=image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:latest `
  -n dev252

# Följ logs för jobbet
oc get jobs -n dev252
oc logs -f job/testapp1-run-<timestamp> -n dev252
```

