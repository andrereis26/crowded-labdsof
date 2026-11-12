#Requires -Version 7.4
<#
.SYNOPSIS
Shows what runs locally.

.DESCRIPTION
The services of the stack (stack-up.ps1) and the gateway, the infrastructure
(infra-up.ps1), and the apps that start.ps1 runs from source. A Swarm task
counts as running only once its health check passes, so "1/1" replicas means
healthy.
Bash twin (Debian, macOS): scripts/local/status.sh
#>
[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib.ps1')

$config = Import-DotEnv
Assert-Docker

Write-Output 'Stack (stack-up.ps1):'
if (Test-StackExist) {
    docker stack services $StackName --format 'table {{.Name}}\t{{.Mode}}\t{{.Replicas}}\t{{.Image}}'
    # Tasks that should run but do not, with Swarm's reason.
    (Get-NativeOutput docker @('stack', 'ps', $StackName, '--filter', 'desired-state=running',
        '--format', "{{.Name}}`t{{.CurrentState}}`t{{.Error}}")) -split "`n" |
        Where-Object { $_ -and $_ -notmatch "`tRunning" }
    $gateway = Get-NativeOutput docker @('ps', '--all', '--filter', "name=^$Gateway`$", '--format', '{{.Status}}')
    Write-Output "Gateway: $(if ($gateway) { $gateway } else { 'not running' })"
} else {
    Write-Output '  not running'
}

Write-Output ''
Write-Output 'Infrastructure (infra-up.ps1):'
if (Test-InfraRunning $config) {
    Invoke-InfraCompose $config @('ps', '--format', 'table {{.Service}}\t{{.Status}}\t{{.Ports}}')
} else {
    Write-Output '  not running'
}

Write-Output ''
Write-Output 'Apps from source (start.ps1):'
foreach ($app in $Apps) {
    $port = Get-AppPort $config $app
    $process = Get-AppProcess $app
    $state = if (-not $process) {
        'stopped'
    } elseif (Test-Port $port) {
        "running, process $($process.Id), http://localhost:$port/"
    } else {
        "running, process $($process.Id), starting"
    }
    Write-Output ('  {0,-16} {1}' -f $app, $state)
}
