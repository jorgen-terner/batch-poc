# inf-javabatch

Quarkus-baserad Job-worker som körs i en Kubernetes Job-container och anropar externa hook-URL:er via miljövariablerna `START` och `STOP`.

## Beteende

- Vid containerstart anropas `START` via HTTP GET.
- Processen hålls sedan igång tills podden termineras.
- Vid terminering anropas `STOP` via HTTP GET.
- Efter `STOP` väntar processen `STOP_WAIT_SECONDS` innan den lämnar över till poddens normala nedstängning.

## Miljövariabler

- `START` - obligatorisk URL som anropas när Jobbet startar.
- `STOP` - valfri URL som anropas när podden termineras.
- `HTTP_TIMEOUT_SECONDS` - timeout per hook-anrop, default `30`.
- `STOP_WAIT_SECONDS` - väntetid efter `STOP`, default `15`.
- `JAVA_OPTS` - extra JVM-flaggor för runtime-scriptet.

## Bygg

```bash
./gradlew :inf-javabatch:build
./gradlew :inf-javabatch:packageRpm
```

RPM-filen skapas i `inf-javabatch/build/distributions/`.