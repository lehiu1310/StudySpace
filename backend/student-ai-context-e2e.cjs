const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');

const base = process.env.STUDYSPACE_BASE_URL || 'http://localhost:8080';
const password = process.env.STUDYSPACE_DEMO_PASSWORD;
const username = process.env.STUDYSPACE_DEMO_USER || 'demo_cohort_10';
const screenshot = `${__dirname}/test-evidence/demo-ai-student-chat-live-context-desktop.png`;
const assert = (condition, message) => {
  if (!condition) throw new Error(`FAIL ${message}`);
  console.log(`PASS ${message}`);
};

async function api(path, token, method = 'GET', body) {
  const response = await fetch(`${base}/api/${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const data = await response.json();
  if (!response.ok) throw new Error(`API ${path} returned HTTP ${response.status}`);
  return data;
}

async function main() {
  if (!password) throw new Error('STUDYSPACE_DEMO_PASSWORD is required.');
  const login = await api('auth/login', null, 'POST', { username, password });
  const courses = await api('courses', login.token);
  const course = courses.find(item => item.title === 'Cơ sở dữ liệu · Lớp thực hành mẫu');
  assert(!!course, 'fixed student account can access the published demo course');

  const detail = await api(`courses/${course.id}`, login.token);
  const lesson = detail.chapters.flatMap(chapter => chapter.lessons || [])
    .find(item => !item.locked && String(item.content || '').trim().length > 80);
  assert(!!lesson, 'an unlocked lesson has substantive context text');

  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const errors = [];
    let payloadContextLength = -1;
    page.on('pageerror', error => errors.push(error.message));
    page.on('request', request => {
      if (request.url().endsWith('/api/ai/chat') && request.method() === 'POST') {
        try {
          payloadContextLength = String(JSON.parse(request.postData()).lessonContext || '').length;
        } catch {
          payloadContextLength = 0;
        }
      }
    });

    await page.goto(base);
    await page.evaluate(token => sessionStorage.setItem('studyspace_student_session', token), login.token);
    await page.goto(`${base}/student.html`);
    await page.locator('#greeting-name').waitFor();
    await page.locator('#courses-nav').click();
    await page.locator(`#course-list [data-course="${course.id}"]`).click();
    await page.locator('#course-view:not([hidden])').waitFor();
    await page.locator(`[data-open-lesson="${lesson.id}"]`).click();
    await page.locator('#lesson-view:not([hidden])').waitFor();
    assert((await page.locator('#lesson-content').innerText()).includes(lesson.title), 'student UI displays the selected contextual lesson');
    await page.locator('#lesson-ai-question').fill('Trong bài học này, hãy tóm tắt ngắn gọn ý chính và một khái niệm cần nhớ.');
    await page.locator('#lesson-ai-ask-button').click();
    await page.locator('#lesson-ai-answer .ai-provider-label').waitFor({ timeout: 30000 });

    const answer = await page.locator('#lesson-ai-answer').innerText();
    const provider = await page.locator('#lesson-ai-answer .ai-provider-label').innerText();
    assert(payloadContextLength === String(lesson.content).length, `chat request includes lesson context (${payloadContextLength}/${String(lesson.content).length} characters)`);
    assert(provider.includes('AI trực tiếp · Gemini') && !/ngữ cảnh bài học.*trống/i.test(answer), 'Gemini responds with the selected lesson context');
    assert(errors.length === 0, `no browser errors (${errors.length})`);
    await page.screenshot({ path: screenshot, animations: 'disabled' });
    console.log(`Screenshot: ${screenshot}`);
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
