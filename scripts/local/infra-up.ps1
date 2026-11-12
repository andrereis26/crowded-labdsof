#Requires -Version 7.4
<#
.SYNOPSIS
Starts the infrastructure alone, for apps run from source by start.ps1.

.DESCRIPTION
PostgreSQL migrated and seeded (database/compose.yaml), RabbitMQ and Redis,
in containers, with the passwords of .env and their ports on 127.0.0.1.
Running it again keeps what runs and migrates again (a no-op). The database
lives in memory: infra-down.ps1, or a restart, empties it.
Bash twin (Debian, macOS): scripts/local/infra-up.sh
#>
[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib.ps1')

$config = Import-DotEnv
Assert-Docker
if (Test-StackExist) {
    Exit-WithError 'the stack of stack-up.ps1 uses the same ports: run scripts/local/stack-down.ps1 first.'
}

Write-Step 'Starting PostgreSQL, RabbitMQ and Redis'
Invoke-InfraCompose $config @('up', '--detach', '--wait', 'postgres', 'rabbitmq', 'redis')
Write-Step 'Migrating the database and loading the demo data'
Invoke-InfraCompose $config @('build', '--quiet', 'migrate')
Invoke-InfraCompose $config @('run', '--rm', 'migrate')

Write-Output @"

The infrastructure is running, on 127.0.0.1 only:
  PostgreSQL  127.0.0.1:$($config.DB_PORT), database $($config.POSTGRES_DB) (users and passwords in .env)
  RabbitMQ    127.0.0.1:$($config.RABBITMQ_PORT)
  Redis       127.0.0.1:$($config.REDIS_PORT)
Run an app with scripts/local/start.ps1 <$($Apps -join '|')>.
"@
