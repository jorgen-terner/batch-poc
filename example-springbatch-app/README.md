# example-springbatch-app

En minimal Spring Batch-applikation som kan startas via `op-proxy-app`.

Jobbet gör följande:
1. Startar
2. Sover i `SLEEP_SECONDS`
3. Upprepar sleep `EXTRA` gånger
4. Loggar efter varje sleep-runda
4. Avslutar

## Bygg lokalt

Kör från repo-roten:

```bash
./gradlew :example-springbatch-app:build -x test
```

JAR byggs till:

`example-springbatch-app/build/libs/example-springbatch-app-0.1.0.jar`

## Bygg image i OpenShift

Kör från repo-roten:

```bash
oc new-build --name=example-springbatch-app --binary=true --strategy=docker --to=example-springbatch-app:latest
oc patch bc/example-springbatch-app -p '{"spec":{"strategy":{"dockerStrategy":{"dockerfilePath":"example-springbatch-app/Dockerfile"}}}}'
oc start-build example-springbatch-app --from-dir=. --follow
```

## Registrera template

```bash
oc apply -f example-springbatch-app/template.yaml
```

Template-namn:

`example-springbatch-template`

## Starta via op-proxy-app

```bash
curl -sS -X POST "http://localhost:8080/api/templates/example-springbatch-template/start" \
  -H "Content-Type: application/json" \
  --data-raw '{
    "clientRequestId": "demo-1",
    "parameters": [
      { "name": "SLEEP_SECONDS", "value": "5" },
      { "name": "EXTRA", "value": "3" }
    ]
  }'
```

När jobbet startar loggar den ungefär:

- `Spring Batch example job started. Will sleep 3 time(s), 5 second(s) each time.`
- `Sleep round 1/3 started.`
- `Sleep round 1/3 finished.`
- `Sleep round 2/3 started.`
- `Sleep round 2/3 finished.`
- `Sleep round 3/3 started.`
- `Sleep round 3/3 finished.`
