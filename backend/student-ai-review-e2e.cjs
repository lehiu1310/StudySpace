const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const { execFileSync } = require('node:child_process');
const path = require('node:path');

const base = process.env.STUDYSPACE_BASE_URL || 'http://localhost:8080';
const username = process.env.STUDYSPACE_DEMO_USER || 'demo_cohort_10';
const password = process.env.STUDYSPACE_DEMO_PASSWORD;
const expectedProvider = process.env.STUDYSPACE_EXPECT_AI_PROVIDER || 'gemini';
const out = path.join(__dirname, 'test-evidence');
let attemptId = 0;
let questionIds = [];
const assert = (ok, message) => {
  if (!ok) throw new Error(`FAIL ${message}`);
  console.log(`PASS ${message}`);
};

async function api(pathName, token, method = 'GET', body) {
  const response = await fetch(`${base}/api/${pathName}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const data = await response.json();
  if (!response.ok) throw new Error(`API ${pathName} returned HTTP ${response.status}`);
  return data;
}

function cleanup() {
  if (!attemptId) return;
  const ids = questionIds.join(',');
  const sql = [
    'BEGIN',
    `DELETE FROM attempt_answers WHERE attempt_id=${attemptId}`,
    `DELETE FROM quiz_attempts WHERE id=${attemptId}`,
    ids ? `DELETE FROM questions WHERE id IN (${ids}) AND active=FALSE` : 'SELECT 1',
    'COMMIT',
  ].join(';');
  execFileSync('docker', [
    'exec', 'backend-postgres-1', 'psql', '-U', 'studyspace', '-d', 'studyspace',
    '-v', 'ON_ERROR_STOP=1', '-c', sql,
  ], { stdio: 'ignore' });
  console.log(`CLEANUP removed temporary AI review attempt ${attemptId} and ${questionIds.length} generated question rows.`);
}

function correctOptionsForAttempt() {
  const rows = execFileSync('docker', [
    'exec', 'backend-postgres-1', 'psql', '-U', 'studyspace', '-d', 'studyspace',
    '-At', '-v', 'ON_ERROR_STOP=1', '-c',
    `SELECT question_id || ':' || correct_option FROM attempt_answers WHERE attempt_id=${attemptId} ORDER BY position`,
  ], { encoding: 'utf8' }).trim();
  return new Map(rows.split(/\r?\n/).filter(Boolean).map(row => row.split(':').map(Number)));
}

async function main() {
  if (!password) throw new Error('STUDYSPACE_DEMO_PASSWORD is required.');
  const login = await api('auth/login', null, 'POST', { username, password });
  assert(login.account?.role === 'STUDENT', 'fixed learner account authenticates');

  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const errors = [];
    let reviewPayload = null;
    page.on('pageerror', error => errors.push(error.message));
    page.on('response', async response => {
      if (response.url().endsWith('/api/ai/review-quiz') && response.request().method() === 'POST' && response.ok()) {
        reviewPayload = await response.json();
        attemptId = Number(reviewPayload.id);
        questionIds = (reviewPayload.questions || []).map(question => Number(question.question_id)).filter(Number.isSafeInteger);
      }
    });

    await page.goto(base);
    await page.evaluate(token => sessionStorage.setItem('studyspace_student_session', token), login.token);
    await page.goto(`${base}/student.html`);
    await page.locator('#greeting-name').waitFor();
    await page.locator('#ai-review-button:not([disabled])').waitFor();
    await page.locator('#ai-review-button').click();
    await page.locator('#quiz-view:not([hidden])').waitFor({ timeout: 45000 });
    await page.locator('#quiz-content .ai-provider-label').waitFor({ timeout: 45000 });

    const provider = (await page.locator('#quiz-content .ai-provider-label').innerText()).trim();
    const expectedLabel = expectedProvider === 'gemini' ? 'AI trực tiếp · Gemini' : 'Gợi ý offline';
    assert(provider === expectedLabel, `review quiz displays provider ${expectedLabel}`);
    assert(reviewPayload?.questions?.length === 5 && questionIds.length === 5, 'review quiz contains five questions');
    assert(reviewPayload.questions.every(question => !('correct_option' in question) && !('explanation' in question)), 'answer key and explanations are hidden before submit');
    assert(await page.locator('#quiz-content .quiz-question').count() === 5, 'student UI renders all five questions');
    assert(await page.locator('#quiz-content .quiz-question small').count() === 0, 'no answer explanation is exposed before submit');
    await page.screenshot({ path: path.join(out, 'demo-ai-review-quiz-live-desktop.png'), animations: 'disabled' });

    await page.setViewportSize({ width: 390, height: 844 });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), 'review quiz has no horizontal overflow at 390px');
    await page.screenshot({ path: path.join(out, 'demo-ai-review-quiz-live-mobile.png'), animations: 'disabled' });

    const answerKeys = correctOptionsForAttempt();
    assert(answerKeys.size === 5, 'database has the five generated answer keys for controlled scoring');
    for (let index = 0; index < reviewPayload.questions.length; index++) {
      const question = reviewPayload.questions[index];
      const correct = answerKeys.get(Number(question.question_id));
      const selected = index === 0 ? (correct + 1) % 4 : correct;
      await page.locator(`#quiz-question-${index} input[name="q-${question.question_id}"][value="${selected}"]`).check();
    }
    page.once('dialog', dialog => dialog.accept());
    await page.locator('#submit-quiz').click();
    await page.locator('#quiz-content .quiz-result').waitFor({ timeout: 20000 });
    assert((await page.locator('#quiz-content .quiz-result').innerText()).includes('4/5 câu'), 'server grading records exactly one intentionally wrong answer');
    assert(await page.locator('#quiz-content [data-explain]').count() === 1, 'result exposes AI explanation only for the wrong answer');
    await page.locator('#quiz-content [data-explain="0"]').click();
    await page.waitForFunction(() => document.querySelector('#ai-explain-0')?.textContent.includes('AI trực tiếp · Gemini'), null, { timeout: 45000 });
    assert((await page.locator('#ai-explain-0').innerText()).length > 50, 'Gemini explains the submitted wrong answer in Vietnamese');
    await page.screenshot({ path: path.join(out, 'demo-ai-review-quiz-live-result-mobile.png'), animations: 'disabled' });
    assert(errors.length === 0, `no browser errors (${errors.length})`);
    console.log(`Screenshots: ${path.join(out, 'demo-ai-review-quiz-live-{desktop,mobile}.png')} and ${path.join(out, 'demo-ai-review-quiz-live-result-mobile.png')}`);
  } finally {
    try { cleanup(); } finally { await browser.close(); }
  }
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
