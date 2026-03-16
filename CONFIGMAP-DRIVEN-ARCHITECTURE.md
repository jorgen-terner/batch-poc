# ConfigMap-Driven Architecture

## Översikt

**NY ARKITEKTUR (v2):** ConfigMap är nu "Single Source of Truth" för alla batch-applikationer. All konfiguration - från image-namn till miljövariabler - definieras i ConfigMap.

### Förbättringar från v1

| Aspekt | v1 (Gammal) | v2 (Ny - ConfigMap-driven) |
|--------|-------------|----------------------------|
| **Image** | Måste anges i varje API-request | Definieras i ConfigMap |
| **Job Config** | Måste anges i varje API-request | Definieras i ConfigMap |
| **Env Vars** | Endast från ConfigMap | Från ConfigMap + optional overrides |
| **API Request** | Många parametrar | Endast `configMapName` krävs |
| **Caching** | Ingen | ConfigMap cachas med resourceVersion |
| **GitOps** | Delvis | Fullt stöd - allt i ConfigMap |

## Arkitektur

```
┌─────────────────────────────────────────────────┐
│ ConfigMap (inf-batch-testapp1-config)           │
├─────────────────────────────────────────────────┤
│ # Job Configuration                             │
│ image: "registry.../inf-batch-testapp1:latest"  │
│ parallelism: "1"                                │
│ completions: "1"                                │
│ ttlSecondsAfterFinished: "3600"                 │
│                                                 │
│ # Application Environment                       │
│ BATCH_SIZE: "100"                               │
│ SIMULATION_STEPS: "5"                           │
│ LOG_LEVEL: "INFO"                               │
└─────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│ API Request (Minimal!)                          │
├─────────────────────────────────────────────────┤
│ {                                               │
│   "configMapName": "inf-batch-testapp1-config"  │
│ }                                               │
└─────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│ inf-batch-job Service                           │
├─────────────────────────────────────────────────┤
│ 1. Läs ConfigMap (med caching)                  │
│ 2. Extrahera job-config (image, parallelism)   │
│ 3. Applicera optional overrides från request   │
│ 4. Skapa Kubernetes Job med envFrom            │
└─────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│ Kubernetes Job                                  │
├─────────────────────────────────────────────────┤
│ spec:                                           │
│   template:                                     │
│     spec:                                       │
│       containers:                               │
│       - image: <från ConfigMap>                 │
│         envFrom:                                │
│         - configMapRef:                         │
│             name: inf-batch-testapp1-config     │
└─────────────────────────────────────────────────┘
```

## ConfigMap Structure

### Reserved Keys (Job Configuration)

Dessa nycklar läses av inf-batch-job för att konfigurera Kubernetes Job:

| Key | Type | Required | Default | Description |
|-----|------|----------|---------|-------------|
| `image` | string | **YES** | - | Container image att köra |
| `jobName` | string | No | Från ConfigMap-namn | Bas-namn för genererade jobs |
| `imagePullPolicy` | string | No | `IfNotPresent` | Image pull policy |
| `parallelism` | int | No | - | Antal parallella pods |
| `completions` | int | No | - | Antal slutförda pods som krävs |
| `ttlSecondsAfterFinished` | int | No | 86400 | TTL efter completion |
| `backoffLimit` | int | No | 3 | Max antal retries |
| `command` | string | No | - | Komma-separerad kommandolista |

### Application Keys (Environment Variables)

**ALLA andra nycklar** i ConfigMap blir miljövariabler i batch-jobbets container!

```yaml
data:
  # Reserved - job config
  image: "..."
  parallelism: "2"
  
  # Application env vars (läses av din batch-app)
  DATABASE_URL: "jdbc:postgresql://..."
  BATCH_SIZE: "500"
  LOG_LEVEL: "DEBUG"
  CUSTOM_SETTING: "value"
```

Batch-appen kan läsa dessa:
```java
String dbUrl = System.getenv("DATABASE_URL");
int batchSize = Integer.parseInt(System.getenv("BATCH_SIZE"));
```

## API Usage

### Minimal Request

```json
{
  "configMapName": "inf-batch-testapp1-config"
}
```

ConfigMap innehåller allt som behövs!

### Optional Parameters

```json
{
  "configMapName": "inf-batch-testapp1-config",
  "jobName": "custom-run",             // Override genererat namn
  "image": "registry/app:v2.0",         // Override image från ConfigMap
  "parallelism": 5,                     // Override parallelism från ConfigMap
  "completions": 10,                    // Override completions från ConfigMap
  "imagePullPolicy": "Always",          // Override imagePullPolicy
  "ttlSecondsAfterFinished": 7200,      // Override TTL
  "command": ["sh", "-c", "custom"],    // Override command
  "env": {                              // Extra/override env vars
    "RUN_ID": "special-20260312",
    "BATCH_SIZE": "1000"
  }
}
```

**Override Priority:**
1. Request parameters (högst prioritet)
2. ConfigMap values
3. System defaults (lägst prioritet)

## Caching

ConfigMap-data cachas för performance:

- **Cache Key:** ConfigMap name
- **Cache Validation:** Kubernetes `resourceVersion`
- **Cache Invalidation:** Automatisk när ConfigMap uppdateras i Kubernetes

### Hur det fungerar

```
Request 1: configMapName="app-config"
  → Läs från Kubernetes API (resourceVersion: "12345")
  → Cacha data
  → Bygg Job

Request 2: configMapName="app-config"
  → Läs från Kubernetes API (resourceVersion: "12345")
  → resourceVersion samma → Använd cache!
  → Bygg Job (snabbt!)

ConfigMap uppdateras i Kubernetes
  → resourceVersion becomes "12346"

Request 3: configMapName="app-config"
  → Läs från Kubernetes API (resourceVersion: "12346")
  → resourceVersion ändrad → Uppdatera cache
  → Bygg Job med nya värden
```

**Resultat:** Ingen config blir "gammal" - cache uppdateras automatiskt när ConfigMap ändras!

## Komplett Exempel: inf-batch-testapp1

### 1. ConfigMap (configmap.yaml)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: inf-batch-testapp1-config
  namespace: dev252
data:
  # Job configuration
  image: "image-registry.openshift-image-registry.svc:5000/dev252/inf-batch-testapp1:latest"
  imagePullPolicy: "Always"
  jobName: "testapp1"
  parallelism: "1"
  completions: "1"
  ttlSecondsAfterFinished: "3600"
  
  # Application environment
  SIMULATION_STEPS: "5"
  SIMULATION_DELAY_SECONDS: "2"
  BATCH_SIZE: "100"
  LOG_LEVEL: "INFO"
```

### 2. Deploy ConfigMap

```powershell
oc apply -f configmap.yaml -n dev252
```

### 3. Start Job

**Super enkelt - bara ConfigMap-namnet!**

```powershell
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{"configMapName": "inf-batch-testapp1-config"}'
```

### 4. Runtime Override

Testa med 10 steg istället för 5:

```powershell
curl -X POST http://localhost:8080/api/jobs `
  -H "Content-Type: application/json" `
  -d '{
    "configMapName": "inf-batch-testapp1-config",
    "env": {
      "SIMULATION_STEPS": "10",
      "RUN_ID": "experiment-001"
    }
  }'
```

## Migration från v1 till v2

### v1 API Request (Gammal)

```json
{
  "jobName": "test-batch-1",
  "image": "image-registry.../inf-batch-testapp1:latest",
  "imagePullPolicy": "Always",
  "parallelism": 1,
  "completions": 1,
  "ttlSecondsAfterFinished": 3600,
  "env": {
    "BATCH_SIZE": "100",
    "SIMULATION_STEPS": "5",
    "LOG_LEVEL": "INFO"
  }
}
```

### v2 Migration

**Steg 1:** Flytta till ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: inf-batch-testapp1-config
data:
  image: "image-registry.../inf-batch-testapp1:latest"
  imagePullPolicy: "Always"
  jobName: "test-batch"
  parallelism: "1"
  completions: "1"
  ttlSecondsAfterFinished: "3600"
  BATCH_SIZE: "100"
  SIMULATION_STEPS: "5"
  LOG_LEVEL: "INFO"
```

**Steg 2:** Förenkla API request

```json
{
  "configMapName": "inf-batch-testapp1-config"
}
```

**Resultat:** 95% mindre JSON, samma funktionalitet! 🎉

## Best Practices

### 1. Naming Convention

```
<app-name>-config
```

Exempel:
- `inf-batch-testapp1-config`
- `invoice-processor-config`
- `data-migration-config`

### 2. Versioning

Använd labels för versioning:

```yaml
metadata:
  name: inf-batch-testapp1-config
  labels:
    app: inf-batch-testapp1
    version: "2.1.0"
    environment: "production"
```

### 3. Environment-Specific ConfigMaps

```yaml
# dev-config
metadata:
  name: inf-batch-testapp1-config-dev
data:
  image: "registry/app:dev"
  LOG_LEVEL: "DEBUG"
  DATABASE_URL: "jdbc:postgresql://dev-db:5432/db"

# prod-config
metadata:
  name: inf-batch-testapp1-config-prod
data:
  image: "registry/app:v1.2.3"
  LOG_LEVEL: "WARN"
  DATABASE_URL: "jdbc:postgresql://prod-db:5432/db"
```

Kör olika environments:

```json
{"configMapName": "inf-batch-testapp1-config-dev"}
{"configMapName": "inf-batch-testapp1-config-prod"}
```

### 4. Dokumentera Reserved Keys

I din ConfigMap, kommentera tydligt:

```yaml
data:
  # ===== JOB CONFIGURATION (inf-batch-job) =====
  image: "..."
  parallelism: "2"
  
  # ===== APPLICATION CONFIGURATION =====
  BATCH_SIZE: "100"
```

### 5. Validation i Batch-App

Validera alltid required env vars vid start:

```java
String batchSize = System.getenv("BATCH_SIZE");
if (batchSize == null) {
    throw new IllegalStateException("BATCH_SIZE must be set in ConfigMap!");
}
```

## Fördelar

✅ **Single Source of Truth** - All config i ett ställe  
✅ **GitOps Ready** - Versionskontroll för konfiguration  
✅ **Minimal API** - Enkla requests  
✅ **Caching** - Performance-optimerat  
✅ **Flexible Overrides** - Runtime customization möjlig  
✅ **Separation of Concerns** - Varje app äger sin ConfigMap  
✅ **No Stale Config** - Automatisk cache-invalidering  

## Troubleshooting

### "ConfigMap not found"

```powershell
# Verifiera att ConfigMap finns
oc get configmap inf-batch-testapp1-config -n dev252

# Visa innehåll
oc describe configmap inf-batch-testapp1-config -n dev252
```

### "'image' måste anges"

ConfigMap måste ha `image`-nyckel:

```yaml
data:
  image: "registry.../myapp:latest"  # Required!
```

### Cache visar gamla värden

Cache uppdateras automatiskt baserat på `resourceVersion`. Om du ser gamla värden:

```powershell
# Kontrollera resourceVersion
oc get configmap inf-batch-testapp1-config -n dev252 -o yaml | grep resourceVersion

# Rensa cache manuellt (om behövs - via admin API)
curl -X DELETE http://localhost:8080/api/admin/cache/inf-batch-testapp1-config
```

## Summary

ConfigMap-driven architecture ger en kraftfull, flexibel och GitOps-vänlig lösning för batch job management. Genom att flytta all konfiguration till ConfigMaps blir API-requests minimala och all business logic finns i versionskontrollerade filer.
