[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SecretFile
)

# TD-005 repository-external HMAC secret file preflight. This script is read-only and
# never prints the file path, content, digest, byte sample, or decoded value.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param([string]$Code, [ValidateSet('PASS', 'BLOCKED', 'ERROR')][string]$Status,
          [string]$Detail)
    $checks.Add([pscustomobject]@{ Code = $Code; Status = $Status; Detail = $Detail })
}

try {
    if (-not [IO.Path]::IsPathRooted($SecretFile)) {
        Add-Check 'ABSOLUTE_PATH' 'BLOCKED' 'absolute=false'
        throw [InvalidOperationException]::new('blocked')
    }
    Add-Check 'ABSOLUTE_PATH' 'PASS' 'absolute=true'

    $item = Get-Item -LiteralPath $SecretFile -Force -ErrorAction Stop
    if ($item.PSIsContainer) {
        Add-Check 'REGULAR_FILE' 'BLOCKED' 'regularFile=false reason=directory'
        throw [InvalidOperationException]::new('blocked')
    }
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        Add-Check 'REGULAR_FILE' 'BLOCKED' 'regularFile=false reason=reparse-point'
        throw [InvalidOperationException]::new('blocked')
    }
    Add-Check 'REGULAR_FILE' 'PASS' 'regularFile=true reparsePoint=false'

    $repoRoot = [IO.Path]::GetFullPath((Resolve-Path (Join-Path $PSScriptRoot '../..')).Path)
    $resolvedFile = [IO.Path]::GetFullPath($item.FullName)
    $repoPrefix = $repoRoot.TrimEnd([char[]]@('\', '/')) + [IO.Path]::DirectorySeparatorChar
    $insideRepository = $resolvedFile.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $resolvedFile.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)
    if ($insideRepository) {
        Add-Check 'OUTSIDE_REPOSITORY' 'BLOCKED' 'outsideRepository=false'
    } else {
        Add-Check 'OUTSIDE_REPOSITORY' 'PASS' 'outsideRepository=true'
    }

    $bytes = [IO.File]::ReadAllBytes($resolvedFile)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    if ($hasBom) {
        Add-Check 'UTF8_BOM' 'BLOCKED' 'bom=false'
    } else {
        Add-Check 'UTF8_BOM' 'PASS' 'bom=false'
    }

    try {
        $decoded = [Text.UTF8Encoding]::new($false, $true).GetString($bytes)
        Add-Check 'STRICT_UTF8' 'PASS' 'strictUtf8=true'
    } catch [Text.DecoderFallbackException] {
        $decoded = $null
        Add-Check 'STRICT_UTF8' 'BLOCKED' 'strictUtf8=false'
    }

    $hasLineBreak = $bytes -contains 0x0A -or $bytes -contains 0x0D
    if ($hasLineBreak) {
        Add-Check 'LINE_BREAK' 'BLOCKED' 'lineBreak=false'
    } else {
        Add-Check 'LINE_BREAK' 'PASS' 'lineBreak=false'
    }

    if ($bytes.Length -ge 32) {
        Add-Check 'MINIMUM_LENGTH' 'PASS' 'utf8BytesGe32=true'
    } else {
        Add-Check 'MINIMUM_LENGTH' 'BLOCKED' 'utf8BytesGe32=false'
    }
    if ($null -eq $decoded -or [string]::IsNullOrWhiteSpace($decoded) -or $decoded.Contains([char]0)) {
        Add-Check 'SECRET_SHAPE' 'BLOCKED' 'nonBlankAndNoNul=false'
    } else {
        Add-Check 'SECRET_SHAPE' 'PASS' 'nonBlankAndNoNul=true'
    }

    try {
        $broadReadSids = @('S-1-1-0', 'S-1-5-11', 'S-1-5-32-545')
        $broadReaders = @((Get-Acl -LiteralPath $resolvedFile).Access | Where-Object {
            if ($_.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow) { return $false }
            try {
                $sid = $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
            } catch { return $true }
            $canRead = ($_.FileSystemRights -band [Security.AccessControl.FileSystemRights]::ReadData) -ne 0
            $canRead -and $broadReadSids -contains $sid
        })
        if ($broadReaders.Count -eq 0) {
            Add-Check 'FILE_ACL' 'PASS' 'broadReadPrincipals=0'
        } else {
            Add-Check 'FILE_ACL' 'BLOCKED' 'broadReadPrincipalsPresent=true'
        }
    } catch {
        Add-Check 'FILE_ACL' 'BLOCKED' 'aclReadable=false'
    }

    $bytes = $null
    $decoded = $null
} catch [System.Management.Automation.ItemNotFoundException] {
    Add-Check 'REGULAR_FILE' 'BLOCKED' 'regularFile=false reason=not-found'
} catch [System.InvalidOperationException] {
    # A precise BLOCKED check has already been recorded.
} catch {
    Add-Check 'PREFLIGHT' 'ERROR' 'unexpected-validation-error'
}

$checks | Format-Table -AutoSize -Wrap | Out-String -Width 160 | Write-Output
if (@($checks | Where-Object Status -eq 'ERROR').Count -gt 0) { exit 1 }
if (@($checks | Where-Object Status -eq 'BLOCKED').Count -gt 0) { exit 2 }
exit 0
