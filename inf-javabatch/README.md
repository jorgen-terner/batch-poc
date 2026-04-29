# inf-javabatch

Quarkus-baserad Job-worker som körs i en Kubernetes Job-container och anropar externa hook-URL:er via miljövariabler.

## Beteende

- Vid containerstart anropas `START` via HTTP GET.
- Response body från `START` tolkas som `executionId`.
- `STATUS` anropas periodiskt som `STATUS?execId=<executionId>` tills terminal status nås.
- Vid lyckad terminal status avslutar processen med exit-kod `0` (Kubernetes Job blir `Complete`).
- Vid felstatus avslutar processen med exit-kod `1` (Kubernetes Job blir `Failed`).
- Vid shutdown innan terminal status anropas `STOP?execId=<executionId>`.
- Efter `STOP` väntar processen `STOP_WAIT_SECONDS` innan den lämnar över till poddens normala nedstängning.

## Miljövariabler

- `START` - obligatorisk URL som anropas när Jobbet startar.
- `STATUS` - obligatorisk URL för statuskontroll.
- `STOP` - valfri URL som anropas när podden termineras.
- `EXEC_ID_PARAM_NAME` - request-parameternamn för execution id, default `execId`.
- `HTTP_TIMEOUT_SECONDS` - timeout per hook-anrop, default `30`.
- `COMMON_HEADERS` - valfria headrar för alla hook-anrop (`START`, `STATUS`, `STOP`). Format: `Header=Value;Another-Header=Value2`.
- `START_HEADERS` - valfria headrar enbart för `START` (samma format som ovan).
- `STATUS_HEADERS` - valfria headrar enbart för `STATUS` (samma format som ovan).
- `STOP_HEADERS` - valfria headrar enbart för `STOP` (samma format som ovan).
- `STATUS_POLL_INTERVAL_SECONDS` - pollingintervall för `STATUS`, default `5`.
- `MAX_POLL_SECONDS` - maximal total pollingtid i sekunder, default `3600`.
- `STOP_WAIT_SECONDS` - väntetid efter `STOP`, default `15`.
- `FAIL_OPEN_ON_STATUS_ERROR` - default `true`. Om `STATUS` inte kan läsas fortsätter polling när `true`, annars avbryts jobbet med fel.
- `MAX_STATUS_ERROR_RETRIES` - max antal misslyckade `STATUS`-anrop i rad innan jobbet avbryts, default `12`.
- `MAX_UNKNOWN_STATUS_RETRIES` - max antal okända `STATUS` i rad innan jobbet avbryts, default `12`.
- `ACTIVE_STATUS_VALUES` - kommaseparerade statusvärden som tolkas som pågående körning.
- `SUCCESS_STATUS_VALUES` - kommaseparerade statusvärden som tolkas som lyckad avslutning.
- `FAILURE_STATUS_VALUES` - kommaseparerade statusvärden som tolkas som misslyckad avslutning.
- `JAVA_OPTS` - extra JVM-flaggor för runtime-scriptet.

## Bygg

```bash
./gradlew :inf-javabatch:build
./gradlew :inf-javabatch:packageRpm
```

RPM-filen skapas i `inf-javabatch/build/distributions/`.