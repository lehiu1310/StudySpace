$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080/api'
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$studentName = "e2estudent$stamp"
$teacherName = "e2eteacher$stamp"
$password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$student = $null; $teacher = $null; $admin = $null
$courseId = 0L; $chapterId = 0L; $lesson1Id = 0L; $lesson2Id = 0L; $questionId = 0L; $classId = 0L; $attemptId = 0L
$secrets = Import-Clixml -LiteralPath (Join-Path $PSScriptRoot '.local-secrets.xml')
$adminPassword = [Net.NetworkCredential]::new('', $secrets.Admin).Password
$dbPassword = [Net.NetworkCredential]::new('', $secrets.Database).Password

function CallApi($path, $method = 'GET', $headers = @{}, $body = $null) {
    $params = @{ Uri = "$base/$path"; Method = $method; Headers = $headers; ErrorAction = 'Stop' }
    if ($null -ne $body) { $params.ContentType = 'application/json'; $params.Body = $body | ConvertTo-Json -Depth 20 -Compress }
    Invoke-RestMethod @params
}
function Assert($condition, $message) { if (!$condition) { throw $message } }
function Say($message) { Write-Output "PASS: $message" }

try {
    $admin = CallApi 'auth/login' 'POST' @{} @{ username = 'admin'; password = $adminPassword }
    $adminHeaders = @{ Authorization = "Bearer $($admin.token)" }
    $student = CallApi 'auth/register' 'POST' @{} @{ username = $studentName; password = $password }
    $studentHeaders = @{ Authorization = "Bearer $($student.token)" }
    $teacherRegistration = CallApi 'auth/register' 'POST' @{} @{ username = $teacherName; password = $password }
    CallApi "admin/users/$($teacherRegistration.account.id)/role" 'PUT' $adminHeaders @{ role = 'TEACHER' } | Out-Null
    $teacher = CallApi 'auth/login' 'POST' @{} @{ username = $teacherName; password = $password }
    $teacherHeaders = @{ Authorization = "Bearer $($teacher.token)" }
    $me = CallApi 'auth/me' 'GET' $studentHeaders
    Assert ($me.role -eq 'STUDENT') 'New account did not get STUDENT role.'
    Assert ((CallApi 'auth/me' 'GET' $teacherHeaders).role -eq 'TEACHER') 'Teacher account did not get TEACHER role.'
    Say 'registration, role provisioning, login and session identity for both roles'

    $course = CallApi 'teacher/content/courses' 'POST' $teacherHeaders @{ title = "QA Course $stamp"; description = 'Temporary end-to-end test course'; published = $true }
    $courseId = [long]$course.id
    $chapter = CallApi 'teacher/content/chapters' 'POST' $teacherHeaders @{ courseId = $courseId; title = 'QA Chapter'; position = 0 }
    $chapterId = [long]$chapter.id
    $lesson = CallApi 'teacher/content/lessons' 'POST' $teacherHeaders @{ chapterId = $chapterId; title = 'QA Lesson 1'; content = 'Test lesson content'; materialUrl = ''; position = 0 }
    $lesson1Id = [long]$lesson.id
    $lesson = CallApi 'teacher/content/lessons' 'POST' $teacherHeaders @{ chapterId = $chapterId; title = 'QA Lesson 2'; content = 'Second test lesson'; materialUrl = ''; position = 1 }
    $lesson2Id = [long]$lesson.id
    $question = CallApi 'teacher/content/questions' 'POST' $teacherHeaders @{ chapterId = $chapterId; prompt = 'Which choice is correct?'; options = @('Correct','Wrong A','Wrong B','Wrong C'); correctOption = 0; explanation = 'The first choice is correct.'; difficulty = 1 }
    $questionId = [long]$question.id
    $content = CallApi 'teacher/content' 'GET' $teacherHeaders
    Assert (@($content.courses | Where-Object id -eq $courseId).Count -eq 1) 'Teacher content list did not include the new course.'
    CallApi "teacher/content/courses/$courseId" 'PUT' $teacherHeaders @{ title = "QA Course $stamp"; description = 'Temporary E2E course edited'; published = $true }
    Say 'teacher creates/edits published course, chapter, lessons and quiz question'

    $class = CallApi 'teacher/classes' 'POST' $teacherHeaders @{ courseId = $courseId; name = 'QA Class' }
    $classId = [long]$class.id
    $available = CallApi 'courses' 'GET' $studentHeaders
    Assert (@($available | Where-Object id -eq $courseId).Count -eq 1) 'Published course is not visible to student.'
    $detail = CallApi "courses/$courseId" 'GET' $studentHeaders
    $first = $detail.chapters[0].lessons[0]; $second = $detail.chapters[0].lessons[1]
    Assert (!$first.locked -and $second.locked) 'Sequential lesson lock state is incorrect before completion.'
    CallApi 'classes/join' 'POST' $studentHeaders @{ code = $class.joinCode } | Out-Null
    $myClasses = CallApi 'classes/mine' 'GET' $studentHeaders
    Assert (@($myClasses | Where-Object { [long]$_.id -eq $classId }).Count -eq 1) 'Student class enrollment is missing.'
    Say 'student sees published course, lesson sequencing, joins class and sees enrollment'

    CallApi "lessons/$lesson1Id/complete" 'PUT' $studentHeaders | Out-Null
    $detail = CallApi "courses/$courseId" 'GET' $studentHeaders
    Assert ($detail.chapters[0].lessons[0].completed -and !$detail.chapters[0].lessons[1].locked) 'Lesson completion did not unlock the next lesson.'
    $attempt = CallApi "chapters/$chapterId/attempts" 'POST' $studentHeaders
    $attemptId = [long]$attempt.id
    Assert (!($attempt.questions[0].PSObject.Properties.Name -contains 'correct_option')) 'Quiz answer key leaked before submission.'
    $answers = @{}; foreach ($q in $attempt.questions) { $answers[[string]$q.question_id] = 0 }
    CallApi "attempts/$attemptId/answers" 'PUT' $studentHeaders @{ answers = $answers } | Out-Null
    $result = CallApi "attempts/$attemptId/submit" 'POST' $studentHeaders
    Assert ($result.submitted_at -and $result.score -eq $result.total -and $result.questions[0].explanation) 'Quiz result, score or explanation was not returned.'
    $progress = CallApi 'progress' 'GET' $studentHeaders
    Assert (@($progress.history | Where-Object id -eq $attemptId).Count -eq 1) 'Submitted quiz is missing from student progress.'
    Say 'lesson completion unlock, quiz answer privacy, submission, grading, explanation and student progress'

    CallApi "courses/$courseId/discussion" 'GET' $studentHeaders | Out-Null
    CallApi "courses/$courseId/discussion" 'POST' $studentHeaders @{ body = 'Temporary QA discussion message' } | Out-Null
    $discussion = CallApi "courses/$courseId/discussion" 'GET' $teacherHeaders
    Assert (@($discussion.messages | Where-Object body -eq 'Temporary QA discussion message').Count -eq 1) 'Teacher did not see student discussion message.'
    CallApi "courses/$courseId/discussion" 'DELETE' $studentHeaders | Out-Null
    Say 'course discussion message visibility across student and teacher sessions'

    $report = CallApi "teacher/classes/$classId/report" 'GET' $teacherHeaders
    Assert ($report.summary.student_count -eq 1 -and $report.summary.quiz_attempts -eq 1 -and $report.summary.lesson_completed -eq 1 -and $report.questions.Count -eq 1) 'Teacher class report aggregates do not match student activity.'
    $classes = CallApi 'teacher/classes' 'GET' $teacherHeaders
    Assert (@($classes | Where-Object id -eq $classId).Count -eq 1) 'Teacher class list is missing the created class.'
    Say 'teacher class list and report aggregate enrollment, lesson completion, quiz and wrong-answer analytics'

    CallApi 'auth/me' 'GET' $studentHeaders | Out-Null
    CallApi 'auth/logout' 'POST' $studentHeaders @{} | Out-Null
    $loggedOutRejected = $false
    try { CallApi 'auth/me' 'GET' $studentHeaders | Out-Null } catch { $loggedOutRejected = $true }
    Assert $loggedOutRejected 'Logged-out session was still accepted.'
    CallApi 'auth/logout' 'POST' $teacherHeaders @{} | Out-Null
    Say 'logout revokes session'
}
finally {
    if ($student -and $teacher -and $courseId -gt 0) {
        $sid = [long]$student.account.id
        $tid = [long]$teacher.account.id
        $sql = @"
BEGIN;
DELETE FROM attempt_answers WHERE attempt_id IN (SELECT id FROM quiz_attempts WHERE user_id=$sid);
DELETE FROM quiz_attempts WHERE user_id=$sid;
DELETE FROM lesson_progress WHERE user_id=$sid;
DELETE FROM class_enrollments WHERE student_id=$sid OR class_id=$classId;
DELETE FROM course_messages WHERE course_id=$courseId;
DELETE FROM course_presence WHERE course_id=$courseId;
DELETE FROM study_classes WHERE id=$classId AND teacher_id=$tid AND course_id=$courseId;
DELETE FROM questions WHERE id=$questionId AND chapter_id=$chapterId;
DELETE FROM lessons WHERE id IN ($lesson1Id,$lesson2Id) AND chapter_id=$chapterId;
DELETE FROM chapters WHERE id=$chapterId AND course_id=$courseId;
DELETE FROM courses WHERE id=$courseId AND created_by=$tid;
DELETE FROM personal_study_snapshots WHERE user_id IN ($sid,$tid);
DELETE FROM sessions WHERE user_id IN ($sid,$tid);
DELETE FROM portal_tickets WHERE user_id IN ($sid,$tid);
DELETE FROM accounts WHERE (id=$sid AND username='$studentName') OR (id=$tid AND username='$teacherName');
COMMIT;
"@
        $env:PGPASSWORD = $dbPassword
        & (Join-Path $PSScriptRoot 'portable/pgsql/bin/psql.exe') -h 127.0.0.1 -p 55432 -U studyspace -d studyspace -v ON_ERROR_STOP=1 -c $sql | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'QA cleanup failed; inspect temporary QA rows before rerunning.' }
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        Write-Output 'CLEANUP: removed temporary QA users and all created test data.'
    }
    if ($admin) { try { CallApi 'auth/logout' 'POST' @{ Authorization = "Bearer $($admin.token)" } @{} | Out-Null } catch {} }
}
