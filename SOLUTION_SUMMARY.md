# Lösning: Runner tillbaka in i Spring Boot-jaren

## Mål

Ta bort kravet på extern `inf-javabatch-runner.jar` och i stället paketera hela runnern i `inv-batch-test-app.jar`.

## Genomförda ändringar

1. `inf-batch-runner-api` är borttagen från repot.
2. API-klasserna (`infrastruktur.batch.api`) ligger nu i `inf-javabatch-runner`.
3. `inv-batch-test-app` beror nu på `project(':inf-javabatch-runner')`.
4. Startscriptet kör nu appen med `java -jar` utan `loader.path`/extern classpath.
5. Dockerfilen kräver inte längre katalog för extern runner-jar.
6. Klassladdningslogik för SPI/ServiceLoader är borttagen och integrationen använder API-fasader.

## Berörda filer

- `settings.gradle`
- `inf-javabatch-runner/build.gradle`
- `inf-javabatch-runner/src/main/java/infrastruktur/batch/api/*`
- `inv-batch-test-app/build.gradle`
- `inv-batch-test-app/run-inv-batch-test-app.sh`
- `inv-batch-test-app/Dockerfile`
- `inv-batch-test-app/RUNNER_JAR_SETUP.md`

## Effekt

- En artefakt att deploya: `inv-batch-test-app.jar`.
- Inga separata volume mounts eller classpath-injektioner för runner behövs.
- API + implementation av runner levereras tillsammans i samma Spring Boot-applikation.
- Färre rörliga delar (inga service-filer, ingen fallback-reflektion) ger enklare felsökning.
