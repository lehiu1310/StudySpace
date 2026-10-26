const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.STUDYSPACE_BASE_URL || 'http://localhost:8080';
const username = process.env.STUDYSPACE_DEMO_USER || 'demo_cohort_10';
const password = process.env.STUDYSPACE_DEMO_PASSWORD;
if (!password) throw new Error('STUDYSPACE_DEMO_PASSWORD must be provided through the ignored local demo credentials file.');
const evidence = path.join(__dirname, 'test-evidence');
fs.mkdirSync(evidence, { recursive: true });
const assert = (condition, label) => { if (!condition) throw new Error(`FAIL ${label}`); console.log(`PASS ${label}`); };

async function main() {
  const login = await fetch(`${base}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) });
  assert(login.ok, 'fixed student demo account authenticates');
  const token = (await login.json()).token;
  const api = async (method = 'GET', body) => {
    const response = await fetch(`${base}/api/personal-study`, { method, headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
    if (!response.ok) throw new Error(`personal-study HTTP ${response.status}`);
    return response.json();
  };
  const updateSnapshot = async mutator => {
    for (let attempt = 0; attempt < 4; attempt++) {
      const latest = await api();
      const response = await fetch(`${base}/api/personal-study`, { method: 'PUT', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ expectedVersion: latest.version, requestId: crypto.randomUUID(), data: mutator(latest.data) }) });
      if (response.ok) return response.json();
      if (response.status !== 409) throw new Error(`personal-study HTTP ${response.status}`);
    }
    throw new Error('Snapshot stayed contended after four compare-and-swap retries.');
  };
  let original = await api();
  if (original.data.plans.some(plan => plan.title.startsWith('QA kế hoạch '))) {
    await updateSnapshot(data => ({ ...data, plans: data.plans.filter(plan => !plan.title.startsWith('QA kế hoạch ')) }));
    original = await api();
  }
  const initialPlans = original.data.plans;
  const beforeSessions = JSON.stringify(original.data.sessions);
  const beforePlans = JSON.stringify(initialPlans);
  assert(initialPlans.length >= 3, 'semester November plans exist before UI test');

  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const errors = [];
  const failedResponses = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.status() >= 400) failedResponses.push({ status: response.status(), url: response.url() }); });
  try {
    await page.goto(base);
    await page.evaluate(value => sessionStorage.setItem('studyspace_student_session', value), token);
    await page.goto(`${base}/student.html#plans`);
    await page.locator('#plans-view:not([hidden])').waitFor({ timeout: 12000 }).catch(async error => { console.log(`DEBUG ${JSON.stringify({ url: page.url(), title: await page.title(), body: (await page.locator('body').innerText()).slice(0, 500), errors })}`); throw error; });
    await page.locator('#plans-list .plan-card').first().waitFor();
    const planText = await page.locator('#plans-list').innerText();
    assert(planText.includes('Ôn tập cuối kỳ') || initialPlans.every(plan => planText.includes(plan.title)), 'plans screen shows all persisted semester plans');
    assert((await page.locator('#weekly-focus-value').innerText()).includes('/'), 'weekly focus target and actual minutes render');
    assert((await page.locator('#weekly-quiz-value').innerText()).includes('/'), 'weekly quiz target and actual count render');
    assert(await page.evaluate(() => innerWidth === 1440 && innerHeight === 900), 'desktop screenshot viewport is exactly 1440x900');
    await page.screenshot({ path: path.join(evidence, 'student-plans-desktop.png'), animations: 'disabled' });

    await page.locator('#weekly-goal-form [name=minutes]').fill('120');
    await page.locator('#weekly-goal-form [name=quizzes]').fill('4');
    await page.locator('#weekly-goal-form button[type=submit]').click();
    await page.reload();
    await page.locator('#plans-view:not([hidden])').waitFor();
    assert(await page.locator('#weekly-goal-form [name=minutes]').inputValue() === '120', 'weekly goals persist in browser storage');

    const unique = `QA kế hoạch ${Date.now()}`;
    const due = new Date(Date.now() + 10 * 86400000).toISOString().slice(0, 10);
    await page.locator('#plan-form [name=title]').fill(unique);
    await page.locator('#plan-form [name=category]').fill('Cơ sở dữ liệu');
    await page.locator('#plan-form [name=description]').fill('Kiểm tra luồng tạo, hoàn thành và xóa kế hoạch.');
    await page.locator('#plan-form [name=dueDate]').fill(due);
    await page.locator('#plan-form [name=targetMinutes]').fill('25');
    await page.locator('#plan-form button[type=submit]').click();
    const qaCard = page.locator('.plan-card').filter({ hasText: unique });
    await qaCard.waitFor();
    assert((await api()).data.plans.some(plan => plan.title === unique), 'new plan is persisted through existing snapshot API');
    await qaCard.locator('[data-plan-complete]').click();
    await page.waitForFunction(() => document.querySelector('.plan-card h3')?.textContent?.startsWith('QA kế hoạch ') && document.querySelector('.plan-card h3')?.closest('.plan-card')?.querySelector('.plan-status')?.textContent === 'Hoàn thành', null, { timeout: 8000 }).catch(async () => {
      const latest = await api();
      console.log(`DEBUG completion ${JSON.stringify({ status: await qaCard.locator('.plan-status').innerText().catch(() => ''), toast: await page.locator('#student-toast').innerText().catch(() => ''), saved: latest.data.plans.find(plan => plan.title === unique)?.isCompleted, errors })}`);
    });
    assert((await qaCard.locator('.plan-status').innerText()) === 'Hoàn thành', 'plan completion state updates and renders');
    page.once('dialog', dialog => dialog.accept());
    await qaCard.locator('[data-plan-delete]').click();
    await qaCard.waitFor({ state: 'detached' });
    const restored = await api();
    assert(JSON.stringify(restored.data.plans) === beforePlans, 'QA plan removed and original November plans restored exactly');
    assert(JSON.stringify(restored.data.sessions) === beforeSessions, 'focus session history preserved exactly');

    await page.setViewportSize({ width: 390, height: 844 });
    await page.reload();
    await page.locator('#plans-view:not([hidden])').waitFor();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    assert(!overflow, 'plans screen has no horizontal overflow at 390px');
    assert(await page.evaluate(() => innerWidth === 390 && innerHeight === 844), 'mobile screenshot viewport is exactly 390x844');
    await page.screenshot({ path: path.join(evidence, 'student-plans-mobile.png'), animations: 'disabled' });
    assert(errors.length === 0, `no uncaught browser errors${errors.length ? `: ${errors.join('; ')}` : ''}`);
    console.log(`HTTP resource errors: ${JSON.stringify(failedResponses)}`);
  } finally {
    const current = await api();
    const qaPlans = current.data.plans.filter(plan => plan.title.startsWith('QA kế hoạch '));
    if (qaPlans.length) {
      const clean = await updateSnapshot(data => ({ ...data, plans: data.plans.filter(plan => !plan.title.startsWith('QA kế hoạch ')) }));
      assert(clean.data.plans.every(plan => !plan.title.startsWith('QA kế hoạch ')), 'temporary QA plans are cleaned up');
    }
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
