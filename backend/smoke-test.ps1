$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080/api'
$user = 'qa_' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$session = Invoke-RestMethod -Uri "$base/auth/register" -Method Post -ContentType 'application/json' -Body (@{username=$user;password=$password}|ConvertTo-Json)
$headers = @{Authorization='Bearer '+$session.token}
$courses = Invoke-RestMethod -Uri "$base/courses" -Headers $headers
if ($courses.Count -lt 2) { throw 'Expected seeded courses.' }
$course = Invoke-RestMethod -Uri "$base/courses/$($courses[0].id)" -Headers $headers
$chapter = $course.chapters[0]
$lesson = $chapter.lessons[0]
Invoke-RestMethod -Uri "$base/lessons/$($lesson.id)/complete" -Method Put -Headers $headers | Out-Null
$attempt = Invoke-RestMethod -Uri "$base/chapters/$($chapter.id)/attempts" -Method Post -Headers $headers
if ($attempt.questions[0].PSObject.Properties.Name -contains 'correct_option') { throw 'Correct answers leaked before submission.' }
$answers = @{}
foreach ($question in $attempt.questions) { $answers[[string]$question.question_id]=0 }
Invoke-RestMethod -Uri "$base/attempts/$($attempt.id)/answers" -Method Put -Headers $headers -ContentType 'application/json' -Body (@{answers=$answers}|ConvertTo-Json) | Out-Null
$result = Invoke-RestMethod -Uri "$base/attempts/$($attempt.id)/submit" -Method Post -Headers $headers
if (!$result.submitted_at -or !($result.questions[0].PSObject.Properties.Name -contains 'correct_option')) { throw 'Missing quiz result.' }
$progress = Invoke-RestMethod -Uri "$base/progress" -Headers $headers
if ($progress.history.Count -ne 1 -or $progress.courses[0].completed_count -ne 1) { throw 'Progress mismatch.' }
$secrets = Import-Clixml -LiteralPath (Join-Path $PSScriptRoot '.local-secrets.xml')
$admin = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body (@{username='admin';password=[Net.NetworkCredential]::new('', $secrets.Admin).Password}|ConvertTo-Json)
$adminHeaders=@{Authorization='Bearer '+$admin.token}
$report=Invoke-RestMethod -Uri "$base/admin/reports" -Headers $adminHeaders
if (!($report.students | Where-Object username -eq $user)) { throw 'Student missing from admin report.' }
Invoke-RestMethod -Uri "$base/auth/logout" -Method Post -Headers $headers | Out-Null
Write-Output 'PASS: PostgreSQL register -> courses -> lesson progress -> quiz -> result -> student/admin report -> logout'
# Remove only this smoke-test account and its generated records; never touch existing users.
$env:PGPASSWORD=[Net.NetworkCredential]::new('', $secrets.Database).Password
$sql="BEGIN; DELETE FROM attempt_answers WHERE attempt_id IN (SELECT id FROM quiz_attempts WHERE user_id=$($session.account.id)); DELETE FROM quiz_attempts WHERE user_id=$($session.account.id); DELETE FROM lesson_progress WHERE user_id=$($session.account.id); DELETE FROM sessions WHERE user_id=$($session.account.id); DELETE FROM accounts WHERE id=$($session.account.id) AND username='$user'; COMMIT;"
& (Join-Path $PSScriptRoot 'portable/pgsql/bin/psql.exe') -h 127.0.0.1 -p 55432 -U studyspace -d studyspace -v ON_ERROR_STOP=1 -c $sql | Out-Null
Invoke-RestMethod -Uri "$base/auth/logout" -Method Post -Headers $adminHeaders | Out-Null
