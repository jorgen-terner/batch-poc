# inf-javabatch-runner

Lättviktig Java-runner (JAR) för batch-hook-flödet:

- anropar `START`
- pollar `STATUS`
- anropar `STOP` vid shutdown när jobbet fortfarande är aktivt

Syftet är att ersätta separat `inf-javabatch`-process i podden med en liten fristående runner-JAR.

## Bygg

```bash
./gradlew :inf-javabatch-runner:jar
```

Artefakt:

- `inf-javabatch-runner/build/libs/inf-javabatch-runner.jar`

## Miljövariabler

Samma kontrakt som tidigare runner:

- `START` (required)
- `STATUS` (required)
- `STOP` (optional)
- `EXEC_ID_PARAM_NAME` (default `execId`)
- `HTTP_TIMEOUT_SECONDS` (default `30`)
- `COMMON_HEADERS`, `START_HEADERS`, `STATUS_HEADERS`, `STOP_HEADERS`
- `STATUS_POLL_INTERVAL_SECONDS` (default `5`)
- `MAX_POLL_SECONDS` (default `3600`)
- `STOP_WAIT_SECONDS` (default `15`)
- `FAIL_OPEN_ON_STATUS_ERROR` (default `true`)
- `MAX_STATUS_ERROR_RETRIES` (default `12`)
- `MAX_UNKNOWN_STATUS_RETRIES` (default `12`)
- `ACTIVE_STATUS_VALUES` (default `STARTING,STARTED,STOPPING`)
- `SUCCESS_STATUS_VALUES` (default `COMPLETED`)
- `FAILURE_STATUS_VALUES` (default `FAILED,ABANDONED,UNKNOWN,STOPPED`)
