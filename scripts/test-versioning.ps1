# Integration checks against the actual Gradle version script in disposable Git repos.
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$wrapper = Join-Path $repo 'gradlew.bat'
$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ('sinkpoint-version-' + [guid]::NewGuid())
New-Item -ItemType Directory $fixture | Out-Null

function Invoke-Git([string[]] $Arguments) {
    & git -C $fixture @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "git $Arguments failed" }
}

function Assert-Version([string] $Name, [int] $Code, [string] $Failure = '') {
    $output = (& $wrapper --no-daemon --console=plain -q -p $fixture printAppVersion 2>&1 | Out-String)
    $exitCode = $LASTEXITCODE
    if ($Failure) {
        if ($exitCode -eq 0 -or -not $output.Contains($Failure)) {
            throw "Expected failure '$Failure', got: $output"
        }
    } elseif ($exitCode -ne 0 -or $output -notmatch "(?m)^versionName=$([regex]::Escape($Name))\r?$" -or $output -notmatch "(?m)^versionCode=$Code\r?$") {
        throw "Expected $Name / $Code, got: $output"
    }
    Write-Host "PASS: $Name / $Code $Failure"
}

# Retain fixtures on failure for diagnosis. Successful fixtures live in the OS temp directory.
Write-Host "Versioning fixture: $fixture"
Invoke-Git @('init', '-b', 'main')
Invoke-Git @('config', 'user.name', 'Version Test')
Invoke-Git @('config', 'user.email', 'version-test@example.invalid')
Set-Content (Join-Path $fixture 'baseline.txt') 'baseline'
Invoke-Git @('add', 'baseline.txt')
Invoke-Git @('commit', '-m', 'baseline')
$baseline = (& git -C $fixture rev-parse HEAD).Trim()
Copy-Item (Join-Path $repo 'gradle/versioning.gradle.kts') $fixture
Set-Content (Join-Path $fixture 'settings.gradle.kts') 'rootProject.name = "version-test"'
Set-Content (Join-Path $fixture 'build.gradle.kts') 'apply(from = "versioning.gradle.kts")'
$properties = Join-Path $fixture 'version.properties'
Set-Content $properties "versionName=1.2.1`nversionCode=4`nbaseCommit=$baseline"
Assert-Version '1.2.1' 4

Invoke-Git @('commit', '--allow-empty', '-m', 'next')
Assert-Version '1.2.2' 5
Assert-Version '1.2.2' 5 # A retry must not consume a version.

Invoke-Git @('checkout', '-b', 'feature')
Invoke-Git @('commit', '--allow-empty', '-m', 'feature')
Invoke-Git @('checkout', 'main')
Invoke-Git @('merge', '--no-ff', 'feature', '-m', 'merge feature')
Assert-Version '1.2.4' 7 # Counts feature and merge commits; APK/AAB share this history.

Set-Content $properties "versionName=2.0.0`nversionCode=7`nbaseCommit=$baseline"
Assert-Version '2.0.3' 10

Set-Content (Join-Path $fixture '.git/shallow') ((& git -C $fixture rev-parse HEAD).Trim())
Assert-Version '' 0 'Automatic app versioning requires full Git history'
Write-Host 'All versioning integration checks passed.'
# The final case intentionally runs Gradle with a failing shallow checkout.
# Do not propagate that expected failure as this test script's exit status.
exit 0
