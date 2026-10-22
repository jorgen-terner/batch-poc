# inv-batch-test-ear (legacy)

Den här modulen är avvecklad efter migrering till Spring Boot på Kubernetes.

- Aktiv runtime ligger nu i [inv-batch-test-app](../inv-batch-test-app/README.md).
- `inv-batch-test-ear` är bortkopplad från aktiv build i [settings.gradle](../settings.gradle).
- [build.gradle](./build.gradle) innehåller endast en legacy-info task.
