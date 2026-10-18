const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.STUDYSPACE_BASE_URL || 'http://localhost:8080';
const username = process.env.STUDYSPACE_TEACHER_USERNAME;
const password = process.env.STUDYSPACE_TEACHER_PASSWORD;

async function main() {
  if (!username || !password) throw new Error('Set STUDYSPACE_TEACHER_USERNAME and STUDYSPACE_TEACHER_PASSWORD.');
  const login = await fetch(`${base}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const session = await login.json();
  if (!login.ok || session.account?.role !== 'TEACHER') throw new Error('Teacher demo login failed.');
  const authHeaders = { Authorization: `Bearer ${session.token}` };
  const [teacherContentResponse, teacherClassesResponse] = await Promise.all([
    fetch(`${base}/api/teacher/content`, { headers: authHeaders }),
    fetch(`${base}/api/teacher/classes`, { headers: authHeaders }),
  ]);
  const teacherContent = await teacherContentResponse.json();
  const teacherClasses = await teacherClassesResponse.json();
  if (!teacherContentResponse.ok || !teacherClassesResponse.ok) throw new Error('Could not load teacher demo content/classes.');
  const courseIds = new Set(teacherContent.courses.map(course => Number(course.id)));
  const chapterIds = new Set(teacherContent.chapters.filter(chapter => courseIds.has(Number(chapter.course_id))).map(chapter => Number(chapter.id)));
  const ownedLessons = teacherContent.lessons.filter(lesson => chapterIds.has(Number(lesson.chapter_id)));
  const ownedQuestions = teacherContent.questions.filter(question => chapterIds.has(Number(question.chapter_id)));
  if (teacherContent.courses.length < 3 || ownedLessons.length < 15 || ownedQuestions.length < 30 || teacherClasses.length < 2 || teacherClasses.some(item => Number(item.student_count) < 20)) {
    throw new Error(`Rich cohort below target: courses=${teacherContent.courses.length}, lessons=${ownedLessons.length}, questions=${ownedQuestions.length}, classes=${teacherClasses.length}.`);
  }

  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  const output = path.join(__dirname, '..', 'docs', 'test-evidence');
  fs.mkdirSync(output, { recursive: true });
  async function assertMobileTargets(page, screen) {
    const small = await page.evaluate(() => [...document.querySelectorAll('a[href],button,input:not([type="hidden"]),select,summary')]
      .filter(node => { const rect = node.getBoundingClientRect(), style = getComputedStyle(node); return node.checkVisibility() && rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden' && rect.height < 44; })
      .map(node => ({ label: (node.innerText || node.getAttribute('aria-label') || node.title || node.name || '').trim().slice(0, 48), height: Math.round(node.getBoundingClientRect().height) })));
    if (small.length) throw new Error(`${screen}: touch targets below 44px: ${JSON.stringify(small)}`);
  }
  try {
    for (const [name, viewport] of [
      ['desktop', { width: 1440, height: 900 }],
      ['mobile', { width: 390, height: 844 }],
      ['mobile-360', { width: 360, height: 844 }],
      ['tablet-768', { width: 768, height: 900 }],
    ]) {
      const context = await browser.newContext({ viewport });
      await context.addInitScript(token => {
        sessionStorage.setItem('studyspace_teacher_session', token);
        sessionStorage.setItem('studyspace_active_role', 'TEACHER');
      }, session.token);
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(`${base}/teacher.html`, { waitUntil: 'networkidle' });
      await page.waitForFunction(() => !document.querySelector('#workspace')?.hidden && !document.querySelector('#teacher-overview')?.hidden);
      const screenshotName = name === 'desktop' ? 'teacher-dashboard-after-1440x900.png' : name === 'mobile' ? 'teacher-dashboard-after-390x844.png' : `teacher-dashboard-after-${name}.png`;
      await page.screenshot({ path: path.join(output, screenshotName) });

      const dashboard = await page.evaluate(() => ({
        width: innerWidth,
        documentWidth: document.documentElement.scrollWidth,
        attention: [...document.querySelectorAll('.teacher-attention-grid>section')].map(section => section.querySelector('h4')?.textContent),
        mobileNav: getComputedStyle(document.querySelector('.teacher-mobile-nav')).display,
        hiddenContext: getComputedStyle(document.querySelector('.topbar-context')).display === 'none',
        touchHeights: [...document.querySelectorAll('.teacher-mobile-nav a,.teacher-mobile-nav button')].map(node => node.getBoundingClientRect().height),
        stats: [...document.querySelectorAll('.teacher-stats article')].map(card => {
          const bounds = selector => {
            const rect = card.querySelector(selector).getBoundingClientRect();
            return { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right };
          };
          return { heading: bounds('.stat-heading'), value: bounds('strong'), caption: bounds('p') };
        }),
      }));
      if (dashboard.documentWidth > dashboard.width) throw new Error(`${name}: horizontal overflow ${dashboard.documentWidth}/${dashboard.width}`);
      if (dashboard.attention.length !== 3) throw new Error(`${name}: attention dashboard blocks missing.`);
      if (dashboard.stats.length !== 4 || dashboard.stats.some(({ heading, value, caption }) =>
        heading.bottom > value.top + 1 || value.bottom > caption.top + 1 ||
        heading.left < 0 || value.left < 0 || caption.left < 0 || caption.right > dashboard.width
      )) throw new Error(`${name}: statistic rows overlap or overflow.`);

      if (dashboard.width < 768 && (dashboard.mobileNav === 'none' || !dashboard.hiddenContext)) throw new Error(`${name}: mobile header/navigation layout missing.`);
      if (dashboard.width >= 768 && dashboard.mobileNav !== 'none') throw new Error(`${name}: mobile navigation should be hidden on desktop/tablet.`);
      if (dashboard.width < 768 && dashboard.touchHeights.some(height => height < 44)) throw new Error(`${name}: a mobile navigation target is below 44px.`);
      if (dashboard.width < 768) await assertMobileTargets(page, `${name} overview`);
      await page.locator('#teacher-user-trigger').click();
      if (!(await page.locator('#logout').isVisible())) throw new Error(`${name}: logout is not inside the account menu.`);
      await page.locator('#theme-toggle').click();
      if (!(await page.locator('#logout').isHidden())) throw new Error(`${name}: account menu did not close after outside click.`);
      if (await page.locator('html').getAttribute('data-theme') !== 'dark') throw new Error(`${name}: dark theme did not activate.`);
      await page.locator('#theme-toggle').click();
      if (dashboard.width < 768) {
        await page.locator('#teacher-more-trigger').click();
        await page.getByRole('dialog', { name: 'Thêm tác vụ' }).waitFor({ state: 'visible' });
        await page.locator('#teacher-more-drawer a[href="#teacher-chapters"]').click();
        await page.waitForFunction(() => !document.querySelector('#teacher-chapters')?.hidden);
        await page.locator('.teacher-mobile-nav a[href="#teacher-courses"]').click();
        await page.waitForFunction(() => !document.querySelector('#teacher-courses')?.hidden);
        await page.locator('[data-open-editor="course-editor"]').click();
        if (!(await page.locator('#course-editor').evaluate(node => node.open))) throw new Error(`${name}: course editor did not open as drawer.`);
        await page.locator('#course-editor [data-close-editor]').click();
      }
      if (name === 'desktop' || name === 'mobile') {
        const suffix = name === 'desktop' ? '1440x900' : '390x844';
        const nav = dashboard.width < 768 ? '.teacher-mobile-nav' : '.teacher-sidebar';
        for (const [section, list, screenshot] of [
          ['teacher-courses', '#course-list', 'courses'],
          ['teacher-chapters', '#chapter-list', 'chapters'],
          ['teacher-lessons', '#lesson-list', 'lessons'],
        ]) {
          if (section === 'teacher-chapters' && dashboard.width < 768) {
            await page.locator('#teacher-more-trigger').click();
            await page.locator('#teacher-more-drawer a[href="#teacher-chapters"]').click();
          } else if (section === 'teacher-lessons' && dashboard.width < 768) {
            await page.locator('#teacher-more-trigger').click();
            await page.locator('#teacher-more-drawer a[href="#teacher-lessons"]').click();
          } else {
            await page.locator(`${nav} a[href="#${section}"]`).click();
          }
          await page.waitForFunction(id => !document.getElementById(id)?.hidden, section);
          if (dashboard.width < 768) await assertMobileTargets(page, `${name} ${section}`);
          const records = page.locator(`${list} .teacher-course-card,${list} .teacher-entity-card`);
          if (await records.count()) {
            if (section === 'teacher-courses' && !(await records.first().locator('.teacher-status-chip').count())) throw new Error(`${name}: course publication state is missing.`);
            if (section !== 'teacher-courses' && !(await records.first().locator('.teacher-entity-meta').count())) throw new Error(`${name}: parent course/chapter relationship is missing.`);
          }
          await page.screenshot({ path: path.join(output, `teacher-${screenshot}-after-${suffix}.png`) });
        }
        await page.locator(`${nav} a[href="#teacher-courses"]`).click();
        await page.waitForFunction(() => !document.querySelector('#teacher-courses')?.hidden);
      }
      if (name === 'desktop' || name === 'mobile') {
        await page.locator(dashboard.width < 768 ? '.teacher-mobile-nav a[href="#teacher-courses"]' : '.teacher-sidebar a[href="#teacher-courses"]').click();
        await page.waitForFunction(() => !document.querySelector('#teacher-courses')?.hidden);
        const cards = await page.locator('.teacher-course-card').count();
        if (cards) {
          const first = page.locator('.teacher-course-card').first();
          await first.locator('summary').click();
          for (const action of ['Sửa', 'Ẩn khóa']) if (!(await first.getByRole('button', { name: action, exact: true }).count())) throw new Error(`${name}: course menu missing ${action}.`);
          if (await first.getByRole('button', { name: 'Xóa', exact: true }).count()) throw new Error(`${name}: unsupported delete action must not be rendered.`);
          await first.locator('summary').click();
        }
      }

      const routeSelector = dashboard.width < 768 ? '.teacher-mobile-nav a[href="#teacher-questions"]' : '.teacher-sidebar a[href="#teacher-questions"]';
      await page.locator(routeSelector).click();
      await page.waitForFunction(() => !document.querySelector('#teacher-questions')?.hidden);
      await page.locator(dashboard.width < 768 ? '.teacher-mobile-nav a[href="#teacher-classes"]' : '.teacher-sidebar a[href="#teacher-classes"]').click();
      await page.waitForFunction(() => !document.querySelector('#teacher-classes')?.hidden);
      if (errors.length) throw new Error(`${name}: ${errors.join('; ')}`);
      console.log(`${name}: PASS, viewport=${dashboard.width}, documentWidth=${dashboard.documentWidth}, nonOverlappingStats=${dashboard.stats.length}, attentionBlocks=${dashboard.attention.length}, mobileNav=${dashboard.mobileNav}, ${dashboard.width < 768 ? `navTargets>=44=${dashboard.touchHeights.every(height => height >= 44)}` : 'mobileTargets=n/a'}, accountMenu=PASS, themeToggle=PASS, routes=chapters/courses/quiz/classes, editorDrawer=PASS`);
      await context.close();
    }
  } finally {
    await browser.close();
  }

  const studentLogin = await fetch(`${base}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'demo_cohort_01', password }),
  });
  const studentSession = await studentLogin.json();
  if (!studentLogin.ok || studentSession.account?.role !== 'STUDENT') throw new Error('Seeded student login failed.');
  const browserForStudent = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const context = await browserForStudent.newContext({ viewport: { width: 390, height: 844 } });
    await context.addInitScript(token => {
      sessionStorage.setItem('studyspace_student_session', token);
      sessionStorage.setItem('studyspace_active_role', 'STUDENT');
    }, studentSession.token);
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(`${base}/student.html`, { waitUntil: 'networkidle' });
    await page.waitForFunction(() => document.querySelector('#course-total')?.textContent !== 'Đang tải…');
    await page.screenshot({ path: path.join(__dirname, '..', 'docs', 'test-evidence', 'student-demo-after-390x844.png') });
    await page.locator('#courses-nav').click();
    await page.waitForFunction(() => !document.querySelector('#courses-view')?.hidden);
    await page.locator('.course-card').filter({ hasText: 'Cơ sở dữ liệu · Lớp thực hành mẫu' }).waitFor({ state: 'visible' });
    await page.locator('.course-card').filter({ hasText: 'Cơ sở dữ liệu · Lớp thực hành mẫu' }).click();
    await page.waitForFunction(() => !document.querySelector('#course-view')?.hidden);
    const lesson = page.locator('#course-content [data-open-lesson]').first();
    await lesson.waitFor({ state: 'visible' });
    const courseTitle = await page.locator('#course-content h1').textContent();
    await lesson.click();
    await page.waitForFunction(() => !document.querySelector('#lesson-view')?.hidden);
    if (!await page.locator('#lesson-content h1').isVisible()) throw new Error('Student lesson screen did not render.');
    if (errors.length) throw new Error(`student: ${errors.join('; ')}`);
    console.log(`teacher->student: PASS, student=demo_cohort_01, course=${courseTitle.trim()}, lesson=opened, courses=${teacherContent.courses.length}, lessons=${ownedLessons.length}, questions=${ownedQuestions.length}, classes=${teacherClasses.length}, studentsPerClass>=20`);
    await context.close();
  } finally {
    await browserForStudent.close();
  }
}

main().catch(error => { console.error(error); process.exit(1); });
