# Runner setup (inbyggd i app-jaren)

`inv-batch-test-app` bygger nu in hela `inf-javabatch-runner` i Spring Boot-jaren.

Det betyder:

- ingen separat `inf-javabatch-runner.jar` behöver monteras
- ingen `RUNNER_DIR` behövs
- startscriptet kör bara `java -jar /app/inv-batch-test-app.jar`

## Lokal körning

```bash
./gradlew :inv-batch-test-app:bootJar
java -jar inv-batch-test-app/build/libs/inv-batch-test-app.jar
```

## Docker/Kubernetes

Containern behöver endast `inv-batch-test-app.jar`. Tidigare volume-mount för runner-jar kan tas bort.
