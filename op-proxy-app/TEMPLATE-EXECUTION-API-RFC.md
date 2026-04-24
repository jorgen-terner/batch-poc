# RFC: Template/Execution API for op-proxy-app (v2)

Detta dokument beskriver v2-kontraktet för template-baserade executions.

Malen ar:

1. Konsekvent namngivning: execution/start/stop.
2. Enkel klientmodell med tydliga state-overgangar.
3. Delad intern logik med v1 dar det ar rimligt.

## 1. API-kontrakt (v2)

### 1.1 Starta ny execution fran template

`POST /api/v2/templates/{templateName}/start`

Request body (`StartExecutionRequestVO`):

```json
{
  "clientRequestId": "order-4711",
  "timeoutSeconds": 1800,
  "parameters": [
    { "name": "ORDER_ID", "value": "4711" },
    { "name": "RUN_TYPE", "value": "FULL" }
  ]
}
```

Response (`ExecutionActionResponseVO`):

```json
{
  "templateName": "inv-javabatch",
  "executionName": "inv-javabatch-20260420101530-a1b2c3",
  "clientRequestId": "order-4711",
  "action": "start",
  "state": "PENDING",
  "message": "Execution started",
  "createdAt": "2026-04-20T10:15:30Z"
}
```

### 1.2 Hamta execution-status

`GET /api/v2/executions/{executionName}`

Response (`ExecutionStatusResponseVO`):

```json
{
  "templateName": "inv-javabatch",
  "executionName": "inv-javabatch-20260420101530-a1b2c3",
  "phase": "RUNNING",
  "activePods": 1,
  "succeededPods": 0,
  "failedPods": 0,
  "startTime": "2026-04-20T10:15:35Z",
  "completionTime": null,
  "elapsedSeconds": 42
}
```

### 1.3 Stoppa execution

`POST /api/v2/executions/{executionName}/stop`

Ingen request body.

Response (`ExecutionActionResponseVO`):

```json
{
  "templateName": "inv-javabatch",
  "executionName": "inv-javabatch-20260420101530-a1b2c3",
  "action": "stop",
  "state": "STOPPED",
  "message": "Execution stopped (graceful stop), execution job deleted",
  "createdAt": "2026-04-20T10:20:00Z"
}
```

## 2. Namnstrategi

`executionName` skickas inte in av klienten. `executionName` sätts av processad OpenShift-template via `metadata.name`.

Regler:

1. `executionName` styrs av template-forfattaren i `template.yaml`.
2. För multi-instance kan templaten generera suffix (t.ex. `generate: expression`).
3. För single-instance används fast namn; om jobb med samma namn redan finns returneras `AlreadyExists` från Kubernetes.
4. `clientRequestId` kan användas för korrelation och framtida idempotens per `templateName + clientRequestId`.

## 3. VO-kontrakt (records)

Modelklasser under `infrastruktur.batch.model`:

1. `StartExecutionRequestVO`
2. `ExecutionActionResponseVO`
3. `ExecutionStatusResponseVO`

## 4. Resurser och tjänster

1. `TemplateExecutionResource` (v2)
2. `TemplateExecutionService`

Legacy v1 (`JobResource` och `JobControlService`) lever vidare parallellt tills klienter är migrerade.

## 5. Exit-koder och CLI (v2)

1. `0` = `SUCCEEDED` eller `STOPPED`
2. `10` = `RUNNING` eller `PENDING`
3. `2` = `FAILED`
4. `4` = `UNKNOWN`
5. `124` = timeout i watch-läge

## 6. Migrering

### Fas 1

1. Introducera v2-endpoints enligt sektion 1.
2. Behåll v1-endpoints oförändrade.

### Fas 2

1. Migrera klienter till `/api/v2/templates/.../start` och `/api/v2/executions/...`.

### Fas 3

1. Markera v1 som deprecated.
2. Avveckla v1 när inga klienter återstår.
