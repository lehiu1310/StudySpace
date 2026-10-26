const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');

const base = 'http://127.0.0.1:8080';
const apiBase = `${base}/api`;
const stamp = Date.now();
const fixedDemo = process.env.STUDYSPACE_FIXED_DEMO === '1';
const teacherScreenshot = process.env.STUDYSPACE_E2E_TEACHER_SCREENSHOT || 'docs/test-evidence/phase2-teacher-dashboard.png';
const teacherName = fixedDemo ? 'demo_cohort_teacher' : `ui_teacher_${stamp}`;
const studentName = fixedDemo ? 'demo_cohort_10' : `ui_student_${stamp}`;
const password = fixedDemo ? process.env.STUDYSPACE_DEMO_PASSWORD : `QA!${stamp}Aa`;
let teacher = null;
let student = null;
let admin = null;
let courseId = 0;
let chapterId = 0;
let lessonIds = [];
let questionId = 0;
let classId = 0;
let attemptId = 0;
let materialFilename = '';
let browser;

async function api(path, token, method = 'GET', body) {
  const response = await fetch(`${apiBase}/${path}`, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${data.error || text}`);
  return data;
}
function check(condition, message) {
  if (!condition) throw new Error(message);
  console.log(`PASS: ${message}`);
}
async function waitStatus(page, text) {
  await page.waitForFunction(expected => document.querySelector('#status')?.textContent.includes(expected), text);
}
async function openEditor(page, id) {
  const editor = page.locator(`#${id}`);
  if (!(await editor.evaluate(element => element.open))) await page.locator(`[data-open-editor="${id}"]`).click();
}

async function main() {
  if (!process.env.STUDYSPACE_DB_PASSWORD) throw new Error('STUDYSPACE_DB_PASSWORD is required for cleanup.');
  if (fixedDemo) {
    if (!password) throw new Error('STUDYSPACE_DEMO_PASSWORD is required for the fixed demo pair.');
    [teacher, student] = await Promise.all([
      api('auth/login', null, 'POST', { username: teacherName, password }),
      api('auth/login', null, 'POST', { username: studentName, password }),
    ]);
  } else {
    if (!process.env.STUDYSPACE_ADMIN_PASSWORD) throw new Error('STUDYSPACE_ADMIN_PASSWORD is required.');
    admin = await api('auth/login', null, 'POST', { username: 'admin', password: process.env.STUDYSPACE_ADMIN_PASSWORD });
    student = await api('auth/register', null, 'POST', { username: studentName, password });
    const teacherRegistration = await api('auth/register', null, 'POST', { username: teacherName, password });
    await api(`admin/users/${teacherRegistration.account.id}/role`, admin.token, 'PUT', { role: 'TEACHER' });
    teacher = await api('auth/login', null, 'POST', { username: teacherName, password });
  }

  const pageErrors = [];
  browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const teacherContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await teacherContext.grantPermissions(['clipboard-read', 'clipboard-write']);
  const teacherPage = await teacherContext.newPage();
  teacherPage.on('pageerror', error => pageErrors.push(`teacher: ${error.message}`));
  await teacherPage.goto(`${base}/login.html?staff=1`);
  await teacherPage.locator('#username').fill(teacherName);
  await teacherPage.locator('#password').fill(password);
  await teacherPage.locator('#submit-button').click();
  await teacherPage.waitForURL('**/teacher.html');
  await teacherPage.locator('#workspace:not([hidden])').waitFor();
  await teacherPage.waitForTimeout(400);
  await teacherPage.screenshot({ path: teacherScreenshot });
  check(fixedDemo ? Number(await teacherPage.locator('#stat-courses').textContent()) >= 3 : await teacherPage.locator('#stat-courses').textContent() === '0', 'teacher signs in and workspace loads');

  const courseTitle = `QA Journey ${stamp}`;
  await teacherPage.locator('.teacher-sidebar a[href="#teacher-courses"]').click();
  await teacherPage.locator('#teacher-courses:not([hidden])').waitFor();
  await teacherPage.locator('[data-open-editor="course-editor"]').click();
  await teacherPage.locator('#course-form [name=title]').fill(courseTitle);
  await teacherPage.locator('#course-form [name=description]').fill('Lộ trình kiểm thử từ giảng viên đến học viên.');
  await teacherPage.locator('#course-form button.primary-action').click();
  await waitStatus(teacherPage, 'Đã lưu khóa học.');
  let content = await api('teacher/content', teacher.token);
  courseId = Number(content.courses.find(course => course.title === courseTitle)?.id);
  check(courseId > 0 && !content.courses.find(course => course.id === courseId).published, 'teacher creates a draft course from the actual form');
  await teacherPage.locator('.teacher-sidebar a[href="#teacher-courses"]').click();
  const draftCard = teacherPage.locator('#course-list .teacher-course-card').filter({ hasText: courseTitle });
  await draftCard.locator('summary').click();
  await draftCard.getByRole('button', { name: 'Sửa' }).click();
  await teacherPage.locator('#course-form [name=description]').fill('Lộ trình đã được cập nhật trước khi giao lớp.');
  await teacherPage.locator('#course-form button.primary-action').click();
  await waitStatus(teacherPage, 'Đã lưu khóa học dưới dạng bản nháp');
  content = await api('teacher/content', teacher.token);
  check(content.courses.find(course => course.id === courseId).description.includes('cập nhật'), 'teacher edits course content from the actual portal');

  await teacherPage.locator('.teacher-sidebar a[href="#teacher-chapters"]').click();
  await openEditor(teacherPage, 'chapter-editor');
  await teacherPage.locator('#chapter-form select[name=courseId]').selectOption(String(courseId));
  await teacherPage.locator('#chapter-form [name=title]').fill('Nền tảng cơ sở dữ liệu');
  await teacherPage.locator('#chapter-form [name=position]').fill('0');
  await teacherPage.locator('#chapter-form button.primary-action').click();
  await waitStatus(teacherPage, 'Đã lưu chương.');
  content = await api('teacher/content', teacher.token);
  chapterId = Number(content.chapters.find(chapter => chapter.course_id === courseId)?.id);
  check(chapterId > 0, 'teacher adds a chapter through the editor');

  for (const [index, title] of ['Mô hình dữ liệu quan hệ', 'Khóa chính và khóa ngoại'].entries()) {
    await openEditor(teacherPage, 'lesson-editor');
    await teacherPage.locator('#lesson-form select[name=chapterId]').selectOption(String(chapterId));
    await teacherPage.locator('#lesson-form [name=title]').fill(title);
    await teacherPage.locator('#lesson-form [name=content]').fill(`Nội dung ${title}: dữ liệu phải có cấu trúc và ràng buộc toàn vẹn.`);
    await teacherPage.locator('#lesson-form [name=position]').fill(String(index));
    if (index === 1) await teacherPage.locator('#lesson-form [name=materialFile]').setInputFiles({
      name: 'ghi-chu-khoa-hoc.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\nStudySpace QA PDF fixture'),
    });
    await teacherPage.locator('#lesson-form button.primary-action').click();
    await waitStatus(teacherPage, 'Đã lưu nội dung');
  }
  content = await api('teacher/content', teacher.token);
  lessonIds = content.lessons.filter(lesson => lesson.chapter_id === chapterId).map(lesson => Number(lesson.id));
  check(lessonIds.length === 2, 'teacher creates two ordered lessons');
  materialFilename = content.lessons.find(lesson => lesson.id === lessonIds[1]).material_url?.split('/').pop() || '';
  check(materialFilename.endsWith('.pdf') && await teacherPage.locator('#lesson-list .teacher-entity-count').filter({ hasText: 'PDF' }).count() === 1, 'teacher uploads a PDF and attaches it to a lesson');

  await teacherPage.locator('.teacher-sidebar a[href="#teacher-questions"]').click();
  await openEditor(teacherPage, 'question-editor');
  await teacherPage.locator('#question-form select[name=chapterId]').selectOption(String(chapterId));
  await teacherPage.locator('#question-form [name=prompt]').fill('Khóa chính dùng để làm gì?');
  for (const [name, value] of Object.entries({ a: 'Định danh duy nhất một bản ghi', b: 'Lưu nhiều bảng trong một ô', c: 'Thay thế mọi khóa ngoại', d: 'Sắp xếp giao diện' }))
    await teacherPage.locator(`#question-form [name=${name}]`).fill(value);
  await teacherPage.locator('#question-form [name=correctOption]').selectOption('0');
  await teacherPage.locator('#question-form [name=explanation]').fill('Khóa chính xác định duy nhất từng dòng trong bảng.');
  await teacherPage.locator('#question-form button.primary-action').click();
  await waitStatus(teacherPage, 'Đã lưu nội dung');
  content = await api('teacher/content', teacher.token);
  questionId = Number(content.questions.find(question => question.chapter_id === chapterId)?.id);
  const questionCount = Number(await teacherPage.locator('#stat-questions').textContent());
  check(questionId > 0 && (fixedDemo ? questionCount >= 31 : questionCount === 1), 'teacher writes an explained quiz question and dashboard counts update');
  await teacherPage.locator('.teacher-sidebar a[href="#teacher-courses"]').click();
  const publishCard = teacherPage.locator('#course-list .teacher-course-card').filter({ hasText: courseTitle });
  await publishCard.locator('summary').click();
  await publishCard.getByRole('button', { name: 'Sửa' }).click();
  await teacherPage.locator('#course-form [name=published]').check();
  await teacherPage.locator('#course-form button.primary-action').click();
  await waitStatus(teacherPage, 'Đã lưu và xuất bản');
  content = await api('teacher/content', teacher.token);
  check(content.courses.find(course => course.id === courseId).published, 'teacher publishes only after adding real lessons');

  await teacherPage.locator('.teacher-sidebar a[href="#teacher-classes"]').click();
  await teacherPage.locator('#class-form select[name=courseId]').selectOption(String(courseId));
  await teacherPage.locator('#class-form [name=name]').fill('Lớp CSDL · Nhóm 01');
  await teacherPage.locator('#class-form button.primary-action').click();
  await waitStatus(teacherPage, 'Đã tạo lớp học');
  const classroom = (await api('teacher/classes', teacher.token)).find(item => item.course_id === courseId);
  classId = Number(classroom?.id);
  check(classId > 0 && classroom.join_code, 'teacher creates a class and receives its join code in the real portal');
  await teacherPage.locator(`#class-list [data-copy-code="${classroom.join_code}"]`).click();
  await waitStatus(teacherPage, 'Đã sao chép mã lớp');
  check(true, 'teacher copies the class join code from the portal');

  const studentContext = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const studentPage = await studentContext.newPage();
  studentPage.on('dialog', dialog => dialog.accept());
  studentPage.on('pageerror', error => pageErrors.push(`student: ${error.message}`));
  await studentPage.goto(`${base}/login.html`);
  await studentPage.locator('#username').fill(studentName);
  await studentPage.locator('#password').fill(password);
  await studentPage.locator('#submit-button').click();
  await studentPage.waitForURL('**/student.html');
  await studentPage.waitForFunction(() => !document.querySelector('#student-toast')?.classList.contains('visible'));
  await studentPage.locator('#classes-nav').click();
  await studentPage.locator('#class-code').fill('BADCODE');
  await studentPage.locator('#class-join-form button').click();
  await studentPage.waitForFunction(() => document.querySelector('#student-toast')?.classList.contains('visible'));
  const invalidCodeMessage = await studentPage.locator('#student-toast').textContent();
  check(invalidCodeMessage.includes('Mã lớp') && invalidCodeMessage.includes('không hợp lệ'), `student sees invalid-code feedback: ${invalidCodeMessage}`);
  await studentPage.locator('#class-code').fill(classroom.join_code);
  await studentPage.locator('#class-join-form button').click();
  await studentPage.locator('.student-class-card').waitFor();
  await studentPage.locator('#discussion-class-select').selectOption(String(classId));
  await studentPage.waitForFunction(name => document.querySelector('.student-class-card h3')?.textContent.includes(name), classroom.name);
  const joinedClassCard = studentPage.locator('.student-class-card').filter({ hasText: classroom.name }).first();
  await joinedClassCard.waitFor();
  check((await joinedClassCard.locator('h3').textContent()).includes(classroom.name), 'student joins the class in the portal and sees the assigned course');
  await studentPage.locator('[data-send-course] input[name=body]').fill('Em đã vào lớp và bắt đầu học ạ.');
  await studentPage.locator('[data-send-course] button').click();
  await studentPage.getByText('Em đã vào lớp và bắt đầu học ạ.').waitFor();
  check(true, 'student sends a message in the class discussion from the portal');
  await studentPage.locator('#courses-nav').click();
  await studentPage.locator(`[data-course="${courseId}"]`).waitFor();
  await studentPage.locator(`[data-course="${courseId}"]`).click();
  await studentPage.locator('#course-content').getByText('Nền tảng cơ sở dữ liệu').waitFor();
  check(await studentPage.locator(`[data-open-lesson="${lessonIds[0]}"]`).isEnabled(), 'student sees the teacher-published course and first lesson');
  check(await studentPage.locator(`[data-open-lesson="${lessonIds[1]}"]`).isDisabled(), 'later lesson is locked until earlier lesson is completed');
  await studentPage.locator(`[data-open-lesson="${lessonIds[0]}"]`).click();
  await studentPage.locator('#lesson-view:not([hidden])').waitFor();
  await studentPage.locator('#complete-current-lesson').click();
  await studentPage.locator('#next-lesson').waitFor();
  await studentPage.locator('#back-lesson').click();
  check(await studentPage.locator(`[data-open-lesson="${lessonIds[1]}"]`).isEnabled(), 'completing lesson unlocks the next lesson in the UI');
  await studentPage.locator(`[data-quiz="${chapterId}"]`).click();
  await studentPage.locator('#quiz-view:not([hidden])').waitFor();
  await studentPage.locator('input[type=radio][value="1"]').check();
  await studentPage.locator('#submit-quiz').click();
  await studentPage.locator('#done-quiz').waitFor();
  const resultText = await studentPage.locator('#quiz-content').innerText();
  check(resultText.includes('Điểm: 0/1') && resultText.includes('Khóa chính xác định duy nhất'), 'student submits quiz and sees score plus explanation');
  const progress = await api('progress', student.token);
  attemptId = Number(progress.history.find(attempt => attempt.chapter_id === chapterId)?.id);
  check(attemptId > 0, 'quiz result is recorded in student history');
  await studentPage.locator('#done-quiz').click();
  await studentPage.locator('#course-view:not([hidden])').waitFor();

  await teacherPage.locator(`#class-list [data-class-report="${classId}"]`).click();
  await teacherPage.locator('#class-report h3').waitFor();
  const reportText = await teacherPage.locator('#class-report').innerText();
  check(reportText.includes('1/2') && reportText.includes('1/1'), 'teacher opens class report and sees completion and wrong-answer analytics');
  await teacherPage.locator(`#class-list [data-class-discussion="${courseId}"]`).click();
  await teacherPage.getByText('Em đã vào lớp và bắt đầu học ạ.').waitFor();
  await teacherPage.locator('.discussion-compose input[name=body]').fill('Chào em, hãy xem phần khóa chính nhé.');
  await teacherPage.locator('.discussion-compose button').click();
  await teacherPage.getByText('Chào em, hãy xem phần khóa chính nhé.').waitFor();
  await studentPage.locator('#classes-nav').click();
  await studentPage.locator(`[data-refresh-discussion="${courseId}"]`).click();
  await studentPage.getByText('Chào em, hãy xem phần khóa chính nhé.').waitFor();
  check(true, 'teacher and student exchange messages in the class discussion');
  const report = await api(`teacher/classes/${classId}/report`, teacher.token);
  check(report.summary.student_count === 1 && report.summary.lesson_completed === 1 && report.summary.quiz_attempts === 1, 'teacher report reflects the student course, lesson and quiz activity');
  await teacherPage.setViewportSize({ width: 390, height: 844 });
  check(!(await teacherPage.evaluate(() => document.documentElement.scrollWidth > innerWidth)), 'teacher mobile portal has no horizontal overflow');

  const overflow = await studentPage.evaluate(() => document.documentElement.scrollWidth > innerWidth);
  check(!overflow && pageErrors.length === 0, `student mobile flow has no horizontal overflow or runtime errors (${pageErrors.length} page errors)`);
  await studentPage.locator('#logout').click();
  await studentPage.waitForURL('**/login.html');
  await teacherPage.locator('#teacher-user-trigger').click();
  await teacherPage.locator('#logout').click();
  await teacherPage.waitForURL(url => url.pathname === '/login.html' && url.searchParams.has('staff'));
  check(true, 'student and teacher can log out through their portals');
  await teacherContext.close();
  await studentContext.close();
}

async function cleanup() {
  if (browser) await browser.close().catch(() => {});
  if (!teacher || !student) return;
  const sid = Number(student.account.id);
  const tid = Number(teacher.account.id);
  if (materialFilename && /^[0-9a-f-]{36}\.pdf$/i.test(materialFilename)) {
    const path = require('node:path');
    const materialDirectory = path.resolve(__dirname, 'uploads');
    const materialPath = path.resolve(materialDirectory, materialFilename);
    if (materialPath.startsWith(materialDirectory + path.sep)) require('node:fs').rmSync(materialPath, { force: true });
  }
  const sql = fixedDemo ? `BEGIN;
DELETE FROM attempt_answers WHERE attempt_id IN (SELECT id FROM quiz_attempts WHERE id=${attemptId || 0} AND user_id=${sid});
DELETE FROM quiz_attempts WHERE id=${attemptId || 0} AND user_id=${sid};
DELETE FROM lesson_progress WHERE user_id=${sid} AND lesson_id IN (${lessonIds.length ? lessonIds.join(',') : '0'});
DELETE FROM class_enrollments WHERE student_id=${sid} AND class_id=${classId || 0};
DELETE FROM course_messages WHERE course_id=${courseId || 0};
DELETE FROM course_presence WHERE course_id=${courseId || 0};
DELETE FROM course_learning WHERE user_id IN (${sid},${tid}) AND course_id=${courseId || 0};
DELETE FROM study_classes WHERE id=${classId || 0} AND teacher_id=${tid};
DELETE FROM questions WHERE chapter_id IN (SELECT id FROM chapters WHERE course_id=${courseId || 0}) OR id=${questionId || 0};
DELETE FROM lessons WHERE chapter_id IN (SELECT id FROM chapters WHERE course_id=${courseId || 0}) OR id IN (${lessonIds.length ? lessonIds.join(',') : '0'});
DELETE FROM chapters WHERE course_id=${courseId || 0} OR id=${chapterId || 0};
DELETE FROM courses WHERE id=${courseId || 0} AND created_by=${tid};
COMMIT;` : `BEGIN;
DELETE FROM attempt_answers WHERE attempt_id IN (SELECT id FROM quiz_attempts WHERE user_id=${sid});
DELETE FROM quiz_attempts WHERE user_id=${sid};
DELETE FROM lesson_progress WHERE user_id=${sid};
DELETE FROM class_enrollments WHERE student_id=${sid} OR class_id=${classId || 0};
DELETE FROM course_messages WHERE course_id=${courseId || 0};
DELETE FROM course_presence WHERE course_id=${courseId || 0};
DELETE FROM course_learning WHERE user_id IN (${sid},${tid}) OR course_id=${courseId || 0};
DELETE FROM study_classes WHERE id=${classId || 0} AND teacher_id=${tid};
DELETE FROM questions WHERE chapter_id IN (SELECT id FROM chapters WHERE course_id=${courseId || 0}) OR id=${questionId || 0};
DELETE FROM lessons WHERE chapter_id IN (SELECT id FROM chapters WHERE course_id=${courseId || 0}) OR id IN (${lessonIds.length ? lessonIds.join(',') : '0'});
DELETE FROM chapters WHERE course_id=${courseId || 0} OR id=${chapterId || 0};
DELETE FROM courses WHERE id=${courseId || 0} AND created_by=${tid};
DELETE FROM personal_study_snapshots WHERE user_id IN (${sid},${tid});
DELETE FROM sessions WHERE user_id IN (${sid},${tid});
DELETE FROM portal_tickets WHERE user_id IN (${sid},${tid});
DELETE FROM accounts WHERE (id=${sid} AND username='${studentName}') OR (id=${tid} AND username='${teacherName}');
COMMIT;`;
  const databaseUrl = new URL((process.env.DB_URL || 'jdbc:postgresql://127.0.0.1:5432/studyspace').replace(/^jdbc:/, ''));
  if (databaseUrl.protocol !== 'postgresql:') throw new Error('QA cleanup requires a PostgreSQL DB_URL.');
  const databaseName = decodeURIComponent(databaseUrl.pathname.replace(/^\//, '')) || 'studyspace';
  const psql = require('node:child_process').spawnSync(
    `${__dirname}/portable/pgsql/bin/psql.exe`,
    ['-h', databaseUrl.hostname, '-p', databaseUrl.port || '5432', '-U', 'studyspace', '-d', databaseName, '-v', 'ON_ERROR_STOP=1', '-c', sql],
    { encoding: 'utf8', env: { ...process.env, PGPASSWORD: process.env.STUDYSPACE_DB_PASSWORD } },
  );
  if (psql.status !== 0) throw new Error(`QA cleanup failed: ${psql.stderr || psql.error}`);
  console.log(fixedDemo
    ? 'CLEANUP: removed only temporary course, class, test quiz and test lesson progress; preserved both fixed demo accounts and semester data.'
    : 'CLEANUP: removed QA users, class, course, lessons, quiz and progress.');
}

main().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  try { await cleanup(); } catch (error) { console.error(error); process.exitCode = 1; }
});
