# op-proxy-client

Java-baserad CLI-klient för att starta och bevaka batch-körningar via `op-proxy-app`.

Klienten kan bevaka körningar på två sätt:

- `polling` (standard)
- `SSE-strömning` med `--sse`

## Krav

- Java 21
- Gradle (eller använd wrapper från repo-roten: `./gradlew` eller `gradlew.bat`)
- En körande `op-proxy-app`

## Bygg klienten

Om du står i repo-roten (`batch-poc`):

```bash
./gradlew :op-proxy-client:clean :op-proxy-client:jar
```

På Windows (PowerShell/CMD):

```powershell
gradlew.bat :op-proxy-client:clean :op-proxy-client:jar
```

Byggd fat-jar hamnar i:

- `op-proxy-client/build/libs/op-proxy-client-0.1.0.jar`

## Kör klienten

Grundformat:

```bash
java -jar op-proxy-client/build/libs/op-proxy-client-0.1.0.jar <templateName> [flaggor]
```

Exempel (standard polling):

```bash
java -jar op-proxy-client/build/libs/op-proxy-client-0.1.0.jar my-template \
  --base-url http://localhost:8080 \
  --namespace prod \
  -p FOO=bar -p BATCH_SIZE=100
```

Exempel (SSE-strömning):

```bash
java -jar op-proxy-client/build/libs/op-proxy-client-0.1.0.jar my-template \
  --base-url http://localhost:8080 \
  --namespace prod \
  --sse --interval-seconds 3
```

## Vanliga flaggor

- `--base-url` bas-URL till `op-proxy-app` (default: `http://localhost:8080` eller miljövariabel `OP_PROXY_BASE_URL`)
- `--namespace` Kubernetes-namespace (default: `default`)
- `--client-request-id` valfritt klient-id som skickas med i startanropet
- `--timeout-seconds` sätter körningens timeout
- `-p`, `--parameter` template-parameter i format `NAME=VALUE` (kan anges flera gånger)
- `--interval-seconds` polling-/strömningsintervall i sekunder (default `5`, min `1`)
- `--watch-timeout-seconds` max bevakningstid (default `3600`, `0` = ingen timeout)
- `--sse` använd SSE-strömning i stället för polling
- `--show-logs` skriv ut pod-loggar när körning avslutas (pollingläge)
- `--logs-tail-lines` max antal loggrader per pod (default: alla)
- `-h`, `--help` visar hjälp

## Exit-koder

- `0` SUCCEEDED / STOPPED / CANCELLED
- `2` FAILED
- `3` SUSPENDED
- `4` UNKNOWN eller oväntat läge
- `124` timeout i watch-läge
- `130` avbruten (t.ex. Ctrl+C)
- `1` konfigurationsfel, valideringsfel eller nätverksfel

## Avbryt körning

Om klienten avbryts (t.ex. Ctrl+C) skickar den automatiskt ett stop-anrop för aktiv körning innan processen avslutas.

## Felsökning

- Kontrollera att `op-proxy-app` är nåbar på `--base-url`
- Kontrollera att template-namnet finns i målmiljön
- Sätt lägre `--interval-seconds` för tätare statusuppdatering
- Använd `--show-logs` i pollingläge för snabbare felsökning av pod-output
