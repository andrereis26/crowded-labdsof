#Requires -Version 7.4
<#
.SYNOPSIS
Runs gitleaks at the version the Security workflow uses.

.DESCRIPTION
The installed binary if it is that version, otherwise the official Docker
image. Takes gitleaks' own arguments. On Windows the Docker image sees the
work tree at /repo, so a linked worktree, whose .git file names a Windows
path, needs gitleaks 8.30.1 installed instead.
Bash twin (Debian, macOS): scripts/gitleaks.sh

.EXAMPLE
scripts/gitleaks.ps1 git --pre-commit --staged --redact
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$version = '8.30.1'
$image = "zricethezav/gitleaks:v$version"

$installed = Get-Command gitleaks -ErrorAction SilentlyContinue
if ($installed -and ((& gitleaks version) -replace '^v', '') -eq $version) {
    & gitleaks @args
    exit $LASTEXITCODE
}

$dockerRunning = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    docker info 2>&1 | Out-Null
    $dockerRunning = $LASTEXITCODE -eq 0
}
if ($dockerRunning) {
    $top = (git rev-parse --show-toplevel)
    $gitDir = (Resolve-Path (git rev-parse --git-common-dir)).Path
    $environment = @('-e', 'GIT_CONFIG_COUNT=1', '-e', 'GIT_CONFIG_KEY_0=safe.directory', '-e', 'GIT_CONFIG_VALUE_0=*')
    if ($IsWindows) {
        # A Linux container has no C:\ paths: the work tree goes to /repo.
        $relative = [IO.Path]::GetRelativePath($top, $PWD.Path).Replace('\', '/')
        & docker run --rm -v "${top}:/repo" -w "/repo/$relative" @environment $image @args
    } else {
        # The work tree and the Git directory at their own paths, so Git
        # commands inside the container (and linked worktrees) see the same paths.
        & docker run --rm --user "$(id -u):$(id -g)" -v "${top}:${top}" -v "${gitDir}:${gitDir}" -w $PWD.Path @environment $image @args
    }
    exit $LASTEXITCODE
}

[Console]::Error.WriteLine("gitleaks $version is needed: install it (https://github.com/gitleaks/gitleaks#installing) or start Docker.")
exit 1
