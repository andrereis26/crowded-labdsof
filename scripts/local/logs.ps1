#Requires -Version 7.4
<#
.SYNOPSIS
Follows the log of one service.

.DESCRIPTION
The first that runs of:
  a service of the stack (stack-up.ps1), e.g. core, database, postgres;
  the stack's gateway: gateway;
  a service of the infrastructure (infra-up.ps1): postgres, migrate, rabbitmq, redis;
  an app that start.ps1 runs from source.
Stop following with Ctrl+C.
Bash twin (Debian, macOS): scripts/local/logs.sh

.PARAMETER Service
The service, e.g. core.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Service
)

. (Join-Path $PSScriptRoot 'lib.ps1')

$config = Import-DotEnv
Assert-Docker

if ((Test-StackExist) -and (Test-Native docker @('service', 'inspect', "${StackName}_$Service"))) {
    docker service logs --follow --tail 100 "${StackName}_$Service"
    exit
}
if ($Service -eq 'gateway' -and (Test-Native docker @('container', 'inspect', $Gateway))) {
    docker logs --follow --tail 100 $Gateway
    exit
}
if (Test-InfraRunning $config) {
    $services = Invoke-WithEnvironment $config { Get-NativeOutput docker (@(Get-InfraComposeArgument) + @('ps', '--all', '--services')) }
    if (($services -split "`n") -contains $Service) {
        Invoke-InfraCompose $config @('logs', '--follow', '--tail', '100', $Service)
        exit
    }
}
if ($Apps -contains $Service -and (Test-Path -LiteralPath (Get-LogFile $Service))) {
    if (-not (Get-AppProcess $Service)) { Write-Step "$Service does not run; its last log:" }
    Get-Content -LiteralPath (Get-LogFile $Service) -Tail 100 -Wait
    exit
}
Exit-WithError "no running service named ${Service}: see scripts/local/status.ps1."
