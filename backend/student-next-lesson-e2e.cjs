const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');

const base = process.env.STUDYSPACE_BASE_URL || 'http://127.0.0.1:8080';
const username = process.env.STUDYSPACE_DEMO_USER || 'demo_cohort_10';
const password = process.env.STUDYSPACE_DEMO_PASSWORD;
const check = (ok, message) => {
  if (!ok) throw new Error(`FAIL ${message}`);
  console.log(`PASS ${message}`);
};

async function main() {
  if (!password) throw new Error('Set STUDYSPACE_DEMO_PASSWORD from the ignored local credentials file.');
  const response = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  check(response.ok, 'fixed student demo account logs in');
  const token = (await response.json()).token;
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }]) {
      const page = await browser.newPage({ viewport });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.route(url => /\/api\/courses\/\d+$/.test(url.href), async route => {
        await new Promise(resolve => setTimeout(resolve, 1500));
        await route.continue();
      });
      await page.goto(base);
      await page.evaluate(value => sessionStorage.setItem('studyspace_student_session', value), token);
      await page.goto(`${base}/student.html`);
      await page.locator('#greeting-name').waitFor();
      await page.waitForFunction(() => document.querySelector('#continue-lesson-title')?.textContent === 'Đang tìm bài tiếp theo…');
      check(true, `${viewport.width}px shows a loading state, not a false empty state`);
      await page.waitForFunction(() => document.querySelector('#continue-lesson-title')?.textContent === 'Phân rã đến 3NF', null, { timeout: 20000 });
      check(!(await page.locator('#continue-next-lesson').isDisabled()), `${viewport.width}px enables the actual next lesson`);
      check(!(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)), `${viewport.width}px has no horizontal overflow`);
      check(errors.length === 0, `${viewport.width}px has no uncaught JavaScript errors`);
      await page.locator('#continue-next-lesson').click();
      await page.locator('#lesson-view:not([hidden])').waitFor({ timeout: 10000 });
      check((await page.locator('#lesson-content h1').innerText()) === 'Phân rã đến 3NF', `${viewport.width}px opens the selected lesson`);
      await page.close();
    }
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
