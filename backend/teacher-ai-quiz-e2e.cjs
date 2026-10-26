const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.STUDYSPACE_BASE_URL || 'http://localhost:8080';
const password = process.env.STUDYSPACE_DEMO_PASSWORD;
const username = process.env.STUDYSPACE_DEMO_TEACHER || 'demo_cohort_teacher';
if (!password) throw new Error('STUDYSPACE_DEMO_PASSWORD must come from the ignored local credentials file.');
const expectedProvider = process.env.STUDYSPACE_EXPECT_AI_PROVIDER || 'offline';
const liveRun = expectedProvider === 'gemini';
const output = path.join(__dirname, 'test-evidence', `demo-ai-teacher-quiz-${liveRun ? 'live' : 'fixed'}-desktop.png`);
const mobileOutput = path.join(__dirname, 'test-evidence', `demo-ai-teacher-quiz-${liveRun ? 'live' : 'fixed'}-mobile.png`);
const assert = (ok, label) => { if (!ok) throw new Error(`FAIL ${label}`); console.log(`PASS ${label}`); };

async function main() {
  const login = await fetch(`${base}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) });
  assert(login.ok, 'fixed teacher demo account authenticates');
  const token = (await login.json()).token;
  const response = await fetch(`${base}/api/teacher/content`, { headers: { Authorization: `Bearer ${token}` } });
  const content = await response.json();
  assert(response.ok && content.chapters?.length > 0, 'teacher account has a real lesson chapter as source');

  const browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const pageErrors = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    await page.goto(base);
    await page.evaluate(value => sessionStorage.setItem('studyspace_teacher_session', value), token);
    await page.goto(`${base}/teacher.html#teacher-questions`);
    await page.waitForFunction(() => document.querySelectorAll('#ai-chapter-select option').length > 1);
    await page.locator('#ai-chapter-select').selectOption(String(content.chapters[0].id));
    await page.locator('#ai-question-count').selectOption('5');
    await page.locator('#ai-generate-questions').click();
    await page.waitForFunction(() => document.querySelector('.teacher-ai-draft') || document.querySelector('.teacher-ai-error'), null, { timeout: 32000 });
    const uiError = await page.locator('.teacher-ai-error').innerText().catch(() => '');
    if (uiError) throw new Error(`Teacher AI quiz UI error: ${uiError}`);
    await page.locator('.teacher-ai-draft').nth(4).waitFor();
    const result = page.locator('#teacher-ai-results');
    const label = await result.locator('.teacher-ai-result-head span').innerText();
    const questions = await result.locator('.teacher-ai-draft b').allTextContents();
    const unique = new Set(questions.map(question => question.trim().toLocaleLowerCase('vi')));
    assert(questions.length === 5 && unique.size === 5, 'UI renders five distinct draft questions');
    const expectedLabel = liveRun ? 'AI trực tiếp · Gemini' : 'Gợi ý offline';
    assert(label.includes(expectedLabel), `UI shows expected provider label: ${expectedLabel}`);
    assert(await result.locator('.teacher-ai-draft ol li').count() === 20, 'five drafts have four choices each');
    assert(await result.locator('.teacher-ai-draft p strong').count() === 5, 'each draft includes a correct-answer explanation');
    assert((await result.innerText()).includes('Chưa lưu vào ngân hàng'), 'generated questions remain drafts until teacher review');
    await page.screenshot({ path: output, animations: 'disabled' });
    assert(pageErrors.length === 0, `no uncaught UI errors${pageErrors.length ? `: ${pageErrors.join('; ')}` : ''}`);
    console.log(`Screenshot: ${output}`);

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${base}/teacher.html#teacher-questions`);
    await page.waitForFunction(() => document.querySelectorAll('#ai-chapter-select option').length > 1);
    await page.locator('#ai-chapter-select').selectOption(String(content.chapters[0].id));
    await page.locator('#ai-question-count').selectOption('5');
    await page.locator('#ai-generate-questions').click();
    await page.waitForFunction(() => document.querySelector('.teacher-ai-draft') || document.querySelector('.teacher-ai-error'), null, { timeout: 32000 });
    const mobileError = await page.locator('.teacher-ai-error').innerText().catch(() => '');
    if (mobileError) throw new Error(`Teacher AI quiz mobile UI error: ${mobileError}`);
    await page.locator('.teacher-ai-draft').nth(4).waitFor();
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), 'teacher quiz UI has no horizontal overflow at 390px');
    await page.screenshot({ path: mobileOutput, animations: 'disabled' });
    assert(pageErrors.length === 0, `mobile view has no uncaught UI errors${pageErrors.length ? `: ${pageErrors.join('; ')}` : ''}`);
    console.log(`Screenshot: ${mobileOutput}`);
  } finally {
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
