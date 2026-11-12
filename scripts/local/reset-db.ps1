#Requires -Version 7.4
<#
.SYNOPSIS
Drops the local database and creates it again, migrated.

.DESCRIPTION
With the demo data when SEED_DEMO_DATA is true, in the stack of stack-up.ps1,
or else in the infrastructure of infra-up.ps1. The services' open connections
are closed; their connection pools open new ones.
Bash twin (Debian, macOS): scripts/local/reset-db.sh
#>
[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib.ps1')

# Runs in the PostgreSQL container, as the user that owns the database, over
# the local socket, which needs no password.
$recreate = 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d postgres ' +
'-c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE)" ' +
'-c "CREATE DATABASE \"$POSTGRES_DB\""'

$config = Import-DotEnv
Assert-Docker

if (Test-StackExist) {
    $container = ((Get-NativeOutput docker @('ps', '--quiet', '--filter', "label=com.docker.swarm.service.name=${StackName}_postgres")) -split "`n")[0]
    if (-not $container) { Exit-WithError "the stack's PostgreSQL does not run: see scripts/local/status.ps1." }
    Write-Step "Dropping and creating the database $($config.POSTGRES_DB)"
    docker exec $container sh -c $recreate
    Write-Step "Migrating it (the stack's database job)"
    docker service update --force --detach "${StackName}_database" | Out-Null
    Wait-SwarmJob "${StackName}_database"
} elseif (Test-InfraRunning $config) {
    Write-Step "Dropping and creating the database $($config.POSTGRES_DB)"
    Invoke-InfraCompose $config @('exec', '-T', 'postgres', 'sh', '-c', $recreate)
    Write-Step 'Migrating it'
    Invoke-InfraCompose $config @('run', '--rm', 'migrate')
} else {
    Exit-WithError 'neither the stack (stack-up.ps1) nor the infrastructure (infra-up.ps1) runs.'
}
Write-Step 'The database is new.'
