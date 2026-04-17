# inv-javabatch

Denna mapp innehåller deploymentfiler för att köra `inf-javabatch` som Kubernetes Job.
Imagen byggs med Quarkus fast-jar-artifakter producerade av modulen `inf-javabatch`.

## Filer

- `Dockerfile` - bygger runtime-image och kopierar Quarkus-artifakter från `inf-javabatch/build/quarkus-app`.
- `configmap.yaml` - sätter miljövariabler enligt README för `inf-javabatch`.
- `job.yaml` - suspended Job-mall som laddar miljövariabler via ConfigMap.

## 1. Bygg Quarkus-artifakter

Kör från repo-roten `c:/repos/batch-poc`:

```bash
./gradlew :inf-javabatch:build -x test
```

Verifiera att katalogen finns:

```bash
ls inf-javabatch/build/quarkus-app
```

## 2. Bygg image med OpenShift (oc)

Kör från repo-roten `c:/repos/batch-poc`:

```bash
oc new-build --name=inv-javabatch --binary=true --strategy=docker --to=inv-javabatch:latest
oc patch bc/inv-javabatch -p '{"spec":{"strategy":{"dockerStrategy":{"dockerfilePath":"inv-javabatch/Dockerfile"}}}}'
oc start-build inv-javabatch --from-dir=. --follow
```

Om BuildConfig redan finns kan du hoppa över `oc new-build`.
Sätt alltid `image` i `job.yaml` till OpenShifts interna registry, till exempel:
`image-registry.openshift-image-registry.svc:5000/dev252/inv-javabatch:latest`
Byt `dev252` till ditt namespace.

### Spara imagen från garbage collection

Images rensas automatiskt av Kubernetes efter ett par arbetsdagar om de inte refereras. För att behålla din byggda image, tagga den med en permanent tag efter bygg:

```bash
# Alternativ 1: Tagga med datum/tid
oc tag inv-javabatch:latest inv-javabatch:$(date +%Y%m%d-%H%M%S)

# Alternativ 2: Tagga med stable
oc tag inv-javabatch:latest inv-javabatch:stable

# Alternativ 3: Tagga med version
oc tag inv-javabatch:latest inv-javabatch:v0.1.0
```

Du kan också uppdatera BuildConfig för att automatiskt behålla senaste byggen:

```bash
oc patch bc/inv-javabatch -p '{
  "spec": {
    "successfulBuildsHistoryLimit": 10,
    "failedBuildsHistoryLimit": 3
  }
}'
```

## 4. Applicera Kubernetes-resurser

```bash
oc apply -f inv-javabatch/configmap.yaml
oc apply -f inv-javabatch/job.yaml
```

Jobbet skapas i suspend-läge. Starta det genom att unsuspenda:

```bash
oc patch job inv-javabatch-suspended -p '{"spec":{"suspend":false}}'
```

## Miljövariabler i ConfigMap

Konfigurerade variabler (från `inf-javabatch/README.md`):

- `START` (obligatorisk)
- `STATUS` (obligatorisk)
- `STOP` (valfri)
- `EXEC_ID_PARAM_NAME` (default `execId`)
- `HTTP_TIMEOUT_SECONDS` (default `30`)
- `STATUS_POLL_INTERVAL_SECONDS` (default `5`)
- `MAX_POLL_SECONDS` (default `3600`)
- `STOP_WAIT_SECONDS` (default `15`)
- `FAIL_OPEN_ON_STATUS_ERROR` (default `true`)
- `MAX_STATUS_ERROR_RETRIES` (default `12`)
- `MAX_UNKNOWN_STATUS_RETRIES` (default `12`)
- `ACTIVE_STATUS_VALUES` (default `STARTING,STARTED,STOPPING`)
- `SUCCESS_STATUS_VALUES` (default `COMPLETED`)
- `FAILURE_STATUS_VALUES` (default `FAILED,ABANDONED,UNKNOWN,STOPPED`)
- `JAVA_OPTS` (valfri)
