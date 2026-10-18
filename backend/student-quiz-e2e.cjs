const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const base = 'http://localhost:8080';
const evidence = path.join(__dirname, 'docs', 'test-evidence');
const demoPassword = process.env.STUDYSPACE_DEMO_PASSWORD;
const assert = (value, message) => { if (!value) throw new Error(message); console.log(`PASS ${message}`); };
let account = null;
let browser = null;
let username = '';

function cleanupDatabase() {
  if (!account?.account?.id) return;
  if (!process.env.STUDYSPACE_DB_PASSWORD) throw new Error('STUDYSPACE_DB_PASSWORD is required to clean up the temporary learner.');
  const id = Number(account.account.id);
  const sql = `BEGIN;
DELETE FROM attempt_answers WHERE attempt_id IN (SELECT id FROM quiz_attempts WHERE user_id=${id});
DELETE FROM quiz_attempts WHERE user_id=${id};
DELETE FROM lesson_progress WHERE user_id=${id};
DELETE FROM course_messages WHERE user_id=${id};
DELETE FROM course_presence WHERE user_id=${id};
DELETE FROM personal_study_snapshots WHERE user_id=${id};
DELETE FROM portal_tickets WHERE user_id=${id};
DELETE FROM sessions WHERE user_id=${id};
DELETE FROM class_enrollments WHERE student_id=${id};
DELETE FROM course_learning WHERE user_id=${id};
DELETE FROM accounts WHERE id=${id} AND username='${username}';
COMMIT;`;
  const result = spawnSync(path.join(__dirname, 'portable', 'pgsql', 'bin', 'psql.exe'), [
    '-h', '127.0.0.1', '-p', '55432', '-U', 'studyspace', '-d', 'studyspace', '-v', 'ON_ERROR_STOP=1', '-c', sql,
  ], { encoding: 'utf8', env: { ...process.env, PGPASSWORD: process.env.STUDYSPACE_DB_PASSWORD } });
  if (result.status !== 0) throw new Error(`Temporary learner cleanup failed: ${result.stderr || result.error}`);
  console.log('CLEANUP: removed quiz learner, attempts and progress.');
}

(async () => {
  if (!process.env.STUDYSPACE_DB_PASSWORD) throw new Error('STUDYSPACE_DB_PASSWORD is required for safe test cleanup.');
  if (!demoPassword) throw new Error('STUDYSPACE_DEMO_PASSWORD is required for the seeded demo learner login.');
  fs.mkdirSync(evidence, { recursive: true });
  const stamp = Date.now();
  username = `quiz_e2e_${stamp}`;
  const registration = await fetch(`${base}/api/auth/register`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: `Quiz-E2E-${stamp}!` }),
  });
  assert(registration.ok, 'test learner registered against local API');
  account = await registration.json();
  browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(base);
  await page.evaluate(token => sessionStorage.setItem('studyspace_student_session', token), account.token);
  await page.goto(`${base}/student.html`);
  await page.locator('#study-time-chart .focus-empty').waitFor();
  await page.locator('#study-time-preview').screenshot({ path: path.join(evidence, 'phase4-focus-empty.png') });
  assert(await page.locator('#study-time-chart').getByText('Chưa có phiên tập trung nào tuần này').isVisible(), 'empty focus chart uses unified empty state');
  await page.evaluate(() => { const native = window.setInterval; window.setInterval = (callback, delay, ...args) => native(callback, delay === 1000 ? 1 : delay, ...args); });
  await page.locator('[data-start-focus]').click();
  await page.locator('.web-focus-dialog').waitFor();
  await page.waitForFunction(() => /^24:0\d$/.test(document.querySelector('#web-focus-clock')?.textContent || ''));
  const focusSave = page.waitForResponse(response => response.url().endsWith('/api/personal-study') && response.request().method() === 'PUT');
  await page.locator('#web-focus-stop').click();
  await focusSave;
  await page.locator('#study-time-chart .focus-chart').waitFor();
  assert(true, 'completed focus session syncs to personal study and fills the weekly chart');

  await page.locator('#courses-nav').click();
  await page.locator('[data-course]').first().waitFor();
  await page.locator('[data-course]').first().click();
  await page.locator('#course-view:not([hidden])').waitFor();
  await page.locator('[data-quiz]').first().click();
  await page.locator('#quiz-view:not([hidden])').waitFor();
  await page.locator('#quiz-countdown').waitFor();
  assert(/^Còn \d+:\d{2}$/.test(await page.locator('#quiz-countdown').textContent()), 'quiz timer uses server deadline');
  await page.locator('[data-mark-question]').first().click();
  assert(await page.locator('[data-mark-question]').first().getAttribute('class').then(value => value.includes('is-marked')), 'question review mark toggles on');
  const save = page.waitForResponse(response => response.url().includes('/answers') && response.request().method() === 'PUT');
  await page.locator('.quiz-option input').first().check();
  await save;
  await page.locator('.quiz-dialog').screenshot({ path: path.join(evidence, 'phase4-quiz-in-progress.png') });
  await page.reload();
  await page.locator('#quiz-view:not([hidden])').waitFor();
  assert(await page.locator('.quiz-option input').first().isChecked(), 'selected answer restored after page reload');
  assert(await page.locator('[data-mark-question]').first().getAttribute('class').then(value => value.includes('is-marked')), 'review mark restored after page reload');
  const deadline = await page.evaluate(async () => {
    const saved = JSON.parse(sessionStorage.getItem(`studyspace_active_quiz_${sessionStorage.getItem('studyspace_student_session').slice(-12)}`));
    const token = sessionStorage.getItem('studyspace_student_session');
    const attempt = await fetch(`/api/attempts/${saved.id}`, { headers: { Authorization: `Bearer ${token}` } }).then(response => response.json());
    return attempt.deadline;
  });
  await page.evaluate(deadlineMs => { window.__baseNow = Date.now.bind(Date); Date.now = () => deadlineMs - 59000; }, deadline);
  await page.getByText('Còn 1 phút. Hãy kiểm tra và nộp bài.').waitFor();
  await page.evaluate(() => { Date.now = window.__baseNow; });
  assert(true, 'one-minute warning appears before deadline');

  let confirmMessage = '';
  page.once('dialog', async dialog => { confirmMessage = dialog.message(); await dialog.accept(); });
  await page.locator('#submit-quiz').click();
  await page.locator('.quiz-result').waitFor();
  assert(confirmMessage.includes('Câu chưa trả lời') && confirmMessage.includes('Câu đã đánh dấu'), 'submission confirmation lists unanswered and marked questions');
  await page.addStyleTag({ content: '.student-topbar{position:static!important;top:auto!important}' });
  await page.locator('.quiz-dialog').screenshot({ path: path.join(evidence, 'phase4-quiz-result.png') });
  await page.getByRole('button', { name: 'Quay lại nội dung học' }).click();
  if (!(await page.locator('[data-quiz]').first().isVisible().catch(() => false))) {
    await page.locator('#courses-nav').click();
    await page.locator('[data-course]').first().click();
  }
  const chapterId = Number(await page.locator('[data-quiz]').first().getAttribute('data-quiz'));
  await page.evaluate(async id => {
    const token = sessionStorage.getItem('studyspace_student_session');
    const attempt = await fetch(`/api/chapters/${id}/attempts`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } }).then(response => response.json());
    sessionStorage.setItem(`studyspace_active_quiz_${token.slice(-12)}`, JSON.stringify({ id: attempt.id, marked: [] }));
  }, chapterId);
  await page.reload();
  await page.locator('#quiz-view:not([hidden])').waitFor();
  const attemptDeadline = await page.evaluate(async () => {
    const saved = JSON.parse(sessionStorage.getItem(`studyspace_active_quiz_${sessionStorage.getItem('studyspace_student_session').slice(-12)}`));
    const token = sessionStorage.getItem('studyspace_student_session');
    return fetch(`/api/attempts/${saved.id}`, { headers: { Authorization: `Bearer ${token}` } }).then(response => response.json()).then(result => result.deadline);
  });
  await page.evaluate(deadlineMs => { window.__baseNow = Date.now.bind(Date); Date.now = () => deadlineMs + 1000; }, attemptDeadline);
  await page.locator('.quiz-result').waitFor();
  assert(true, 'quiz auto-submits when its server deadline has passed');
  assert(errors.length === 0, `no browser errors (${errors.join('; ')})`);
  const demoLogin = await fetch(`${base}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'demo_focus_student', password: demoPassword }) });
  assert(demoLogin.ok, 'seeded demo student can sign in');
  const demo = await demoLogin.json();
  const demoPage = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await demoPage.goto(base);
  await demoPage.evaluate(token => sessionStorage.setItem('studyspace_student_session', token), demo.token);
  await demoPage.goto(`${base}/student.html`);
  await demoPage.locator('#study-time-chart .focus-chart').waitFor();
  await demoPage.locator('#study-time-preview').screenshot({ path: path.join(evidence, 'phase4-demo-focus-chart.png') });
  assert(await demoPage.locator('#study-time-chart .focus-chart-day').count() === 7, 'demo account shows the seeded current-week focus chart');
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => {
  try {
    if (browser) await browser.close();
    cleanupDatabase();
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
});
