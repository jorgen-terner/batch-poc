# Uppdaterat batchflöde

Detta dokument innehåller fyra versioner av samma flöde:

- en presentationsvänlig översikt
- en teknisk variant med RBAC, cache och monitorering
- en arkitekturvariant med tydlig ansvarsfördelning
- en fältvariant som visar vad som kommer från request respektive `ConfigMap`

## 1. Presentationsvänlig översikt

Det här diagrammet är avsett för verksamhetsdialog, presentationer och snabb onboarding.

```mermaid
flowchart LR
    caller[Beställare eller API-klient] -->|POST /api/jobs\nconfigMapName\nvalfria request-overrides| ibj[inf-batch-job]

    cm[(Gemensam ConfigMap\nexempel: inf-batch-javabatch-config)]
    job[[Kubernetes Job]]
    pod[(Job Pod)]
    runtime[Vald batch-image\nexempel: inf-javabatch\neller annan image]

    ibj -->|läser konfiguration| cm
    cm -->|innehåller till exempel image, command\noch runtime-parametrar| ibj
    ibj -->|skapar Job| job
    job --> pod
    cm -->|samma ConfigMap injiceras| pod
    pod -->|startar container| runtime
```

### Tolkning

- `configMapName` är primär indata till `inf-batch-job`.
- `inf-batch-job` använder `ConfigMap` för att avgöra hur `Job` ska skapas.
- Den image som körs kan vara `inf-javabatch`, men även andra batch-images.
- Samma `ConfigMap` används både av `inf-batch-job` och av den startade batch-containern.

## 2. Teknisk variant

Det här diagrammet visar implementationen mer detaljerat, inklusive cache, RBAC och monitorering.

```mermaid
flowchart LR
    caller[API-klient] -->|POST /api/jobs\nconfigMapName\nenv overrides\nvalfri image override| api[inf-batch-job API]

    subgraph ns[Namespace i OpenShift eller Kubernetes]
        cm[(ConfigMap)]
        saJob[ServiceAccount\ninf-batch-job]
        cache[(ConfigMap cache)]
        kjob[[Kubernetes Job]]
        pod[(Job Pod)]
        saPod[ServiceAccount\nför Job Pod]
    end

    subgraph img[Batch-images]
        img1[inf-javabatch]
        img2[annan batch-image]
    end

    subgraph mon[Monitorering]
        metrics[Metrics reporter]
        async[Async monitorering\nför t.ex. JAVABATCH]
    end

    api -->|läser configMapName| cm
    cm -->|hämtas via Kubernetes API| api
    api -->|lagrar eller återanvänder| cache
    cache -->|configMapData| api

    api -->|kombinerar ConfigMap\nmed request-overrides| build[Bygg Job-spec]
    build -->|sätter image, command, ttl,\nparallelism, completions| kjob
    build -->|sätter label configMap| kjob
    build -->|sätter envFrom configMapRef| pod
    build -->|sätter env overrides| pod

    img1 -. väljs via image eller ConfigMap .-> kjob
    img2 -. väljs via image eller ConfigMap .-> kjob

    kjob --> pod
    pod --> saPod
    saPod -->|behover get pa ConfigMaps| cm
    saJob -. create/get/list/delete Jobs .-> kjob

    pod -->|kör vald image| runtime[Batch-container]
    runtime -->|läser miljövariabler\nfrån samma ConfigMap| cm

    api -->|BATCH_TYP styr reporter| metrics
    metrics --> async
    async -->|läser status och skickar statistik| kjob
    api -->|kan återläsa ConfigMap\nför metadata eller restart| cm
```

### Teknisk tolkning

- `inf-batch-job` läser `ConfigMap` via Kubernetes API och cachar resultatet.
- `Job` byggs från `ConfigMap` plus eventuella request-overrides.
- `image` kan komma från requesten eller från `ConfigMap`.
- `Job`-podden får `envFrom` med samma `ConfigMap`, så batch-containern läser samma värden vid runtime.
- `ServiceAccount` för själva `Job`-podden behöver rätt att läsa `ConfigMaps`.
- `BATCH_TYP` kan styra vilken monitorering eller rapportering som aktiveras.

## 3. Arkitekturreview med ansvarsfördelning

Det här diagrammet gör det tydligt vad som är orkestrering, vad som är plattform och vad som är batch-applikationens eget ansvar.

```mermaid
flowchart LR
    caller[Anropare] -->|jobName\nconfigMapName\nrequest-overrides| ibj

    subgraph orchestrator[Ansvar: inf-batch-job]
        ibj[inf-batch-job]
        readcm[Läser ConfigMap och tolkar job-config]
        buildjob[Bygger Kubernetes Job-spec]
        monitor[Initierar monitorering\nvid behov]
    end

    subgraph platform[Ansvar: Kubernetes eller OpenShift]
        cm[(ConfigMap)]
        job[[Kubernetes Job]]
        pod[(Job Pod)]
    end

    subgraph app[Ansvar: batch-image]
        image[Vald image\nexempel: inf-javabatch\neller annan batch-image]
        appcfg[Läser runtime-parametrar\nfrån miljövariabler]
        execute[Kör batchlogik]
        report[Rapporterar status eller resultat\nenligt batchtyp]
    end

    ibj --> readcm
    readcm -->|hämtar configMapData| cm
    cm -->|image, command, BATCH_TYP,\nruntime-parametrar| readcm
    readcm --> buildjob
    buildjob -->|skapar Job med image, command,\nlabels, envFrom och overrides| job
    job --> pod
    pod -->|startar vald image| image

    cm -->|samma ConfigMap exponeras\ntill podden| pod
    image --> appcfg
    appcfg --> execute
    execute --> report

    ibj --> monitor
    monitor -. observerar Job och status .-> job

    classDef orch fill:#d9ecff,stroke:#2b6cb0,color:#102a43;
    classDef plat fill:#e6fffa,stroke:#2f855a,color:#123c2f;
    classDef appl fill:#fff7e6,stroke:#b7791f,color:#5f370e;

    class ibj,readcm,buildjob,monitor orch;
    class cm,job,pod platform;
    class image,appcfg,execute,report appl;
```

### Ansvar per del

- `inf-batch-job` ansvarar för att tolka requesten, läsa `ConfigMap`, bygga `Job` och initiera eventuell monitorering.
- Kubernetes eller OpenShift ansvarar för att köra `Job`, skapa podden och exponera `ConfigMap` till containern.
- batch-imagen ansvarar för att läsa sina runtime-parametrar och köra själva batchlogiken.
- batch-imagen behöver inte veta hur `Job` skapades, bara att rätt miljövariabler finns tillgängliga.
- `inf-batch-job` ska inte bära batchlogik för specifika images, utan bara orkestrera och komplettera med gemensam monitorering.

## 4. Fält från request respektive ConfigMap

Det här diagrammet visar vilka delar som typiskt kommer från API-requesten respektive från `ConfigMap`, och vilka värden som kan override:as.

```mermaid
flowchart LR
    req[API-request] --> reqFields
    cm[(ConfigMap)] --> cmFields

    reqFields[Vanliga request-falt\nconfigMapName\njobName\nenv-overrides\nimage override\ncommand override] --> merge[Sammanstallning i inf-batch-job]
    cmFields[Vanliga ConfigMap-falt\nimage\ncommand\njobName\nparallelism\ncompletions\nttlSecondsAfterFinished\nBATCH_TYP\nruntime-parametrar] --> merge

    merge --> resolved[Resultat for Job-spec]
    resolved --> job[[Kubernetes Job]]
    resolved --> pod[(Job Pod)]

    reqOverride[Request kan override:a\nimage\ncommand\njobName\nenv] -.-> merge
    cmPrimary[ConfigMap ar normalt\nprimar kalla for grundconfig] -.-> merge

    pod --> runtime[Batch-container]
    cm -->|envFrom| pod
```

### Fälttolkning

- `configMapName` kommer från requesten och används för att slå upp rätt `ConfigMap`.
- Grundkonfiguration som `image`, `command`, `parallelism`, `completions`, `ttlSecondsAfterFinished` och `BATCH_TYP` kommer normalt från `ConfigMap`.
- Requesten kan override:a till exempel `image`, `command`, `jobName` och extra `env`.
- Batch-containern får fortfarande samma `ConfigMap` vid runtime, även om vissa fält har override:ats i `Job`-specen.