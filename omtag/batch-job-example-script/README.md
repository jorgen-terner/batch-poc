# batch-job-example-script

Exempel for OpenShift/Kubernetes dar ett suspended Job styrs av batch-job-app.

Innehall:
- worker.sh: Bash-batchscript som gor arbete och rapporterar till report-endpoint.
- suspended-job-openshift.yaml: Job-definition med spec.suspend=true.
- setup-openshift-example.sh: Skapar ConfigMap fran worker.sh och applicerar Job.
- batch-job-control.sh: Klientscript mot batch-job-app API (start/stop/restart/status/metrics).

## 1) Skapa suspended Job i OpenShift

Forutsattningar:
- oc CLI ar installerad och inloggad
- batch-job-app ar deployad i klustret

Kommando:

./setup-openshift-example.sh <namespace> <job-name> <batch-app-base-url>

Exempel:

./setup-openshift-example.sh default sample-batch-job http://batch-job-app.default.svc.cluster.local:8080

Detta gor:
1. Skapar/uppdaterar ConfigMap sample-batch-script fran worker.sh
2. Applicerar suspended Job
3. Satter miljovariabler pa Job

## 2) Styr Job via samma granssnitt som batch-job-app exponerar

Anvand batch-job-control.sh:

./batch-job-control.sh start default sample-batch-job http://localhost:8080
./batch-job-control.sh status default sample-batch-job http://localhost:8080
./batch-job-control.sh metrics default sample-batch-job http://localhost:8080
./batch-job-control.sh stop default sample-batch-job http://localhost:8080
./batch-job-control.sh restart default sample-batch-job http://localhost:8080

## 3) Vad scriptet rapporterar

worker.sh postar JSON till:
/api/v1/jobs/{namespace}/{jobName}/report

Notera: report ar frivillig i batch-job-app. Exemplet skickar report for att visa hur affarsmetrics kan bifogas.

Payloaden foljer JobReportRequest:
- status: String
- metrics: Map<String, Double>
- attributes: Map<String, String>

Exempelstatusar:
- RUNNING under bearbetning
- SUCCEEDED nar klart
- FAILED vid signal/avbrott
