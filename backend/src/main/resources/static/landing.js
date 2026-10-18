'use strict';

const sessions = [
  ['STUDENT', 'studyspace_student_session', '/student.html'],
  ['TEACHER', 'studyspace_teacher_session', '/teacher.html'],
  ['ADMIN', 'studyspace_admin_session', '/admin.html'],
];

async function findDashboard() {
  const activeRole = sessionStorage.getItem('studyspace_active_role');
  const ordered = [
    ...sessions.filter(([role]) => role === activeRole),
    ...sessions.filter(([role]) => role !== activeRole),
  ];

  for (const [role, key, href] of ordered) {
    const token = sessionStorage.getItem(key);
    if (!token) continue;

    try {
      const response = await fetch('/api/auth/me', {
        headers: { Authorization: `Bearer ${token}` },
        signal: AbortSignal.timeout(5000),
      });
      if (response.status === 401) {
        sessionStorage.removeItem(key);
        continue;
      }
      if (!response.ok) return { role, href };

      const account = await response.json();
      const destination = sessions.find(([accountRole]) => accountRole === account.role);
      if (destination) {
        sessionStorage.setItem('studyspace_active_role', destination[0]);
        return { role: destination[0], href: destination[2] };
      }
    } catch {
      return { role, href };
    }
  }

  return null;
}

findDashboard().then(destination => {
  if (!destination) return;
  if (location.pathname !== destination.href) location.replace(destination.href);
});
