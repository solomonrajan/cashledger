<#
.SYNOPSIS
    Automatically scans the codebase for Java and Kotlin files, recalculates migration
    progress, checks off converted files, and updates KOTLIN_MIGRATION.mdx.
.DESCRIPTION
    Run this script locally, via Git hooks, or in CI whenever files are converted
    to keep KOTLIN_MIGRATION.mdx live and in sync with zero manual edits.
#>

param(
    [string]$DocPath = "KOTLIN_MIGRATION.mdx"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $DocPath)) {
    # If called from a subdirectory (e.g. scripts/), search parent directory
    if (Test-Path (Join-Path ".." $DocPath)) {
        $DocPath = (Join-Path ".." $DocPath)
    } else {
        Write-Error "Could not find $DocPath at repository root."
        exit 1
    }
}

$DocItem = Get-Item $DocPath
$RepoRoot = $DocItem.Directory.FullName
$MainDir = Join-Path $RepoRoot "app\src\main\java\com\oriondev\moneywallet"

function Count-Files($paths) {
    $j = 0; $k = 0
    foreach ($p in $paths) {
        if (Test-Path $p) {
            $item = Get-Item $p
            if ($item -is [System.IO.FileInfo]) {
                if ($item.Extension -eq '.java') { $j++ }
                elseif ($item.Extension -eq '.kt') { $k++ }
            } else {
                $jFiles = (Get-ChildItem -Path $p -Recurse -Filter "*.java" -ErrorAction SilentlyContinue)
                if ($jFiles) { $j += $jFiles.Count }
                $kFiles = (Get-ChildItem -Path $p -Recurse -Filter "*.kt" -ErrorAction SilentlyContinue)
                if ($kFiles) { $k += $kFiles.Count }
            }
        }
    }
    return @{ Java = $j; Kotlin = $k; Total = ($j + $k) }
}

# Define Phase Scopes
$PhaseDefs = [ordered]@{
    "Phase 1: Leaf Utilities" = @(Join-Path $MainDir "utils")
    "Phase 2: Domain Models" = @(Join-Path $MainDir "model")
    "Phase 3: Storage & Database" = @(Join-Path $MainDir "storage")
    "Phase 4: API, Service & Background" = @(
        (Join-Path $MainDir "api"),
        (Join-Path $MainDir "background"),
        (Join-Path $MainDir "service"),
        (Join-Path $MainDir "broadcast")
    )
    "Phase 5: Reusable UI & Pickers" = @(
        (Join-Path $MainDir "picker"),
        (Join-Path $MainDir "ui\adapter"),
        (Join-Path $MainDir "ui\view"),
        (Join-Path $MainDir "ui\behavior"),
        (Join-Path $MainDir "ui\drawable"),
        (Join-Path $MainDir "ui\notification"),
        (Join-Path $MainDir "ui\preference"),
        (Join-Path $MainDir "ui\widget")
    )
    "Phase 6: Top-Level Screens & App" = @(
        (Join-Path $MainDir "ui\activity"),
        (Join-Path $MainDir "ui\fragment"),
        (Join-Path $MainDir "App.java"),
        (Join-Path $MainDir "App.kt")
    )
    "Phase 7: Test Suite & Gradle DSL" = @(
        (Join-Path $RepoRoot "app\src\test"),
        (Join-Path $RepoRoot "app\src\androidTest"),
        (Join-Path $RepoRoot "app\src\floss"),
        (Join-Path $RepoRoot "app\src\osm")
    )
}

$PhaseStats = [ordered]@{}
$TotalJava = 0
$TotalKotlin = 0

foreach ($phaseName in $PhaseDefs.Keys) {
    $stats = Count-Files $PhaseDefs[$phaseName]
    $PhaseStats[$phaseName] = $stats
    $TotalJava += $stats.Java
    $TotalKotlin += $stats.Kotlin
}

$TotalFiles = $TotalJava + $TotalKotlin
$OverallPercent = if ($TotalFiles -gt 0) { [math]::Round(($TotalKotlin / $TotalFiles) * 100, 1) } else { 0.0 }

# Unicode characters via safe codepoints (ensures 100% ANSI / UTF-8 compatibility in PowerShell)
$GreenCircle = [char]::ConvertFromUtf32(0x1F7E2)
$YellowCircle = [char]::ConvertFromUtf32(0x1F7E1)
$WhiteCircle = [char]::ConvertFromUtf32(0x26AA)

# Generate Progress Meter Bar (50 blocks)
$BarLength = 50
$FilledBlocks = [int][math]::Round(($OverallPercent / 100) * $BarLength)
$EmptyBlocks = $BarLength - $FilledBlocks
$FilledChar = [char]0x2593
$EmptyChar = [char]0x2591
$ProgressBar = ([string]::new($FilledChar, $FilledBlocks)) + ([string]::new($EmptyChar, $EmptyBlocks))

Write-Host "Migration Progress: $TotalKotlin / $TotalFiles files ($OverallPercent%)" -ForegroundColor Cyan

# Read current document with UTF-8 encoding
$Content = [System.IO.File]::ReadAllText($DocItem.FullName, [System.Text.Encoding]::UTF8)

# 1. Update Frontmatter
$Today = (Get-Date).ToString("yyyy-MM-dd")
$Content = [regex]::Replace($Content, 'last_updated:\s*"[^"]*"', "last_updated: ""$Today""")
$Content = [regex]::Replace($Content, 'overall_progress:\s*"[^"]*"', "overall_progress: ""$OverallPercent%""")

# 2. Update Header Badge
$BadgeColor = if ($OverallPercent -ge 100) { "success" } elseif ($OverallPercent -ge 50) { "yellow" } else { "blue" }
$BadgeRegex = '!\[Current Progress\]\([^\r\n]+\)'
$NewBadge = "![Current Progress](https://img.shields.io/badge/Progress-$TotalKotlin%20%2F%20$TotalFiles%20Files%20($OverallPercent%25)-${BadgeColor}?style=for-the-badge)"
$Content = [regex]::Replace($Content, $BadgeRegex, $NewBadge)

# 3. Update Progress Bar Meter
$MeterRegex = 'Total Files Migrated:\s*\[[^\]]+\]\s*[0-9\.]+%?\s*\([0-9]+\s*\/\s*[0-9]+\)'
$NewMeter = "Total Files Migrated: [$ProgressBar] $OverallPercent% ($TotalKotlin / $TotalFiles)"
$Content = [regex]::Replace($Content, $MeterRegex, $NewMeter)

# 4. Update Summary Table Rows & Phase Section Badges
$FoundFirstActive = $false
$PhaseIndex = 1
foreach ($phaseName in $PhaseDefs.Keys) {
    $st = $PhaseStats[$phaseName]
    $pct = if ($st.Total -gt 0) { [math]::Round(($st.Kotlin / $st.Total) * 100, 1) } else { 0.0 }

    $statusText = ""
    if ($st.Java -eq 0 -and $st.Kotlin -gt 0) {
        $statusText = "$GreenCircle Completed"
    } elseif ($st.Kotlin -gt 0) {
        $statusText = "$YellowCircle In Progress"
        $FoundFirstActive = $true
    } elseif (-not $FoundFirstActive) {
        $statusText = "$YellowCircle Ready to Start"
        $FoundFirstActive = $true
    } else {
        $statusText = "$WhiteCircle Pending"
    }

    # Match row: | **Phase X: ...** | `...` | Java | Kotlin | Total | Pct% | Status | Target |
    $EscapedName = [regex]::Escape($phaseName)
    $RowPattern = '(\|\s*\*\*' + $EscapedName + '\*\*\s*\|\s*[^\|]+\s*\|)\s*[^\|]+\|\s*[^\|]+\|\s*[^\|]+\|\s*[^\|]+\|\s*[^\|]+(\|\s*[^\|]+\|)'
    $Replacement = '${1} ' + $st.Java + ' | ' + $st.Kotlin + ' | ' + $st.Total + ' | ' + $pct + '% | ' + $statusText + ' ${2}'
    $Content = [regex]::Replace($Content, $RowPattern, $Replacement)

    # Match Phase Section header: ### Phase X: ... \n **Dates:** ... **Status:** ...
    $SectionPattern = '(\#\#\# Phase ' + $PhaseIndex + ':[^\r\n]*\r?\n[^\r\n]*\*\*Status:\*\* )[^\r\n]+'
    $SectionReplacement = '${1}' + $statusText
    $Content = [regex]::Replace($Content, $SectionPattern, $SectionReplacement)

    $PhaseIndex++
}

# Update Total row in table
$TotalRowPattern = '(\|\s*\*\*TOTALS\*\*\s*\|\s*[^\|]+\s*\|)\s*[^\|]+\|\s*[^\|]+\|\s*[^\|]+\|\s*[^\|]+\|\s*[^\|]+(\|\s*[^\|]+\|)'
$TotalStatus = if ($OverallPercent -ge 100) { "$GreenCircle Completed" } else { "$YellowCircle Active" }
$TotalReplacement = '${1} **' + $TotalJava + '** | **' + $TotalKotlin + '** | **' + $TotalFiles + '** | **' + $OverallPercent + '%** | **' + $TotalStatus + '** ${2}'
$Content = [regex]::Replace($Content, $TotalRowPattern, $TotalReplacement)

# 5. Automatically Tick/Untick Checkboxes for files
$AllKtFiles = @(Get-ChildItem -Path (Join-Path $RepoRoot "app\src") -Recurse -Filter "*.kt" | Select-Object -ExpandProperty BaseName)

# Regex matches checklist items: - [ ] `Category.java` or - [x] `Urls.java` $\rightarrow$ `Urls.kt` (extra)
$ChecklistPattern = '- \[( |x)\] `([A-Za-z0-9_]+)\.(?:java|kt)`(?:\s*\$\\rightarrow\$\s*`[A-Za-z0-9_]+\.kt`)?(.*)'
$Content = [regex]::Replace($Content, $ChecklistPattern, [System.Text.RegularExpressions.MatchEvaluator]{
    param($match)
    $baseName = $match.Groups[2].Value
    $extra = $match.Groups[3].Value
    $tick = if ($AllKtFiles -contains $baseName) { "x" } else { " " }
    $ext = if ($AllKtFiles -contains $baseName) { "kt" } else { "java" }
    return "- [$tick] ``$baseName.$ext``$extra"
})

# 6. Update Mermaid Flowchart nodes
$FoundFirstMermaidActive = $false
for ($i = 1; $i -le 7; $i++) {
    $pKey = ($PhaseStats.Keys | Where-Object { $_ -match "Phase $i" })
    if ($pKey) {
        $st = $PhaseStats[$pKey]
        $nodeClass = "pending"
        if ($st.Java -eq 0 -and $st.Kotlin -gt 0) {
            $nodeClass = "complete"
        } elseif ($st.Kotlin -gt 0) {
            $nodeClass = "inprog"
            $FoundFirstMermaidActive = $true
        } elseif (-not $FoundFirstMermaidActive) {
            $nodeClass = "inprog"
            $FoundFirstMermaidActive = $true
        }
        $NodePattern = '(P' + $i + '\["[^"]+"\]):::(complete|inprog|pending)'
        $Content = [regex]::Replace($Content, $NodePattern, "`$1:::$nodeClass")
    }
}

# Save updated document with UTF-8 (without BOM)
$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($DocItem.FullName, $Content, $Utf8NoBom)
Write-Host "Successfully updated $($DocItem.FullName)." -ForegroundColor Green
