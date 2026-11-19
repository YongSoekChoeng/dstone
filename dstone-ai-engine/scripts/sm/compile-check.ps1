################################################################################
# sm-workflow compile check (Windows). compile-check.bat runs this file.
# Same behaviour as compile-check.sh:
#   1) copy the source root (read from ../00.request.md, first line after the source-path heading) to <work>/compile-check/source
#   2) overlay <work>/dev-resources (except _delete-files.txt)
#   3) delete paths listed in dev-resources/_delete-files.txt from the copy
#   4) mvn compile; print only [ERROR] lines on failure (exit code != 0)
# Output is plain ASCII on purpose: the engine reads it with the platform charset.
################################################################################
param([Parameter(Mandatory = $true)][string]$WorkDir)

$ErrorActionPreference = 'Stop'
$WorkDir = $WorkDir.TrimEnd('\', '/')
$requestFile = Join-Path (Split-Path $WorkDir -Parent) '00.request.md'
$devDir = Join-Path $WorkDir 'dev-resources'
$checkDir = Join-Path $WorkDir 'compile-check'
$copyDir = Join-Path $checkDir 'source'

if (-not (Test-Path $requestFile)) { Write-Output "request file not found: $requestFile"; exit 2 }

# the source-path heading (Korean) is written with unicode escapes so this file stays ASCII-safe
$heading = '^##\s*' + [regex]::Unescape('\uC18C\uC2A4\uACBD\uB85C')
$lines = Get-Content -LiteralPath $requestFile -Encoding UTF8
$sourceDir = $null
$found = $false
foreach ($line in $lines) {
	if (-not $found) {
		if ($line -match $heading) { $found = $true }
		continue
	}
	if ($line -match '^##') { break }
	if ($line.Trim().Length -gt 0) { $sourceDir = $line.Trim(); break }
}
if (-not $sourceDir) { Write-Output "source path not found in $requestFile"; exit 2 }
$sourceDir = $sourceDir.TrimEnd('\', '/')
if (-not (Test-Path (Join-Path $sourceDir 'pom.xml'))) { Write-Output "pom.xml not found in source: $sourceDir (only Maven projects are supported)"; exit 2 }
if (-not (Test-Path $devDir)) { Write-Output "dev-resources not found: $devDir"; exit 2 }

# 1) fresh copy of the source (robocopy exit codes below 8 mean success)
if (Test-Path $copyDir) { Remove-Item -LiteralPath $copyDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $copyDir | Out-Null
& robocopy $sourceDir $copyDir /E /NFL /NDL /NJH /NJS /NP /XD target .git uploadData node_modules | Out-Null
if ($LASTEXITCODE -ge 8) { Write-Output "copy failed: $sourceDir (robocopy $LASTEXITCODE)"; exit 2 }

# 2) overlay dev-resources
& robocopy $devDir $copyDir /E /NFL /NDL /NJH /NJS /NP /XF _delete-files.txt | Out-Null
if ($LASTEXITCODE -ge 8) { Write-Output "overlay failed: $devDir (robocopy $LASTEXITCODE)"; exit 2 }

# 3) delete listed paths in the copy
$deleteList = Join-Path $devDir '_delete-files.txt'
if (Test-Path $deleteList) {
	foreach ($raw in (Get-Content -LiteralPath $deleteList -Encoding UTF8)) {
		$rel = $raw.Trim().Replace('\', '/').TrimStart('/')
		if ($rel.Length -eq 0 -or $rel.StartsWith('#') -or $rel.Contains('..')) { continue }
		$target = Join-Path $copyDir $rel
		if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
		Write-Output "deleted in copy: $rel"
	}
}

# 4) compile
$logFile = Join-Path $checkDir 'mvn.log'
# The redirect is done by cmd.exe: PowerShell 5.1 turns any stderr line of a native command
# (e.g. JDK warnings) into an error record and would stop here.
$mvnCmd = 'mvn -B -q ' + $env:SM_MVN_OPTS + ' compile -DskipTests > "' + $logFile + '" 2>&1'
Push-Location $copyDir
try {
	& cmd.exe /c $mvnCmd
	$code = $LASTEXITCODE
} finally {
	Pop-Location
}
if ($code -eq 0) { Write-Output "compile OK: $sourceDir + $devDir"; exit 0 }

Write-Output "compile FAILED (exit $code). errors (full log: $logFile):"
# Maven prints the same errors twice: a summary and a detailed part after "Failed to execute goal"
# (with the missing symbol names). Keep only the detailed part when it exists.
$all = @(Get-Content -LiteralPath $logFile)
$start = 0
for ($i = 0; $i -lt $all.Count; $i++) {
	if ($all[$i] -match 'Failed to execute goal') { $start = $i + 1; break }
}
$all[$start..($all.Count - 1)] |
	Where-Object { $_ -match '^\[ERROR\]' -and $_ -notmatch 'To see the full stack trace|Re-run Maven|For more information|-> \[Help|^\[Help' -and $_.Trim() -ne '[ERROR]' } |
	ForEach-Object { $_ -replace '^\[ERROR\] .*compile-check[\\/]source[\\/]', '[ERROR] ' } |
	Select-Object -First 60
exit $code
