param(
  [string]$Namespace = "batch",
  [string]$Release = "batch-api",
  [string]$ImageName = "inf-batch-job",
  [string]$ImageTag = "latest",
  [switch]$SkipBuild,
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
    [string]$Deployment,
    [int]$TimeoutSeconds = 180
  )

  Write-Host "Väntar på deployment/$Deployment i namespace $Namespace..."
  kubectl -n $Namespace rollout status deployment/$Deployment --timeout="${TimeoutSeconds}s" | Out-Host
}

function Wait-HealthCheck {
  param([int]$TimeoutSeconds = 30)
  
  $start = Get-Date
  while ((Get-Date) - $start -lt [TimeSpan]::FromSeconds($TimeoutSeconds)) {
    try {
      $response = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -ErrorAction SilentlyContinue
      if ($response.status -eq "UP") {
        Write-Host "✓ API är redo"
        return
      }
    }
    catch {
      # API inte redo än
    }
    Start-Sleep -Seconds 1
  }
  Write-Host "⚠ API hälsa timeout - försöker ändå att köra test"
}

if (-not (Test-CommandExists -CommandName "kubectl")) {
  throw "kubectl hittades inte i PATH"
}

if (-not (Test-CommandExists -CommandName "helm")) {
  throw "helm hittades inte i PATH"
}

if (-not $SkipBuild) {
  Write-Host "=== Bygger Docker-image ==="
  docker build -t "${ImageName}:${ImageTag}" ./inf-batch-job | Out-Host
  if ($LASTEXITCODE -ne 0) {
    throw "Docker-build misslyckades"
  }
  Write-Host "✓ Docker-image byggd: ${ImageName}:${ImageTag}"
}

if (-not $SkipInstall) {
  Write-Host "`n=== Installerar Helm Chart ==="
  
  # Skapa namespace
  kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f - | Out-Host
  
  # Installera chart
  helm upgrade --install $Release ./helm/inf-batch-job `
    --namespace $Namespace `
    --set image.repository=$ImageName `
    --set image.tag=$ImageTag `
    --set image.pullPolicy=Never `
    | Out-Host
  
  if ($LASTEXITCODE -ne 0) {
    throw "Helm installation misslyckades"
  }
  Write-Host "✓ Helm Chart installationen"
}

Write-Host "`n=== Väntar på deployment ==="
Wait-DeploymentReady -Namespace $Namespace -Deployment "inf-batch-job"

Write-Host "`n=== Startar port-forward ==="
$pfJob = Start-Job -ScriptBlock {
  param($Namespace)
  kubectl -n $Namespace port-forward svc/inf-batch-job 8080:8080 2>&1 | Out-Null
} -ArgumentList $Namespace

Start-Sleep -Seconds 2

Write-Host "✓ Port-forward startat (localhost:8080)"

try {
  Write-Host "`n=== Testar API ==="
  
  Wait-HealthCheck
  
  Write-Host "`nTest 1: Starta ett busybox-job"
  $jobRequest = @{
    jobName = "test-job"
    image = "busybox:latest"
    command = @("echo", "Hello from Kubernetes!")
    env = @{
      "TEST_VAR" = "test-value"
    }
  } | ConvertTo-Json
  
  Write-Host "Request body:"
  Write-Host $jobRequest
  
  $response = Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/api/jobs" `
    -ContentType "application/json" `
    -Body $jobRequest
  
  $jobId = $response.jobId
  Write-Host "✓ Job startat: $jobId"
  Write-Host "  Respons: $($response | ConvertTo-Json)"
  
  Start-Sleep -Seconds 2
  
  Write-Host "`nTest 2: Hämta Job-status"
  $statusResponse = Invoke-RestMethod -Method Get `
    -Uri "http://localhost:8080/api/jobs/$jobId"
  
  Write-Host "✓ Job-status:"
  Write-Host $($statusResponse | ConvertTo-Json -Depth 3)
  
  Write-Host "`nTest 3: Lista alla Jobs"
  $listResponse = Invoke-RestMethod -Method Get `
    -Uri "http://localhost:8080/api/jobs"
  
  Write-Host "✓ Antal Jobs: $($listResponse.Count)"
  
  Write-Host "`n=== Demo slutförda ==="
  Write-Host "`nNyttiga kommandon:"
  Write-Host "  kubectl get jobs -n $Namespace"
  Write-Host "  kubectl logs job/$jobId -n $Namespace"
  Write-Host "  kubectl describe job/$jobId -n $Namespace"
  Write-Host "  kubectl get pods -n $Namespace"
}
catch {
  Write-Host "✗ Fel under test: $_" -ForegroundColor Red
  throw
}
finally {
  if ($pfJob) {
    Write-Host "`nRengör port-forward..."
    Stop-Job -Job $pfJob -ErrorAction SilentlyContinue | Out-Null
    Remove-Job -Job $pfJob -Force -ErrorAction SilentlyContinue | Out-Null
  }
}

