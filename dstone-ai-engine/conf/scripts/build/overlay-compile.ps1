################################################################################
# Generic "overlay and compile" script (Windows). overlay-compile.bat runs this file.
# Same behaviour as overlay-compile.sh:
#   1) fresh copy of <source-dir> into <work-dir>\source (without target, build, .gradle, .git, node_modules)
#   2) overlay <overlay-dir> (same relative paths as the source root)
#   3) delete paths listed in [delete-list-file] (relative to the source root, one per line, # comments)
#   4) run the build command; default by build file: pom.xml -> mvn -B -q compile -DskipTests,
#      build.gradle(.kts) -> gradlew.bat (or gradle) -q compileJava
# Exit code 0 on success. On failure prints only the error lines with source-relative paths.
# Output is plain ASCII on purpose: the engine reads it with the platform charset.
################################################################################
param(
	[Parameter(Mandatory = $true)][string]$SourceDir,
	[Parameter(Mandatory = $true)][string]$OverlayDir,
	[Parameter(Mandatory = $true)][string]$WorkDir,
	[string]$DeleteList = '',
	[string]$BuildCmd = ''
)

$ErrorActionPreference = 'Stop'
function Norm([string]$p) { return $p.Replace('/', '\').TrimEnd('\') }
$SourceDir = Norm $SourceDir
$OverlayDir = Norm $OverlayDir
$WorkDir = Norm $WorkDir
if ($DeleteList) { $DeleteList = Norm $DeleteList }
$copyDir = Join-Path $WorkDir 'source'
$logFile = Join-Path $WorkDir 'build.log'

if (-not (Test-Path -LiteralPath $SourceDir)) { Write-Output "source dir not found: $SourceDir"; exit 2 }
if (-not (Test-Path -LiteralPath $OverlayDir)) { Write-Output "overlay dir not found: $OverlayDir"; exit 2 }

$maven = $false
if (-not $BuildCmd) {
	if (Test-Path (Join-Path $SourceDir 'pom.xml')) {
		$BuildCmd = 'mvn -B -q compile -DskipTests'; $maven = $true
	} elseif ((Test-Path (Join-Path $SourceDir 'build.gradle')) -or (Test-Path (Join-Path $SourceDir 'build.gradle.kts'))) {
		if (Test-Path (Join-Path $SourceDir 'gradlew.bat')) { $BuildCmd = 'gradlew.bat -q compileJava' } else { $BuildCmd = 'gradle -q compileJava' }
	} else {
		Write-Output "no build file (pom.xml, build.gradle) in $SourceDir. give the build command as the 5th argument."; exit 2
	}
} elseif ($BuildCmd.StartsWith('mvn')) {
	$maven = $true
}

# 1) fresh copy (robocopy exit codes below 8 mean success)
if (Test-Path -LiteralPath $copyDir) { Remove-Item -LiteralPath $copyDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $copyDir | Out-Null
& robocopy $SourceDir $copyDir /E /NFL /NDL /NJH /NJS /NP /XD target build .gradle .git node_modules | Out-Null
if ($LASTEXITCODE -ge 8) { Write-Output "copy failed: $SourceDir (robocopy $LASTEXITCODE)"; exit 2 }

# 2) overlay (skip the delete list when it lives inside the overlay dir)
$excludeFile = @()
if ($DeleteList -and $DeleteList.StartsWith($OverlayDir + '\')) { $excludeFile = @('/XF', (Split-Path $DeleteList -Leaf)) }
& robocopy $OverlayDir $copyDir /E /NFL /NDL /NJH /NJS /NP @excludeFile | Out-Null
if ($LASTEXITCODE -ge 8) { Write-Output "overlay failed: $OverlayDir (robocopy $LASTEXITCODE)"; exit 2 }

# 3) delete listed paths in the copy
if ($DeleteList -and (Test-Path -LiteralPath $DeleteList)) {
	foreach ($raw in (Get-Content -LiteralPath $DeleteList -Encoding UTF8)) {
		$rel = $raw.Trim().Replace('\', '/').TrimStart('/')
		if ($rel.Length -eq 0 -or $rel.StartsWith('#') -or $rel.Contains('..')) { continue }
		$target = Join-Path $copyDir $rel
		if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
		Write-Output "deleted in copy: $rel"
	}
}

# 4) build. The redirect is done by cmd.exe: PowerShell 5.1 turns any stderr line of a native
#    command (e.g. JDK warnings) into an error record and would stop here.
Push-Location $copyDir
try {
	& cmd.exe /c ($BuildCmd + ' > "' + $logFile + '" 2>&1')
	$code = $LASTEXITCODE
} finally {
	Pop-Location
}
if ($code -eq 0) { Write-Output "compile OK: $SourceDir + $OverlayDir ($BuildCmd)"; exit 0 }

Write-Output "compile FAILED (exit $code, $BuildCmd). errors (full log: $logFile):"
$all = @(Get-Content -LiteralPath $logFile)
$start = -1
if ($maven) {
	for ($i = 0; $i -lt $all.Count; $i++) { if ($all[$i] -match 'Failed to execute goal') { $start = $i + 1; break } }
}
if ($start -ge 0) {
	# Maven prints the errors twice; keep the detailed part (with missing symbol names)
	$lines = $all[$start..($all.Count - 1)] | Where-Object { $_ -match '^\[ERROR\]' -and $_ -notmatch 'To see the full stack trace|Re-run Maven|For more information|-> \[Help|^\[Help' -and $_.Trim() -ne '[ERROR]' }
} elseif ($all | Where-Object { $_ -match '(?i)error' }) {
	$lines = $all | Select-String -Pattern '(?i)error' -Context 0, 2 | ForEach-Object { $_.Line; $_.Context.PostContext }
} else {
	$lines = $all | Select-Object -Last 60
}
$prefix = [regex]::Escape($copyDir) + '[\\/]'
$prefixSlash = [regex]::Escape($copyDir.Replace('\', '/')) + '/'
$lines | ForEach-Object { ($_ -replace $prefix, '') -replace $prefixSlash, '' } |
	ForEach-Object { $_ -replace '^(\[ERROR\] )/?[A-Za-z]:/.*?/source/', '$1' } |
	Select-Object -First 80
exit $code
