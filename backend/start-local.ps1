param([switch]$Seed,[switch]$UseExistingDatabase,[switch]$RichDemo)
$ErrorActionPreference = 'Stop'
$geminiSource = . (Join-Path $PSScriptRoot 'load-gemini-config.ps1')
$env:GRADLE_USER_HOME = 'D:\DevData\Gradle'
$project = Split-Path -Parent $PSScriptRoot
$secretFile = Join-Path $PSScriptRoot '.local-secrets.xml'
if (!(Test-Path -LiteralPath $secretFile)) {
    $dbPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $adminPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    @{
        Database = ConvertTo-SecureString $dbPassword -AsPlainText -Force
        Admin = ConvertTo-SecureString $adminPassword -AsPlainText -Force
    } | Export-Clixml -LiteralPath $secretFile
}
$secrets = Import-Clixml -LiteralPath $secretFile
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $secrets.Database).Password
$env:STUDYSPACE_ADMIN_PASSWORD = [Net.NetworkCredential]::new('', $secrets.Admin).Password
$env:STUDYSPACE_SEED = if ($Seed) { 'true' } else { 'false' }
if ($RichDemo) {
    if ([string]::IsNullOrWhiteSpace($env:STUDYSPACE_DEMO_PASSWORD)) {
        $demoDoc = Join-Path $project 'docs\DEMO_ACCOUNTS.md'
        if (!(Test-Path -LiteralPath $demoDoc)) { throw 'Set STUDYSPACE_DEMO_PASSWORD at runtime or create the ignored local docs/DEMO_ACCOUNTS.md before using -RichDemo.' }
        $demoLine = Get-Content -LiteralPath $demoDoc | Where-Object { $_ -match '^Rich cohort password:\s*.+$' } | Select-Object -First 1
        if ($demoLine -notmatch '^Rich cohort password:\s*(.+)$') { throw 'Rich cohort password is missing from ignored local docs/DEMO_ACCOUNTS.md.' }
        $env:STUDYSPACE_DEMO_PASSWORD = $Matches[1].Trim()
    }
}
if (!$UseExistingDatabase) {
    docker compose -f (Join-Path $PSScriptRoot 'compose.yaml') up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL failed to start. Check Docker Desktop.' }
}
if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    $env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/studyspace'
}
if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'D:\adr studio\jbr' }
& (Join-Path $project 'gradlew.bat') -p $PSScriptRoot installDist --no-daemon --max-workers=2 --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Server build failed.' }
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$lib = Join-Path $PSScriptRoot 'build\install\studyspace-server\lib\*'
$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listener) { throw 'Port 8080 is occupied. Stop the old StudySpace server before restarting.' }
$serverArguments = @('-cp', ('"' + $lib + '"'), 'com.studyspace.server.StudySpaceServer')
if ($RichDemo) { $serverArguments += '--studyspace.seed.rich-demo=true' }
$process = Start-Process -FilePath $java -ArgumentList $serverArguments -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $PSScriptRoot 'server.log') -RedirectStandardError (Join-Path $PSScriptRoot 'server-error.log')
$process.Id | Set-Content -LiteralPath (Join-Path $PSScriptRoot '.server.pid')
if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) { Write-Output 'Gemini: chưa cấu hình' }
else { Write-Output "Gemini: đã cấu hình (nguồn: $geminiSource)" }
Write-Output 'StudySpace server starting at http://localhost:8080. See server.log.'
Write-Output 'Admin credentials are stored using Windows DPAPI in .local-secrets.xml.'
