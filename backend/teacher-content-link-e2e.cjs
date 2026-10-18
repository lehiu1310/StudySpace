const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const assert = (value, message) => { if (!value) throw new Error(message); console.log(`PASS ${message}`); };
let browser = null;
let teacherId = 0;
let courseTitle = '';

(async () => {
  const base = 'http://localhost:8080';
  const username = process.env.STUDYSPACE_TEACHER_USERNAME;
  const password = process.env.STUDYSPACE_TEACHER_PASSWORD;
  if (!username || !password) throw new Error('Set STUDYSPACE_TEACHER_USERNAME and STUDYSPACE_TEACHER_PASSWORD for this local test.');
  if (!process.env.STUDYSPACE_DB_PASSWORD) throw new Error('STUDYSPACE_DB_PASSWORD is required for safe test cleanup.');
  const login = await fetch(`${base}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }),
  });
  assert(login.ok, 'teacher can authenticate against local API');
  const loginData = await login.json();
  const { token } = loginData;
  teacherId = Number(loginData.account.id);
  browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base);
    await page.evaluate(value => sessionStorage.setItem('studyspace_teacher_session', value), token);
    await page.goto(`${base}/teacher.html#teacher-courses`);
    await page.locator('#course-list').waitFor();

    const stamp = Date.now();
    courseTitle = `ZZ_TEST_LINK_${stamp}`;
    const chapterTitle = `ZZ_TEST_CHAPTER_${stamp}`;
    const lessonTitle = `ZZ_TEST_LESSON_${stamp}`;
    await page.locator('#course-editor summary').click();
    await page.locator('#course-form [name=title]').fill(courseTitle);
    await page.locator('#course-form [name=description]').fill('Temporary UI flow verification content.');
    const courseResponse = page.waitForResponse(response => response.url().endsWith('/api/teacher/content/courses') && response.request().method() === 'POST');
    await page.locator('#course-form button.primary-action').click();
    const courseId = (await (await courseResponse).json()).id;
    await page.waitForURL('**/teacher.html#teacher-chapters');
    assert(await page.locator('#chapter-editor').getAttribute('open') !== null, 'saving a course opens the chapter form');
    assert(await page.locator('#chapter-form [name=courseId]').inputValue() === String(courseId), 'new course is selected for its first chapter');

    await page.locator('#chapter-form [name=title]').fill(chapterTitle);
    const chapterResponse = page.waitForResponse(response => response.url().endsWith('/api/teacher/content/chapters') && response.request().method() === 'POST');
    await page.locator('#chapter-form button.primary-action').click();
    const chapterId = (await (await chapterResponse).json()).id;
    await page.waitForURL('**/teacher.html#teacher-lessons');
    assert(await page.locator('#lesson-editor').getAttribute('open') !== null, 'saving a chapter opens the lesson form');
    assert(await page.locator('#lesson-form [name=chapterId]').inputValue() === String(chapterId), 'new chapter is selected for its first lesson');

    await page.locator('#lesson-form [name=title]').fill(lessonTitle);
    await page.locator('#lesson-form [name=content]').fill('Temporary content to verify the chapter relationship.');
    const lessonResponse = page.waitForResponse(response => response.url().endsWith('/api/teacher/content/lessons') && response.request().method() === 'POST');
    await page.locator('#lesson-form button.primary-action').click();
    const lessonId = (await (await lessonResponse).json()).id;
    const content = await fetch(`${base}/api/teacher/content`, { headers: { Authorization: `Bearer ${token}` } }).then(response => response.json());
    const chapter = content.chapters.find(item => Number(item.id) === Number(chapterId));
    const lesson = content.lessons.find(item => Number(item.id) === Number(lessonId));
    assert(Number(chapter.course_id) === Number(courseId), 'saved chapter belongs to the newly created course');
    assert(Number(lesson.chapter_id) === Number(chapterId), 'saved lesson belongs to the newly created chapter');
    assert(errors.length === 0, `browser has no JavaScript errors (${errors.join('; ')})`);
    console.log(`TEST_DATA course=${courseTitle} chapter=${chapterTitle} lesson=${lessonTitle}`);
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  try {
    if (browser) await browser.close();
    if (teacherId && courseTitle) {
      const sql = `BEGIN;
DELETE FROM attempt_answers WHERE attempt_id IN (SELECT id FROM quiz_attempts WHERE chapter_id IN (SELECT ch.id FROM chapters ch JOIN courses co ON co.id=ch.course_id WHERE co.created_by=${teacherId} AND co.title='${courseTitle}'));
DELETE FROM quiz_attempts WHERE chapter_id IN (SELECT ch.id FROM chapters ch JOIN courses co ON co.id=ch.course_id WHERE co.created_by=${teacherId} AND co.title='${courseTitle}');
DELETE FROM lesson_progress WHERE lesson_id IN (SELECT l.id FROM lessons l JOIN chapters c ON c.id=l.chapter_id JOIN courses co ON co.id=c.course_id WHERE co.created_by=${teacherId} AND co.title='${courseTitle}');
DELETE FROM questions WHERE chapter_id IN (SELECT id FROM chapters WHERE course_id IN (SELECT id FROM courses WHERE created_by=${teacherId} AND title='${courseTitle}'));
DELETE FROM lessons WHERE chapter_id IN (SELECT id FROM chapters WHERE course_id IN (SELECT id FROM courses WHERE created_by=${teacherId} AND title='${courseTitle}'));
DELETE FROM chapters WHERE course_id IN (SELECT id FROM courses WHERE created_by=${teacherId} AND title='${courseTitle}');
DELETE FROM course_learning WHERE course_id IN (SELECT id FROM courses WHERE created_by=${teacherId} AND title='${courseTitle}');
DELETE FROM courses WHERE created_by=${teacherId} AND title='${courseTitle}';
COMMIT;`;
      const cleanup = spawnSync(path.join(__dirname, 'portable', 'pgsql', 'bin', 'psql.exe'), [
        '-h', '127.0.0.1', '-p', '55432', '-U', 'studyspace', '-d', 'studyspace', '-v', 'ON_ERROR_STOP=1', '-c', sql,
      ], { encoding: 'utf8', env: { ...process.env, PGPASSWORD: process.env.STUDYSPACE_DB_PASSWORD } });
      if (cleanup.status !== 0) throw new Error(`Temporary course cleanup failed: ${cleanup.stderr || cleanup.error}`);
      console.log('CLEANUP: removed temporary linked course, chapter and lesson.');
    }
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
});
