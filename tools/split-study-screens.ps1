$ErrorActionPreference = 'Stop'
$root = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\src\main\java\com\example'
$mainPath = Join-Path $root 'MainActivity.kt'
$source = [IO.File]::ReadAllText($mainPath)
$header = $source.Substring(0, $source.IndexOf('class MainActivity :'))
$markers = @(
    @{ Marker = '// --- DASHBOARD SCREEN ---'; File = 'DashboardLegacy.kt' },
    @{ Marker = '// --- FOCUS ROOM / POMODORO TIMER SCREEN ---'; File = 'FocusScreen.kt' },
    @{ Marker = '// --- TASKS & PLANNING SCHEDULER SCREEN ---'; File = 'PlannerLegacy.kt' },
    @{ Marker = '// --- STUDY GROUPS SCREEN ---'; File = 'GroupsLegacy.kt' },
    @{ Marker = '// --- PROFILE SETTINGS & HIGH SECURITY CONFIGURATION SCREEN ---'; File = 'ProfileLegacy.kt' },
    @{ Marker = "@Composable`r`nfun LockScreenOverlay"; File = 'LegacyAuthentication.kt' }
)
$pieces = @()
for ($i = 0; $i -lt $markers.Count; $i++) {
    $start = $source.IndexOf($markers[$i].Marker)
    if ($start -lt 0 -and $markers[$i].File -eq 'LegacyAuthentication.kt') { $start = $source.IndexOf("@Composable`nfun LockScreenOverlay") }
    if ($start -lt 0) { throw "Missing marker: $($markers[$i].File)" }
    $pieces += @{ Start = $start; File = Join-Path $root $markers[$i].File }
    if (Test-Path -LiteralPath $pieces[-1].File) { throw "Refusing to overwrite $($pieces[-1].File)" }
}
for ($i = 0; $i -lt $pieces.Count; $i++) {
    $end = if ($i + 1 -lt $pieces.Count) { $pieces[$i + 1].Start } else { $source.Length }
    if ($end -le $pieces[$i].Start) { throw 'Markers are not ordered.' }
    $pieces[$i].Content = $header + $source.Substring($pieces[$i].Start, $end - $pieces[$i].Start)
}
foreach ($piece in $pieces) { [IO.File]::WriteAllText($piece.File,$piece.Content,[Text.UTF8Encoding]::new($false)) }
[IO.File]::WriteAllText($mainPath,$source.Substring(0,$pieces[0].Start),[Text.UTF8Encoding]::new($false))
Write-Output 'Separated existing screen blocks without changing their contents.'
