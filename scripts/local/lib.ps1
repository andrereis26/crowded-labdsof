# Settings and functions shared by the scripts in scripts/local/. Dot-sourced,
# not run. lib.sh is its Bash twin: keep them in step.
#
# For PowerShell 7.4 or later on Windows 11. The values of .env never go into
# the session's environment, which outlives the script: Import-DotEnv returns
# them, and Invoke-WithEnvironment sets them for one command only.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# A native command (docker, git...) that fails stops the script, like set -e.
$PSNativeCommandUseErrorActionPreference = $true

$script:Root = (Resolve-Path ([IO.Path]::Combine($PSScriptRoot, '..', '..'))).Path
$script:LocalDir = [IO.Path]::Combine($Root, 'scripts', 'local')
$script:EnvFile = Join-Path $Root '.env'
$script:EnvExample = Join-Path $Root '.env.example'
# Git-ignored files the scripts write: JWT keys, logs and process ids.
$script:StateDir = Join-Path $Root '.local'

$script:StackName = 'crowded'
$script:StackFile = [IO.Path]::Combine($Root, 'deployment', 'docker-compose.swarm.yml')
$script:ObservabilityFile = [IO.Path]::Combine($Root, 'deployment', 'observability', 'docker-compose.observability.yml')
$script:InfraProject = 'crowded-infra'
$script:Gateway = 'crowded-local-gateway'
# The router's image (deployment/docker-compose.swarm.yml).
$script:GatewayImage = 'nginxinc/nginx-unprivileged:1.31.7-alpine'
# Label of the Swarm secrets and the gateway, which stack-down removes.
$script:LocalLabel = 'crowded.local=true'

# The apps start.ps1 runs from source, and the images stack-up.ps1 builds
# (database is the migration job, ADR 0012).
$script:Apps = @('core', 'jobs', 'notifications', 'backoffice', 'participant-app')
$script:ImageServices = @('database', 'core', 'jobs', 'notifications', 'backoffice', 'participant-app')

function Write-Step([string]$Message) {
    [Console]::Error.WriteLine("==> $Message")
}

function Exit-WithError([string]$Message) {
    [Console]::Error.WriteLine("Error: $Message")
    exit 1
}

# Runs a native command and tells whether it succeeded, discarding its output.
# A command that is not installed fails.
function Test-Native([string]$FilePath, [string[]]$ArgumentList) {
    if (-not (Get-Command $FilePath -ErrorAction SilentlyContinue)) { return $false }
    $PSNativeCommandUseErrorActionPreference = $false
    & $FilePath @ArgumentList *> $null
    return $LASTEXITCODE -eq 0
}

# The output of a native command as one trimmed string, or '' when it fails.
function Get-NativeOutput([string]$FilePath, [string[]]$ArgumentList) {
    if (-not (Get-Command $FilePath -ErrorAction SilentlyContinue)) { return '' }
    $PSNativeCommandUseErrorActionPreference = $false
    $output = & $FilePath @ArgumentList 2> $null
    if ($LASTEXITCODE -ne 0) { return '' }
    return (($output | ForEach-Object { "$_" }) -join "`n").Trim()
}

# Runs docker with exactly <InputText> on its standard input. PowerShell's own
# pipe would add a line break, which would become part of a secret.
function Invoke-DockerWithInput([string[]]$ArgumentList, [string]$InputText) {
    $info = [System.Diagnostics.ProcessStartInfo]::new('docker')
    foreach ($argument in $ArgumentList) { $info.ArgumentList.Add($argument) }
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($info)
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($InputText)
    $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
    $process.StandardInput.Close()
    $null = $process.StandardOutput.ReadToEnd()
    $errors = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { Exit-WithError "docker $($ArgumentList -join ' ') failed: $errors" }
}

# Keys of .env.example.
function Get-EnvKey {
    foreach ($line in Get-Content -LiteralPath $EnvExample) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=') { $Matches[1] }
    }
}

# Reads .env into a hashtable, and fails when a key of .env.example has no
# value. Values are never printed.
function Import-DotEnv {
    if (-not (Test-Path -LiteralPath $EnvFile)) { Exit-WithError 'no .env: run scripts/local/setup.ps1 first.' }
    $config = @{}
    $number = 0
    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        $number++
        if ($line -eq '' -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { Exit-WithError ".env, line ${number}: expected KEY=value." }
        $config[$Matches[1]] = $Matches[2]
    }
    $missing = @(Get-EnvKey | Where-Object { -not $config[$_] })
    if ($missing) { Exit-WithError ".env has no value for: $($missing -join ' '). Copy them from .env.example." }
    return $config
}

# Runs <ScriptBlock> with <Environment> (a hashtable) added to the process
# environment, and restores the environment afterwards.
# The block sees this function's variables before its caller's (dynamic
# scope), hence their unusual names.
function Invoke-WithEnvironment([hashtable]$Environment, [scriptblock]$ScriptBlock) {
    $savedEnvironment = @{}
    foreach ($environmentName in $Environment.Keys) {
        $savedEnvironment[$environmentName] = [Environment]::GetEnvironmentVariable($environmentName)
        [Environment]::SetEnvironmentVariable($environmentName, [string]$Environment[$environmentName])
    }
    try {
        & $ScriptBlock
    } finally {
        foreach ($environmentName in $savedEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($environmentName, $savedEnvironment[$environmentName])
        }
    }
}

function Assert-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Exit-WithError 'Docker is not installed (scripts/local/setup.ps1 says how).' }
    if (-not (Test-Native docker @('info'))) { Exit-WithError 'Docker is not running: start Docker Desktop first.' }
    if (-not (Test-Native docker @('compose', 'version'))) { Exit-WithError 'Docker Compose v2 (docker compose) is missing.' }
}

# First 12 hex digits of the SHA-256 of a string (UTF-8) or of a file.
function Get-ShortHash([string]$Text, [string]$Path) {
    $bytes = if ($Path) { [IO.File]::ReadAllBytes($Path) } else { [System.Text.UTF8Encoding]::new($false).GetBytes($Text) }
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant().Substring(0, 12)
}

# core -> CORE, participant-app -> PARTICIPANT_APP
function ConvertTo-UpperName([string]$Name) {
    return $Name.ToUpperInvariant().Replace('-', '_')
}

function ConvertFrom-Base64Text([string]$Value, [string]$Key) {
    try {
        return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Value))
    } catch {
        Exit-WithError "$Key in .env is not valid base64."
    }
}

# Writes the JWT key pair of .env to .local/jwt/, readable by the user only
# (a new folder under the user's profile inherits that).
function Write-JwtKey([hashtable]$Config) {
    $dir = Join-Path $StateDir 'jwt'
    $null = New-Item -ItemType Directory -Force -Path $dir
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText((Join-Path $dir 'private.pem'), (ConvertFrom-Base64Text $Config.JWT_PRIVATE_KEY_BASE64 'JWT_PRIVATE_KEY_BASE64'), $utf8)
    [IO.File]::WriteAllText((Join-Path $dir 'public.pem'), (ConvertFrom-Base64Text $Config.JWT_PUBLIC_KEY_BASE64 'JWT_PUBLIC_KEY_BASE64'), $utf8)
}

# The major version of the Java that the Maven wrapper runs on: JAVA_HOME's,
# else the one on PATH. '' when there is none.
function Get-JavaMajor {
    $java = if ($env:JAVA_HOME) { [IO.Path]::Combine($env:JAVA_HOME, 'bin', 'java') } else { 'java' }
    if (-not (Get-Command $java -ErrorAction SilentlyContinue)) { return '' }
    $PSNativeCommandUseErrorActionPreference = $false
    $output = (& $java -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
    if ($output -match 'version "(\d+)') { return $Matches[1] }
    return ''
}

function Test-SwarmActive {
    return (Get-NativeOutput docker @('info', '--format', '{{.Swarm.LocalNodeState}}')) -eq 'active'
}

function Test-StackExist {
    if (-not (Test-SwarmActive)) { return $false }
    return @((Get-NativeOutput docker @('stack', 'ls', '--format', '{{.Name}}')) -split "`n") -contains $StackName
}

# Arguments of docker compose for the infrastructure of infra-up.ps1.
function Get-InfraComposeArgument {
    return @('compose', '--project-name', $InfraProject,
        '-f', ([IO.Path]::Combine($Root, 'database', 'compose.yaml')), '-f', (Join-Path $LocalDir 'infra.compose.yaml'))
}

# docker compose for the infrastructure, with the values of .env.
function Invoke-InfraCompose([hashtable]$Config, [string[]]$ArgumentList) {
    Invoke-WithEnvironment $Config { & docker @(Get-InfraComposeArgument) @ArgumentList }
}

function Test-InfraRunning([hashtable]$Config) {
    $ids = Invoke-WithEnvironment $Config { Get-NativeOutput docker (@(Get-InfraComposeArgument) + @('ps', '--quiet')) }
    return [bool]$ids
}

# Whether something accepts connections on 127.0.0.1:<Port>.
function Test-Port([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        return $client.ConnectAsync('127.0.0.1', $Port).Wait(1000)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

# Waits until the newest run of the Swarm job <Service> has completed, and
# fails, showing its log, as soon as that run fails.
function Wait-SwarmJob([string]$Service, [int]$TimeoutSeconds = 300) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ($true) {
        $index = Get-NativeOutput docker @('service', 'inspect', '--format', '{{if .JobStatus}}{{.JobStatus.JobIteration.Index}}{{end}}', $Service)
        $states = @()
        foreach ($task in (Get-NativeOutput docker @('service', 'ps', '--quiet', '--no-trunc', $Service)) -split "`n") {
            if ($task) { $states += Get-NativeOutput docker @('inspect', '--format', '{{if .JobIteration}}{{.JobIteration.Index}}{{end}}={{.Status.State}}', $task) }
        }
        if ($states -contains "$index=complete") { return }
        if ($states -contains "$index=failed" -or $states -contains "$index=rejected") {
            Write-Step "$Service failed. Its log:"
            $PSNativeCommandUseErrorActionPreference = $false
            & docker service logs --raw --tail 60 $Service
            Exit-WithError "$Service failed."
        }
        if ((Get-Date) -ge $deadline) { Exit-WithError "timed out after ${TimeoutSeconds}s waiting for $Service." }
        Start-Sleep -Seconds 3
    }
}

# The port of an app run from source, from .env.
function Get-AppPort([hashtable]$Config, [string]$App) {
    return [int]$Config["$(ConvertTo-UpperName $App)_PORT"]
}

function Get-PidFile([string]$App) { return [IO.Path]::Combine($StateDir, 'run', "$App.pid") }
function Get-LogFile([string]$App) { return [IO.Path]::Combine($StateDir, 'logs', "$App.log") }

# The process of an app that start.ps1 started and that still runs, or $null.
function Get-AppProcess([string]$App) {
    $file = Get-PidFile $App
    if (-not (Test-Path -LiteralPath $file)) { return $null }
    $id = (Get-Content -LiteralPath $file -Raw).Trim()
    if ($id -notmatch '^\d+$') { return $null }
    return Get-Process -Id ([int]$id) -ErrorAction SilentlyContinue
}
