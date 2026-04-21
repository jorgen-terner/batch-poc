# RFC: Template/Run API for op-proxy-app (v2)

Detta dokument beskriver ett konkret forslag for att införa ett template-baserat API parallellt med dagens suspended-job API.

Malen ar:

1. Ny v2-modell byggd runt `template` och `run`.
2. Maximal delning av gemensam logik.
3. Enkel avveckling av suspended-logik senare utan stor omskrivning.
4. Konsekvent namngivning med suffix `VO` (inte DTO).

## 1. API-kontrakt (v2)

### 1.1 Skapa ny run från template

`POST /api/v2/templates/{templateName}/runs`

Request body (`CreateRunRequestVO`):

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

Response (`RunActionResponseVO`):

```json
{
  "templateName": "inv-javabatch",
  "runName": "inv-javabatch-20260420101530-a1b2c3",
  "clientRequestId": "order-4711",
  "action": "create",
  "state": "PENDING",
  "message": "Run created",
  "createdAt": "2026-04-20T10:15:30Z"
}
```

### 1.2 Hamta run-status

`GET /api/v2/runs/{runName}`

Response (`RunStatusResponseVO`):

```json
{
  "templateName": "inv-javabatch",
  "runName": "inv-javabatch-20260420101530-a1b2c3",
  "phase": "RUNNING",
  "activePods": 1,
  "succeededPods": 0,
  "failedPods": 0,
  "startTime": "2026-04-20T10:15:35Z",
  "completionTime": null,
  "elapsedSeconds": 42
}
```

### 1.3 Avbryt run

`POST /api/v2/runs/{runName}/cancel`

Request body (`CancelRunRequestVO`):

```json
{
  "deletePods": false
}
```

Response (`RunActionResponseVO`):

```json
{
  "templateName": "inv-javabatch",
  "runName": "inv-javabatch-20260420101530-a1b2c3",
  "action": "cancel",
  "state": "CANCELLED",
  "message": "Run cancelled",
  "createdAt": "2026-04-20T10:20:00Z"
}
```

## 2. Namnstrategi

`runName` skickas inte in av klienten. Servern genererar alltid ett nytt `runName` och returnerar det i svaret.

Regler:

1. `runName` genereras alltid av servern.
2. `clientRequestId` kan anvandas for korrelation och framtida idempotens per `templateName + clientRequestId`.
3. Namngenereringen inkluderar tidsdel och kort random-del for att minska risk for kollisioner.

Detta minskar API-logik och validering, samtidigt som samtidiga korningar blir enklare.

## 3. VO-kontrakt (nya records)

Forelagna nya modelklasser under `infrastruktur.batch.model`:

1. `CreateRunRequestVO`
2. `CancelRunRequestVO`
3. `RunActionResponseVO`
4. `RunStatusResponseVO`

Notera: suffix `VO` anvands konsekvent.

## 4. Resource- och service-uppdelning

### 4.1 Nya API-resurser

1. `TemplateRunResource` (v2)
2. `RunQueryResource` (v2) - kan slas ihop med `TemplateRunResource` om ni vill ha fa klasser

### 4.2 Tjanster

1. `TemplateRunService`
2. `RunQueryService`
3. `RunCancellationService`

### 4.3 Legacy kvar tillfälligt

1. Behall nuvarande `JobResource` for v1-endpoints.
2. Hall nuvarande `JobControlService` for suspended-flodet.

## 5. Gemensam logik att dela

Foljande delar kan delas mellan v1 och v2:

1. Parameter-normalisering och validering
2. Timeout-validering
3. Fas-resolver (RUNNING, SUCCEEDED, FAILED, PENDING)
4. Kubernetes access (las Job/Pod, skapa Job, ta bort Job/Pod)

Forslag: extrahera delad kod till mindre komponenter i stallet for att lata `JobControlService` vaxa.

## 6. Exitkoder och CLI (forslag)

Behall befintliga exitkoder i v1.

For v2-CLI (om ni bygger ett separat kommando) bor `SUSPENDED` inte vara central status:

1. `0` = `SUCCEEDED`
2. `10` = `RUNNING` eller `PENDING`
3. `2` = `FAILED`
4. `5` = `CANCELLED`
5. `4` = `UNKNOWN`

## 7. Migreringsplan

### Fas 1: Introducera v2 utan brytning

1. Lagga till nya v2-endpoints och VO-klasser.
2. Behall v1-endpoints oforandrade.
3. Dokumentera v2 som rekommenderad väg for nya klienter.

### Fas 2: Flytta klienter

1. Nya integrationer använder enbart `/api/v2/templates/.../runs`.
2. Existerande klienter migreras stegvis.

### Fas 3: Avveckla suspended-logik

1. Markera v1 som deprecated.
2. Ta bort `JobResource` (v1) och suspended-specifik logik i `JobControlService`.
3. Behall delade komponenter (status, parameterhantering).

## 8. Forsta implementation cut (praktiskt nasta steg)

Minsta leverans som ger affarsnytta snabbt:

1. `POST /api/v2/templates/{templateName}/runs`
2. `GET /api/v2/runs/{runName}`
3. `POST /api/v2/runs/{runName}/cancel`
4. Nya VO-klasser enligt sektion 3

Detta racker for att starta, folja och avbryta template-baserade korningar.
