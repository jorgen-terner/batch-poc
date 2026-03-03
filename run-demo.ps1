param(
  [string]$Namespace = "batch",
  [string]$BaseRelease = "batch-demo",
  [string]$SpringRelease = "sb-example",
  [string]$ScriptRelease = "script-example",
  [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

function Test-CommandExists {
  param([string]$CommandName)
  $cmd = Get-Command $CommandName -ErrorAction SilentlyContinue
  return $null -ne $cmd
}

function Wait-DeploymentReady {
  param(
    [string]$Namespace,
    [string]$Name,
    [int]$TimeoutSeconds = 180
  )

  Write-Host "Väntar på deployment/$Name i namespace $Namespace..."
  kubectl -n $Namespace rollout status deployment/$Name --timeout="${TimeoutSeconds}s" | Out-Host
}

if (-not (Test-CommandExists -CommandName "kubectl")) {
  throw "kubectl hittades inte i PATH."
}

if (-not (Test-CommandExists -CommandName "helm")) {
  throw "helm hittades inte i PATH."
}

if (-not $SkipInstall) {
  Write-Host "Installerar/uppgraderar Helm charts..."
  helm upgrade --install $BaseRelease ./helm/k8s-jobs-example -n $Namespace --create-namespace | Out-Host
  helm upgrade --install $SpringRelease ./spring-batch-example -n $Namespace | Out-Host
  helm upgrade --install $ScriptRelease ./script-example -n $Namespace | Out-Host
}

$triggerDeployment = "$BaseRelease-k8s-jobs-example-trigger-api"
$triggerService = "$BaseRelease-k8s-jobs-example-trigger-api"

Wait-DeploymentReady -Namespace $Namespace -Name $triggerDeployment

Write-Host "Startar port-forward mot service/$triggerService..."
$pfJob = Start-Job -ScriptBlock {
  param($Namespace, $Service)
  kubectl -n $Namespace port-forward "svc/$Service" 8080:8080
} -ArgumentList $Namespace, $triggerService

Start-Sleep -Seconds 3

try {
  $springCron = "$SpringRelease-spring-batch-example-template"
  $scriptCron = "$ScriptRelease-script-example-template"

  Write-Host "Triggar Spring Batch-jobb från $springCron"
  $springResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/trigger/$springCron"
  $springJobName = $springResponse.jobName

  Write-Host "Triggar Script-jobb från $scriptCron"
  $scriptResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/trigger/$scriptCron"
  $scriptJobName = $scriptResponse.jobName

  Write-Host "Skapade jobb:"
  Write-Host "- Spring: $springJobName"
  Write-Host "- Script: $scriptJobName"

  Start-Sleep -Seconds 2

  Write-Host "Hämtar status via trigger-API"
  $springStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/jobs/$springJobName"
  $scriptStatus = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/jobs/$scriptJobName"

  Write-Host "Spring status: $($springStatus.phase) (active=$($springStatus.active), succeeded=$($springStatus.succeeded), failed=$($springStatus.failed))"
  Write-Host "Script status: $($scriptStatus.phase) (active=$($scriptStatus.active), succeeded=$($scriptStatus.succeeded), failed=$($scriptStatus.failed))"

  Write-Host "\nTips:"
  Write-Host "kubectl get jobs -n $Namespace"
  Write-Host "kubectl logs job/$springJobName -n $Namespace"
  Write-Host "kubectl logs job/$scriptJobName -n $Namespace"
}
finally {
  if ($pfJob) {
    Stop-Job -Job $pfJob -ErrorAction SilentlyContinue | Out-Null
    Remove-Job -Job $pfJob -Force -ErrorAction SilentlyContinue | Out-Null
  }
}
