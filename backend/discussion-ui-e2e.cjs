const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const base = 'http://localhost:8080';
const evidence = path.join(__dirname, 'docs', 'test-evidence');
const codes = (process.env.DISCUSSION_CLASS_CODES || 'XL9LUMTG,LBQWVT5E').split(',').map(value => value.trim()).filter(Boolean);
const assert = (condition, message) => {
  if (!condition) throw new Error(message);
  console.log(`PASS ${message}`);
};
let account = null;
let browser = null;
let username = '';

function cleanup() {
  if (browser) return browser.close();
}

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
  console.log('CLEANUP: removed temporary discussion learner and enrollments.');
}

(async () => {
  if (!process.env.STUDYSPACE_DB_PASSWORD) throw new Error('STUDYSPACE_DB_PASSWORD is required for safe test cleanup.');
  fs.mkdirSync(evidence, { recursive: true });
  const stamp = Date.now();
  username = `discussion_e2e_${stamp}`;
  const registration = await fetch(`${base}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: `Discussion-${stamp}!` }),
  });
  assert(registration.ok, 'temporary learner registered');
  account = await registration.json();
  const profile = await fetch(`${base}/api/auth/me`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${account.token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName: 'Học viên kiểm thử' }),
  });
  assert(profile.ok, 'test profile uses a neutral display name');
  for (const code of codes) {
    const joined = await fetch(`${base}/api/classes/join`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${account.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    });
    assert(joined.ok, `learner joined class ${code}`);
  }
  console.log(`TEST_USERNAME=${username}`);

  browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));

  await page.goto(base);
  await page.evaluate(token => sessionStorage.setItem('studyspace_student_session', token), account.token);
  await page.goto(`${base}/student.html#classes`);
  await page.locator('#classes-view:not([hidden])').waitFor();
  assert(await page.locator('.student-class-card').count() === 1, 'classes screen uses one shared discussion frame');
  const classSelect = page.locator('#discussion-class-select');
  assert(await classSelect.locator('option').count() >= 2, 'shared discussion frame has a selector for the learner classes');
  assert(!(await page.locator('.class-teacher-name').innerText()).includes('@'), 'teacher display name does not expose a role-style username');
  await page.waitForFunction(() => { const node=document.querySelector('.student-discussion-body'); return node && !node.querySelector('.loading-line'); });
  assert(await page.locator('.student-discussion-body .loading-line').count() === 0, 'first visit automatically loads the selected discussion');
  const firstClass = await classSelect.inputValue();
  const secondClass = await classSelect.locator('option').nth(1).getAttribute('value');
  await classSelect.selectOption(secondClass);
  assert(await classSelect.inputValue() !== firstClass, 'class selector changes the selected class');
  await page.waitForFunction(() => { const node=document.querySelector('.student-discussion-body'); return node && !node.querySelector('.loading-line'); });
  const selectedClass = (await (await fetch(`${base}/api/classes/mine`, { headers: { Authorization: `Bearer ${account.token}` } })).json()).find(item => String(item.id) === secondClass);
  const discussion = await (await fetch(`${base}/api/courses/${selectedClass.course_id}/discussion`, { headers: { Authorization: `Bearer ${account.token}` } })).json();
  const otherOnlineCount = discussion.online.filter(person => person.username !== username).length;
  assert(await page.locator('.discussion-online').count() === (otherOnlineCount > 0 ? 1 : 0), 'online indicator excludes the signed-in learner');

  await page.reload();
  await page.locator('#classes-view:not([hidden])').waitFor();
  await page.waitForFunction(() => { const node=document.querySelector('.student-discussion-body'); return node && !node.querySelector('.loading-line'); });
  assert(true, 'reload on #classes restores the screen and loads discussions');

  await context.setOffline(true);
  await page.locator('.student-class-card').locator('[data-refresh-discussion]').click();
  const retry = page.locator('.student-class-card').locator('[data-retry-discussion]');
  await retry.waitFor({ timeout: 5000 });
  assert(await retry.isVisible(), 'offline request leaves loading and shows a retry action');
  await context.setOffline(false);
  await retry.click();
  await page.locator('.student-class-card').locator('.discussion-messages').waitFor();
  assert(true, 'retry after restoring the network loads discussion successfully');

  const testMessage = `Tin nhắn kiểm thử ${Date.now()}`;
  await page.locator('.student-discussion-form input[name="body"]').fill(testMessage);
  await page.locator('.student-discussion-form button[type="submit"]').click();
  await page.locator('.discussion-messages article').filter({ hasText: testMessage }).waitFor();
  assert(await page.locator('.discussion-messages article').filter({ hasText: testMessage }).locator('b').innerText() === 'Học viên kiểm thử', 'discussion shows the learner display name');
  assert(await page.locator('.discussion-messages article').filter({ hasText: testMessage }).locator('time').innerText().then(value => value.trim().length > 0), 'discussion shows the message time');
  await page.screenshot({ path: path.join(evidence, 'phase1-class-discussion-desktop.png'), animations: 'disabled' });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: path.join(evidence, 'phase1-class-discussion-mobile.png'), animations: 'disabled' });
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'mobile discussion has no horizontal overflow');
  assert(errors.length === 0, `no uncaught browser errors (${errors.join('; ')})`);
  await browser.close();
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
}).finally(async () => {
  try {
    await cleanup();
    cleanupDatabase();
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
});
