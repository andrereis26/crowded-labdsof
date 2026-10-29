#Requires -Version 7.4
<#
.SYNOPSIS
Stops the infrastructure of infra-up.ps1 and deletes its data (#111).

.DESCRIPTION
Apps still running from source lose their connections: stop them with stop.ps1.
Bash twin (Debian, macOS): scripts/local/infra-down.sh
#>
[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib.ps1')

$config = Import-DotEnv
Assert-Docker
Write-Step 'Stopping the infrastructure'
Invoke-InfraCompose $config @('down', '--volumes', '--remove-orphans')
