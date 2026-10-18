import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const sharp = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const root = path.resolve('docs/reference');
const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
const viewports = [{ name: 'desktop-1440x900', width: 1440, height: 900 }, { name: 'mobile-390x844', width: 390, height: 844 }];
const errors = [];
for (const viewport of viewports) {
  const page = await browser.newPage({ viewport: { width: viewport.width, height: viewport.height }, deviceScaleFactor: 1 });
  page.on('pageerror', error => errors.push(`${viewport.name}: ${error.message}`));
  await page.goto('http://localhost:8080/login.html', { waitUntil: 'networkidle' });
  await page.screenshot({ path: path.join(root, 'studyspace', viewport.name, 'login.png'), fullPage: true });
  const size = await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth }));
  if (size.width !== viewport.width || size.scrollWidth > viewport.width) throw Error(`Login overflow at ${viewport.name}: ${JSON.stringify(size)}`);
  await page.getByRole('button', { name: 'Đăng ký học viên' }).click();
  if (!(await page.getByRole('button', { name: /Tạo tài khoản/ }).isVisible())) throw Error('Register state did not open');
  await page.getByRole('button', { name: 'Đăng nhập' }).last().click();
  await page.locator('#password-toggle').click();
  if (await page.locator('#password').getAttribute('type') !== 'text') throw Error('Password visibility toggle did not work');
  await page.locator('#password-toggle').click();
  await page.close();
}
const staff = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await staff.goto('http://localhost:8080/login.html?staff=1', { waitUntil: 'networkidle' });
if (await staff.locator('#switch-mode').isVisible()) throw Error('Staff page still shows student registration toggle');
await staff.screenshot({ path: path.join(root, 'studyspace', 'desktop-1440x900', 'staff-login.png'), fullPage: true });
const invalid = await browser.newPage({ viewport: { width: 900, height: 760 } });
await invalid.goto('http://localhost:8080/login.html', { waitUntil: 'networkidle' });
await invalid.locator('#username').fill('nobody-account');
await invalid.locator('#password').fill('invalid-pass-123');
await invalid.locator('#submit-button').click();
await invalid.locator('#form-error').waitFor({ state: 'visible', timeout: 10000 });
const feedback = await invalid.locator('#form-error').innerText();
if (!feedback.includes('Tên đăng nhập') || !feedback.includes('mật khẩu')) throw Error(`Unfriendly login feedback: ${feedback}`);
await invalid.close();
for (const [role, destination] of [['STUDENT', '/login.html'], ['TEACHER', '/teacher.html'], ['ADMIN', '/admin.html']]) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let body = {};
    if (url.pathname === '/api/auth/login' || url.pathname === '/api/auth/register') body = { token: 'test-session-token', account: { id: 7, username: 'test-user', displayName: 'Test User', role } };
    else if (url.pathname === '/api/auth/me') body = { id: 7, username: 'test-user', displayName: 'Test User', role };
    else if (url.pathname === '/api/teacher/content' || url.pathname === '/api/admin/content') body = { courses: [], lessons: [], questions: [], chapters: [] };
    else if (url.pathname === '/api/courses/1') body = { id: 1, title: 'Kotlin nền tảng', description: 'Lộ trình học thử', chapters: [{ id: 11, title: 'Cú pháp cơ bản', question_count: 1, lessons: [{ id: 101, title: 'Biến và kiểu dữ liệu', content: 'Tìm hiểu val, var và kiểu dữ liệu.', completed: true, locked: false }, { id: 102, title: 'Null safety', content: 'Phân biệt kiểu nullable.', completed: false, locked: false }] }] };
    else if (url.pathname === '/api/courses') body = [{ id: 1, title: 'Kotlin nền tảng', description: 'Lộ trình học thử', lesson_count: 2, completed_count: 1 }];
    else if (url.pathname === '/api/progress') body = { history: [{ id: 1, chapter_title: 'Cú pháp cơ bản', submitted_at: Date.now(), score: 4, total: 5 }], mastery: [], lessonActivity: [] };
    else if (url.pathname === '/api/lessons/102/complete') body = { ok: true };
    else if (url.pathname === '/api/chapters/11/attempts') body = { id: 8, chapter_id: 11, total: 1, questions: [{ question_id: 9, prompt: 'Từ khóa khai báo biến không gán lại?', option_a: 'val', option_b: 'var', option_c: 'fun', option_d: 'class', selected_option: null }] };
    else if (url.pathname === '/api/attempts/8/answers') body = { ok: true };
    else if (url.pathname === '/api/attempts/8/submit') body = { id: 8, total: 1, score: 1, questions: [{ question_id: 9, prompt: 'Từ khóa khai báo biến không gán lại?', option_a: 'val', option_b: 'var', option_c: 'fun', option_d: 'class', selected_option: 0, correct_option: 0, explanation: 'val không thể gán lại.' }] };
    else if (url.pathname === '/api/admin/users' || url.pathname === '/api/admin/reports') body = url.pathname.endsWith('/users') ? [] : { students: [], questions: [] };
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto('http://localhost:8080/login.html', { waitUntil: 'domcontentloaded' });
  if (role === 'STUDENT') await page.getByRole('button', { name: 'Đăng ký học viên' }).click();
  await page.locator('#username').fill('test-user');
  await page.locator('#password').fill('test-password');
  await page.locator('#submit-button').click();
  if (role === 'STUDENT') {
    await page.waitForURL('**/student.html');
    await page.getByRole('heading', { name: 'Môn học' }).waitFor();
    if (!(await page.locator('#course-list').innerText()).includes('Kotlin nền tảng')) throw Error('Student dashboard course list missing');
    await page.screenshot({ path: path.join(root, 'studyspace', 'desktop-1440x900', 'student-dashboard.png'), fullPage: true });
    await page.getByRole('button', { name: /Kotlin nền tảng/ }).click();
    await page.getByRole('button', { name: 'Đánh dấu học xong' }).click();
    await page.getByRole('button', { name: /Làm quiz/ }).click();
    await page.locator('input[name="q-9"][value="0"]').check();
    await page.getByRole('button', { name: 'Nộp bài và xem kết quả' }).click();
    await page.getByText('Điểm: 1/1 câu (100%)').waitFor();
  } else {
    await page.waitForURL(`**${destination}`);
    await page.locator('#workspace').waitFor({ state: 'visible' });
    if (await page.locator('#workspace').isHidden()) throw Error(`${role} did not reach its dashboard`);
  }
  await page.close();
}
for (const viewport of viewports) {
  const name = viewport.name;
  const current = path.join(root, 'studyspace', name, 'login.png');
  const reference = path.join(root, 'vitaltrack', name, 'login.png');
  await sharp({ create: { width: viewport.width * 2, height: viewport.height, channels: 4, background: '#e2e8f0' } })
    .composite([{ input: await sharp(reference).resize(viewport.width, viewport.height, { fit: 'cover' }).png().toBuffer(), left: 0, top: 0 }, { input: await sharp(current).resize(viewport.width, viewport.height, { fit: 'cover' }).png().toBuffer(), left: viewport.width, top: 0 }])
    .png().toFile(path.join(root, `login-comparison-${name}.png`));
}
await browser.close();
if (errors.length) throw Error(errors.join('\n'));
console.log(`A2 UI checks passed; captured desktop/mobile login and student dashboard, staff login, registration, invalid-login feedback, student lesson/quiz flow and role routing for STUDENT, TEACHER and ADMIN. ${viewports.length} viewports, no JS errors.`);
