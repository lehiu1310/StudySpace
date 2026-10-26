param([switch]$Seed,[switch]$RichDemo)
$ErrorActionPreference = 'Stop'
$geminiSource = . (Join-Path $PSScriptRoot 'load-gemini-config.ps1')
$env:GRADLE_USER_HOME = 'D:\DevData\Gradle'
$project = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $PSScriptRoot 'portable'
$pg = Join-Path $runtime 'pgsql\bin'
$data = Join-Path $runtime 'data'
$secretFile = Join-Path $PSScriptRoot '.local-secrets.xml'
if (!(Test-Path -LiteralPath (Join-Path $pg 'pg_ctl.exe'))) {
    $archive = Join-Path $PSScriptRoot 'postgresql-binaries.zip'
    if (!(Test-Path -LiteralPath $archive)) { throw 'Download the PostgreSQL Windows x64 binary archive from EDB to backend/postgresql-binaries.zip first.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $runtime
}
if (!(Test-Path -LiteralPath $secretFile)) {
    @{
        Database = ConvertTo-SecureString ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))) -AsPlainText -Force
        Admin = ConvertTo-SecureString ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))) -AsPlainText -Force
    } | Export-Clixml -LiteralPath $secretFile
}
$secrets = Import-Clixml -LiteralPath $secretFile
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $secrets.Database).Password
$env:PGPASSWORD = $env:DB_PASSWORD
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55432/studyspace'
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
if (!(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
    $passwordFile = Join-Path $runtime 'init-password.tmp'
    try {
        [IO.File]::WriteAllText($passwordFile, $env:DB_PASSWORD, [Text.UTF8Encoding]::new($false))
        & (Join-Path $pg 'initdb.exe') -D $data -U studyspace -A scram-sha-256 --encoding=UTF8 --locale=C --pwfile=$passwordFile
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL initialization failed.' }
    } finally { if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile } }
}
& (Join-Path $pg 'pg_ctl.exe') status -D $data
if ($LASTEXITCODE -ne 0) {
    & (Join-Path $pg 'pg_ctl.exe') start -D $data -l (Join-Path $runtime 'postgres.log') -o '-h 127.0.0.1 -p 55432' -w
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup failed.' }
}
$exists = & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p 55432 -U studyspace -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='studyspace'"
if ($exists -ne '1') {
    & (Join-Path $pg 'createdb.exe') -h 127.0.0.1 -p 55432 -U studyspace studyspace
    if ($LASTEXITCODE -ne 0) { throw 'Database creation failed.' }
}
if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'D:\adr studio\jbr' }
& (Join-Path $project 'gradlew.bat') -p $PSScriptRoot installDist --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Server build failed.' }
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) { throw 'Port 8080 is occupied. Stop the old StudySpace server before restarting.' }
$lib = Join-Path $PSScriptRoot 'build\install\studyspace-server\lib\*'
$serverArguments = @('-cp', ('"' + $lib + '"'), 'com.studyspace.server.StudySpaceServer')
if ($RichDemo) { $serverArguments += '--studyspace.seed.rich-demo=true' }
$process = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList $serverArguments -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $PSScriptRoot 'server.log') -RedirectStandardError (Join-Path $PSScriptRoot 'server-error.log')
$process.Id | Set-Content -LiteralPath (Join-Path $PSScriptRoot '.server.pid')
if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) { Write-Output 'Gemini: chưa cấu hình' }
else { Write-Output "Gemini: đã cấu hình (nguồn: $geminiSource)" }
Write-Output 'PostgreSQL: 127.0.0.1:55432. StudySpace: http://localhost:8080.'
