const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = 'http://localhost:8080';
const password = process.env.STUDYSPACE_ADMIN_PASSWORD;
const evidence = path.join(__dirname, 'test-evidence');

(async () => {
  if (!password) throw new Error('Set STUDYSPACE_ADMIN_PASSWORD from the local DPAPI secret before running this check.');
  fs.mkdirSync(evidence, { recursive: true });
  const login = await fetch(`${base}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password }) });
  if (!login.ok) throw new Error(`Admin login failed: HTTP ${login.status}`);
  const token = (await login.json()).token;
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const failures = [];
  try {
    for (const [name, viewport] of [['desktop', { width: 1440, height: 900 }], ['mobile', { width: 390, height: 844 }]]) {
      const context = await browser.newContext({ viewport });
      const page = await context.newPage();
      page.on('pageerror', error => failures.push(`${name}: ${error.message}`));
      await page.goto(base);
      await page.evaluate(value => sessionStorage.setItem('studyspace_admin_session', value), token);
      await page.goto(`${base}/admin.html`);
      await page.locator('#workspace:not([hidden])').waitFor({ timeout: 15000 });
      await page.locator('[data-tab="users"]').click();
      if (await page.locator('[data-tab="users"]').getAttribute('aria-current') !== 'page') throw new Error('Users tab does not show its active state.');
      await page.locator('#user-list .row').first().waitFor({ timeout: 10000 });
      const userCount = await page.locator('#user-list .row').count();
      await page.locator('#user-search').fill('khong-co-ket-qua-admin-filter');
      if (await page.locator('#user-list .row').count() !== 0) throw new Error('User search filter did not narrow results.');
      await page.locator('#user-search').fill('');
      await page.locator('#user-role-filter').selectOption('TEACHER');
      const teacherRows = await page.locator('#user-list .row').count();
      if (teacherRows > userCount) throw new Error('Role filter returned more rows than the unfiltered list.');
      await page.screenshot({ path: path.join(evidence, `admin-management-users-${name}.png`), fullPage: true });
      await page.locator('[data-tab="courses"]').click();
      if (await page.locator('[data-tab="courses"]').getAttribute('aria-current') !== 'page') throw new Error('Courses tab does not show its active state.');
      await page.locator('#course-filter').selectOption('draft');
      const draftCountText = await page.locator('#course-count').innerText();
      await page.screenshot({ path: path.join(evidence, `admin-management-courses-${name}.png`), fullPage: true });
      await page.locator('[data-tab="audit"]').click();
      await page.locator('#audit-list').waitFor();
      if (await page.locator('[data-tab="audit"]').getAttribute('aria-current') !== 'page') throw new Error('Audit tab does not show its active state.');
      await page.screenshot({ path: path.join(evidence, `admin-management-${name}.png`), fullPage: true });
      const horizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1);
      if (name === 'mobile' && horizontalOverflow) throw new Error('Admin page overflows horizontally at 390px.');
      console.log(`PASS ${name}: authenticated admin; user search/role filter; course status filter (${draftCountText}); audit view; ${userCount} initial users; ${teacherRows} teachers; overflow=${horizontalOverflow}`);
      await context.close();
    }
    if (failures.length) throw new Error(failures.join('\n'));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
