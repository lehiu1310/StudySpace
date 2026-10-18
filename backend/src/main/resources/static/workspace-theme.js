'use strict';
(() => {
  const key = 'studyspace-theme';
  const apply = mode => {
    document.documentElement.dataset.theme = mode;
    document.querySelectorAll('.theme-toggle').forEach(button => {
      button.setAttribute('aria-pressed', String(mode === 'dark'));
      button.textContent = mode === 'dark' ? '☀' : '◐';
      button.title = mode === 'dark' ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối';
    });
  };
  window.toggleStudySpaceTheme = () => {
    const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    localStorage.setItem(key, next);
    apply(next);
  };
  apply(localStorage.getItem(key) || 'light');
  document.addEventListener('DOMContentLoaded', () => {
    if (document.body.classList.contains('teacher-page')) {
      const actions = document.querySelector('.topbar-actions');
      if (actions && !$('theme-toggle')) {
        const button = document.createElement('button');
        button.id = 'theme-toggle'; button.className = 'theme-toggle'; button.type = 'button';
        button.setAttribute('aria-label', 'Đổi giao diện sáng tối');
        button.onclick = window.toggleStudySpaceTheme;
        actions.prepend(button); apply(document.documentElement.dataset.theme);
      }
    }
  });
})();
