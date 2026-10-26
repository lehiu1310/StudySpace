const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = 'http://localhost:8080';
const out = path.join(__dirname, 'test-evidence');
const teacherUser = process.env.STUDYSPACE_DEMO_TEACHER || 'demo_cohort_teacher';
const studentUser = process.env.STUDYSPACE_DEMO_USER || 'demo_cohort_10';
const password = process.env.STUDYSPACE_DEMO_PASSWORD;
const assert = (ok, label) => { if (!ok) throw new Error(`FAIL ${label}`); console.log(`PASS ${label}`); };

async function signIn(username) {
  const response = await fetch(`${base}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  assert(response.ok, `${username} logs in on local backend`);
  return (await response.json()).token;
}

async function takeRoleScreenshots(browser, role, token, viewports) {
  for (const viewport of viewports) {
    const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height } });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base);
    const storageKey = role === 'teacher' ? 'studyspace_teacher_session' : 'studyspace_student_session';
    await page.evaluate(({ key, value }) => sessionStorage.setItem(key, value), { key: storageKey, value: token });
    if (role === 'teacher') {
      await page.goto(`${base}/teacher.html#teacher-classes`);
      const demoClassRow = page.locator('.teacher-class-row').filter({ hasText: 'CSDL · Lớp thực hành 01' });
      await demoClassRow.waitFor();
      await demoClassRow.locator('[data-class-report]').click();
      await page.locator('#class-report:not([hidden])').waitFor();
      await page.waitForFunction(() => !!window.Chart?.getChart('class-chapter-chart') && !!window.Chart?.getChart('class-question-chart'));
      assert(await page.locator('.class-heatmap tbody tr').count() === 28, `${viewport.name}: heatmap contains all 28 learners`);
      assert(await page.evaluate(() => ['class-chapter-chart','class-question-chart'].every(id => document.getElementById(id).getBoundingClientRect().width > 0)), `${viewport.name}: both class charts have visible canvas dimensions`);
      assert((await page.locator('#class-report').innerText()).includes('28'), `${viewport.name}: teacher report shows class of 28`);
      assert((await page.locator('#class-report').innerText()).includes('Chuẩn hóa dữ liệu'), `${viewport.name}: teacher report shows chapter breakdown`);
      if (viewport.name === 'mobile') {
        await page.locator('#class-report-charts').evaluate(element => element.scrollIntoView({ block: 'start' }));
        await page.screenshot({ path: path.join(out, 'demo-cohort-teacher-report-mobile.png'), animations: 'disabled' });
      } else {
        await page.locator('#class-report').screenshot({ path: path.join(out, 'demo-cohort-teacher-report-desktop.png'), animations: 'disabled' });
      }
      if (viewport.name === 'mobile') {
        await page.locator('#class-report [data-close-panel]').click();
        await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
      }
      await page.screenshot({ path: path.join(out, `demo-cohort-teacher-${viewport.name}.png`), animations: 'disabled' });
    } else {
      await page.goto(`${base}/student.html`);
      await page.locator('#greeting-name').waitFor();
      await page.locator('#courses-nav').click();
      await page.locator('#courses-view:not([hidden])').waitFor();
      const demoCourse = page.locator('#course-list').getByText('Cơ sở dữ liệu · Lớp thực hành mẫu', { exact: true });
      await demoCourse.waitFor();
      await demoCourse.evaluate(element => element.scrollIntoView({ block: 'center' }));
      await page.screenshot({ path: path.join(out, `demo-cohort-student-${viewport.name}.png`), animations: 'disabled' });
    }
    assert(errors.length === 0, `${viewport.name}: no uncaught ${role} UI errors (${errors.join('; ')})`);
    await context.close();
  }
}

(async () => {
  if (!password) throw new Error('Set STUDYSPACE_DEMO_PASSWORD using the ignored local demo account file.');
  fs.mkdirSync(out, { recursive: true });
  const teacherToken = await signIn(teacherUser);
  const studentToken = await signIn(studentUser);
  const classes = await fetch(`${base}/api/teacher/classes`, { headers: { Authorization: `Bearer ${teacherToken}` } }).then(r => r.json());
  const demoClass = classes.find(row => row.join_code === 'DEMO2026');
  assert(!!demoClass && Number(demoClass.student_count) === 28, 'teacher class API returns the isolated 28-person demo cohort');
  const report = await fetch(`${base}/api/teacher/classes/${demoClass.id}/report`, { headers: { Authorization: `Bearer ${teacherToken}` } }).then(r => r.json());
  assert(Number(report.summary.student_count) === 28, 'class report contains all enrolled learners');
  assert(Number(report.summary.quiz_attempts) > 250, 'class report has multi-week quiz activity');
  assert(report.chapters.find(ch => ch.title === 'Chuẩn hóa dữ liệu')?.average_score < report.chapters.find(ch => ch.title === 'Mô hình quan hệ và khóa')?.average_score, 'normalization chapter is visibly harder in real score data');
  assert(report.questions.length > 0 && report.questions.some(q => Number(q.wrong_percent) >= 40), 'report has questions with meaningful wrong-answer rates');
  const courses = await fetch(`${base}/api/courses`, { headers: { Authorization: `Bearer ${studentToken}` } }).then(r => r.json());
  assert(courses.some(course => course.title === 'Cơ sở dữ liệu · Lớp thực hành mẫu'), 'student course API exposes the published demo course');

  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  try {
    const viewports = [{ name: 'desktop', width: 1440, height: 900 }, { name: 'mobile', width: 390, height: 844 }];
    await takeRoleScreenshots(browser, 'teacher', teacherToken, viewports);
    await takeRoleScreenshots(browser, 'student', studentToken, viewports);
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
