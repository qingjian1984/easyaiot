[CmdletBinding()]
param(
    [ValidateSet('baseline', 'release-port', 'events', 'template-api', 'api')]
    [string]$Stage = 'baseline',
    [ValidateSet('standard', 'full')]
    [string]$ExpectedProfile,
    [ValidateRange(1, 1024)]
    [int]$ExpectedPartitions,
    [ValidateRange(1, 100)]
    [int]$ExpectedReplicationFactor,
    [ValidateRange(1, [long]::MaxValue)]
    [long]$ExpectedRetentionMs,
    [string]$DeviceContainer = 'iot-device',
    [string]$KafkaContainer = 'kafka-server',
    [string]$PostgresContainer = 'postgres-server',
    [string]$Database = 'iot-device20'
)

# Read-only activation preflight. This script never creates topics, starts/restarts
# containers, changes environment variables, writes database rows, or prints secrets.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param([string]$Code, [ValidateSet('PASS', 'BLOCKED', 'ERROR')][string]$Status,
          [string]$Detail)
    $checks.Add([pscustomobject]@{ Code = $Code; Status = $Status; Detail = $Detail })
}

function Invoke-Docker {
    param([string[]]$Arguments)
    # Windows PowerShell promotes native stderr to ErrorRecord. Keep it captured so an
    # expected "container absent" result becomes BLOCKED instead of aborting the script.
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& docker @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    [pscustomobject]@{ Code = $code; Text = (($lines | ForEach-Object { "$_" }) -join "`n") }
}

function Get-ContainerInspect {
    param([string]$Name)
    $result = Invoke-Docker -Arguments @('inspect', $Name)
    if ($result.Code -ne 0) { return $null }
    @($result.Text | ConvertFrom-Json)[0]
}

function Add-ContainerHealthCheck {
    param([string]$Code, [string]$Name, [object]$Inspect)
    if ($null -eq $Inspect) {
        Add-Check $Code 'BLOCKED' "container=$Name absent"
        return
    }
    $running = [bool]$Inspect.State.Running
    $health = if ($null -ne $Inspect.State.Health) { [string]$Inspect.State.Health.Status } else { 'none' }
    if ($running -and ($health -eq 'healthy' -or $health -eq 'none')) {
        Add-Check $Code 'PASS' "container=$Name running=true health=$health"
    } else {
        Add-Check $Code 'BLOCKED' "container=$Name running=$running health=$health"
    }
}

function Get-ContainerEnvironment {
    param([object]$Inspect)
    $map = @{}
    if ($null -eq $Inspect -or $null -eq $Inspect.Config.Env) { return $map }
    foreach ($entry in @($Inspect.Config.Env)) {
        $separator = $entry.IndexOf('=')
        if ($separator -gt 0) { $map[$entry.Substring(0, $separator)] = $entry.Substring($separator + 1) }
    }
    $map
}

function Get-BooleanEnvironment {
    param([hashtable]$Environment, [string]$Name)
    if (-not $Environment.ContainsKey($Name)) { return $false }
    [string]::Equals([string]$Environment[$Name], 'true', [System.StringComparison]::OrdinalIgnoreCase)
}

function Read-EnvFile {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $map }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $map[$Matches[1]] = $Matches[2] }
    }
    $map
}

if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
    Add-Check 'DOCKER_CLI' 'ERROR' 'docker command not found'
    $checks | Format-Table -AutoSize | Out-String | Write-Output
    exit 1
}
Add-Check 'DOCKER_CLI' 'PASS' 'docker command available'

if ([string]::IsNullOrWhiteSpace($ExpectedProfile)) {
    Add-Check 'PROFILE_POLICY' 'BLOCKED' 'pass -ExpectedProfile standard|full after owner approval'
} else {
    Add-Check 'PROFILE_POLICY' 'PASS' "expected=$ExpectedProfile"
}
if ($ExpectedPartitions -le 0 -or $ExpectedReplicationFactor -le 0 -or $ExpectedRetentionMs -le 0) {
    Add-Check 'TOPIC_POLICY' 'BLOCKED' 'pass approved partitions, replication factor, and retention.ms'
} else {
    Add-Check 'TOPIC_POLICY' 'PASS' ("partitions={0} replicationFactor={1} retentionMs={2}" -f `
            $ExpectedPartitions, $ExpectedReplicationFactor, $ExpectedRetentionMs)
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$deviceEnv = Read-EnvFile (Join-Path $repoRoot 'DEVICE/.env')
$staticProfile = if ($deviceEnv.ContainsKey('EASYAIOT_CAPABILITY_PROFILE')) {
    [string]$deviceEnv['EASYAIOT_CAPABILITY_PROFILE']
} else { '' }
$staticManifest = if ($deviceEnv.ContainsKey('EASYAIOT_CAPABILITY_MANIFEST_LOCATION')) {
    [string]$deviceEnv['EASYAIOT_CAPABILITY_MANIFEST_LOCATION']
} else { '' }
if ([string]::IsNullOrWhiteSpace($staticProfile) -or [string]::IsNullOrWhiteSpace($staticManifest)) {
    Add-Check 'DEVICE_ENV_CAPABILITY' 'BLOCKED' 'DEVICE/.env profile or manifest location is missing'
} elseif (-not [string]::IsNullOrWhiteSpace($ExpectedProfile) -and $staticProfile -ne $ExpectedProfile) {
    Add-Check 'DEVICE_ENV_CAPABILITY' 'BLOCKED' "DEVICE/.env profile=$staticProfile expected=$ExpectedProfile"
} else {
    Add-Check 'DEVICE_ENV_CAPABILITY' 'PASS' "profile=$staticProfile manifestConfigured=true"
}

$postgres = Get-ContainerInspect $PostgresContainer
$kafka = Get-ContainerInspect $KafkaContainer
$device = Get-ContainerInspect $DeviceContainer
Add-ContainerHealthCheck 'POSTGRES_HEALTH' $PostgresContainer $postgres
Add-ContainerHealthCheck 'KAFKA_HEALTH' $KafkaContainer $kafka
Add-ContainerHealthCheck 'IOT_DEVICE_HEALTH' $DeviceContainer $device

if ($null -ne $device) {
    $environment = Get-ContainerEnvironment $device
    $profile = if ($environment.ContainsKey('EASYAIOT_CAPABILITY_PROFILE')) {
        [string]$environment['EASYAIOT_CAPABILITY_PROFILE']
    } else { '' }
    $manifestConfigured = $environment.ContainsKey('EASYAIOT_CAPABILITY_MANIFEST_LOCATION') -and
        -not [string]::IsNullOrWhiteSpace([string]$environment['EASYAIOT_CAPABILITY_MANIFEST_LOCATION'])
    if ([string]::IsNullOrWhiteSpace($ExpectedProfile) -or $profile -ne $ExpectedProfile -or -not $manifestConfigured) {
        Add-Check 'RUNTIME_CAPABILITY' 'BLOCKED' "profile=$profile manifestConfigured=$manifestConfigured"
    } else {
        Add-Check 'RUNTIME_CAPABILITY' 'PASS' "profile=$profile manifestConfigured=true"
    }

    $templateApi = Get-BooleanEnvironment $environment 'EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED'
    $bindingApi = Get-BooleanEnvironment $environment 'EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED'
    $release = Get-BooleanEnvironment $environment 'EASYAIOT_POWER_MODEL_COLLECTOR_RELEASE_PORT_ENABLED'
    $events = Get-BooleanEnvironment $environment 'POWER_MODEL_EVENTS_ENABLED'
    $expected = switch ($Stage) {
        'baseline' { @($false, $false, $false, $false) }
        'release-port' { @($false, $false, $true, $false) }
        'events' { @($false, $false, $true, $true) }
        'template-api' { @($true, $false, $true, $true) }
        'api' { @($true, $true, $true, $true) }
    }
    if ($templateApi -eq $expected[0] -and $bindingApi -eq $expected[1] -and
        $release -eq $expected[2] -and $events -eq $expected[3]) {
        Add-Check 'ACTIVATION_STAGE' 'PASS' "stage=$Stage templateApi=$templateApi bindingApi=$bindingApi releasePort=$release events=$events"
    } else {
        Add-Check 'ACTIVATION_STAGE' 'BLOCKED' "stage=$Stage templateApi=$templateApi bindingApi=$bindingApi releasePort=$release events=$events"
    }

    if ($Stage -in @('template-api', 'api')) {
        $secret = if ($environment.ContainsKey('EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET')) {
            [string]$environment['EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET']
        } else { '' }
        $environmentBytes = [Text.Encoding]::UTF8.GetByteCount($secret)
        $secretFileResult = Invoke-Docker -Arguments @('exec', $DeviceContainer, 'sh', '-c',
            'p=/run/secrets/easyaiot.power-model.idempotency-hmac-secret; if [ -f "$p" ]; then wc -c < "$p"; else echo 0; fi')
        $secretFileBytes = 0
        if ($secretFileResult.Code -eq 0 -and $secretFileResult.Text.Trim() -match '^\d+$') {
            $secretFileBytes = [int]$secretFileResult.Text.Trim()
        }
        $valid = $environmentBytes -ge 32 -or $secretFileBytes -ge 32
        if ($valid) {
            $source = if ($secretFileBytes -ge 32) { 'configtree-file' } else { 'environment-compatibility' }
            Add-Check 'IDEMPOTENCY_SECRET' 'PASS' "configured=true source=$source utf8BytesGe32=true"
        } else {
            Add-Check 'IDEMPOTENCY_SECRET' 'BLOCKED' 'configured=false-or-short sources=environment,configtree-file'
        }
        $secret = $null
    } else {
        Add-Check 'IDEMPOTENCY_SECRET' 'PASS' 'not required before write API stages; value not read or printed'
    }
}

if ($null -ne $postgres -and [bool]$postgres.State.Running) {
    $sql = @"
BEGIN READ ONLY;
SELECT 'MIG', count(*), count(*) FILTER (WHERE status='SUCCEEDED'),
       max(CASE WHEN migration_id='V007' THEN btrim(script_sha256) END)
FROM public.schema_migration_history WHERE migration_id ~ '^V00[1-7]$';
SELECT 'QUEUE',
       (SELECT count(*) FROM public.power_model_release_outbox),
       (SELECT count(*) FROM public.power_model_event_inbox),
       (SELECT count(*) FROM public.iot_collector_config_release),
       (SELECT count(*) FROM public.collector_workload_binding_projection);
SELECT 'INDEX', count(*) FROM pg_index WHERE NOT indisvalid;
SELECT 'BUSINESS',
       (SELECT count(*) FROM public.product),
       (SELECT count(*) FROM public.device),
       (SELECT count(*) FROM public.product_properties);
COMMIT;
"@
    $dbResult = Invoke-Docker -Arguments @('exec', $PostgresContainer, 'psql', '-U', 'postgres',
        '-d', $Database, '-X', '-v', 'ON_ERROR_STOP=1', '-At', '-F', '|', '-c', $sql)
    if ($dbResult.Code -ne 0) {
        Add-Check 'DATABASE_FACTS' 'ERROR' 'read-only query failed'
    } else {
        $migration = @($dbResult.Text -split "`r?`n" | Where-Object { $_ -like 'MIG|*' })
        $queue = @($dbResult.Text -split "`r?`n" | Where-Object { $_ -like 'QUEUE|*' })
        $index = @($dbResult.Text -split "`r?`n" | Where-Object { $_ -like 'INDEX|*' })
        $business = @($dbResult.Text -split "`r?`n" | Where-Object { $_ -like 'BUSINESS|*' })
        if ($migration.Count -eq 1) {
            $m = $migration[0] -split '\|'
            $hashOk = $m[3] -eq '6590d6daa33e6e3382f17b1ef1ced0ed854c5322857062617d2b77c621e38685'
            if ($m[1] -eq '7' -and $m[2] -eq '7' -and $hashOk) {
                Add-Check 'MIGRATIONS_V001_V007' 'PASS' 'count=7 succeeded=7 v007HashMatch=true'
            } else { Add-Check 'MIGRATIONS_V001_V007' 'BLOCKED' 'migration count/status/hash mismatch' }
        } else { Add-Check 'MIGRATIONS_V001_V007' 'ERROR' 'migration result missing' }
        if ($queue.Count -eq 1 -and $queue[0] -eq 'QUEUE|0|0|0|0') {
            Add-Check 'DATABASE_BACKLOG' 'PASS' 'outbox=0 inbox=0 release=0 projection=0'
        } else { Add-Check 'DATABASE_BACKLOG' 'BLOCKED' 'one or more write-chain tables are non-empty' }
        if ($index.Count -eq 1 -and $index[0] -eq 'INDEX|0') {
            Add-Check 'INVALID_INDEXES' 'PASS' 'count=0'
        } else { Add-Check 'INVALID_INDEXES' 'BLOCKED' 'invalid index count is non-zero or unavailable' }
        if ($business.Count -eq 1 -and $business[0] -eq 'BUSINESS|4|4|17') {
            Add-Check 'BUSINESS_BASELINE' 'PASS' 'product=4 device=4 productProperties=17'
        } else { Add-Check 'BUSINESS_BASELINE' 'BLOCKED' 'business baseline differs from 4/4/17 or is unavailable' }
    }
}

if ($null -ne $kafka -and [bool]$kafka.State.Running) {
    $topicResult = Invoke-Docker -Arguments @('exec', $KafkaContainer,
        '/opt/kafka/bin/kafka-topics.sh', '--bootstrap-server', 'localhost:9092', '--list')
    if ($topicResult.Code -ne 0) {
        Add-Check 'KAFKA_TOPICS' 'ERROR' 'topic list failed'
    } else {
        $topics = @($topicResult.Text -split "`r?`n")
        $mainExists = $topics -contains 'power-model-release-v1'
        $dlqExists = $topics -contains 'power-model-release-v1-dlq'
        if ($mainExists -and $dlqExists) {
            Add-Check 'KAFKA_TOPICS' 'PASS' 'main=true dlq=true'
            if ($ExpectedPartitions -gt 0 -and $ExpectedReplicationFactor -gt 0 -and
                    $ExpectedRetentionMs -gt 0) {
                $policyMatches = $true
                foreach ($topic in @('power-model-release-v1', 'power-model-release-v1-dlq')) {
                    $describe = Invoke-Docker -Arguments @('exec', $KafkaContainer,
                        '/opt/kafka/bin/kafka-topics.sh', '--bootstrap-server', 'localhost:9092',
                        '--describe', '--topic', $topic)
                    $configs = Invoke-Docker -Arguments @('exec', $KafkaContainer,
                        '/opt/kafka/bin/kafka-configs.sh', '--bootstrap-server', 'localhost:9092',
                        '--entity-type', 'topics', '--entity-name', $topic, '--describe')
                    $shapeMatches = $describe.Code -eq 0 -and
                        $describe.Text -match "PartitionCount:\s*$ExpectedPartitions(?:\s|$)" -and
                        $describe.Text -match "ReplicationFactor:\s*$ExpectedReplicationFactor(?:\s|$)"
                    $retentionMatches = $configs.Code -eq 0 -and
                        $configs.Text -match "retention\.ms=$ExpectedRetentionMs(?:,|\s|$)"
                    if (-not $shapeMatches -or -not $retentionMatches) { $policyMatches = $false }
                }
                if ($policyMatches) {
                    Add-Check 'KAFKA_TOPIC_POLICY_ACTUAL' 'PASS' 'both topics match approved shape and explicit retention.ms'
                } else {
                    Add-Check 'KAFKA_TOPIC_POLICY_ACTUAL' 'BLOCKED' 'topic shape or explicit retention.ms mismatch'
                }
            }
        } else {
            Add-Check 'KAFKA_TOPICS' 'BLOCKED' "main=$mainExists dlq=$dlqExists"
        }
    }

    $groupResult = Invoke-Docker -Arguments @('exec', $KafkaContainer,
        '/opt/kafka/bin/kafka-consumer-groups.sh', '--bootstrap-server', 'localhost:9092', '--list')
    $groupExists = $groupResult.Code -eq 0 -and
        @($groupResult.Text -split "`r?`n") -contains 'iot-device-power-model-release'
    if ($groupResult.Code -ne 0) {
        Add-Check 'KAFKA_CONSUMER_GROUP' 'ERROR' 'consumer group list failed'
    } elseif ($Stage -notin @('events', 'template-api', 'api')) {
        Add-Check 'KAFKA_CONSUMER_GROUP' 'PASS' "required=false stage=$Stage exists=$groupExists"
    } elseif ($groupExists) {
        $describeGroup = Invoke-Docker -Arguments @('exec', $KafkaContainer,
            '/opt/kafka/bin/kafka-consumer-groups.sh', '--bootstrap-server', 'localhost:9092',
            '--describe', '--group', 'iot-device-power-model-release')
        $rows = if ($describeGroup.Code -eq 0) {
            @($describeGroup.Text -split "`r?`n" | Where-Object {
                $_.TrimStart().StartsWith('iot-device-power-model-release ')
            })
        } else { @() }
        $lagTotal = 0L
        $online = $rows.Count -gt 0
        foreach ($row in $rows) {
            $columns = @($row.Trim() -split '\s+')
            if ($columns.Count -lt 7 -or $columns[5] -notmatch '^\d+$' -or $columns[6] -eq '-') {
                $online = $false
                continue
            }
            $lagTotal += [long]$columns[5]
        }
        if ($online -and $lagTotal -eq 0) {
            Add-Check 'KAFKA_CONSUMER_GROUP' 'PASS' "online=true partitions=$($rows.Count) lag=0"
        } else {
            Add-Check 'KAFKA_CONSUMER_GROUP' 'BLOCKED' "online=$online partitions=$($rows.Count) lag=$lagTotal"
        }
    } else {
        Add-Check 'KAFKA_CONSUMER_GROUP' 'BLOCKED' 'iot-device-power-model-release absent'
    }
}

$checks | Format-Table -AutoSize -Wrap | Out-String -Width 240 | Write-Output
if (@($checks | Where-Object Status -eq 'ERROR').Count -gt 0) { exit 1 }
if (@($checks | Where-Object Status -eq 'BLOCKED').Count -gt 0) { exit 2 }
exit 0
