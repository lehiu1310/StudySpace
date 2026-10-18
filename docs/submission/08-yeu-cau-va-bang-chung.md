# 8. Yêu cầu đề tài và bằng chứng trong repo

Cập nhật: 2026-10-19. Đây là bảng điều hướng bằng chứng; mô tả không thay cho
việc chạy trực tiếp ứng dụng khi bảo vệ.

| Yêu cầu của cô | Bằng chứng trong repo |
|---|---|
| Quản lý nội dung học tập | [Use case](03-use-cases.md), [hướng dẫn giảng viên](04-huong-dan-su-dung.md#giảng-viên), [ảnh cổng giảng viên](screenshots/teacher-dashboard.png), `backend/src/main/resources/static/teacher.html`, API trong `backend/src/main/java/com/studyspace/server/TeacherContentController.java` |
| Quiz trắc nghiệm | [Use case](03-use-cases.md#luồng-học-viên), [ảnh quiz đang làm](screenshots/student-quiz-active.png), [ảnh kết quả](screenshots/student-quiz-result.png), `backend/src/main/java/com/studyspace/server/LearningController.java` |
| Theo dõi tiến độ học tập | [Tổng quan](01-tong-quan.md#luồng-dữ-liệu-học-tập), [ảnh báo cáo tiến độ học viên](screenshots/student-progress-report-desktop.png), [ảnh danh mục](screenshots/student-courses.png), bảng `lesson_progress`, `course_learning`, `quiz_attempts` trong [ERD](02-erd.md) |
| Quản lý người dùng | [Use case ba vai trò](03-use-cases.md), [ảnh dashboard admin](screenshots/admin-dashboard.png), `backend/src/main/java/com/studyspace/server/AdminController.java` |
| Báo cáo thống kê | [Ảnh báo cáo lớp giảng viên](screenshots/teacher-class-report-desktop.png), [ảnh báo cáo quản trị có biểu đồ Chart.js](screenshots/admin-chart-report-desktop.png), [ảnh báo cáo focus học viên](screenshots/student-overview-focus.png), [hướng dẫn](04-huong-dan-su-dung.md), API `/api/admin/reports` và tiến độ lớp trong `AdminController.java` / `TeacherContentController.java` |
| Cơ sở dữ liệu quan hệ | [ERD đối chiếu PostgreSQL + Room](02-erd.md), schema khai báo trong `backend/src/main/resources/schema.sql` |
| Ứng dụng web | Các cổng web học viên/giảng viên/admin tại `/student.html`, `/teacher.html`, `/admin.html`; backend Spring Boot phục vụ giao diện và REST API; [ảnh thiết kế](07-thiet-ke-giao-dien.md) |
| Trực quan hóa dữ liệu | Chart.js tại `backend/src/main/resources/static/admin.js` trong báo cáo quản trị; [ảnh biểu đồ Chart.js](screenshots/admin-chart-report-desktop.png). [Báo cáo lớp](screenshots/teacher-class-report-desktop.png) hiện là bảng/heatmap, không phải biểu đồ Chart.js. Radar Android trong `StudyExperienceScreens.kt` (ảnh Android: chưa có) |
| ERD | [02-erd.md](02-erd.md): 18 bảng, 102 cột, 26 FK từ PostgreSQL live; thêm sơ đồ entity Room và snapshot đồng bộ |
| Use case | [03-use-cases.md](03-use-cases.md) — học viên, giảng viên, admin |
| Thiết kế giao diện | [07-thiet-ke-giao-dien.md](07-thiet-ke-giao-dien.md) — ảnh web thật; Android ghi rõ chưa có ảnh hiện trạng |
| Hướng dẫn sử dụng | [04-huong-dan-su-dung.md](04-huong-dan-su-dung.md), [tài khoản demo không chứa mật khẩu](../../backend/docs/DEMO_ACCOUNTS.md) |
| Android | APK debug trong `app/build/outputs/apk/debug/app-debug.apk`; unit test hiện tại 56/56 PASS. **Chưa kiểm tra trực quan APK hiện tại trên thiết bị.** |

## Kiểm chứng cấu trúc dữ liệu

Ngày 2026-10-18, truy vấn `information_schema` trên PostgreSQL local trả 18
bảng, 102 cột và 26 khóa ngoại. So khớp tên bảng/cột với ERD cho kết quả 18/18
bảng và 102/102 cột; không thiếu/thừa. Room Android có năm entity, nhưng không
khai báo foreign key SQLite; các quan hệ owner/task được quản lý bằng logic ứng
dụng. Chi tiết và luồng snapshot ở [02-erd.md](02-erd.md).
