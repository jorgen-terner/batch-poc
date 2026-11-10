# Konfiguration av extern inf-javabatch-runner.jar

Detta dokument beskriver hur du konfigurerar `inv-batch-test-app` att använda `inf-javabatch-runner.jar` som en separat jar-fil istället för att ha den inbakad i applikationen.

## Vad ändrades

### 1. Shell-script (`run-inv-batch-test-app.sh`)

Scriptet uppdaterades för att:
- Automatiskt hitta alla `*.jar`-filer i `${RUNNER_DIR}`
- Lägga dem på systemets klassväg (classpath) så att `ServiceLoader` kan hitta dem
- Använda `PropertiesLauncher` utan `-Dloader.path` (eftersom jar-filerna redan är på classpath)

**Nyckelförändring:**
```bash
# Tidigare (fungerar inte med ServiceLoader):
exec java ${JAVA_OPTS:-} -Dloader.path="${LOADER_PATH}" -cp "${APP_JAR}" \
  org.springframework.boot.loader.launch.PropertiesLauncher

# Nytt (fungerar):
exec java ${JAVA_OPTS:-} \
  -Dloader.path="" \
  -cp "${APP_JAR}:${RUNNER_CLASSPATH}" \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

### 2. Java ServiceLoader-fallback (`StepProgressListenerProvider.java`)

Klassloadern uppdaterades för att:
- Först försöka ladda `StepProgressListenerFactory` med context class loader
- Om det misslyckas, försöka med SystemClassLoader
- Detta gör att tjänsten kan hittas oavsett hur ClassLoaders är konfigurerade

```java
// Fallback: försök med system class loader
ServiceLoader<StepProgressListenerFactory> systemLoader = ServiceLoader.load(
    StepProgressListenerFactory.class,
    ClassLoader.getSystemClassLoader());

for (StepProgressListenerFactory factory : systemLoader) {
    return factory.create(jobRegistry);
}
```

## Deployment-konfiguration

### Docker

Dockerfile är redan konfigurerad för detta:

```dockerfile
COPY run-inv-batch-test-app.sh /usr/local/bin/run-inv-batch-test-app.sh
RUN mkdir -p /opt/inf-javabatch-runner
```

### Kubernetes - Job

För att använda detta i ett Kubernetes Job, måste du montera runner-jar-filen som en ConfigMap eller Secret, eller använda en Init Container:

#### Alternativ 1: Via Init Container (rekommenderat)

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: inv-batch-test-app-job
spec:
  template:
    spec:
      initContainers:
        - name: setup-runner
          image: busybox:latest
          command:
            - 'sh'
            - '-c'
            - 'cp /runner-src/inf-javabatch-runner.jar /opt/inf-javabatch-runner/'
          volumeMounts:
            - name: runner-jar-vol
              mountPath: /runner-src
            - name: runner-dir
              mountPath: /opt/inf-javabatch-runner
      containers:
        - name: inv-batch-test-app-job
          image: inv-batch-test-app:latest
          command:
            - "/usr/local/bin/run-inv-batch-test-app.sh"
          volumeMounts:
            - name: runner-dir
              mountPath: /opt/inf-javabatch-runner
      volumes:
        - name: runner-jar-vol
          configMap:
            name: inf-javabatch-runner-jar
            defaultMode: 0755
        - name: runner-dir
          emptyDir: {}
      restartPolicy: Never
```

#### Alternativ 2: Via hostPath (för lokal dev/test)

```yaml
volumes:
  - name: runner-dir
    hostPath:
      path: /tmp/inf-javabatch-runner
      type: Directory
```

#### Alternativ 3: Via Persistent Volume Claim

```yaml
volumes:
  - name: runner-dir
    persistentVolumeClaim:
      claimName: runner-jar-pvc
```

### Kubernetes - Deployment

Om du använder Deployment (för API-läge):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: inv-batch-test-app
spec:
  template:
    spec:
      containers:
        - name: inv-batch-test-app
          image: inv-batch-test-app:latest
          volumeMounts:
            - name: runner-dir
              mountPath: /opt/inf-javabatch-runner
      volumes:
        - name: runner-dir
          emptyDir: {}
```

## Lokal testning

För lokal testning utan Docker:

```bash
# Bygg projektet
./gradlew clean build

# Kopiera jar-filer
mkdir -p /tmp/inf-javabatch-runner
cp inf-javabatch-runner/build/libs/inf-javabatch-runner.jar /tmp/inf-javabatch-runner/

# Kör applikationen
export RUNNER_DIR=/tmp/inf-javabatch-runner
export APP_JAR=inv-batch-test-app/build/libs/inv-batch-test-app.jar
bash inv-batch-test-app/run-inv-batch-test-app.sh
```

## Miljövariabler

| Variabel | Standard | Beskrivning |
|----------|----------|-------------|
| `APP_JAR` | `/app/inv-batch-test-app.jar` | Sökväg till Spring Boot-jar-filen |
| `RUNNER_DIR` | `/opt/inf-javabatch-runner` | Directory där `inf-javabatch-runner.jar` ligger |
| `JAVA_OPTS` | (tom) | Ytterligare JVM-parametrar |

## Fördelar med denna lösning

✅ **Enkel**: Ingen egen klassladdningslogik behövs  
✅ **Modulär**: Kan byta `inf-javabatch-runner.jar` utan att bygga om appen  
✅ **Robust**: Använder Spring Boot's standard `PropertiesLauncher`  
✅ **Standard**: Utnyttjar Java's inbyggda `ServiceLoader` SPI-mekanism  
✅ **Portabel**: Fungerar på Linux, macOS, Windows och Docker  

## Felsökning

### Problem: "No StepProgressListener implementation found on classpath"

**Orsak**: Runner-jar-filen finns inte eller är inte på klassvägen.

**Lösning**:
1. Verifiera att `${RUNNER_DIR}` existerar och innehåller en `.jar`-fil:
   ```bash
   ls -la /opt/inf-javabatch-runner/
   ```

2. Kontrollera att `inf-javabatch-runner.jar` byggs korrekt:
   ```bash
   jar tf inf-javabatch-runner/build/libs/inf-javabatch-runner.jar | grep META-INF/services
   ```

3. Inspektera klassvägen i körningsfelet:
   ```
   Diagnostics: java.class.path=... loader.path.sysprop=...
   ```

### Problem: "Wrong type of jar"

**Orsak**: Du använde en fattig JAR istället för den överskrivna JAR-filen.

**Lösning**: Se till att du använder `inf-javabatch-runner.jar` från `build/libs/`, inte från `build/`.
