<#
.SYNOPSIS
    Installs the git pre-commit hook that keeps KOTLIN_MIGRATION.mdx automatically up to date.
.DESCRIPTION
    Configures git to use .githooks directory for hooks.
#>

$RepoRoot = (Get-Item $PSScriptRoot).Parent.FullName
Set-Location $RepoRoot

git config core.hooksPath .githooks
Write-Host "Git hooks path configured to .githooks" -ForegroundColor Green
Write-Host "Pre-commit hook is now active. Any commit containing .java or .kt files will automatically recalculate and update KOTLIN_MIGRATION.mdx." -ForegroundColor Cyan

