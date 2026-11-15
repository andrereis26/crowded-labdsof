#Requires -Version 7.4
<#
.SYNOPSIS
Runs one app from source on this machine, in the background.

.DESCRIPTION
Against the infrastructure of infra-up.ps1 (or the data stores of
stack-up.ps1), with its configuration from .env:
  core, jobs, notifications  mvnw.cmd spring-boot:run, as the service's own
                             database user, on CORE_PORT, JOBS_PORT...
  backoffice                 npm run dev, on BACKOFFICE_PORT
  participant-app            flutter run -d web-server, on PARTICIPANT_APP_PORT
It waits until the app listens on its port. The app's output goes to
.local/logs/<app>.log; stop it with stop.ps1. Running it again for an app
that runs does nothing.
Bash twin (Debian, macOS): scripts/local/start.sh

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

# A first Maven or Flutter build downloads its dependencies.
$timeoutSeconds = if ($env:TIMEOUT_SECONDS) { [int]$env:TIMEOUT_SECONDS } else { 600 }

$config = Import-DotEnv
$running = Get-AppProcess $App
if ($running) {
    Write-Step "$App already runs (process $($running.Id))."
    exit 0
}
$port = Get-AppPort $config $App
if (Test-Port $port) { Exit-WithError "port $port ($App) is already in use." }

$dir = Join-Path $Root $App
$environment = @{}
switch ($App) {
    { $_ -in 'core', 'jobs', 'notifications' } {
        if ((Get-JavaMajor) -ne '25') { Exit-WithError 'Maven needs JDK 25: set JAVA_HOME to a JDK 25 installation.' }
        if (-not (Test-Port $config.DB_PORT)) {
            Exit-WithError "nothing listens on 127.0.0.1:$($config.DB_PORT): run scripts/local/infra-up.ps1 first."
        }
        Write-JwtKey $config
        # Spring Boot reads file: URLs with forward slashes on Windows too.
        $jwtDir = (Join-Path $StateDir 'jwt').Replace('\', '/')
        $environment = @{
            SERVER_PORT = $port
            DB_URL = "jdbc:postgresql://127.0.0.1:$($config.DB_PORT)/$($config.POSTGRES_DB)"
            DB_USERNAME = "crowded_$App"
            DB_PASSWORD = $config["$(ConvertTo-UpperName $App)_DB_PASSWORD"]
            RABBITMQ_HOST = '127.0.0.1'
            RABBITMQ_PORT = $config.RABBITMQ_PORT
            RABBITMQ_USERNAME = $config.RABBITMQ_USERNAME
            RABBITMQ_PASSWORD = $config.RABBITMQ_PASSWORD
            REDIS_HOST = '127.0.0.1'
            REDIS_PORT = $config.REDIS_PORT
            REDIS_PASSWORD = $config.REDIS_PASSWORD
            JWT_PUBLIC_KEY_LOCATION = "file:$jwtDir/public.pem"
        }
        # Only Core signs tokens.
        if ($App -eq 'core') { $environment.JWT_PRIVATE_KEY_LOCATION = "file:$jwtDir/private.pem" }
        # Only Core imports tickets.
        if ($App -eq 'core') { $environment.CROWDED_TICKETS_ENTRYCODEKEY = $config.TICKET_ENTRY_CODE_KEY }
        if ($App -eq 'core' -and $config['GROQ_API_KEY']) { $environment.CROWDED_AI_GROQ_APIKEY = $config['GROQ_API_KEY'] }
        $command = if ($IsWindows) { 'mvnw.cmd -B -ntp spring-boot:run' } else { './mvnw -B -ntp spring-boot:run' }
    }
    'backoffice' {
        if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { Exit-WithError 'npm is missing: run scripts/local/setup.ps1.' }
        if (-not (Test-Path -LiteralPath (Join-Path $dir 'node_modules'))) {
            Write-Step "Installing the backoffice's dependencies"
            npm --prefix $dir ci --no-audit --no-fund
        }
        $command = "npm run dev -- --host 127.0.0.1 --port $port --strictPort"
    }
    'participant-app' {
        if (-not (Get-Command flutter -ErrorAction SilentlyContinue)) { Exit-WithError 'flutter is missing: run scripts/local/setup.ps1.' }
        $command = "flutter run -d web-server --web-hostname 127.0.0.1 --web-port $port"
    }
}

$logPath = Get-LogFile $App
$pidPath = Get-PidFile $App
$null = New-Item -ItemType Directory -Force -Path (Split-Path $logPath), (Split-Path $pidPath)
Write-Step "Starting $App (log: $([IO.Path]::GetRelativePath($Root, $logPath)))"

# Through the command interpreter, which runs mvnw.cmd, npm.cmd and flutter.bat
# and sends their output to the log. stop.ps1 ends it with every process it
# started. The app gets its own settings and none of the other values of .env.
if ($IsWindows) {
    $info = [System.Diagnostics.ProcessStartInfo]::new('cmd.exe', "/d /s /c `"$command > `"$logPath`" 2>&1`"")
} else {
    $info = [System.Diagnostics.ProcessStartInfo]::new('/bin/sh')
    $info.ArgumentList.Add('-c')
    $info.ArgumentList.Add("exec $command > '$logPath' 2>&1 < /dev/null")
}
$info.WorkingDirectory = $dir
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
foreach ($key in Get-EnvKey) { $null = $info.Environment.Remove($key) }
foreach ($key in $environment.Keys) { $info.Environment[$key] = [string]$environment[$key] }
$process = [System.Diagnostics.Process]::Start($info)
Set-Content -LiteralPath $pidPath -Value $process.Id

$deadline = (Get-Date).AddSeconds($timeoutSeconds)
while (-not (Test-Port $port)) {
    if ($process.HasExited) {
        Remove-Item -LiteralPath $pidPath -Force
        Get-Content -LiteralPath $logPath -Tail 40 | ForEach-Object { [Console]::Error.WriteLine($_) }
        Exit-WithError "$App stopped while starting. Its log: $logPath"
    }
    if ((Get-Date) -ge $deadline) {
        & (Join-Path $PSScriptRoot 'stop.ps1') $App
        Exit-WithError "$App did not listen on port $port within ${timeoutSeconds}s. Its log: $logPath"
    }
    Start-Sleep -Seconds 2
}
Write-Step "$App runs on http://localhost:$port/ (process $($process.Id)). Stop it with scripts/local/stop.ps1 $App."
