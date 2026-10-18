import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const output = path.resolve('docs/reference/studyspace');
const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
const errors = [];
const fixtures = { courses: [{ id: 1, title: 'Lập trình Kotlin', description: 'Cú pháp, kiểu dữ liệu và tư duy lập trình.', published: true }], chapters: [{ id: 2, course_id: 1, title: 'Cú pháp cơ bản', position: 1 }], lessons: [{ id: 3, chapter_id: 2, title: 'Biến và null safety', position: 1, material_url: '' }], questions: [{ id: 4, chapter_id: 2, prompt: 'Từ khóa khai báo biến không gán lại?', option_a: 'val', option_b: 'var', option_c: 'fun', option_d: 'class', correct_option: 0, difficulty: 1, active: true }] };

for (const viewport of [{ name: 'desktop-1440x900', width: 1440, height: 900 }, { name: 'mobile-390x844', width: 390, height: 844 }]) {
  const page = await browser.newPage({ viewport: { width: viewport.width, height: viewport.height }, deviceScaleFactor: 1 });
  page.on('pageerror', error => errors.push(`${viewport.name}: ${error.message}`));
  let content = { courses: [], chapters: [], lessons: [], questions: [] };
  await page.addInitScript(() => sessionStorage.setItem('studyspace_teacher_session', 'ui-test-session'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/auth/me') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 1, username: 'teacher-demo', displayName: 'Giảng viên', role: 'TEACHER' }) });
    if (url.pathname === '/api/teacher/content') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(content) });
    if (url.pathname.startsWith('/api/teacher/content/')) return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    return route.fulfill({ status: 200, contentType: 'application/json', body: '{"ok":true}' });
  });
  await page.goto('http://localhost:8080/teacher.html', { waitUntil: 'networkidle' });
  await page.locator('#workspace').waitFor({ state: 'visible' });
  if (await page.locator('#course-editor[open],#chapter-editor[open],#lesson-editor[open],#question-editor[open]').count()) throw Error(`An editor is open by default at ${viewport.name}`);
  if (!(await page.locator('#course-list').innerText()).includes('Chưa có khóa học')) throw Error('Course empty state missing');
  if (await page.locator('.empty-state button').count()) throw Error('Duplicate add action found in the empty state');
  const iconLayout = await page.evaluate(() => {
    const stat = document.querySelector('.stat-icon svg').getBoundingClientRect();
    const nav = document.querySelector('.teacher-sidebar>a svg').getBoundingClientRect();
    const statParent = document.querySelector('.stat-icon').getBoundingClientRect();
    const navParent = document.querySelector('.teacher-sidebar>a span').getBoundingClientRect();
    return { statWidth: stat.width, statHeight: stat.height, navWidth: nav.width, navHeight: nav.height, statCentered: Math.abs(stat.x + stat.width / 2 - (statParent.x + statParent.width / 2)) < 1 && Math.abs(stat.y + stat.height / 2 - (statParent.y + statParent.height / 2)) < 1, navCentered: Math.abs(nav.x + nav.width / 2 - (navParent.x + navParent.width / 2)) < 1 && Math.abs(nav.y + nav.height / 2 - (navParent.y + navParent.height / 2)) < 1, badgeBorder: getComputedStyle(document.querySelector('.section-count')).borderTopWidth, eyebrowClip: getComputedStyle(document.querySelector('.teacher-welcome .eyebrow')).clipPath };
  });
  if (iconLayout.statWidth !== iconLayout.navWidth || iconLayout.statHeight !== iconLayout.navHeight || !iconLayout.statCentered || !iconLayout.navCentered) throw Error(`Teacher SVG icons are not aligned consistently: ${JSON.stringify(iconLayout)}`);
  if (iconLayout.badgeBorder === '0px' || iconLayout.eyebrowClip !== 'none') throw Error(`Teacher badge styling is clipped: ${JSON.stringify(iconLayout)}`);
  await page.screenshot({ path: path.join(output, viewport.name, 'teacher-viewport.png') });
  await page.screenshot({ path: path.join(output, viewport.name, 'teacher-empty.png'), fullPage: true });
  await page.locator('#course-editor summary').click();
  if (!(await page.locator('#course-editor').evaluate(el => el.open))) throw Error('Course editor did not open');
  await page.locator('#course-form [name=title]').fill('Kiểm thử giao diện');
  await page.locator('#course-form [name=description]').fill('Nội dung thử nghiệm form giảng viên.');
  await page.locator('#course-form .primary-action').click();
  await page.getByText('Đã lưu nội dung.').waitFor();
  if (await page.locator('#course-editor').evaluate(el => el.open)) throw Error('Course editor did not close after save');
  content = fixtures;
  await page.locator('#refresh-button').count().catch(() => {});
  await page.reload({ waitUntil: 'networkidle' });
  await page.locator('#workspace').waitFor({ state: 'visible' });
  await page.locator('#course-list').getByRole('button', { name: 'Sửa' }).click();
  if (!(await page.locator('#course-editor').evaluate(el => el.open))) throw Error('Edit did not open the course editor');
  if (await page.locator('#course-form [name=title]').inputValue() !== 'Lập trình Kotlin') throw Error('Edit did not populate existing values');
  const dimensions = await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth }));
  if (dimensions.scrollWidth > dimensions.width) throw Error(`Horizontal overflow at ${viewport.name}: ${JSON.stringify(dimensions)}`);
  await page.close();
}
await browser.close();
if (errors.length) throw Error(errors.join('\n'));
console.log('Teacher portal checks passed at desktop and mobile: responsive layout, empty state, collapsed forms, create/save, edit prefill and no JS errors.');
