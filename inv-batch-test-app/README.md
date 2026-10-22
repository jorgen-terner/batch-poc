# inv-batch-test-app migration (WildFly/EAR -> Spring Boot on Kubernetes)

Den här modulen är migrerad från WildFly/EAR-körning till fristående Spring Boot-applikation för Kubernetes.

## Vad som ändrats

### 1) Build & packaging

- [build.gradle](./build.gradle)
  - Bytt från `war`-plugin till `java` + Spring Boot.
  - Konfigurerat Java 21 toolchain.
  - Bytt beroenden till Spring Boot 3-kompatibla varianter.
  - Lagt till Atomikos starter för XA/JTA (`com.atomikos:transactions-spring-boot3-starter`).
  - Lagt till DB2 JDBC-driver (`com.ibm.db2:jcc`).
  - Ersatt Springfox med Springdoc OpenAPI.
  - Konfigurerat `bootJar` (`inv-batch-test-app.jar`) som huvudartefakt.

### 2) Applikationsstart

- [BatchApplication.java](./src/main/java/test/inv/batch/BatchApplication.java)
  - Tagit bort servlet-container-arv (`SpringBootServletInitializer`, `WebApplicationInitializer`).
  - Applikationen startas nu som vanlig Spring Boot JAR.

### 3) Transaktioner

- [TransactionConfiguration.java](./src/main/java/test/inv/batch/conf/TransactionConfiguration.java)
  - XA/JTA hanteras i Spring Boot via Atomikos i `xa`-profilen.
  - `JtaTransactionManager` tillåter custom isolation levels (motsvarar tidigare WildFly-beteende).

### 4) API-dokumentation

- [SwaggerConfig.java](./src/main/java/test/inv/batch/conf/SwaggerConfig.java)
  - Migrerad från Springfox till Springdoc (`OpenAPI` bean).
- [BatchRestControllerMyApp.java](./src/main/java/test/inv/batch/rest/myapp/BatchRestControllerMyApp.java)
  - Ersatt Swagger 2-annoteringar med OpenAPI 3-annoteringar.

### 5) Runtime-konfiguration för Kubernetes

- [application.properties](./src/main/resources/application.properties)
  - Context-path satt till `/inv-batch-app/batch` för att motsvara tidigare WildFly `context-root + servlet-mapping`.
  - Aktiverat liveness/readiness probes via actuator.
  - Konfigurerat OpenAPI-endpoints:
    - `/inv-batch-app/batch/swagger`
    - `/inv-batch-app/batch/swagger/v2/api-docs`
  - Tagit bort `spring.main.web-application-type=none` så HTTP-endpoints exponeras.
  - Delat upp databas-konfiguration i profiler:
    - [application-h2.properties](./src/main/resources/application-h2.properties) (fallback/test)
    - [application-xa.properties](./src/main/resources/application-xa.properties) (DB2 XA för Kubernetes)

### 6) Borttagna WildFly/Jakarta EE deploymentfiler

- Borttagna filer:
  - src/main/webapp/WEB-INF/web.xml
  - src/main/webapp/WEB-INF/dispatcher-servlet.xml
  - src/main/webapp/WEB-INF/jboss-web.xml
  - src/main/webapp/WEB-INF/beans.xml
  - src/main/java/test/inv/batch/rest/JSONConfigurator.java
  - src/main/resources/META-INF/services/jakarta.ws.rs.ext.Providers

### 7) Container + Kubernetes manifests

- [Dockerfile](./Dockerfile)
  - Kör `inv-batch-test-app.jar` i Java 21 runtime.
- [k8s/configmap.yaml](./k8s/configmap.yaml)
- [k8s/deployment.yaml](./k8s/deployment.yaml)
- [k8s/secret.yaml](./k8s/secret.yaml)
- [k8s/service.yaml](./k8s/service.yaml)
  - Innehåller resursgränser samt liveness/readiness probes.
  - DB2 XA-konfig läses via env vars:
    - `INV_DBHOST`, `INV_DBPORT`, `INV_DBNAME`
    - `INV_BATCH2_USERNAME`, `INV_BATCH2_PASSWORD`

### 8) EAR-modul avvecklad

- [settings.gradle](../settings.gradle)
  - `inv-batch-test-ear` är kommenterad och tas inte längre med i aktiv build.
- [inv-batch-test-ear/build.gradle](../inv-batch-test-ear/build.gradle)
  - Markerad som legacy/retired modul.

## Bygg och verifiering

Kör från reporoten:

```bash
./gradlew clean assemble
```

För lokal körning av appen:

```bash
./gradlew :inv-batch-test-app:bootRun
```

Exempel endpoint:

- Start batch: `GET /inv-batch-app/batch/myapp/start`
- Health readiness: `GET /inv-batch-app/batch/actuator/health/readiness`
