const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.STUDYSPACE_BASE_URL || 'http://localhost:8080';
const accounts = [
  ['STUDENT', 'studyspace_student_session', '/student.html', process.env.STUDYSPACE_STUDENT_USERNAME, process.env.STUDYSPACE_STUDENT_PASSWORD],
  ['TEACHER', 'studyspace_teacher_session', '/teacher.html', process.env.STUDYSPACE_TEACHER_USERNAME, process.env.STUDYSPACE_TEACHER_PASSWORD],
  ['ADMIN', 'studyspace_admin_session', '/admin.html', 'admin', process.env.STUDYSPACE_ADMIN_PASSWORD],
];

async function login(username, password) {
  const response = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const result = await response.json();
  if (!response.ok) throw new Error(`Đăng nhập ${username} lỗi ${response.status}: ${result.error}`);
  return result;
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const evidence = path.join(__dirname, 'test-evidence');
  fs.mkdirSync(evidence, { recursive: true });
  try {
    for (const [role, key, destination, username, password] of accounts) {
      if (!username || !password) throw new Error(`Thiếu thông tin tài khoản test ${role}.`);
      const session = await login(username, password);
      if (session.account.role !== role) throw new Error(`${username} có role ${session.account.role}, cần ${role}.`);

      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      await context.addInitScript(({ storageKey, token, activeRole }) => {
        sessionStorage.setItem(storageKey, token);
        sessionStorage.setItem('studyspace_active_role', activeRole);
      }, { storageKey: key, token: session.token, activeRole: role });
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(`${base}${destination}`, { waitUntil: 'domcontentloaded' });
      if (role === 'STUDENT') await page.waitForFunction(() => document.querySelector('#username-label')?.textContent !== 'Học viên', null, { timeout: 15000 });
      if (role === 'TEACHER') await page.locator('#workspace:not([hidden])').waitFor({ timeout: 15000 });
      if (role === 'ADMIN') await page.locator('#workspace:not([hidden])').waitFor({ timeout: 15000 });

      await page.goto(`${base}/`, { waitUntil: 'domcontentloaded' });
      await page.waitForURL(url => url.pathname === destination, { timeout: 10000 });
      if (role === 'STUDENT') await page.waitForFunction(() => document.querySelector('#completed-count')?.textContent !== '—', null, { timeout: 15000 });
      await page.evaluate(() => document.getAnimations().forEach(animation => { try { animation.finish(); } catch {} }));
      await page.screenshot({ path: path.join(evidence, `home-routing-${role.toLowerCase()}.png`), fullPage: true });
      if (errors.length) throw new Error(`${role} có lỗi JavaScript: ${errors.join('; ')}`);
      console.log(`PASS ${role}: mở / chuyển thẳng tới ${destination}`);

      if (role === 'STUDENT') {
        if (await page.getByRole('button', { name: 'Trang chủ', exact: true }).count()) throw new Error('Menu học viên vẫn còn mục Trang chủ.');
        await page.locator('#courses-nav').click();
        await page.locator('#home-button').click();
        if (new URL(page.url()).pathname !== '/student.html') throw new Error('Nút Trang chủ học viên đã rời khỏi dashboard.');
        await page.locator('#home-view').waitFor({ state: 'visible' });
        console.log('PASS STUDENT: không còn menu Trang chủ; Tổng quan quay về màn chính');
      }
      await fetch(`${base}/api/auth/logout`, { method: 'POST', headers: { Authorization: `Bearer ${session.token}` } });
      await context.close();
    }

    const guest = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    await guest.goto(`${base}/`, { waitUntil: 'domcontentloaded' });
    await guest.getByRole('heading', { name: /Biết mình yếu chương nào/ }).waitFor();
    console.log('PASS GUEST: chưa đăng nhập vẫn thấy trang giới thiệu');
    await guest.screenshot({ path: path.join(evidence, 'home-routing-guest.png'), fullPage: true });
  } finally {
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
