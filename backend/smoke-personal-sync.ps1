$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080/api'
$name = 'sync_qa_' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$session = $null
try {
    $session = Invoke-RestMethod "$base/auth/register" -Method Post -ContentType 'application/json' -Body (@{username=$name;password=$password}|ConvertTo-Json)
    $headers = @{Authorization='Bearer '+$session.token}
    $initial = Invoke-RestMethod "$base/personal-study" -Headers $headers
    if ($initial.version -ne 0) { throw 'New account has unexpected cloud data.' }
    $plan = @{id=1;title='QA SQL';category='Database';description='';targetMinutes=25;elapsedTimeSeconds=0;isCompleted=$true;dueDate=2000000000000;isSmartReminderEnabled=$false;reminderHour=20;reminderMinute=0;createdAt=1000}
    $focus = @{id=1;taskId=1;taskTitle='QA SQL';durationMinutes=25;sessionType='focus';timestamp=1000}
    $body = @{expectedVersion=0;requestId=[guid]::NewGuid().ToString();data=@{plans=@($plan);sessions=@($focus)}}
    $json = $body | ConvertTo-Json -Depth 8
    foreach ($retry in 1..2) {
        $saved=Invoke-RestMethod "$base/personal-study" -Method Put -Headers $headers -ContentType 'application/json' -Body $json
        if ($saved.version -ne 1 -or $saved.data.sessions.Count -ne 1 -or !$saved.data.plans[0].isCompleted) { throw 'Roundtrip/idempotency mismatch.' }
    }
    $body.requestId=[guid]::NewGuid().ToString()
    $conflict=Invoke-WebRequest "$base/personal-study" -Method Put -Headers $headers -ContentType 'application/json' -Body ($body|ConvertTo-Json -Depth 8) -SkipHttpErrorCheck
    if ([int]$conflict.StatusCode -ne 409) { throw 'Stale device was not rejected.' }
    $read=Invoke-RestMethod "$base/personal-study" -Headers $headers
    if ($read.version -ne 1 -or $read.data.plans.Count -ne 1) { throw 'Conflict changed stored data.' }
    Write-Output 'PASS: PostgreSQL personal sync -> full plan/session roundtrip -> retry without duplicates -> stale version rejected'
} finally {
    if ($session) {
        $secrets=Import-Clixml (Join-Path $PSScriptRoot '.local-secrets.xml')
        $env:PGPASSWORD=[Net.NetworkCredential]::new('', $secrets.Database).Password
        $id=[long]$session.account.id
        if ($name -notmatch '^sync_qa_[0-9]+$' -or $id -le 0) { throw 'Unexpected QA account identity.' }
        $sql="BEGIN; DELETE FROM personal_study_snapshots WHERE user_id=$id; DELETE FROM sessions WHERE user_id=$id; DELETE FROM accounts WHERE id=$id AND username='$name'; COMMIT;"
        & (Join-Path $PSScriptRoot 'portable/pgsql/bin/psql.exe') -h 127.0.0.1 -p 55432 -U studyspace -d studyspace -v ON_ERROR_STOP=1 -c $sql | Out-Null
        Remove-Item Env:PGPASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'QA cleanup failed.' }
    }
}
