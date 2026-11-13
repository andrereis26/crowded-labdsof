#Requires -Version 7.4
<#
.SYNOPSIS
Prepares a fresh clone for local development, on Windows.

.DESCRIPTION
1. Checks the tools and versions the projects need, and says how to install
   any that is missing.
2. Creates .env from .env.example, with a freshly generated random value for
   every secret, unless .env exists: it is never overwritten.
3. Installs the Git hooks (lefthook install).
Exits with an error while a tool is missing; run it again once installed.
Bash twin (Debian, macOS): scripts/local/setup.sh
#>
[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'lib.ps1')

$script:MissingCount = 0

function Write-Ok([string]$What) {
    Write-Output "  ok       $What"
}

function Write-Missing([string]$What, [string]$Hint) {
    Write-Output "  missing  $What"
    Write-Output "           $Hint"
    $script:MissingCount++
}

# The first x.y.z (or x.y) in a text, or ''.
function Get-VersionOf([string]$Text) {
    if ($Text -match '\d+\.\d+(\.\d+)?') { return $Matches[0] }
    return ''
}

function Test-Tool {
    Write-Step 'Checking the tools'
    $dockerRunning = $false

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Missing 'Docker Desktop with Compose v2' 'winget install Docker.DockerDesktop'
    } elseif (-not (Test-Native docker @('info'))) {
        Write-Missing 'Docker is installed but not running' 'start Docker Desktop'
    } else {
        $compose = Get-NativeOutput docker @('compose', 'version', '--short')
        if ($compose) {
            $dockerRunning = $true
            Write-Ok "Docker $(Get-NativeOutput docker @('version', '--format', '{{.Server.Version}}')), Compose $compose"
        } else {
            Write-Missing 'Docker Compose v2 (docker compose)' 'update Docker Desktop'
        }
    }

    $java = Get-JavaMajor
    if ($java -eq '25') {
        Write-Ok "JDK 25 ($(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'java on PATH' }))"
    } else {
        Write-Missing "JDK 25 as JAVA_HOME (found: $(if ($java) { $java } else { 'none' }))" `
            'winget install EclipseAdoptium.Temurin.25.JDK, then set JAVA_HOME to its folder'
    }

    $node = Get-VersionOf (Get-NativeOutput node @('--version'))
    if ($node -and [version]$node -ge [version]'22.12') {
        Write-Ok "Node.js $node"
    } else {
        Write-Missing "Node.js 22.12 or later; CI uses 24 (found: $(if ($node) { $node } else { 'none' }))" 'winget install OpenJS.NodeJS.LTS'
    }

    $flutter = ''
    if ((Get-NativeOutput flutter @('--version')) -match 'Flutter (\d+\.\d+\.\d+)') { $flutter = $Matches[1] }
    if ($flutter -like '3.47.*') {
        Write-Ok "Flutter $flutter"
    } else {
        Write-Missing "Flutter 3.47, the CI version 3.47.7 (found: $(if ($flutter) { $flutter } else { 'none' }))" 'https://docs.flutter.dev/install/archive (3.47.7)'
    }

    $lefthook = Get-VersionOf (Get-NativeOutput lefthook @('version'))
    if ($lefthook -like '2.*') {
        Write-Ok "Lefthook $lefthook"
    } else {
        Write-Missing "Lefthook 2 (found: $(if ($lefthook) { $lefthook } else { 'none' }))" 'winget install evilmartians.lefthook'
    }

    $gitleaks = Get-VersionOf (Get-NativeOutput gitleaks @('version'))
    if ($gitleaks -eq '8.30.1') {
        Write-Ok "gitleaks $gitleaks"
    } elseif ($dockerRunning) {
        Write-Ok 'gitleaks 8.30.1 through its Docker image (scripts/gitleaks.ps1)'
    } else {
        Write-Missing 'gitleaks 8.30.1, or Docker running' 'https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1'
    }
}

function ConvertTo-Base64Text([string]$Text) {
    return [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Text))
}

# A new key for the entry codes of imported tickets: Base64 of 256 random bits.
function Get-RandomTicketEntryCodeKey {
    return [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
}

# Writes .env: .env.example with every empty value filled with a new secret.
function Initialize-DotEnv {
    $rsa = [System.Security.Cryptography.RSA]::Create(2048)
    try {
        $private = ConvertTo-Base64Text $rsa.ExportPkcs8PrivateKeyPem()
        $public = ConvertTo-Base64Text $rsa.ExportSubjectPublicKeyInfoPem()
    } finally {
        $rsa.Dispose()
    }
    $lines = foreach ($line in Get-Content -LiteralPath $EnvExample) {
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=$') {
            $key = $Matches[1]
            $value = switch ($key) {
                'JWT_PRIVATE_KEY_BASE64' { $private }
                'JWT_PUBLIC_KEY_BASE64' { $public }
                'TICKET_ENTRY_CODE_KEY' { Get-RandomTicketEntryCodeKey }
                # Hex: letters and digits only, as the database users and RabbitMQ need.
                default { [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant() }
            }
            "$key=$value"
        } else {
            $line
        }
    }
    $temporary = "$EnvFile.tmp"
    [IO.File]::WriteAllLines($temporary, [string[]]$lines, [System.Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary -Destination $EnvFile
    Write-Step 'Created .env with new random secrets.'
}

Test-Tool

if (Test-Path -LiteralPath $EnvFile) {
    Write-Step 'Keeping the existing .env.'
    # A .env from before ticket import gets its key here, so it needs no edit by hand.
    if (-not (Select-String -LiteralPath $EnvFile -Pattern '^TICKET_ENTRY_CODE_KEY=' -Quiet)) {
        [IO.File]::AppendAllText($EnvFile, "TICKET_ENTRY_CODE_KEY=$(Get-RandomTicketEntryCodeKey)`n", [System.Text.UTF8Encoding]::new($false))
        Write-Step 'Added TICKET_ENTRY_CODE_KEY to .env.'
    }
    $present = @(Get-Content -LiteralPath $EnvFile | ForEach-Object { if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=') { $Matches[1] } })
    $absent = @(Get-EnvKey | Where-Object { $present -notcontains $_ })
    if ($absent) { Write-Warning ".env lacks $($absent -join ' '): copy them from .env.example." }
} else {
    Initialize-DotEnv
}

if (Get-Command lefthook -ErrorAction SilentlyContinue) {
    Write-Step 'Installing the Git hooks'
    Push-Location $Root
    try { lefthook install } finally { Pop-Location }
}

if ($MissingCount -gt 0) {
    Exit-WithError "$MissingCount tool(s) missing or at another version: install them and run this again."
}
Write-Step 'Ready. Next: scripts/local/stack-up.ps1 for the whole system, or scripts/local/infra-up.ps1 and scripts/local/start.ps1 <app>.'
