$ErrorActionPreference = 'Stop'
$pidFile = Join-Path $PSScriptRoot '.server.pid'
if (!(Test-Path -LiteralPath $pidFile)) { return }
$serverId = [int](Get-Content -LiteralPath $pidFile)
$server = Get-CimInstance Win32_Process -Filter "ProcessId=$serverId"
if ($null -eq $server) { return }
if ($server.Name -ne 'java.exe' -or $server.CommandLine -notmatch 'com\.studyspace\.server\.StudySpaceServer' -or $server.CommandLine -notmatch [regex]::Escape($PSScriptRoot)) {
    throw 'PID does not belong to this StudySpace server. Nothing stopped.'
}
Stop-Process -Id $serverId
Write-Output 'Stopped this project server. PostgreSQL data is untouched.'
