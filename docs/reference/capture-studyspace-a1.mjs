import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const sharp = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const output = path.resolve('docs/reference');
const pairs = [
  { name: 'desktop-1440x900', width: 1440, height: 900 },
  { name: 'mobile-390x844', width: 390, height: 844 },
];
const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
const errors = [];
for (const viewport of pairs) {
  const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('http://localhost:8080/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(1500);
  const text = await page.locator('body').innerText();
  if (!text.includes('Biết mình yếu chương nào.')) throw new Error(`Landing did not render at ${viewport.name}`);
  const target = path.join(output, 'studyspace', viewport.name);
  await fs.mkdir(target, { recursive: true });
  const currentPath = path.join(target, 'landing.png');
  await page.screenshot({ path: currentPath });
  await page.screenshot({ path: path.join(target, 'landing-full.png'), fullPage: true });
  const dimensions = await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth }));
  if (dimensions.width !== viewport.width || dimensions.scrollWidth > viewport.width) throw new Error(`Horizontal overflow at ${viewport.name}: ${JSON.stringify(dimensions)}`);
  if (viewport.width < 700) {
    await page.locator('.mobile-menu summary').click();
    if (!(await page.locator('.mobile-menu-panel').isVisible())) throw new Error('Mobile navigation did not open');
    await page.locator('.mobile-menu-panel a[href="#features"]').click();
    await page.waitForFunction(() => location.hash === '#features');
  }
  const referencePath = path.join(output, 'vitaltrack', viewport.name, 'landing.png');
  const reference = await sharp(referencePath).png().toBuffer();
  const current = await sharp(currentPath).png().toBuffer();
  await sharp({ create: { width: viewport.width * 2, height: viewport.height, channels: 4, background: '#e2e8f0' } })
    .composite([{ input: reference, left: 0, top: 0 }, { input: current, left: viewport.width, top: 0 }])
    .png().toFile(path.join(output, `landing-comparison-${viewport.name}.png`));
  await context.close();
}
await browser.close();
console.log(JSON.stringify({ viewports: pairs.map(({ name }) => name), pageErrors: errors }, null, 2));
