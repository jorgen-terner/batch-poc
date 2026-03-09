# inf-batch-testapp1

En enkel Quarkus testapplikation för att demonstrera batch job execution via inf-batch-job.

## Beskrivning

Denna applikation är en Quarkus command-mode applikation som:
- Loggar start och slut av batch job
- Simulerar 5 steg av arbete med 2 sekunders delay mellan varje
- Returnerar exit code 0 vid lyckad körning

## Bygga applikationen

```bash
# Bygg lokalt med Gradle
.\gradlew.bat :inf-batch-testapp1:build -x test

# Bygg Docker image
cd inf-batch-testapp1
docker build -t <registry>/inf-batch-testapp1:latest ..
```

## Testa lokalt

```bash
# Kör direkt med Java
java -jar build/quarkus-app/quarkus-run.jar

# Kör med Docker
docker run --rm <registry>/inf-batch-testapp1:latest
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

## Skicka in job till inf-batch-job

```bash
# Port-forward till inf-batch-job
kubectl port-forward svc/inf-batch-job 8080:8080

# Starta ett batch job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "test-batch-1",
    "image": "<registry>/inf-batch-testapp1:latest",
    "namespace": "default"
  }'

# Kontrollera job status
curl -X GET http://localhost:8080/api/jobs/{jobId}

# Lista alla jobs
curl -X GET http://localhost:8080/api/jobs
```

## Deployment

Pusha imagen till ditt container registry:

```bash
docker push <registry>/inf-batch-testapp1:latest
```

Uppdatera OpenShift deployment för att ha tillgång till imagen.
