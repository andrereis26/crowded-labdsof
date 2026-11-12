#Requires -Version 7.4
<#
.SYNOPSIS
Runs the whole system on this machine as the production environment runs it.

.DESCRIPTION
The Swarm stack of deployment/docker-compose.swarm.yml, with the local
override stack.local.yml. It
  1. turns on single-node Swarm if it is off, on 127.0.0.1;
  2. builds every app's image from the working copy (the participant app
     runs flutter build web first), or with -Pull pulls the Docker Hub images
     tagged main;
  3. creates the Swarm secrets from .env, named after a hash of their value;
  4. deploys the stack and its overlay networks: the database job migrates
     and seeds the database before Core, Jobs and Notifications first start;
  5. starts the gateway, which publishes the system and the data stores on
     127.0.0.1 only (Swarm cannot bind a port to one address);
  6. waits until every service is healthy and prints the URLs.
Running it again updates what changed. stack-down.ps1 removes it all.
Bash twin (Debian, macOS): scripts/local/stack-up.sh

.PARAMETER Pull
Use the images tagged main instead of building them.

.PARAMETER WithObservability
Add Prometheus, Loki, Alloy and Grafana.
#>
[CmdletBinding()]
param(
    [switch]$Pull,
    [switch]$WithObservability
)

. (Join-Path $PSScriptRoot 'lib.ps1')

$timeoutSeconds = if ($env:TIMEOUT_SECONDS) { [int]$env:TIMEOUT_SECONDS } else { 600 }

$stackFiles = @('-c', $StackFile, '-c', (Join-Path $LocalDir 'stack.local.yml'))
if ($WithObservability) {
    if (-not (Test-Path -LiteralPath $ObservabilityFile)) {
        Exit-WithError "-WithObservability needs $([IO.Path]::GetRelativePath($Root, $ObservabilityFile)), which this checkout does not have yet."
    }
    $stackFiles += @('-c', $ObservabilityFile)
}

$config = Import-DotEnv
Assert-Docker
if (Test-InfraRunning $config) {
    Exit-WithError 'the infrastructure of infra-up.ps1 uses the same ports: run scripts/local/infra-down.ps1 first.'
}
# What the stack file reads: .env, then the images, secrets and versions below.
$stackEnvironment = $config.Clone()

# --- 1. Swarm -------------------------------------------------------------------
$swarmState = Get-NativeOutput docker @('info', '--format', '{{.Swarm.LocalNodeState}}')
switch ($swarmState) {
    'active' { }
    'inactive' {
        Write-Step 'Turning on single-node Swarm'
        docker swarm init --advertise-addr 127.0.0.1 --listen-addr 127.0.0.1:2377 | Out-Null
    }
    default { Exit-WithError "Swarm is in state '$swarmState': fix it, or turn it off with docker swarm leave --force." }
}

# --- 2. Images ------------------------------------------------------------------
if ($Pull) {
    foreach ($service in $ImageServices) {
        $image = "$($config.DOCKERHUB_NAMESPACE)/crowded-${service}:main"
        Write-Step "Pulling $image"
        docker pull --quiet $image | Out-Null
        $stackEnvironment["$(ConvertTo-UpperName $service)_IMAGE"] = $image
    }
} else {
    if (-not (Get-Command flutter -ErrorAction SilentlyContinue)) { Exit-WithError 'flutter is missing: install it (setup.ps1), or use -Pull.' }
    Write-Step 'Building the participant app for the web'
    Push-Location (Join-Path $Root 'participant-app')
    try { flutter build web --release } finally { Pop-Location }
    foreach ($service in $ImageServices) {
        $image = "crowded/${service}:local"
        Write-Step "Building $image"
        docker build --quiet --tag $image (Join-Path $Root $service) | Out-Null
        # Deployed by a tag made from the image id: Swarm keeps a service running
        # while its image reference is unchanged, so a rebuilt image needs a new
        # tag, and an unchanged one keeps its tag and its containers.
        $id = (Get-NativeOutput docker @('image', 'inspect', '--format', '{{.Id}}', $image)).Trim() -replace '^sha256:', ''
        $image = "crowded/${service}:local-$($id.Substring(0, 12))"
        docker tag "crowded/${service}:local" $image
        $stackEnvironment["$(ConvertTo-UpperName $service)_IMAGE"] = $image
    }
}

# --- 3. Secrets -----------------------------------------------------------------
# Creates the secret crowded_local_<key>_<hash> unless it exists, and records
# its name as <KEY>_SECRET, which the stack file reads.
$secretNames = @()
function Add-StackSecret([string]$Key, [string]$Value) {
    $name = "${StackName}_local_$($Key.ToLowerInvariant())_$(Get-ShortHash -Text $Value)"
    if (-not (Test-Native docker @('secret', 'inspect', $name))) {
        Invoke-DockerWithInput @('secret', 'create', '--label', $LocalLabel, $name, '-') $Value
    }
    $stackEnvironment["${Key}_SECRET"] = $name
    $script:secretNames += $name
}

Write-Step 'Creating the secrets'
foreach ($key in 'POSTGRES_PASSWORD', 'CORE_DB_PASSWORD', 'JOBS_DB_PASSWORD', 'NOTIFICATIONS_DB_PASSWORD', 'RABBITMQ_PASSWORD', 'REDIS_PASSWORD') {
    Add-StackSecret $key $config[$key]
}
Add-StackSecret 'JWT_PRIVATE_KEY' (ConvertFrom-Base64Text $config.JWT_PRIVATE_KEY_BASE64 'JWT_PRIVATE_KEY_BASE64')
Add-StackSecret 'JWT_PUBLIC_KEY' (ConvertFrom-Base64Text $config.JWT_PUBLIC_KEY_BASE64 'JWT_PUBLIC_KEY_BASE64')
# RabbitMQ reads its default user from a config file, not from a secret file.
Add-StackSecret 'RABBITMQ_CONF' "default_user = $($config.RABBITMQ_USERNAME)`ndefault_pass = $($config.RABBITMQ_PASSWORD)"

# --- 4. Deployment --------------------------------------------------------------
# Swarm configs cannot change, so the router's is named after its hash.
$stackEnvironment.ROUTER_CONF_VERSION = Get-ShortHash -Path ([IO.Path]::Combine($Root, 'deployment', 'router', 'nginx.conf'))
# The migration job runs again whenever the database image changes.
$stackEnvironment.CROWDED_RELEASE = "local-$(Get-ShortHash -Text (Get-NativeOutput docker @('image', 'inspect', '--format', '{{.Id}}', $stackEnvironment.DATABASE_IMAGE)))"

function Invoke-StackDeploy([hashtable]$Environment) {
    Invoke-WithEnvironment $Environment {
        docker stack deploy --prune --resolve-image never @stackFiles $StackName | Out-Null
    }
}

if (-not (Test-Native docker @('service', 'inspect', "${StackName}_core"))) {
    # First deployment: Core, Jobs and Notifications wait for the migration.
    Write-Step 'Deploying the stack and migrating the database'
    $firstEnvironment = $stackEnvironment.Clone()
    $firstEnvironment.CORE_REPLICAS = 0
    $firstEnvironment.JOBS_REPLICAS = 0
    $firstEnvironment.NOTIFICATIONS_REPLICAS = 0
    Invoke-StackDeploy $firstEnvironment
    Wait-SwarmJob "${StackName}_database" $timeoutSeconds
}
Write-Step 'Deploying the stack'
Invoke-StackDeploy $stackEnvironment
Wait-SwarmJob "${StackName}_database" $timeoutSeconds

# Secrets from an earlier .env, unless a service still uses them.
foreach ($secret in (Get-NativeOutput docker @('secret', 'ls', '--filter', "label=$LocalLabel", '--format', '{{.Name}}')) -split "`n") {
    if ($secret -and $secretNames -notcontains $secret) { $null = Test-Native docker @('secret', 'rm', $secret) }
}

# --- 5. Gateway -----------------------------------------------------------------
Write-Step 'Starting the gateway on 127.0.0.1'
$null = Test-Native docker @('rm', '--force', $Gateway)
docker run --detach --name $Gateway --label $LocalLabel --restart unless-stopped `
    --network "${StackName}_edge" `
    --publish "127.0.0.1:$($config.HTTP_PORT):8080" `
    --publish "127.0.0.1:$($config.DB_PORT):5432" `
    --publish "127.0.0.1:$($config.RABBITMQ_PORT):5672" `
    --publish "127.0.0.1:$($config.REDIS_PORT):6379" `
    --volume "$(Join-Path $LocalDir 'gateway.nginx.conf'):/etc/nginx/nginx.conf:ro" `
    --volume "$([IO.Path]::Combine($Root, 'deployment', 'router', 'nginx.conf')):/etc/nginx/conf.d/default.conf:ro" `
    $GatewayImage | Out-Null
docker network connect "${StackName}_backend" $Gateway

# --- 6. Health ------------------------------------------------------------------
Write-Step 'Waiting until every service is healthy'
$deadline = (Get-Date).AddSeconds($timeoutSeconds)
while ($true) {
    $pending = @()
    foreach ($line in (Get-NativeOutput docker @('stack', 'services', $StackName, '--format', '{{.Name}} {{.Replicas}}')) -split "`n") {
        $name, $replicas = $line -split ' ', 2
        # The migration job: "0/1 (1/1 completed)". It completed before this wait.
        if (-not $replicas -or $replicas -like '*completed*') { continue }
        $running, $desired = ($replicas -split ' ')[0] -split '/'
        if ($running -ne $desired) { $pending += "$($name.Substring($StackName.Length + 1))($replicas)" }
        # A service moving to a new image still counts its old task as running.
        $update = (Get-NativeOutput docker @('service', 'inspect', '--format', '{{if .UpdateStatus}}{{.UpdateStatus.State}}{{end}}', $name)).Trim()
        if ($update -eq 'updating') { $pending += "$($name.Substring($StackName.Length + 1))(updating)" }
    }
    if (-not $pending) { break }
    if ((Get-Date) -ge $deadline) {
        Exit-WithError "not healthy after ${timeoutSeconds}s: $($pending -join ' '). See scripts/local/status.ps1 and logs.ps1 <service>."
    }
    Start-Sleep -Seconds 5
}

# Tags of earlier builds, now unused: an image a container still uses keeps its.
if (-not $Pull) {
    foreach ($service in $ImageServices) {
        $current = $stackEnvironment["$(ConvertTo-UpperName $service)_IMAGE"]
        foreach ($tag in (Get-NativeOutput docker @('image', 'ls', "crowded/$service", '--format', '{{.Tag}}')) -split "`n") {
            $tag = $tag.Trim()
            $old = "crowded/${service}:$tag"
            # Swarm keeps the stopped containers of earlier tasks, which still use theirs.
            if ($tag -like 'local-*' -and $old -ne $current -and
                -not (Get-NativeOutput docker @('ps', '--all', '--quiet', '--filter', "ancestor=$old"))) {
                [void](Test-Native docker @('image', 'rm', $old))
            }
        }
    }
    # A tag that could not be removed is not a failure of the deployment.
    $global:LASTEXITCODE = 0
}

Write-Output @"

Crowded is running, on 127.0.0.1 only:
  Participant app  http://localhost:$($config.HTTP_PORT)/
  Backoffice       http://localhost:$($config.HTTP_PORT)/backoffice/
  Core API         http://localhost:$($config.HTTP_PORT)/api/
  Notifications    http://localhost:$($config.HTTP_PORT)/api/notifications/
  PostgreSQL       127.0.0.1:$($config.DB_PORT), database $($config.POSTGRES_DB) (users and passwords in .env)
  RabbitMQ         127.0.0.1:$($config.RABBITMQ_PORT)
  Redis            127.0.0.1:$($config.REDIS_PORT)
See scripts/local/status.ps1 and logs.ps1 <service>; remove it all with stack-down.ps1.
"@
