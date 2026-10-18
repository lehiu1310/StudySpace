import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root = path.resolve('docs/reference');
const referenceBase = 'http://127.0.0.1:5173';
const studyBase = 'http://localhost:8080';
const viewports = [
  { name: 'desktop-1440x900', width: 1440, height: 900 },
  { name: 'mobile-390x844', width: 390, height: 844 },
];

const routes = [
  ['landing', '/', 'user'], ['guide', '/guide', 'user'],
  ['login', '/login', 'user'], ['register', '/register', 'user'],
  ['forgot-password', '/forgot-password', 'user'],
  ['dashboard', '/dashboard', 'user'], ['health', '/health', 'user'],
  ['ai-diagnostics', '/ai-diagnostics', 'user'], ['devices', '/devices', 'user'],
  ['analytics', '/analytics', 'user'], ['goals', '/goals', 'user'],
  ['reminders', '/reminders', 'user'], ['profile', '/profile', 'user'],
  ['admin-dashboard', '/admin', 'admin'], ['admin-users', '/admin/users', 'admin'],
  ['admin-telemetry', '/admin/telemetry', 'admin'], ['admin-ai-reviews', '/admin/ai-reviews', 'admin'],
  ['admin-devices', '/admin/devices', 'admin'], ['admin-audit-logs', '/admin/audit-logs', 'admin'],
  ['admin-logs', '/admin/logs', 'admin'], ['admin-settings', '/admin/settings', 'admin'],
];

const user = { id: 1, full_name: 'Nguyễn Minh An', email: 'demo@example.test', role: 'user' };
const admin = { id: 2, full_name: 'Quản trị viên', email: 'admin@example.test', role: 'admin' };
const current = {
  weight: { value: 62.4 }, blood_pressure: { value: 118, diastolic: 76 },
  heart_rate: { value: 72 }, blood_sugar: { value: 5.2 },
};
const records = Array.from({ length: 8 }, (_, i) => ({
  id: i + 1, recorded_at: new Date(Date.now() - i * 86400000).toISOString(),
  weight: 62 + i * 0.1, systolic: 118 + (i % 3), diastolic: 76, heart_rate: 70 + i,
  blood_sugar: 5.1, notes: '',
}));

async function mockApi(page, role) {
  await page.addInitScript((r) => localStorage.setItem('vitaltrack_token', `visual-reference-${r}`), role);
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (!pathname.startsWith('/api/')) return route.continue();
    let data = [];
    if (pathname.endsWith('/auth/me')) data = role === 'admin' ? admin : user;
    else if (pathname.endsWith('/health/latest')) data = { latest: records[0], previous_by_metric: { weight: 62.5, heart_rate: 71 }, current };
    else if (pathname.endsWith('/health')) data = records;
    else if (pathname.endsWith('/admin/dashboard')) data = { onlineSessionsCount: 3, totalUsers: 128, activeUsers: 96, totalRecords: 842, systemHealth: 'Ổn định' };
    else if (pathname.endsWith('/admin/statistics')) data = {
      summary: { totalUsers: 128, activeUsers: 96, newUsersInRange: 18, totalHealthRecords: 842, recordsInRange: 264, overallAvgWeight: 62.4, overallAvgSystolic: 118, overallAvgDiastolic: 76, overallAvgHeartRate: 72 },
      timeSeries: Array.from({ length: 6 }, (_, i) => ({ period: `T${i + 1}/2026`, newUsersCount: i + 2, totalUsersCumulative: 90 + i * 6, recordsCount: 32 + i * 4, avgWeight: 62.4, minWeight: 61.2, maxWeight: 64.1, avgSystolic: 118, avgDiastolic: 76, minSystolic: 110, maxSystolic: 126, avgHeartRate: 72, minHeartRate: 60, maxHeartRate: 90 })),
      healthDistribution: [],
    };
    else if (pathname.includes('/admin/logs')) data = { logs: [], total: 0 };
    else if (pathname.includes('/admin/sessions')) data = { sessions: [], activeCount: 0 };
    else if (pathname.includes('/admin/audit-logs')) data = { logs: [], pagination: { page: 1, limit: 20, total: 0, totalPages: 1 }, stats: { total: 0, totalSuccess: 0, totalFailed: 0, totalWarning: 0 }, filters: { availableModules: [], availableActions: [], availableRoles: [], availableResourceTypes: [] } };
    else if (pathname.endsWith('/admin/settings')) data = { settings: {} };
    const body = { success: true, data, message: 'Dữ liệu tham chiếu' };
    await route.fulfill({ status: 200, contentType: 'application/json; charset=utf-8', body: JSON.stringify(body) });
  });
}

async function captureSite(browser, base, site, name, route, viewport, role) {
  const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  if (site === 'vitaltrack' && role) await mockApi(page, role);
  const errors = [];
  page.on('console', (message) => { if (message.type() === 'error') errors.push(`console: ${message.text()}`); });
  page.on('requestfailed', (request) => errors.push(`request: ${request.url()} ${request.failure()?.errorText || ''}`));
  page.on('response', (response) => { if (response.url().includes('.js') && response.headers()['content-type']?.includes('json')) errors.push(`module MIME: ${response.url()} ${response.status()}`); });
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto(`${base}${route}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(1600);
  const directory = path.join(root, site, viewport.name);
  await fs.mkdir(directory, { recursive: true });
  const filename = `${name}.png`;
  const renderedText = await page.locator('body').innerText().catch(() => '');
  if (renderedText.trim().length < 25) throw new Error(`Blank reference capture for ${site}/${name} at ${viewport.name}: title=${await page.title()}, url=${page.url()}, body=${JSON.stringify(renderedText)}, root=${await page.locator('#root').innerHTML().catch(() => '')}, errors=${JSON.stringify(errors)}`);
  await page.screenshot({ path: path.join(directory, filename) });
  const finalUrl = page.url();
  await context.close();
  return { site, name, viewport: viewport.name, finalUrl, errors };
}

const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
const results = [];
for (const viewport of viewports) {
  for (const [name, route, role] of routes) {
    results.push(await captureSite(browser, referenceBase, 'vitaltrack', name, route, viewport, role));
  }
  for (const [name, route] of [['admin-login', '/', null], ['teacher-login', '/teacher.html', null]]) {
    results.push(await captureSite(browser, studyBase, 'studyspace', name, route, viewport, null));
  }
}
await browser.close();
await fs.writeFile(path.join(root, 'capture-results.json'), JSON.stringify(results, null, 2));
console.log(`Captured ${results.length} viewport screenshots. Page errors: ${results.filter((result) => result.errors.length).length}.`);
