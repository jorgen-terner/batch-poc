{{- define "inf-batch-job.name.nginx" }}
{{- .Release.Name }}-nginx
{{- end }}

{{- define "inf-batch-job.name.pvc" }}
{{- .Release.Name }}-logs
{{- end }}

{{- define "inf-batch-job.fullname" }}
{{- .Release.Name }}
{{- end }}

{{- define "inf-batch-job.selectorLabels" }}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "inf-batch-job.labels" }}
helm.sh/chart: {{ .Chart.Name }}
app.kubernetes.io/part-of: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
