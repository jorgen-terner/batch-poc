# inf-javabatch

Quarkus-baserad Job-worker som körs i en Kubernetes Job-container och anropar externa hook-URL:er via miljövariablerna `START` och `STOP`.

## Beteende

- Vid containerstart anropas `START` via HTTP GET.
- Processen hålls sedan igång tills podden termineras.
- Vid terminering anropas `STATUS` via HTTP GET för att läsa `BatchStatus`.
- `STOP` anropas bara när `STATUS` indikerar aktiv körning (`STARTING` eller `STARTED`).
- Efter `STOP` väntar processen `STOP_WAIT_SECONDS` innan den lämnar över till poddens normala nedstängning.

## Miljövariabler

- `START` - obligatorisk URL som anropas när Jobbet startar.
- `STATUS` - valfri URL som returnerar aktuell `org.springframework.batch.core.BatchStatus`.
- `STOP` - valfri URL som anropas när podden termineras.
- `HTTP_TIMEOUT_SECONDS` - timeout per hook-anrop, default `30`.
- `STOP_WAIT_SECONDS` - väntetid efter `STOP`, default `15`.
- `FAIL_OPEN_ON_STATUS_ERROR` - default `true`. Om `STATUS` inte kan läsas avgör denna om `STOP` ändå ska anropas.
- `JAVA_OPTS` - extra JVM-flaggor för runtime-scriptet.

## Bygg

```bash
./gradlew :inf-javabatch:build
./gradlew :inf-javabatch:packageRpm
```

RPM-filen skapas i `inf-javabatch/build/distributions/`.