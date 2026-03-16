# Komplett Workflow: Batch App med ConfigMap

Detta dokument visar hela flödet från utveckling av en batch-app till att köra den via inf-batch-job med ConfigMap-konfiguration.

## Översikt

Varje batch-applikation levererar:
1. **Container Image** - Själva applikationen
2. **ConfigMap** - Applikationens konfiguration
3. **Dokumentation** - Hur man deployar och kör

inf-batch-job behöver bara veta:
- Image-namnet
- ConfigMap-namnet

## Steg-för-steg: inf-batch-testapp1

### Steg 1: Utveckla Batch-applikationen

**TestBatchApp.java** läser miljövariabler:

```java
int simulationSteps = getEnvAsInt("SIMULATION_STEPS", 5);
int delaySeconds = getEnvAsInt("SIMULATION_DELAY_SECONDS", 2);
String batchSize = System.getenv().getOrDefault("BATCH_SIZE", "N/A");
```

### Steg 2: Definiera ConfigMap

**configmap.yaml**:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: inf-batch-testapp1-config
  namespace: dev252
data:
  SIMULATION_STEPS: "5"
  SIMULATION_DELAY_SECONDS: "2"
  BATCH_SIZE: "100"
  LOG_LEVEL: "INFO"
```

### Steg 3: Bygg Applikationen

```powershell
cd c:\repos\batch-poc
.\gradlew :inf-batch-testapp1:build -x test
```

### Steg 4: Deploya ConfigMap

```powershell
cd inf-batch-testapp1
oc apply -f configmap.yaml -n dev252

# Verifiera
oc get configmap inf-batch-testapp1-config -n dev252
```

### Steg 5: Bygg och Pusha Container Image

```powershell
# Skapa BuildConfig (första gången)
oc new-build --binary --name=inf-batch-testapp1 --strategy=docker -n dev252

# Bygg image från modulkatalogen
cd c:\repos\batch-poc\inf-batch-testapp1
oc start-build inf-batch-testapp1 --from-dir=. --follow -n dev252

# Verifiera
oc get imagestream inf-batch-testapp1 -n dev252
```

### Steg 6: Starta Job via inf-batch-job API

```powershell
# Port-forward till inf-batch-job
oc port-forward deployment/inf-batch-job 8080:8080 -n dev252

# Starta batch job med ConfigMap
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "jobName": "testapp1-run",
    "image": "image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:latest",
    "configMapName": "inf-batch-testapp1-config"
  }'
```

### Steg 7: Följ Jobbet

```powershell
# Lista jobs
curl -X GET http://localhost:8080/api/jobs

# Hämta job status
curl -X GET http://localhost:8080/api/jobs/{jobId}

# Följ logs i OpenShift
oc get jobs -n dev252
oc logs -f job/testapp1-run-<id> -n dev252
```

## Förväntad Output

```
============================================================
TEST BATCH APPLICATION STARTED
Timestamp: 2026-03-12T10:00:00
Run ID: default
Configuration:
  - Simulation Steps: 5
  - Delay Between Steps: 2 seconds
  - Log Level: INFO
  - Batch Size: 100
============================================================
Processing batch job...
Step 1 of 5 - Processing...
Step 2 of 5 - Processing...
Step 3 of 5 - Processing...
Step 4 of 5 - Processing...
Step 5 of 5 - Processing...
============================================================
TEST BATCH APPLICATION COMPLETED SUCCESSFULLY
Processed 5 steps
============================================================
```

## Runtime Overrides med Extra Env Vars

Du kan override ConfigMap-värden vid jobbstart:

```powershell
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "jobName": "testapp1-custom",
    "image": "image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:latest",
    "configMapName": "inf-batch-testapp1-config",
    "env": {
      "SIMULATION_STEPS": "10",
      "RUN_ID": "batch-20260312-special"
    }
  }'
```

**Output**:
```
Configuration:
  - Simulation Steps: 10          <- Overridden
  - Delay Between Steps: 2 seconds <- From ConfigMap
  - Log Level: INFO                <- From ConfigMap
  - Batch Size: 100                <- From ConfigMap
Run ID: batch-20260312-special   <- Extra env var
```

## Skapa En Ny Batch-App (t.ex. inf-batch-testapp2)

### 1. Skapa applikation med miljövariabel-support

```java
String databaseUrl = System.getenv().getOrDefault("DATABASE_URL", "localhost");
int batchSize = getEnvAsInt("BATCH_SIZE", 100);
```

### 2. Skapa ConfigMap

**inf-batch-testapp2/configmap.yaml**:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: inf-batch-testapp2-config
  namespace: dev252
data:
  DATABASE_URL: "jdbc:postgresql://db:5432/mydb"
  BATCH_SIZE: "500"
  RETRY_COUNT: "3"
```

### 3. Deploya

```powershell
# ConfigMap
oc apply -f inf-batch-testapp2/configmap.yaml -n dev252

# Build image
oc new-build --binary --name=inf-batch-testapp2 -n dev252
oc start-build inf-batch-testapp2 --from-dir=inf-batch-testapp2 --follow -n dev252
```

### 4. Kör via API

```json
{
  "jobName": "testapp2-run",
  "image": "image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp2:latest",
  "configMapName": "inf-batch-testapp2-config"
}
```

## Hantera Secrets

För känslig data (lösenord, nycklar), använd Secrets istället för ConfigMaps:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: inf-batch-testapp2-secrets
  namespace: dev252
type: Opaque
stringData:
  DB_PASSWORD: "superSecret123"
  API_KEY: "abc-xyz-789"
```

**OBS**: inf-batch-job behöver uppdateras för att stödja `secretName`-parameter på samma sätt som `configMapName`.

## Best Practices

1. **Naming**: Använd konsekvent namngivning: `<app-name>-config`
2. **Defaults**: Ha alltid sensible defaults i applikationskoden
3. **Validering**: Validera required env vars vid applikationsstart
4. **Dokumentation**: Dokumentera alla ConfigMap-nycklar i README
5. **Versioning**: Överväg att versionshantera ConfigMaps tillsammans med images
6. **Testing**: Testa lokalt med Docker + `--env-file` före OpenShift-deployment

## Lokal Testning med ConfigMap-värden

```powershell
# Skapa env-fil från ConfigMap
cat > test.env << EOF
SIMULATION_STEPS=3
SIMULATION_DELAY_SECONDS=1
BATCH_SIZE=50
LOG_LEVEL=DEBUG
RUN_ID=local-test
EOF

# Kör med Docker
docker run --rm --env-file test.env inf-batch-testapp1:latest
```

## Troubleshooting

### Problem: "ConfigMap not found"

**Symptom**: Job skapas men Pod startar inte, error i events

**Lösning**:
```powershell
# Verifiera ConfigMap finns
oc get configmap <naam> -n dev252

# Deploya om behövs
oc apply -f configmap.yaml -n dev252
```

### Problem: Applikationen ser inte miljövariabler

**Symptom**: Default-värden används istället för ConfigMap-värden

**Debugging**:
```powershell
# Hitta pod-namnet
oc get pods -n dev252

# Inspect env vars i pod
oc exec <pod-name> -n dev252 -- env | grep SIMULATION

# Kontrollera Job definition
oc get job <job-name> -n dev252 -o yaml | grep -A 10 envFrom
```

### Problem: RBAC-fel

**Symptom**: "Unable to mount configmap"

**Lösning**: Ge Pod's ServiceAccount läsrättighet till ConfigMaps (se CONFIGMAP-GUIDE.md)

## Sammanfattning

Med denna lösning:
- ✅ Batch-applikationer äger sina egna ConfigMaps
- ✅ inf-batch-job är config-agnostisk och bara orkestrerar
- ✅ Konfiguration kan versionshanteras med applikationskod
- ✅ Runtime overrides möjliga via `env`-parameter
- ✅ Separation of concerns mellan olika batch-appar
