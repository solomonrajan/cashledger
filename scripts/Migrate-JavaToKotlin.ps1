param (
    [string]$ApiKey = "",
    [string]$SearchPath = ""
)

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    Write-Host "Please provide your Gemini API key." -ForegroundColor Red
    Write-Host "Usage: .\scripts\Migrate-JavaToKotlin.ps1 -ApiKey 'YOUR_API_KEY'" -ForegroundColor Yellow
    exit 1
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = if (Test-Path (Join-Path $ScriptDir "..\app")) { (Resolve-Path (Join-Path $ScriptDir "..")).Path } else { (Get-Location).Path }

if ([string]::IsNullOrWhiteSpace($SearchPath)) {
    $SearchPath = Join-Path $RepoRoot "app\src\main\java\com\oriondev\moneywallet\ui"
} elseif (-not [System.IO.Path]::IsPathRooted($SearchPath)) {
    $SearchPath = Join-Path $RepoRoot $SearchPath
}

$Endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro-latest:generateContent?key=$ApiKey"

# List of activities to migrate (based on user's request)
$ActivitiesToMigrate = @(
    "LauncherActivity",
    "MainActivity",
    "TutorialActivity",
    "LockActivity",
    "TransactionListActivity",
    "WalletListActivity",
    "CalendarActivity",
    "MapActivity",
    "PeriodDetailActivity",
    "CurrencyListActivity",
    "NewEditTransactionActivity",
    "NewEditTransferActivity",
    "NewEditWalletActivity",
    "NewEditCategoryActivity",
    "NewEditBudgetActivity",
    "NewEditDebtActivity",
    "NewEditSavingActivity",
    "NewEditEventActivity",
    "NewEditPlaceActivity",
    "NewEditPersonActivity",
    "NewEditRecurrentTransactionActivity",
    "NewEditTransactionModelActivity",
    "SettingsActivity",
    "CategoryPickerActivity",
    "CategorySortActivity",
    "CategoryRuleListActivity",
    "NewEditCategoryRuleActivity",
    "CategoryRuleSortActivity",
    "WalletSortActivity",
    "IconListActivity",
    "PlacePickerActivity",
    "SearchActivity",
    "ImportExportActivity",
    "BackupListActivity",
    "BackendExplorerActivity",
    "AboutActivity"
)

Write-Host "Starting Java to Kotlin migration script..." -ForegroundColor Green

foreach ($Activity in $ActivitiesToMigrate) {
    # Find the java file
    $JavaFiles = Get-ChildItem -Path $SearchPath -Filter "$Activity.java" -Recurse
    
    foreach ($File in $JavaFiles) {
        Write-Host "Found $($File.Name). Translating to Kotlin..." -ForegroundColor Cyan
        
        $JavaCode = Get-Content $File.FullName -Raw
        
        $Prompt = "You are an expert Android developer. Convert the following Android Java class to idiomatic Kotlin. Output ONLY the Kotlin code, without markdown formatting or explanations.`n`n$JavaCode"

        $Body = @{
            contents = @(
                @{
                    parts = @(
                        @{ text = $Prompt }
                    )
                }
            )
        } | ConvertTo-Json -Depth 10

        try {
            $Response = Invoke-RestMethod -Uri $Endpoint -Method Post -ContentType "application/json" -Body $Body
            
            $KotlinCode = $Response.candidates[0].content.parts[0].text
            
            # Clean up potential markdown formatting if the model still outputs it
            if ($KotlinCode.StartsWith('```kotlin')) {
                $KotlinCode = $KotlinCode.Substring(9)
            }
            if ($KotlinCode.StartsWith('```')) {
                $KotlinCode = $KotlinCode.Substring(3)
            }
            if ($KotlinCode.EndsWith('```')) {
                $KotlinCode = $KotlinCode.Substring(0, $KotlinCode.Length - 3)
            }
            $KotlinCode = $KotlinCode.Trim()

            # Save as .kt
            $KotlinPath = $File.FullName -replace '\.java$', '.kt'
            Set-Content -Path $KotlinPath -Value $KotlinCode
            
            Write-Host "Successfully saved to $($KotlinPath)" -ForegroundColor Green
            
            # Delete the old java file
            Remove-Item $File.FullName -Force
            Write-Host "Deleted original $($File.Name)" -ForegroundColor Yellow

        } catch {
            Write-Host "Failed to translate $($File.Name). Error: $_" -ForegroundColor Red
        }
    }
}

# Automatically update KOTLIN_MIGRATION.mdx dashboard
$TrackerScript = Join-Path $ScriptDir "Update-MigrationTracker.ps1"
if (Test-Path $TrackerScript) {
    Write-Host "Updating KOTLIN_MIGRATION.mdx tracker..." -ForegroundColor Cyan
    & $TrackerScript
}

Write-Host "Migration script completed." -ForegroundColor Green
