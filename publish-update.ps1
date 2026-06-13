# PeopleHub - publish a signed release to GitHub Releases.
#
# Bumps the app version, builds the signed release APK locally, pushes the version-bump commit,
# and creates a GitHub Release with the APK attached. The in-app updater then offers it to every
# device. GitHub-only approach (no Firestore): the Release is the single source of truth.
#
# Usage:   .\publish-update.ps1 -Version 1.1.1
#          .\publish-update.ps1 -Version 1.1.1 -AutoConfirm

param(
    [Parameter(Mandatory = $false)] [string]$Version = "",
    [Parameter(Mandatory = $false)] [switch]$AutoConfirm = $false
)

$Owner = "andreaferraboli"
$Repo = "PeopleHub"
$BuildFile = "app\build.gradle.kts"
$Jbr = "C:\Program Files\Android\Android Studio\jbr"
$Quote = [char]34

Write-Host "=== PeopleHub - Publish Release ===" -ForegroundColor Cyan

# 1. Dependencies
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: GitHub CLI (gh) not found. Install: winget install --id GitHub.cli" -ForegroundColor Red
    exit 1
}
gh auth status 1>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: not authenticated. Run: gh auth login" -ForegroundColor Red
    exit 1
}

# 2. Current version
$content = Get-Content $BuildFile -Raw
if ($content -notmatch 'val appVersionName = "(\d+\.\d+\.\d+)"') {
    Write-Host "ERROR: could not find appVersionName in $BuildFile" -ForegroundColor Red
    exit 1
}
$currentVersion = $Matches[1]
Write-Host "Current version: $currentVersion" -ForegroundColor Green

# 3. New version
if ([string]::IsNullOrEmpty($Version)) {
    $Version = Read-Host "New version X.Y.Z"
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Host "ERROR: invalid version. Use X.Y.Z, for example 1.1.1" -ForegroundColor Red
    exit 1
}

# 4. Bump appVersionName (BOM-free write so Gradle reads the .kts cleanly)
if ($Version -ne $currentVersion) {
    Write-Host "Bumping appVersionName to $Version" -ForegroundColor Yellow
    $replacement = 'val appVersionName = ' + $Quote + $Version + $Quote
    $content = $content -replace 'val appVersionName = "\d+\.\d+\.\d+"', $replacement
    $fullPath = (Resolve-Path $BuildFile).Path
    [System.IO.File]::WriteAllText($fullPath, $content, (New-Object System.Text.UTF8Encoding($false)))
}

if (-not $AutoConfirm) {
    $confirm = Read-Host "Build and publish v$Version now? y/n"
    if ($confirm -ne "y") { Write-Host "Cancelled" -ForegroundColor Red; exit 0 }
}

# 5. Build signed release APK
Write-Host "STEP 1/4: Building signed release APK..." -ForegroundColor Yellow
$env:JAVA_HOME = $Jbr
& .\gradlew.bat :app:assembleRelease
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: build failed" -ForegroundColor Red; exit 1 }

$apkPath = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkPath)) { Write-Host "ERROR: APK not found at $apkPath" -ForegroundColor Red; exit 1 }
$releaseApk = "PeopleHub-$Version.apk"
Copy-Item $apkPath $releaseApk -Force
Write-Host "   OK: $releaseApk" -ForegroundColor Green

# 6. Commit the version bump and push (gh will tag the pushed commit)
Write-Host "STEP 2/4: Committing version bump..." -ForegroundColor Yellow
git add $BuildFile
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    git commit -m "Release v$Version"
}
git push origin HEAD
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: git push failed" -ForegroundColor Red; exit 1 }

# 7. Create the GitHub Release with the APK attached (creates the tag on the pushed commit)
Write-Host "STEP 3/4: Creating GitHub Release v$Version..." -ForegroundColor Yellow
$tag = "v$Version"
gh release create $tag $releaseApk --repo "$Owner/$Repo" --title $tag --generate-notes
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: gh release create failed" -ForegroundColor Red; exit 1 }

Write-Host "STEP 4/4: Done." -ForegroundColor Green
Write-Host ""
Write-Host "Published v$Version" -ForegroundColor Green
Write-Host "Release: https://github.com/$Owner/$Repo/releases/tag/$tag" -ForegroundColor Cyan
Write-Host "Devices get the in-app update on next launch, or via Vault, Check for updates." -ForegroundColor Yellow
