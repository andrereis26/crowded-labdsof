#Requires -Version 7.4
<#
.SYNOPSIS
Removes what stack-up.ps1 created.

.DESCRIPTION
The gateway, the stack with its networks, its volumes (the data) and its
secrets. Swarm stays on; turn it off with docker swarm leave --force.
Running it again finds nothing to remove.
Bash twin (Debian, macOS): scripts/local/stack-down.sh
#>
[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib.ps1')

$timeoutSeconds = if ($env:TIMEOUT_SECONDS) { [int]$env:TIMEOUT_SECONDS } else { 120 }

Assert-Docker

# First, or the stack's networks cannot be removed.
if (Test-Native docker @('container', 'inspect', $Gateway)) {
    Write-Step 'Removing the gateway'
    docker rm --force $Gateway | Out-Null
}

if (Test-StackExist) {
    Write-Step 'Removing the stack'
    docker stack rm $StackName | Out-Null
}

# docker stack rm returns before the containers and networks are gone, and a
# volume cannot be removed while a container uses it.
$label = "label=com.docker.stack.namespace=$StackName"
$deadline = (Get-Date).AddSeconds($timeoutSeconds)
while ((Get-NativeOutput docker @('ps', '--all', '--quiet', '--filter', $label)) -or
    (Get-NativeOutput docker @('network', 'ls', '--quiet', '--filter', $label))) {
    if ((Get-Date) -ge $deadline) { Exit-WithError "the stack's containers or networks are still there after ${timeoutSeconds}s." }
    Start-Sleep -Seconds 2
}

$volumes = @((Get-NativeOutput docker @('volume', 'ls', '--quiet', '--filter', $label)) -split "`n" | Where-Object { $_ })
if ($volumes) {
    Write-Step 'Removing the data'
    docker volume rm @volumes | Out-Null
}

if (Test-SwarmActive) {
    $secrets = @((Get-NativeOutput docker @('secret', 'ls', '--quiet', '--filter', "label=$LocalLabel")) -split "`n" | Where-Object { $_ })
    if ($secrets) {
        Write-Step 'Removing the secrets'
        docker secret rm @secrets | Out-Null
    }
}
Write-Step 'The local stack is gone.'
