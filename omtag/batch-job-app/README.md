# batch-job-app

Java 21-applikation for att styra forskapade Kubernetes Jobs (suspended Jobs) via REST API.

## Teknikval

- Gradle Wrapper `9.0.0`
- Java Toolchain `21`
- Logging via `SLF4J` (med Logback)
- Kubernetes integration via Fabric8 Kubernetes Client
- HTTP API via Quarkus (REST + CDI)

## Koncept

I stallet for att skapa nya Jobs med image/version vid varje korning, utgar appen fran att Job redan finns i Kubernetes med:

```yaml
spec:
  suspend: true
```

API:et andrar sedan Job-state genom att patcha `spec.suspend` och hantera pods.

## Starta lokalt (Quarkus)

```bash
./gradlew quarkusDev
```

Bygg jar:

```bash
./gradlew build
```

## CLI (forslag 1)

CLI:t anvander samma JobControlService som API:t men kor utan HTTP-lager.

Visa hjalp:

```bash
./gradlew runCli --args="--help"
```

Exempel:

```bash
./gradlew runCli --args="--namespace default start sample-batch-job"
./gradlew runCli --args="--namespace default status sample-batch-job"
./gradlew runCli --args="--namespace default status sample-batch-job --watch --interval-seconds 5 --timeout-seconds 600"
./gradlew runCli --args="--namespace default metrics sample-batch-job"
./gradlew runCli --args="--namespace default stop sample-batch-job"
./gradlew runCli --args="--namespace default restart sample-batch-job"
```

### Exit-koder (CI/CD)

- `0` = `SUCCEEDED`
- `10` = `RUNNING` eller `PENDING`
- `2` = `FAILED`
- `3` = `SUSPENDED`
- `4` = `UNKNOWN`
- `124` = timeout i `status --watch`

## Endpoints

- `POST /api/v1/jobs/{namespace}/{jobName}/start`
- `POST /api/v1/jobs/{namespace}/{jobName}/stop`
- `POST /api/v1/jobs/{namespace}/{jobName}/restart`
- `GET /api/v1/jobs/{namespace}/{jobName}/status`
- `GET /api/v1/jobs/{namespace}/{jobName}/metrics`
- `POST /api/v1/jobs/{namespace}/{jobName}/report`

### Report payload (fran Job till appen)

`report` ar frivillig. Job-status och grundmetrics hamtas primart fran Kubernetes Job/Pod-status.
Skicka report endast om du vill bifoga affarsdata.

Om `report` saknas helt fungerar `status` och `metrics` anda, och appen anvander da Kubernetes-data (bland annat exit code samt termination reason/message nar de finns).

```json
{
  "status": "RUNNING",
  "metrics": {
    "recordsProcessed": 1234,
    "errorCount": 0
  },
  "attributes": {
    "executionId": "abc-123"
  }
}
```

Minimal report (också giltig):

```json
{
  "status": "SUCCEEDED"
}
```

Tom report payload accepteras ocksa och returnerar `REPORTED` utan att lagra ny rapport.

## Exempel flode

1. Deployment innehaller ett Job med `suspend: true`.
2. Klient anropar `start`.
3. Appen patchar Job till `suspend: false`.
4. Jobbet skickar frivillig rapport till `report`-endpoint under korning.
5. Klient laser `status` och `metrics`.
6. Vid behov anropas `stop` eller `restart`.
