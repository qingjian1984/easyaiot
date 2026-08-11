[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SecretFile,
    [string]$ExpectedJarSha256 = '54bedaec85bed61f7afe012dcbc5eda933c4e6958c8ade6ca3a6c86e4143b009'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$approval = 'USER-APPROVAL-20260811-TD005-IOT-DEVICE-CONFIGTREE-RUNTIME-REPAIR-DEPLOY'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$deviceRoot = Join-Path $repoRoot 'DEVICE'
$candidate = Join-Path $deviceRoot 'target/jars/iot-device-biz.jar'
$baseCompose = Join-Path $deviceRoot 'docker-compose.yml'
$secretCompose = Join-Path $deviceRoot 'docker-compose.power-model-secret.yml'
$legacySecretCompose = Join-Path $deviceRoot 'docker-compose.power-model-secret-legacy-rollback.yml'
$templateCompose = Join-Path $deviceRoot 'docker-compose.power-model-template-api.yml'
$rollbackTag = 'iot-module-device-biz:rollback-td005-configtree-runtime-repair-predeploy-20260811'
$recreationAttempted = $false

function Invoke-DockerChecked {
    param([string[]]$Arguments, [string]$WorkingDirectory = $repoRoot)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        Push-Location $WorkingDirectory
        try {
            $lines = @(& docker @Arguments 2>&1)
            $code = $LASTEXITCODE
        } finally { Pop-Location }
    } finally { $ErrorActionPreference = $previous }
    if ($code -ne 0) {
        $tail = @($lines | Select-Object -Last 30) -join "`n"
        throw "DOCKER_COMMAND_FAILED code=$code args=$($Arguments -join ' ')`n$tail"
    }
    @($lines | ForEach-Object { "$_" })
}

function Invoke-ComposeUp {
    param([string]$SecretOverlay)
    Invoke-DockerChecked -WorkingDirectory $deviceRoot -Arguments @(
        'compose', '-f', $baseCompose, '-f', $SecretOverlay, '-f', $templateCompose,
        'up', '-d', '--no-deps', '--force-recreate', 'iot-device') | Out-Null
}

function Wait-IotDeviceHealthy {
    param([int]$Attempts = 30, [int]$IntervalSeconds = 5)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $json = @(& docker inspect iot-device 2>$null)
            $code = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previous }
        if ($code -eq 0) {
            $inspect = @($json -join "`n" | ConvertFrom-Json)[0]
            if ($inspect.State.Running -and $null -ne $inspect.State.Health -and
                    $inspect.State.Health.Status -eq 'healthy') { return $true }
        }
        Start-Sleep -Seconds $IntervalSeconds
    }
    $false
}

function Get-OtherContainerBaseline {
    $result = @{}
    $ids = @(Invoke-DockerChecked -Arguments @('ps', '-aq'))
    foreach ($id in $ids) {
        if ([string]::IsNullOrWhiteSpace($id)) { continue }
        $inspect = @((Invoke-DockerChecked -Arguments @('inspect', $id)) -join "`n" |
            ConvertFrom-Json)[0]
        $name = ([string]$inspect.Name).TrimStart('/')
        if ($name -eq 'iot-device') { continue }
        $result[$name] = "$($inspect.Id)|$($inspect.Image)|$($inspect.State.StartedAt)"
    }
    $result
}

function Assert-OtherContainersUnchanged {
    param([hashtable]$Before)
    $after = Get-OtherContainerBaseline
    if ($Before.Count -ne $after.Count) { throw 'OTHER_CONTAINER_COUNT_CHANGED' }
    foreach ($name in $Before.Keys) {
        if (-not $after.ContainsKey($name) -or $after[$name] -ne $Before[$name]) {
            throw "OTHER_CONTAINER_CHANGED name=$name"
        }
    }
}

$actualSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $candidate).Hash.ToLowerInvariant()
if ($actualSha -ne $ExpectedJarSha256) { throw 'CANDIDATE_JAR_HASH_MISMATCH' }
$resolvedSecret = (Resolve-Path -LiteralPath $SecretFile).Path
$oldInspect = @((Invoke-DockerChecked -Arguments @('inspect', 'iot-device')) -join "`n" |
    ConvertFrom-Json)[0]
if (-not $oldInspect.State.Running -or $oldInspect.State.Health.Status -ne 'healthy') {
    throw 'OLD_IOT_DEVICE_NOT_HEALTHY'
}
$oldImageId = [string]$oldInspect.Image
$otherBaseline = Get-OtherContainerBaseline

$env:EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE = $resolvedSecret
$env:EASYAIOT_CAPABILITY_PROFILE = 'full'
$env:EASYAIOT_CAPABILITY_MANIFEST_LOCATION = 'file:/opt/easyaiot/capabilities/electric-full.json'
$env:EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED = 'true'
$env:EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED = 'false'
$env:EASYAIOT_POWER_MODEL_COLLECTOR_RELEASE_PORT_ENABLED = 'true'
$env:POWER_MODEL_EVENTS_ENABLED = 'true'
$env:EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET = ''

Invoke-DockerChecked -Arguments @('tag', $oldImageId, $rollbackTag) | Out-Null
try {
    Invoke-DockerChecked -WorkingDirectory $deviceRoot -Arguments @(
        'build', '-f', 'iot-device/iot-device-biz/Dockerfile',
        '-t', 'iot-module-device-biz:latest', '.') | Select-Object -Last 15 | Write-Output

    $newImageId = (Invoke-DockerChecked -Arguments @(
        'image', 'inspect', '-f', '{{.Id}}', 'iot-module-device-biz:latest') | Select-Object -First 1).Trim()
    if ($newImageId -eq $oldImageId) { throw 'NEW_IMAGE_EQUALS_ROLLBACK_IMAGE' }

    $recreationAttempted = $true
    Invoke-ComposeUp -SecretOverlay $secretCompose
    if (-not (Wait-IotDeviceHealthy)) { throw 'NEW_IOT_DEVICE_NOT_HEALTHY_WITHIN_150S' }

    $newInspect = @((Invoke-DockerChecked -Arguments @('inspect', 'iot-device')) -join "`n" |
        ConvertFrom-Json)[0]
    if ([string]$newInspect.Image -ne $newImageId -or $newInspect.RestartCount -ne 0) {
        throw 'NEW_IOT_DEVICE_IMAGE_OR_RESTART_INVARIANT_FAILED'
    }
    $envMap = @{}
    foreach ($entry in $newInspect.Config.Env) {
        $index = $entry.IndexOf('=')
        if ($index -gt 0) { $envMap[$entry.Substring(0, $index)] = $entry.Substring($index + 1) }
    }
    if ($envMap['EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED'] -ne 'true' -or
            $envMap['EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED'] -ne 'false' -or
            [Text.Encoding]::UTF8.GetByteCount(
                [string]$envMap['EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET']) -ne 0) {
        throw 'RUNTIME_ENVIRONMENT_INVARIANT_FAILED'
    }
    Invoke-DockerChecked -Arguments @('exec', 'iot-device', 'sh', '-c',
        'p=/run/secrets/easyaiot.power-model.idempotency-hmac-secret; test -f "$p" && test "$(wc -c < "$p")" -ge 32') | Out-Null
    $classCount = (Invoke-DockerChecked -Arguments @('exec', 'iot-device', 'sh', '-c',
        "jar tf /app/app.jar | grep -E 'PowerModelTemplate(Controller|IdentityService|DraftService|PublishService)\\.class$' | wc -l") |
        Select-Object -First 1).Trim()
    if ($classCount -ne '4') { throw "RUNTIME_TEMPLATE_CLASS_COUNT_FAILED count=$classCount" }
    Assert-OtherContainersUnchanged -Before $otherBaseline
    Write-Output "DEPLOY_RESULT=PASS approval=$approval oldImage=$oldImageId newImage=$newImageId container=$($newInspect.Id) healthy=true restartCount=0 templateClasses=4 configTreeFinalKey=true otherContainersUnchanged=true"
} catch {
    $failure = $_.Exception.Message
    Invoke-DockerChecked -Arguments @('tag', $rollbackTag, 'iot-module-device-biz:latest') | Out-Null
    if ($recreationAttempted) {
        Invoke-ComposeUp -SecretOverlay $legacySecretCompose
        if (-not (Wait-IotDeviceHealthy -Attempts 30 -IntervalSeconds 5)) {
            throw "DEPLOY_FAILED=$failure; ROLLBACK_FAILED=old_iot_device_not_healthy"
        }
    }
    Assert-OtherContainersUnchanged -Before $otherBaseline
    throw "DEPLOY_FAILED=$failure; ROLLBACK_PASS image=$oldImageId otherContainersUnchanged=true"
}
