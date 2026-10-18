const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = 'http://localhost:8080';
const output = path.join(__dirname, 'test-evidence');
const assert = (condition, message) => {
  if (!condition) throw new Error(message);
  console.log(`PASS ${message}`);
};

(async () => {
  if (!process.env.STUDYSPACE_DEMO_USER || !process.env.STUDYSPACE_DEMO_PASSWORD) {
    throw new Error('Set STUDYSPACE_DEMO_USER and STUDYSPACE_DEMO_PASSWORD for the local demo learner.');
  }
  fs.mkdirSync(output, { recursive: true });
  const login = await fetch(`${base}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: process.env.STUDYSPACE_DEMO_USER, password: process.env.STUDYSPACE_DEMO_PASSWORD }),
  });
  assert(login.ok, 'demo learner can sign in against localhost');
  const session = await login.json();
  const coursesResponse = await fetch(`${base}/api/courses`, { headers: { Authorization: `Bearer ${session.token}` } });
  const courses = await coursesResponse.json();
  assert(coursesResponse.ok, 'published course data loads from the real backend');

  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const errors = [];
  try {
    for (const viewport of [{ name: 'desktop', width: 1440, height: 900 }, { name: 'mobile', width: 390, height: 844 }]) {
      const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height } });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(base);
      await page.evaluate(token => sessionStorage.setItem('studyspace_student_session', token), session.token);
      await page.goto(`${base}/student.html`);
      await page.locator('#continue-learning').waitFor();
      await page.waitForFunction(() => document.querySelectorAll('.course-card-state').length > 0 || document.querySelector('#course-list .empty-box'));
      const labels = await page.locator('.course-card-state').allTextContents();
      const expected = courses.map(course => {
        const total = Number(course.lesson_count || 0), done = Number(course.completed_count || 0);
        return total > 0 && done >= total ? 'Hoàn thành' : total > 0 && done > 0 ? 'Đang học' : 'Chưa bắt đầu';
      });
      assert(JSON.stringify(labels) === JSON.stringify(expected), `${viewport.name}: course status matches 0/partial/complete progress from API`);
      assert(!(await page.locator('body').innerText()).includes('01\nBƯỚC TIẾP THEO'), `${viewport.name}: static next-step placeholder is gone`);
      assert(!(await page.locator('body').innerText()).includes('LỘ TRÌNH CỦA BẠN'), `${viewport.name}: duplicate course kicker is gone`);
      if (courses.some(course => Number(course.lesson_count) > Number(course.completed_count))) {
        await page.waitForFunction(() => document.querySelector('#continue-lesson-title')?.textContent !== 'Chưa có bài học tiếp theo');
        assert((await page.locator('#continue-lesson-course').innerText()).trim().length > 0, `${viewport.name}: next lesson has its real course name`);
      }
      await page.screenshot({ path: path.join(output, `display-overview-${viewport.name}.png`), animations: 'disabled' });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `${viewport.name}: overview has no horizontal overflow`);

      await page.evaluate(() => { location.hash = 'classes'; });
      await page.reload();
      await page.locator('#classes-view:not([hidden])').waitFor();
      console.log(`${viewport.name} geometry`, await page.evaluate(() => ({
        scrollY: window.scrollY,
        topbar: document.querySelector('.student-topbar').getBoundingClientRect().toJSON(),
        main: document.querySelector('.student-main').getBoundingClientRect().toJSON(),
        heading: document.querySelector('#classes-view .task-page-heading').getBoundingClientRect().toJSON(),
      })));
      if (await page.locator('#discussion-class-select').count()) {
        await page.locator('.student-discussion-body').waitFor();
        await page.waitForFunction(() => { const body=document.querySelector('.student-discussion-body'); return body && !body.querySelector('.loading-line'); });
        assert(await page.locator('.student-class-card').count() === 1, `${viewport.name}: classes page contains one discussion frame`);
        assert(await page.locator('.class-teacher-name').innerText().then(value => value.startsWith('Giảng viên: ')), `${viewport.name}: teacher is labeled with display name`);
        assert(!(await page.locator('.class-teacher-name').innerText()).includes('@'), `${viewport.name}: role-style username is not shown as the teacher name`);
        assert(await page.locator('.discussion-online').count() === 0, `${viewport.name}: own account is not counted as another online learner`);
      }
      await page.screenshot({ path: path.join(output, `display-classes-${viewport.name}.png`), animations: 'disabled' });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `${viewport.name}: classes page has no horizontal overflow`);
      await context.close();
    }
    assert(errors.length === 0, `no uncaught browser errors (${errors.join('; ')})`);
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
