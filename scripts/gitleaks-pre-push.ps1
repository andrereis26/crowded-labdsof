#Requires -Version 7.4
<#
.SYNOPSIS
Pre-push hook: scans the commits being pushed for secrets.

.DESCRIPTION
Git passes the remote name as the first argument and one line per pushed ref
on standard input:
  <local ref> <local sha> <remote ref> <remote sha>
Bash twin (Debian, macOS): scripts/gitleaks-pre-push.sh

.PARAMETER Remote
The name of the remote being pushed to.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Remote
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$zero = '0' * 40
$status = 0

while ($null -ne ($line = [Console]::In.ReadLine())) {
    $null, $localSha, $null, $remoteSha = -split $line
    # A deleted branch pushes no commits.
    if (-not $localSha -or $localSha -eq $zero) { continue }
    $range = if ($remoteSha -eq $zero) {
        # New branch: the commits that are not on the remote yet.
        "$localSha --not --remotes=$Remote"
    } else {
        "$remoteSha..$localSha"
    }
    & (Join-Path $PSScriptRoot 'gitleaks.ps1') git "--log-opts=$range" --redact --verbose --no-banner
    if ($LASTEXITCODE -ne 0) { $status = 1 }
}

exit $status
