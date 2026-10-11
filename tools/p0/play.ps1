param([int]$Rtt = 100)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $taskRoot
try {
    $env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-25'
    $env:VERSION = '0.1.0-dev'
    $env:PYTHONUTF8 = '1'
    & .\gradlew.bat -I tools/p0/validation.gradle p0LaunchFiles --no-configuration-cache --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Runtime preparation failed' }
    $taskPython = 'C:\Users\layue13\AppData\Local\Programs\Python\Python312\python.exe'
    $taskLabel = 'manual-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    Write-Host 'Starting two clients. WASD/Space move; left light; right heavy; Shift guard in combat; R sheathe; middle mouse lock; F5 camera.'
    Write-Host 'This fixture equips both players and creates an arena. It does not play attacks in manual mode. Close a client to stop the session; automatic limit is 10 minutes.'
    & $taskPython tools/p0/dual.py --label $taskLabel --accept-eula --manual --rtt $Rtt
} finally { Pop-Location }
