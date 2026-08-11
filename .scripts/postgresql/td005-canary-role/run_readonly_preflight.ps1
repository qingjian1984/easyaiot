[CmdletBinding()]
param(
    [string]$PostgresContainer = 'postgres-server',
    [string]$SystemDatabase = 'ruoyi-vue-pro20',
    [string]$DeviceDatabase = 'iot-device20'
)

# TD-005 canary role/data read-only preflight wrapper for Windows PowerShell.
# Explicit UTF-8 is required because the SQL guards compare Chinese baseline names.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-ReadOnlySql {
    param([string]$ScriptPath, [string]$Database)
    $sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $ScriptPath
    if ($sql -notmatch '(?im)^BEGIN TRANSACTION READ ONLY;\s*$' -or
            $sql -match '(?im)^\s*COMMIT\s*;?\s*$') {
        throw "TD005_CANARY_PREFLIGHT_NOT_READ_ONLY"
    }
    $previousEncoding = $OutputEncoding
    try {
        $OutputEncoding = [Text.UTF8Encoding]::new($false)
        $sql | docker exec -i $PostgresContainer psql -U postgres -d $Database `
            -X -v ON_ERROR_STOP=1 -f -
        $code = $LASTEXITCODE
    } finally {
        $OutputEncoding = $previousEncoding
        $sql = $null
    }
    if ($code -ne 0) { throw "TD005_CANARY_PREFLIGHT_FAILED database=$Database" }
}

$directory = $PSScriptRoot
Invoke-ReadOnlySql -ScriptPath (Join-Path $directory 'preflight_canary_role.sql') `
    -Database $SystemDatabase
Invoke-ReadOnlySql -ScriptPath (Join-Path $directory 'preflight_canary_tenant_data.sql') `
    -Database $DeviceDatabase
'CANARY_READINESS=PASS roleLinks=0 activeUsers=1 tenantResidualRows=0 encoding=utf8' | Write-Output
