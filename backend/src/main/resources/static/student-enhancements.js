'use strict';
(() => {
  const originalRender = render;
  const originalRenderClasses = renderClasses;
  const detailByCourse = new Map();
  let announcementItems = [];
  let courseQuery = '';
  let courseStateFilter = 'all';

  function ensureDashboardEnhancements() {
    const home = $('home-view');
    if (!home) return;
    const ai = $('student-ai');
    const metrics = $('student-metrics');
    if (ai && metrics) home.insertBefore(ai, metrics);
    if (!$('overview-insights') && metrics) metrics.insertAdjacentHTML('afterend', `
      <section class="overview-insights" id="overview-insights">
        <article class="weekly-goals-panel"><header><div><span class="student-kicker">MỤC TIÊU TUẦN</span><h2>Nhịp học của bạn</h2></div><span class="streak-badge">🔥 <b id="study-streak">0</b> ngày</span></header>
          <div class="goal-rings"><div class="goal-ring" id="minutes-ring"><div><b id="minutes-value">0</b><small>/ 300 phút</small></div></div><div class="goal-copy"><b>Thời lượng học</b><span id="minutes-goal-copy">0 / 300 phút</span><div class="goal-bar"><i id="minutes-goal-bar"></i></div><b>Quiz đã nộp</b><span id="quizzes-goal-copy">0 / 5 quiz</span><div class="goal-bar"><i id="quizzes-goal-bar"></i></div></div></div>
        </article>
        <article class="quiz-trend-panel"><header><div><span class="student-kicker">KẾT QUẢ GẦN ĐÂY</span><h2>Điểm quiz theo tuần</h2></div><span id="quiz-trend-summary"></span></header><div id="quiz-trend-chart"></div></article>
      </section>
      <section class="student-announcements" id="student-announcements"><header><div><span class="student-kicker">CẬP NHẬT LỚP</span><h2>Thông báo từ giảng viên</h2></div></header><div id="announcement-list"><div class="loading-line"><i></i> Đang tải thông báo</div></div></section>`);
    const focus = $('study-time-preview');
    if (focus && $('overview-insights') && focus.previousElementSibling !== $('overview-insights')) $('overview-insights').after(focus);
    const heading = document.querySelector('#courses-view .student-section .course-section-heading');
    if (heading && !$('course-catalog-tools')) {
      heading.insertAdjacentHTML('afterend', `<div class="course-catalog-tools" id="course-catalog-tools"><label class="course-search"><span aria-hidden="true">⌕</span><input id="course-search-input" type="search" placeholder="Tìm môn học…" aria-label="Tìm môn học"></label><div class="course-filter" role="group" aria-label="Lọc trạng thái môn học"><button type="button" data-course-filter="all" aria-pressed="true">Tất cả</button><button type="button" data-course-filter="active" aria-pressed="false">Đang học</button><button type="button" data-course-filter="complete" aria-pressed="false">Hoàn thành</button></div></div>`);
      $('course-search-input').addEventListener('input', event => { courseQuery = event.target.value.trim().toLocaleLowerCase('vi'); renderCourseCatalog(); });
      document.querySelectorAll('[data-course-filter]').forEach(button => button.addEventListener('click', () => {
        courseStateFilter = button.dataset.courseFilter;
        document.querySelectorAll('[data-course-filter]').forEach(item => item.setAttribute('aria-pressed', String(item === button)));
        renderCourseCatalog();
      }));
    }
  }

  function renderCourseCatalog() {
    const list = $('course-list');
    if (!list || !Array.isArray(courses)) return;
    if (list.querySelector('[data-retry-student]')) return;
    const filtered = courses.filter(course => {
      const state = courseProgressState(course);
      const matchesState = courseStateFilter === 'all' || (courseStateFilter === 'active' ? state === 'Đang học' : state === 'Hoàn thành');
      return matchesState && `${course.title} ${course.description || ''}`.toLocaleLowerCase('vi').includes(courseQuery);
    });
    list.innerHTML = filtered.length ? filtered.map((course, index) => {
      const pct = course.lesson_count ? Math.round(100 * course.completed_count / course.lesson_count) : 0;
      const state = courseProgressState(course);
      const details = detailByCourse.get(Number(course.id));
      const chapterIds = new Set((details?.chapters || []).map(chapter => Number(chapter.id)));
      const scores = (progress.mastery || []).filter(row => chapterIds.has(Number(row.id)) && Number(row.attempts) > 0).map(row => Number(row.percentage));
      const average = scores.length ? Math.round(scores.reduce((sum, score) => sum + score, 0) / scores.length) : null;
      const lessons = (details?.chapters || []).flatMap(chapter => chapter.lessons || []);
      const recent = lessons.find(lesson => lesson.completed) || lessons.find(lesson => !lesson.locked);
      return `<button class="course-card" data-course="${Number(course.id)}" type="button"><span class="course-card-top"><span class="course-icon">${index % 2 ? '⌘' : '▤'}</span><span class="course-status-chip state-${pct === 0 ? 'new' : pct === 100 ? 'done' : 'active'}">${escapeHtml(state)}</span></span><h3>${escapeHtml(course.title)}</h3><p>${escapeHtml(course.description || '')}</p><span class="catalog-meta"><span>Điểm TB <b>${average == null ? '—' : `${average}%`}</b></span><span>Bài gần nhất <b>${escapeHtml(recent?.title || 'Chưa mở bài')}</b></span></span><span class="progress-track"><span style="width:${pct}%"></span></span><span class="course-open">${pct ? 'Tiếp tục học' : 'Bắt đầu học'} <span>→</span></span></button>`;
    }).join('') : empty('Không tìm thấy môn học', courseQuery || courseStateFilter !== 'all' ? 'Thử đổi từ khóa hoặc bộ lọc.' : 'Các môn học đã xuất bản sẽ xuất hiện tại đây.', 'Xóa bộ lọc', 'reset-courses');
    list.querySelectorAll('[data-course]').forEach(button => button.onclick = () => openCourse(Number(button.dataset.course)));
    list.querySelector('[data-empty-action="reset-courses"]')?.addEventListener('click', () => {
      courseQuery = ''; courseStateFilter = 'all';
      if ($('course-search-input')) $('course-search-input').value = '';
      document.querySelectorAll('[data-course-filter]').forEach(item => item.setAttribute('aria-pressed', String(item.dataset.courseFilter === 'all')));
      renderCourseCatalog();
    });
  }

  function activityDates() {
    const dates = new Set();
    const add = value => { const date = new Date(Number(value)); if (!Number.isNaN(date.getTime())) dates.add(`${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`); };
    focusSessions().forEach(session => add(session.timestamp));
    (progress.history || []).filter(item => item.submitted_at != null).forEach(item => add(item.submitted_at));
    (progress.lessonActivity || []).forEach(item => add(item.completed_at));
    return dates;
  }

  function renderInsights() {
    if (!$('overview-insights')) return;
    const days = activityDates(), today = new Date(); today.setHours(0, 0, 0, 0);
    let streak = 0;
    for (let offset = 0; offset < 400; offset++) {
      const date = new Date(today); date.setDate(today.getDate() - offset);
      if (days.has(`${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`)) streak++;
      else if (offset === 0) continue;
      else break;
    }
    const minutes = weeklyFocus().reduce((sum, day) => sum + day.minutes, 0);
    const now = new Date(), monday = new Date(now); monday.setHours(0, 0, 0, 0); monday.setDate(now.getDate() - ((now.getDay() + 6) % 7));
    const thisWeek = (progress.history || []).filter(item => item.submitted_at != null && Number(item.submitted_at) >= monday.getTime());
    $('study-streak').textContent = streak;
    $('minutes-value').textContent = minutes;
    $('minutes-goal-copy').textContent = `${minutes} / 300 phút`;
    $('quizzes-goal-copy').textContent = `${thisWeek.length} / 5 quiz`;
    $('minutes-goal-bar').style.width = `${Math.min(100, minutes / 300 * 100)}%`;
    $('quizzes-goal-bar').style.width = `${Math.min(100, thisWeek.length / 5 * 100)}%`;
    $('minutes-ring').style.setProperty('--goal-progress', `${Math.min(100, minutes / 300 * 100)}%`);
    const weeks = Array.from({ length: 6 }, (_, index) => { const start = new Date(monday); start.setDate(monday.getDate() - (5 - index) * 7); const end = new Date(start); end.setDate(start.getDate() + 7); const scores = (progress.history || []).filter(item => item.submitted_at != null && Number(item.submitted_at) >= start.getTime() && Number(item.submitted_at) < end.getTime()).map(item => 100 * Number(item.score) / Math.max(1, Number(item.total))); return { label: start.toLocaleDateString('vi-VN', { day: 'numeric', month: 'numeric' }), score: scores.length ? Math.round(scores.reduce((sum, value) => sum + value, 0) / scores.length) : null }; });
    const values = weeks.filter(week => week.score != null).map(week => week.score);
    $('quiz-trend-summary').textContent = values.length ? `TB ${Math.round(values.reduce((a, b) => a + b, 0) / values.length)}%` : 'Chưa có dữ liệu';
    $('quiz-trend-chart').innerHTML = values.length ? `<div class="quiz-week-bars" role="img" aria-label="Điểm trung bình quiz theo tuần">${weeks.map((week, index) => `<div><span>${week.score == null ? '—' : `${week.score}%`}</span><i class="${index === weeks.length - 1 ? 'is-current' : ''}" style="height:${week.score == null ? 3 : Math.max(8, week.score)}%"></i><small>${week.label}</small></div>`).join('')}</div>` : `<div class="insight-empty">Chưa có điểm quiz để so sánh theo tuần.</div>`;
  }

  async function loadAnnouncements() {
    const target = $('announcement-list'); if (!target || !token) return;
    target.innerHTML = '<div class="loading-line"><i></i> Đang tải thông báo</div>';
    try {
      announcementItems = await api('classes/announcements');
      target.innerHTML = announcementItems.length ? announcementItems.slice(0, 5).map(item => `<article class="announcement-card"><span class="announcement-mark" aria-hidden="true">✦</span><div><b>${escapeHtml(item.course_title)} · ${escapeHtml(item.class_name)}</b><p>${escapeHtml(item.body)}</p><small>${escapeHtml(item.teacher_name)} · ${new Date(Number(item.created_at)).toLocaleString('vi-VN', { dateStyle: 'medium', timeStyle: 'short' })}</small></div></article>`).join('') : empty('Chưa có thông báo mới', 'Tin nhắn từ giảng viên sẽ hiển thị ở đây khi lớp có cập nhật.');
    } catch (error) {
      target.innerHTML = studentLoadError(error);
      target.querySelector('[data-retry-student]').onclick = loadAnnouncements;
    }
  }

  function renderEnhancements() {
    ensureDashboardEnhancements();
    renderCourseCatalog();
    renderInsights();
    if ($('announcement-list') && !announcementItems.length && !window.studySpaceAnnouncementsRequested) {
      window.studySpaceAnnouncementsRequested = true;
      loadAnnouncements();
    }
  }

  render = function () {
    originalRender();
    ['course-list', 'history-list'].forEach(id => $(id)?.setAttribute('aria-busy', 'false'));
    renderEnhancements();
  };
  renderClasses = function () { originalRenderClasses(); $('student-class-list')?.setAttribute('aria-busy', 'false'); };

  const originalLoadNextLesson = loadNextLesson;
  loadNextLesson = async function () {
    const result = await originalLoadNextLesson();
    const details = await Promise.allSettled(courses.map(course => api(`courses/${course.id}`)));
    details.forEach((item, index) => { if (item.status === 'fulfilled') detailByCourse.set(Number(courses[index].id), item.value); });
    renderCourseCatalog();
    return result;
  };

  document.addEventListener('DOMContentLoaded', () => {
    ensureDashboardEnhancements();
    $('refresh-button')?.addEventListener('click', () => { announcementItems = []; window.studySpaceAnnouncementsRequested = false; });
    document.querySelector('.student-topbar nav')?.insertAdjacentHTML('beforeend', '<button type="button" id="theme-toggle" class="theme-toggle" aria-label="Đổi giao diện sáng tối" title="Đổi giao diện sáng tối">◐</button>');
    $('theme-toggle')?.addEventListener('click', window.toggleStudySpaceTheme);
    if (token) loadAnnouncements();
    window.addEventListener('focus', () => { if (studentScreen === 'home') loadAnnouncements(); });
  });
  setTimeout(() => { ensureDashboardEnhancements(); renderEnhancements(); loadAnnouncements(); }, 0);
})();
