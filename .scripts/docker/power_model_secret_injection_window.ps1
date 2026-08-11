[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SecretFile,
    [switch]$Execute,
    [string]$ApprovalToken = ''
)

# TD-005 HMAC secret injection window. Without both -Execute and the exact approval
# token this script is read-only. It never prints secret content, digest, or byte sample.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$requiredApproval = 'USER-APPROVAL-20260811-TD005-HMAC-SECRET-INJECTION'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$baseCompose = Join-Path $repoRoot 'DEVICE/docker-compose.yml'
$secretCompose = Join-Path $repoRoot 'DEVICE/docker-compose.power-model-secret.yml'
$secretPreflight = Join-Path $PSScriptRoot 'power_model_secret_file_preflight.ps1'
$activationPreflight = Join-Path $PSScriptRoot 'power_model_activation_preflight.ps1'

function Invoke-Native {
    param([string]$File, [string[]]$Arguments)
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& $File @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    [pscustomobject]@{ Code = $code; Text = (($lines | ForEach-Object { "$_" }) -join "`n") }
}

function Test-Stage2Baseline {
    $lines = @(& $activationPreflight -Stage events -ExpectedProfile full `
        -ExpectedPartitions 6 -ExpectedReplicationFactor 1 -ExpectedRetentionMs 2592000000 2>&1)
    $code = $LASTEXITCODE
    $stageText = ($lines | ForEach-Object { "$_" }) -join "`n"
    $stageText | Write-Host
    return ($code -eq 0)
}

function Wait-DeviceHealthy {
    param([int]$Attempts = 60)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $inspect = Invoke-Native -File 'docker' -Arguments @(
            'inspect', '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}',
            'iot-device'
        )
        if ($inspect.Code -eq 0 -and $inspect.Text.Trim() -eq 'healthy') { return $true }
        Start-Sleep -Seconds 2
    }
    $false
}

function Invoke-BaseRollback {
    'ROLLBACK_STARTED target=iot-device overlay=false' | Write-Output
    $rollback = Invoke-Native -File 'docker' -Arguments @(
        'compose', '-f', $baseCompose, 'up', '-d', '--no-deps', '--force-recreate', 'iot-device'
    )
    if ($rollback.Code -ne 0) {
        'ROLLBACK_RESULT=ERROR reason=compose-recreate-failed' | Write-Output
        return $false
    }
    if (-not (Wait-DeviceHealthy)) {
        'ROLLBACK_RESULT=ERROR reason=health-timeout' | Write-Output
        return $false
    }
    if (-not (Test-Stage2Baseline)) {
        'ROLLBACK_RESULT=ERROR reason=stage2-baseline-failed' | Write-Output
        return $false
    }
    'ROLLBACK_RESULT=PASS target=iot-device healthy=true stage2=true' | Write-Output
    $true
}

$fileCheckLines = @(& $secretPreflight -SecretFile $SecretFile 2>&1)
$fileCheckCode = $LASTEXITCODE
($fileCheckLines | ForEach-Object { "$_" }) -join "`n" | Write-Output
if ($fileCheckCode -ne 0) {
    'WINDOW_RESULT=BLOCKED reason=secret-file-preflight' | Write-Output
    exit 2
}

$resolvedSecretFile = (Resolve-Path -LiteralPath $SecretFile).Path
$hadPreviousFileVariable = Test-Path Env:EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE
$previousFileVariable = if ($hadPreviousFileVariable) { $env:EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE } else { $null }

try {
    $env:EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE = $resolvedSecretFile
    $composeCheck = Invoke-Native -File 'docker' -Arguments @(
        'compose', '-f', $baseCompose, '-f', $secretCompose, 'config', '--quiet'
    )
    if ($composeCheck.Code -ne 0) {
        'WINDOW_RESULT=BLOCKED reason=compose-config' | Write-Output
        exit 2
    }
    if (-not (Test-Stage2Baseline)) {
        'WINDOW_RESULT=BLOCKED reason=stage2-precheck' | Write-Output
        exit 2
    }

    if (-not $Execute) {
        'WINDOW_RESULT=READY_ONLY execute=false runtimeChanged=false' | Write-Output
        exit 0
    }
    if (-not [string]::Equals($ApprovalToken, $requiredApproval, [StringComparison]::Ordinal)) {
        'WINDOW_RESULT=BLOCKED reason=approval-token execute=false runtimeChanged=false' | Write-Output
        exit 2
    }

    $apply = Invoke-Native -File 'docker' -Arguments @(
        'compose', '-f', $baseCompose, '-f', $secretCompose,
        'up', '-d', '--no-deps', '--force-recreate', 'iot-device'
    )
    if ($apply.Code -ne 0) {
        'WINDOW_APPLY=ERROR reason=compose-recreate-failed' | Write-Output
        [void](Invoke-BaseRollback)
        exit 1
    }
    if (-not (Wait-DeviceHealthy)) {
        'WINDOW_APPLY=ERROR reason=health-timeout' | Write-Output
        [void](Invoke-BaseRollback)
        exit 1
    }

    $inspectResult = Invoke-Native -File 'docker' -Arguments @('inspect', 'iot-device')
    if ($inspectResult.Code -ne 0) {
        'WINDOW_APPLY=ERROR reason=inspect-failed' | Write-Output
        [void](Invoke-BaseRollback)
        exit 1
    }
    $container = @($inspectResult.Text | ConvertFrom-Json)[0]
    $environment = @{}
    foreach ($entry in @($container.Config.Env)) {
        $separator = $entry.IndexOf('=')
        if ($separator -gt 0) { $environment[$entry.Substring(0, $separator)] = $entry.Substring($separator + 1) }
    }
    $plainSecret = if ($environment.ContainsKey('EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET')) {
        [string]$environment['EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET']
    } else { '' }
    $plainSecretBytes = [Text.Encoding]::UTF8.GetByteCount($plainSecret)
    $plainSecret = $null
    $mountCheck = Invoke-Native -File 'docker' -Arguments @(
        'exec', 'iot-device', 'sh', '-c',
        'p=/run/secrets/easyaiot.power-model.idempotency-hmac-secret-file-content; if [ -f "$p" ]; then wc -c < "$p"; else echo 0; fi'
    )
    $mountBytes = if ($mountCheck.Code -eq 0 -and $mountCheck.Text.Trim() -match '^\d+$') {
        [int]$mountCheck.Text.Trim()
    } else { 0 }
    if ($plainSecretBytes -ne 0 -or $mountBytes -lt 32) {
        'WINDOW_APPLY=ERROR reason=secret-metadata-invariant' | Write-Output
        [void](Invoke-BaseRollback)
        exit 1
    }
    if (-not (Test-Stage2Baseline)) {
        'WINDOW_APPLY=ERROR reason=stage2-postcheck' | Write-Output
        [void](Invoke-BaseRollback)
        exit 1
    }
    'WINDOW_RESULT=PASS target=iot-device healthy=true source=configtree-file plainEnvironmentBytes=0 mountedBytesGe32=true apiEnabled=false' | Write-Output
} finally {
    $resolvedSecretFile = $null
    if ($hadPreviousFileVariable) {
        $env:EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE = $previousFileVariable
    } else {
        Remove-Item Env:EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE -ErrorAction SilentlyContinue
    }
    $previousFileVariable = $null
}
