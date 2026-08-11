[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SecretFile,
    [switch]$Execute,
    [string]$ApprovalToken = ''
)

# TD-005 template API activation window. The default path is read-only. The change path
# only recreates iot-device with template API=true and binding API=false; it never calls
# an API or writes Canary data.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$requiredApproval = 'USER-APPROVAL-20260811-TD005-TEMPLATE-API-ACTIVATION'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$baseCompose = Join-Path $repoRoot 'DEVICE/docker-compose.yml'
$secretCompose = Join-Path $repoRoot 'DEVICE/docker-compose.power-model-secret.yml'
$templateCompose = Join-Path $repoRoot 'DEVICE/docker-compose.power-model-template-api.yml'
$secretPreflight = Join-Path $PSScriptRoot 'power_model_secret_file_preflight.ps1'
$activationPreflight = Join-Path $PSScriptRoot 'power_model_activation_preflight.ps1'
$roleDirectory = Join-Path $repoRoot '.scripts/postgresql/td005-canary-role'

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

function Invoke-ReadOnlySql {
    param([string]$ScriptPath, [string]$Database)
    $sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $ScriptPath
    if ($sql -notmatch '(?im)^BEGIN TRANSACTION READ ONLY;\s*$' -or
            $sql -match '(?im)^\s*COMMIT\s*;?\s*$') {
        throw "TD005_TEMPLATE_API_READINESS_NOT_READ_ONLY path=$ScriptPath"
    }
    $previousEncoding = $OutputEncoding
    try {
        $OutputEncoding = [Text.UTF8Encoding]::new($false)
        $sql | docker exec -i postgres-server psql -U postgres -d $Database `
            -X -v ON_ERROR_STOP=1 -f -
        $code = $LASTEXITCODE
    } finally {
        $OutputEncoding = $previousEncoding
        $sql = $null
    }
    if ($code -ne 0) { throw "TD005_TEMPLATE_API_READINESS_FAILED database=$Database" }
}

function Test-CanaryReadiness {
    try {
        Invoke-ReadOnlySql -ScriptPath (Join-Path $roleDirectory 'verify_canary_role_grant.sql') `
            -Database 'ruoyi-vue-pro20'
        Invoke-ReadOnlySql -ScriptPath (Join-Path $roleDirectory 'preflight_canary_tenant_data.sql') `
            -Database 'iot-device20'
        'CANARY_READINESS=PASS roleLinks=3 forbiddenLinks=0 tenantResidualRows=0' | Write-Host
        return $true
    } catch {
        "CANARY_READINESS=ERROR reason=$($_.Exception.Message)" | Write-Host
        return $false
    }
}

function Test-ActivationStage {
    param([ValidateSet('events', 'template-api')][string]$Stage)
    $lines = @(& $activationPreflight -Stage $Stage -ExpectedProfile full `
        -ExpectedPartitions 6 -ExpectedReplicationFactor 1 -ExpectedRetentionMs 2592000000 2>&1)
    $code = $LASTEXITCODE
    (($lines | ForEach-Object { "$_" }) -join "`n") | Write-Host
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
    return $false
}

function Wait-ActivationStage {
    param([ValidateSet('events', 'template-api')][string]$Stage,
          [int]$Attempts = 6, [int]$IntervalSeconds = 5)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        if (Test-ActivationStage -Stage $Stage) { return $true }
        if ($attempt -lt $Attempts) { Start-Sleep -Seconds $IntervalSeconds }
    }
    return $false
}

function Test-RuntimeInvariants {
    param([bool]$ExpectedTemplateApi)
    $inspect = Invoke-Native -File 'docker' -Arguments @('inspect', 'iot-device')
    if ($inspect.Code -ne 0) { return $false }
    $container = @($inspect.Text | ConvertFrom-Json)[0]
    $environment = @{}
    foreach ($entry in @($container.Config.Env)) {
        $separator = $entry.IndexOf('=')
        if ($separator -gt 0) { $environment[$entry.Substring(0, $separator)] = $entry.Substring($separator + 1) }
    }
    $templateValue = if ($environment.ContainsKey('EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED')) {
        [string]$environment['EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED']
    } else { 'false' }
    $bindingValue = if ($environment.ContainsKey('EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED')) {
        [string]$environment['EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED']
    } else { 'false' }
    $plainSecret = if ($environment.ContainsKey('EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET')) {
        [string]$environment['EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET']
    } else { '' }
    $plainSecretBytes = [Text.Encoding]::UTF8.GetByteCount($plainSecret)
    $plainSecret = $null
    $mount = Invoke-Native -File 'docker' -Arguments @(
        'exec', 'iot-device', 'sh', '-c',
        'p=/run/secrets/easyaiot.power-model.idempotency-hmac-secret; if [ -f "$p" ]; then wc -c < "$p"; else echo 0; fi'
    )
    $mountBytes = if ($mount.Code -eq 0 -and $mount.Text.Trim() -match '^\d+$') {
        [int]$mount.Text.Trim()
    } else { 0 }
    $expected = if ($ExpectedTemplateApi) { 'true' } else { 'false' }
    return ($templateValue -eq $expected -and $bindingValue -eq 'false' -and
        $plainSecretBytes -eq 0 -and $mountBytes -ge 32)
}

function Invoke-SafeRollback {
    'ROLLBACK_STARTED target=iot-device templateApi=false preserveSecret=true' | Write-Host
    $env:EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED = 'false'
    $env:EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED = 'false'
    $rollback = Invoke-Native -File 'docker' -Arguments @(
        'compose', '-f', $baseCompose, '-f', $secretCompose,
        'up', '-d', '--no-deps', '--force-recreate', 'iot-device'
    )
    if ($rollback.Code -ne 0 -or -not (Wait-DeviceHealthy) -or
            -not (Wait-ActivationStage -Stage events) -or
            -not (Test-RuntimeInvariants -ExpectedTemplateApi $false) -or
            -not (Test-CanaryReadiness)) {
        'ROLLBACK_RESULT=ERROR target=iot-device' | Write-Host
        return $false
    }
    'ROLLBACK_RESULT=PASS target=iot-device templateApi=false bindingApi=false preserveSecret=true' | Write-Host
    return $true
}

$fileCheckLines = @(& $secretPreflight -SecretFile $SecretFile 2>&1)
$fileCheckCode = $LASTEXITCODE
(($fileCheckLines | ForEach-Object { "$_" }) -join "`n") | Write-Output
if ($fileCheckCode -ne 0) {
    'WINDOW_RESULT=BLOCKED reason=secret-file-preflight' | Write-Output
    exit 2
}

$resolvedSecretFile = (Resolve-Path -LiteralPath $SecretFile).Path
$previousVariables = @{}
foreach ($name in @('EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE',
        'EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED',
        'EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED')) {
    $previousVariables[$name] = if (Test-Path "Env:$name") { [Environment]::GetEnvironmentVariable($name) } else { $null }
}

try {
    $env:EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE = $resolvedSecretFile
    $env:EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED = 'false'
    $env:EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED = 'false'
    $composeCheck = Invoke-Native -File 'docker' -Arguments @(
        'compose', '-f', $baseCompose, '-f', $secretCompose, '-f', $templateCompose,
        'config', '--quiet'
    )
    if ($composeCheck.Code -ne 0 -or -not (Test-ActivationStage -Stage events) -or
            -not (Test-RuntimeInvariants -ExpectedTemplateApi $false) -or
            -not (Test-CanaryReadiness)) {
        'WINDOW_RESULT=BLOCKED reason=readiness-precheck runtimeChanged=false' | Write-Output
        exit 2
    }

    if (-not $Execute) {
        'WINDOW_RESULT=READY_ONLY execute=false runtimeChanged=false targetStage=template-api' | Write-Output
        exit 0
    }
    if (-not [string]::Equals($ApprovalToken, $requiredApproval, [StringComparison]::Ordinal)) {
        'WINDOW_RESULT=BLOCKED reason=approval-token execute=false runtimeChanged=false' | Write-Output
        exit 2
    }

    $apply = Invoke-Native -File 'docker' -Arguments @(
        'compose', '-f', $baseCompose, '-f', $secretCompose, '-f', $templateCompose,
        'up', '-d', '--no-deps', '--force-recreate', 'iot-device'
    )
    if ($apply.Code -ne 0 -or -not (Wait-DeviceHealthy) -or
            -not (Wait-ActivationStage -Stage template-api) -or
            -not (Test-RuntimeInvariants -ExpectedTemplateApi $true) -or
            -not (Test-CanaryReadiness)) {
        'WINDOW_APPLY=ERROR reason=postcheck' | Write-Output
        $rollbackSucceeded = Invoke-SafeRollback
        exit 1
    }
    'WINDOW_RESULT=PASS target=iot-device templateApi=true bindingApi=false preserveSecret=true canaryWritten=false' | Write-Output
} finally {
    $resolvedSecretFile = $null
    foreach ($name in $previousVariables.Keys) {
        $previous = $previousVariables[$name]
        if ($null -eq $previous) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previous)
        }
    }
}
