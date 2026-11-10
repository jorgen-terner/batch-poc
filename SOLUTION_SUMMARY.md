# Lösning: Externa inf-javabatch-runner.jar-filer

## Problem
`inv-batch-test-app` kunde inte ladda `inf-javabatch-runner.jar` som en separat jar-fil på disk. Spring Boot's `PropertiesLauncher` med `-Dloader.path` kunde inte göra servicefiler från den externa jar-filen tillgängliga för Java's `ServiceLoader`.

```
Error: No StepProgressListener implementation found on classpath.
```

## Rotorsak
1. Spring Boot's `PropertiesLauncher` laddar jar-filer från `-Dloader.path` i en separat `LaunchedClassLoader`
2. Java's `ServiceLoader` använder `Thread.currentContext ClassLoader` för att lokalisera servicefiler (META-INF/services/*)
3. Service files i externa jar-filer nåddes inte av kontextklassloadern

## Lösning

### 1. Klassvägshantering i Shell-skript (`run-inv-batch-test-app.sh`)

**Tidigare (fungerar inte):**
```bash
exec java -Dloader.path="${LOADER_PATH}" -cp "${APP_JAR}" \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

**Nytt (fungerar):**
```bash
# Bygg classpath med runner-jar-filerna explicit
for jar in "${RUNNER_DIR}"/*.jar; do
  RUNNER_CLASSPATH="$RUNNER_CLASSPATH:$jar"
done

exec java \
  -Dloader.path="" \
  -cp "${APP_JAR}:${RUNNER_CLASSPATH}" \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

**Fördelar:**
- Runner-jar-filerna är på systemets klasspath, inte på `-Dloader.path`
- `ServiceLoader` kan hitta servicefiler via `SystemClassLoader`
- Enkelt, ingen specialkonfiguration behövs

### 2. ServiceLoader Fallback-logik

**Ny klassloader-strategi i `StepProgressListenerProvider` och `BatchRunnerProvider`:**

```java
public static StepProgressListener create(JobRegistry jobRegistry) {
    // Försök först med context class loader
    ServiceLoader<StepProgressListenerFactory> loader = 
        ServiceLoader.load(StepProgressListenerFactory.class);
    
    for (StepProgressListenerFactory factory : loader) {
        return factory.create(jobRegistry);
    }
    
    // Fallback: försök med system class loader
    ServiceLoader<StepProgressListenerFactory> systemLoader = 
        ServiceLoader.load(
            StepProgressListenerFactory.class,
            ClassLoader.getSystemClassLoader());
    
    for (StepProgressListenerFactory factory : systemLoader) {
        return factory.create(jobRegistry);
    }
    
    throw new IllegalStateException(...);
}
```

**Fördelar:**
- Robust mot olika ClassLoader-konfigurationer
- Fungerar både med traditionell classpath och Spring Boot's PropertiesLauncher
- Ingen brott på befintlig kod

### 3. Packagestruktur

Provider-klasserna flyttades från `test.inv.batch.runner` till `infrastruktur.batch.contract` för:
- Bättre cohesion (tillsammans med gränssnittsdefiniationerna)
- Klarare API-gränser
- Enklare import i användarkod

**Före:**
```
inf-batch-runner-api/
  src/main/java/infrastruktur/batch/contract/
    - StepProgressListener.java (interface)
    - StepProgressListenerFactory.java (interface)
    - BatchRunner.java (interface)
inv-batch-test-app/
  src/main/java/test/inv/batch/runner/
    - StepProgressListenerProvider.java (moved)
    - BatchRunnerProvider.java (moved)
```

**Efter:**
```
inf-batch-runner-api/
  src/main/java/infrastruktur/batch/contract/
    - StepProgressListener.java (interface)
    - StepProgressListenerFactory.java (interface)
    - BatchRunner.java (interface)
    - StepProgressListenerProvider.java (provider)
    - BatchRunnerProvider.java (provider)
```

## Filer som ändrades

1. **inv-batch-test-app/run-inv-batch-test-app.sh**
   - Uppdaterad för att bygga explicit classpath med runner-jar-filer
   - Bättre felhantering och diagnostik

2. **inf-batch-runner-api/src/main/java/infrastruktur/batch/contract/StepProgressListenerProvider.java** (ny)
   - Moved från inv-batch-test-app
   - Lagt till fallback ServiceLoader-logik

3. **inf-batch-runner-api/src/main/java/infrastruktur/batch/contract/BatchRunnerProvider.java** (ny)
   - Moved från inv-batch-test-app
   - Lagt till fallback ServiceLoader-logik

4. **inv-batch-test-app/src/main/java/test/inv/batch/configuration/myapp/BatchConfigurationMyApp.java**
   - Uppdaterad import för StepProgressListenerProvider

5. **inv-batch-test-app/src/main/java/test/inv/batch/runner/JobHookRunnerStartup.java**
   - Uppdaterad import för BatchRunnerProvider

6. **inv-batch-test-app/RUNNER_JAR_SETUP.md** (ny)
   - Detaljerad dokumentation för deployment-konfiguration
   - Kubernetes-exempel för alla deployment-varianter

## Hur man använder

### Lokal testning
```bash
./gradlew clean build

# Kopiera runner-jar
mkdir -p /tmp/inf-javabatch-runner
cp inf-javabatch-runner/build/libs/inf-javabatch-runner.jar /tmp/inf-javabatch-runner/

# Kör applikationen
export RUNNER_DIR=/tmp/inf-javabatch-runner
export APP_JAR=inv-batch-test-app/build/libs/inv-batch-test-app.jar
bash inv-batch-test-app/run-inv-batch-test-app.sh
```

### Docker
Dockerfile behöver inte ändras - montera runner-jar via volume:
```bash
docker run -v /local/path:/opt/inf-javabatch-runner \
  inv-batch-test-app:latest
```

### Kubernetes (Job)
Se `inv-batch-test-app/RUNNER_JAR_SETUP.md` för detaljerade exempel

## Fördelar med denna lösning

✅ **Enkelt**: Ingen egen klassladdningslogik behövs  
✅ **Modulär**: Kan byta `inf-javabatch-runner.jar` utan rebuild  
✅ **Robust**: Fallback-logik för olika ClassLoader-konfigurationer  
✅ **Standard**: Använder Java's inbyggda ServiceLoader SPI  
✅ **Portabel**: Fungerar på Linux, macOS, Windows och Docker  
✅ **Framtidsproofed**: Kompatibel med Java 9+ moduler i framtiden  

## Bakgrund

Java's `ServiceLoader` SPI är den standard Java använder för plugin-mekanismer och modulär arkitektur:
- Du deklarerar en service interface: `StepProgressListenerFactory`
- En implementering registreras via META-INF/services: `infrastruktur.batch.runner.DefaultStepProgressListenerFactory`
- En provider kollar och laddar dynamiskt: `StepProgressListenerProvider.create(...)`

Detta är samma mekanism som Spring Boot, Hibernate, JSON-B och många andra ramverk använder.

Problemet var att när Spring Boot's `PropertiesLauncher` läser jar-filer från `-Dloader.path`, skapar den en separat ClassLoader som kontextklassloadern inte kan nå, så `ServiceLoader.load()` hittar inte servicefilen.

Lösningen var att helt enkelt lägga jar-filerna på den regulära klassvägen (`-cp`) istället för på `-Dloader.path`. Detta låter `PropertiesLauncher` köra vanligt men fortfarande tillåter modularitet genom att runner-jar-filerna kan byta utan rebuild av appen.
