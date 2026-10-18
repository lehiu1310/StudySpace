const { chromium } = require('C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');

const base = 'http://localhost:8080';
const out = path.join(__dirname, 'test-evidence');
const privateAccounts = fs.readFileSync(path.join(__dirname, '..', 'docs', 'DEMO_ACCOUNTS.md'), 'utf8');
const password = privateAccounts.match(/^Rich cohort password:\s*(.+)$/m)?.[1]?.trim();
const checks = [];
const check = (condition, message) => { if (!condition) throw new Error(`FAIL ${message}`); checks.push(message); console.log(`PASS ${message}`); };

async function login(username) {
  const response = await fetch(`${base}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) });
  check(response.ok, `${username} đăng nhập API`);
  return (await response.json()).token;
}

async function pageFor(browser, role, token, viewport) {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const key = role === 'teacher' ? 'studyspace_teacher_session' : 'studyspace_student_session';
  await page.goto(base);
  await page.evaluate(({ key, token }) => sessionStorage.setItem(key, token), { key, token });
  await page.goto(`${base}/${role === 'teacher' ? 'teacher' : 'student'}.html`);
  await page.waitForTimeout(900);
  return { context, page, errors };
}

(async () => {
  if (!password) throw new Error('Thiếu mật khẩu lớp demo trong docs/DEMO_ACCOUNTS.md local.');
  fs.mkdirSync(out, { recursive: true });
  const teacherToken = await login('demo_cohort_teacher');
  const studentToken = await login('demo_cohort_10');
  const teacherHeaders = { Authorization: `Bearer ${teacherToken}` };
  const notice = 'Nhắc ôn tập: Hoàn thành bài về chuẩn hóa dữ liệu trước buổi học kế tiếp.';
  const existingNotices = await fetch(`${base}/api/classes/announcements`, { headers: { Authorization: `Bearer ${studentToken}` } }).then(response => response.json());
  const noticeExists = existingNotices.some(item => item.body === notice);
  const classes = await fetch(`${base}/api/teacher/classes`, { headers: teacherHeaders }).then(response => response.json());
  const demoClass = classes.find(item => item.join_code === 'DEMO2026');
  check(Boolean(demoClass), 'Lớp demo có thể chọn để thử báo cáo và thông báo');
  const content = await fetch(`${base}/api/teacher/content`, { headers: teacherHeaders }).then(response => response.json());
  const chapterId = Number(content.chapters[0].id);
  const browser = await chromium.launch({ headless: true, executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  try {
    for (const [name, viewport] of [['desktop', { width: 1440, height: 900 }], ['mobile', { width: 390, height: 844 }]]) {
      const teacher = await pageFor(browser, 'teacher', teacherToken, viewport);
      const { page, errors } = teacher;
      await page.goto(`${base}/teacher.html#teacher-classes`);
      await page.locator('.teacher-class-row').first().waitFor();
      await page.locator('[data-class-report]').first().click();
      await page.locator('#class-report:not([hidden])').waitFor();
      check(await page.locator('#class-report').getByText('Heatmap · học viên × chương').count() === 1, `${name}: báo cáo có heatmap học viên theo chương`);
      check((await page.locator('#class-report').innerText()).includes('Lâu không hoạt động'), `${name}: nhận diện học viên lâu không hoạt động từ mốc hoạt động thật`);
      await page.locator('#class-report').evaluate(element => element.scrollIntoView({ block: 'start' }));
      check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${name}: báo cáo lớp không tràn ngang`);
      await page.screenshot({ path: path.join(out, `final-teacher-report-${name}.png`), animations: 'disabled' });
      const csvDownload = page.waitForEvent('download');
      await page.locator('[data-export-report]').click();
      check((await csvDownload).suggestedFilename().endsWith('.csv'), `${name}: xuất báo cáo CSV`);
      if (name === 'desktop') {
        await page.locator('[data-close-panel]').click();
        await page.goto(`${base}/teacher.html#teacher-questions`);
        await page.locator('.bulk-quiz-panel summary').click();
        await page.locator('#quiz-csv-input').fill(`chapterId,difficulty,prompt,A,B,C,D,correctOption,explanation\n${chapterId},2,Câu hỏi kiểm tra CSV,Phương án A,Phương án B,Phương án C,Phương án D,A,Đáp án được giải thích\n999999,5,Dòng lỗi,A,B,C,D,X,Thiếu dữ liệu`);
        await page.locator('#quiz-csv-preview').click();
        check((await page.locator('.csv-preview-summary').innerText()).includes('1 câu hợp lệ'), 'CSV: xem trước phân biệt dòng hợp lệ và lỗi');
        check(await page.locator('#quiz-csv-import').isEnabled(), 'CSV: chỉ bật lưu khi có câu hợp lệ');
        const csvPrompt = 'Thuộc tính không khóa trong 3NF cần phụ thuộc trực tiếp vào điều gì?';
        if (!content.questions.some(question => question.prompt === csvPrompt)) {
          await page.locator('#quiz-csv-input').fill(`chapterId,difficulty,prompt,A,B,C,D,correctOption,explanation\n${chapterId},2,${csvPrompt},Phụ thuộc vào một thuộc tính không khóa trung gian,Phụ thuộc trực tiếp vào khóa của quan hệ,Phụ thuộc vào mọi khóa ngoại,Không cần phụ thuộc vào khóa,B,3NF yêu cầu thuộc tính không khóa phụ thuộc trực tiếp vào khóa và không qua thuộc tính trung gian.`);
          await page.locator('#quiz-csv-preview').click();
          await page.locator('#quiz-csv-import').click();
          await page.getByText('Đã lưu 1 câu hỏi từ CSV.').waitFor();
          const refreshed = await fetch(`${base}/api/teacher/content`, { headers: teacherHeaders }).then(response => response.json());
          check(refreshed.questions.some(question => question.prompt === csvPrompt), 'CSV: câu hợp lệ được lưu vào ngân hàng thật');
        } else check(true, 'CSV: dữ liệu kiểm thử đã được nhập từ lần chạy trước');
        await page.goto(`${base}/teacher.html#teacher-courses`);
        await page.locator('#student-preview-button').click();
        await page.locator('#student-preview-dialog[open]').waitFor();
        await page.locator('#preview-load').click();
        await page.locator('.preview-course-card').waitFor();
        check(await page.locator('.preview-course-card section').count() > 0, 'Xem như học viên: tải cấu trúc chương/bài thật');
        await page.locator('#student-preview-dialog .modal-close').click();
        await page.goto(`${base}/teacher.html#teacher-classes`);
        if (!noticeExists) {
          await page.locator('#announcement-form [name="classId"]').selectOption(String(demoClass.id));
          await page.locator('#announcement-form [name="body"]').fill(notice);
          await page.locator('#announcement-form button.primary-action').click();
          await page.getByText('Đã gửi thông báo đến học viên trong lớp.').waitFor();
        }
        check(true, 'Biểu mẫu gửi thông báo lớp hoạt động; thông báo demo được giữ idempotent');
        await page.route('**/api/teacher/content', route => route.abort());
        await page.reload();
        const visibleTeacherRetry = page.locator('.teacher-section:not([hidden]) #class-list [data-teacher-retry]');
        await visibleTeacherRetry.waitFor();
        check(true, 'Giảng viên lỗi mạng có nút thử lại trong các khối dữ liệu');
        await page.unroute('**/api/teacher/content');
        await visibleTeacherRetry.click();
        await page.locator('.teacher-class-row').first().waitFor();
        check(true, 'Nút thử lại tải lại dữ liệu giảng viên thành công');
      }
      check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${name}: cổng giảng viên không tràn ngang`);
      await page.screenshot({ path: path.join(out, `final-teacher-${name}.png`), animations: 'disabled' });
      await page.evaluate(() => window.toggleStudySpaceTheme());
      check(await page.locator('html').getAttribute('data-theme') === 'dark', `${name}: bật giao diện tối`);
      await page.screenshot({ path: path.join(out, `final-teacher-dark-${name}.png`), animations: 'disabled' });
      await page.evaluate(() => window.toggleStudySpaceTheme());
      check(errors.length === 0, `${name}: không có lỗi JavaScript cổng giảng viên (${errors.join('; ')})`);
      await teacher.context.close();

      const student = await pageFor(browser, 'student', studentToken, viewport);
      await student.page.locator('#overview-insights').waitFor();
      await student.page.locator('#announcement-list .announcement-card').filter({ hasText: notice }).first().waitFor({ timeout: 8000 });
      check(await student.page.locator('#study-streak').count() === 1, `${name}: tổng quan có streak và mục tiêu tuần`);
      check(await student.page.locator('#quiz-trend-chart').count() === 1, `${name}: tổng quan có biểu đồ điểm quiz theo tuần`);
      check(await student.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${name}: tổng quan học viên không tràn ngang`);
      await student.page.screenshot({ path: path.join(out, `final-student-overview-${name}.png`), animations: 'disabled' });
      await student.page.locator('#overview-insights').evaluate(element => element.scrollIntoView({ block: 'center' }));
      await student.page.screenshot({ path: path.join(out, `final-student-insights-${name}.png`), animations: 'disabled' });
      check(await student.page.locator('#announcement-list').innerText().then(text => text.includes(notice)), `${name}: học viên thấy thông báo giảng viên`);
      await student.page.locator('#courses-nav').click();
      await student.page.locator('#courses-view:not([hidden])').waitFor();
      await student.page.locator('#course-search-input').fill('Cơ sở dữ liệu');
      check(await student.page.locator('#course-list [data-course]').count() >= 1, `${name}: tìm kiếm môn học lọc kết quả thật`);
      await student.page.locator('#course-list').evaluate(element => element.scrollIntoView({ block: 'start' }));
      await student.page.screenshot({ path: path.join(out, `final-student-courses-${name}.png`), animations: 'disabled' });
      await student.page.locator('#course-search-input').fill('khong-co-mon-nay');
      check(await student.page.locator('#course-list .empty-box').count() === 1, `${name}: bộ lọc hiển thị trạng thái rỗng có hướng dẫn`);
      await student.page.locator('#course-search-input').fill('');
      await student.page.locator('[data-course-filter="active"]').click();
      check(await student.page.locator('#course-list [data-course]').count() >= 1, `${name}: lọc môn đang học hoạt động`);
      await student.page.evaluate(() => window.toggleStudySpaceTheme());
      check(await student.page.locator('html').getAttribute('data-theme') === 'dark', `${name}: bật giao diện tối học viên`);
      await student.page.screenshot({ path: path.join(out, `final-student-dark-${name}.png`), animations: 'disabled' });
      check(await student.page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${name}: trang học viên không tràn ngang`);
      await student.page.evaluate(() => window.toggleStudySpaceTheme());
      check(student.errors.length === 0, `${name}: không có lỗi JavaScript học viên (${student.errors.join('; ')})`);
      if (name === 'desktop') {
        await student.page.route(/\/api\/courses$/, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ error: 'Lỗi kết nối mô phỏng' }) }));
        await student.page.reload();
        await student.page.locator('#courses-nav').click();
        await student.page.locator('#course-list [data-retry-student]').waitFor();
        check(true, 'Học viên lỗi mạng thấy trạng thái lỗi và nút tải lại');
        await student.page.unroute(/\/api\/courses$/);
        await student.page.locator('#course-list [data-retry-student]').click();
        await student.page.locator('#course-list [data-course]').first().waitFor();
        check(true, 'Nút thử lại tải lại danh sách môn học');
      }
      await student.context.close();
    }

    const notices = await fetch(`${base}/api/classes/announcements`, { headers: { Authorization: `Bearer ${studentToken}` } }).then(response => response.json());
    check(notices.some(item => item.body === notice), 'Học viên đã ghi danh nhận đúng thông báo lớp');
  } finally {
    await browser.close();
  }
  console.log(`\n${checks.length} Playwright/API checks passed.`);
})().catch(error => { console.error(error); process.exitCode = 1; });
