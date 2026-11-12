#Requires -Version 7.4
<#
.SYNOPSIS
Stops an app that start.ps1 runs from source, with every process it started.

.DESCRIPTION
Windows has no signal that asks a console process to stop, so the processes
end at once. An app that does not run is left as it is.
Bash twin (Debian, macOS): scripts/local/stop.sh

.PARAMETER App
core, jobs, notifications, backoffice or participant-app.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('core', 'jobs', 'notifications', 'backoffice', 'participant-app')]
    [string]$App
)

. (Join-Path $PSScriptRoot 'lib.ps1')

$pidPath = Get-PidFile $App
$process = Get-AppProcess $App
if (-not $process) {
    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
    Write-Step "$App does not run."
    exit 0
}

Write-Step "Stopping $App (process $($process.Id))"
# The whole tree: the command interpreter, Maven and its forked JVM, Node...
$process.Kill($true)
if (-not $process.WaitForExit(30000)) { Exit-WithError "$App (process $($process.Id)) did not stop within 30s." }
Remove-Item -LiteralPath $pidPath -Force
Write-Step "$App stopped."
