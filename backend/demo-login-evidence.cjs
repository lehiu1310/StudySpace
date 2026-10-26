const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.STUDYSPACE_BASE_URL || 'http://localhost:8080';
const output = path.join(__dirname, '..', 'docs', 'submission', 'screenshots');
const demoPassword = process.env.STUDYSPACE_DEMO_PASSWORD;
const adminPassword = process.env.STUDYSPACE_ADMIN_PASSWORD;

async function login(username, password) {
  const response = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) throw new Error(`${username}: login returned HTTP ${response.status}`);
  console.log(`PASS login ${username}`);
  return (await response.json()).token;
}

async function openSession(browser, role, token, pathName) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(base);
  await page.evaluate(({ key, value }) => sessionStorage.setItem(key, value), {
    key: `studyspace_${role}_session`, value: token,
  });
  await page.goto(`${base}${pathName}`);
  if (role === 'admin') await page.locator('#workspace:not([hidden])').waitFor({ timeout: 20000 });
  if (role === 'student') await page.locator('#greeting-name').waitFor({ timeout: 20000 });
  if (role === 'teacher') await page.locator('#workspace:not([hidden])').waitFor({ timeout: 20000 });
  if (errors.length) throw new Error(`${role} UI errors: ${errors.join('; ')}`);
  return page;
}

(async () => {
  if (!adminPassword || !demoPassword) throw new Error('Runtime admin and demo password environment variables are required.');
  fs.mkdirSync(output, { recursive: true });
  const adminToken = await login('admin', adminPassword);
  const health = await fetch(`${base}/api/ai/health`, { headers: { Authorization: `Bearer ${adminToken}` } });
  if (!health.ok) throw new Error(`Authenticated AI health check returned HTTP ${health.status}`);
  console.log(`PASS authenticated backend AI health HTTP ${health.status}`);
  const teacherToken = await login('demo_cohort_teacher', demoPassword);
  const studentToken = await login('demo_cohort_10', demoPassword);
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  try {
    const admin = await openSession(browser, 'admin', adminToken, '/admin.html');
    await admin.locator('[data-tab="reports"]').click();
    await admin.locator('#reports:not([hidden])').waitFor();
    await admin.waitForFunction(() => !!window.Chart?.getChart('score-chart'), null, { timeout: 20000 });
    await admin.locator('#reports').evaluate(element => element.scrollIntoView({ block: 'start' }));
    await admin.waitForTimeout(500);
    await admin.screenshot({ path: path.join(output, 'admin-chart-report-desktop.png') });
    console.log('PASS admin report rendered Chart.js and screenshot saved');

    const teacher = await openSession(browser, 'teacher', teacherToken, '/teacher.html#teacher-classes');
    const classRow = teacher.locator('.teacher-class-row').filter({ hasText: 'CSDL · Lớp thực hành 01' });
    await classRow.waitFor({ timeout: 20000 });
    await classRow.locator('[data-class-report]').click();
    await teacher.locator('#class-report:not([hidden])').waitFor({ timeout: 20000 });
    await teacher.waitForFunction(() => !!window.Chart?.getChart('class-chapter-chart') && !!window.Chart?.getChart('class-question-chart'), null, { timeout: 20000 });
    await teacher.locator('.class-heatmap tbody tr').first().waitFor();
    await teacher.locator('#class-report').evaluate(element => element.scrollIntoView({ block: 'start' }));
    await teacher.waitForTimeout(500);
    await teacher.screenshot({ path: path.join(output, 'teacher-class-report-desktop.png') });
    console.log('PASS teacher class report rendered both Chart.js graphs, heatmap and screenshot saved');

    const student = await openSession(browser, 'student', studentToken, '/student.html');
    await student.locator('#report-nav').click();
    await student.locator('#report-view:not([hidden])').waitFor({ timeout: 20000 });
    await student.locator('#report-content .report-panel').first().waitFor();
    await student.locator('#report-view').evaluate(element => element.scrollIntoView({ block: 'start' }));
    await student.waitForTimeout(500);
    await student.screenshot({ path: path.join(output, 'student-progress-report-desktop.png') });
    console.log('PASS student progress report rendered and screenshot saved');
    await Promise.all([admin.close(), teacher.close(), student.close()]);
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
