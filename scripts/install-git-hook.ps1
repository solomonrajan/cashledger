<#
.SYNOPSIS
    Installs the git pre-commit hook that enforces Kotlin null-safety rules (zero '!!' policy).
.DESCRIPTION
    Configures git to use .githooks directory for hooks.
#>

$RepoRoot = (Get-Item $PSScriptRoot).Parent.FullName
Set-Location $RepoRoot

git config core.hooksPath .githooks
Write-Host "Git hooks path configured to .githooks" -ForegroundColor Green
Write-Host "Pre-commit hook is now active. Any commit containing double-bang (!!) in Kotlin files will be blocked." -ForegroundColor Cyan

