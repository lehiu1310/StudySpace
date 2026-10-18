# StudySpace — Hồ sơ mô tả hệ thống

Cập nhật: 2026-10-19. Hồ sơ được đối chiếu với mã nguồn và PostgreSQL local;
ảnh cổng giảng viên được chụp lại bằng Playwright ngày 2026-10-18.

## Mục lục

1. [Tổng quan và kiến trúc](01-tong-quan.md)
2. [ERD và cấu trúc PostgreSQL](02-erd.md)
3. [Use case ba vai trò](03-use-cases.md)
4. [Hướng dẫn sử dụng](04-huong-dan-su-dung.md)
5. [Giới hạn và hướng phát triển](05-gioi-han.md)
6. [Trạng thái kiểm chứng PDF](06-pdf-export.md)
7. [Thiết kế giao diện](07-thiet-ke-giao-dien.md)
8. [Yêu cầu đề tài và bằng chứng](08-yeu-cau-va-bang-chung.md)

## Bằng chứng giao diện

- Học viên, đăng nhập demo, trang Môn học: [student-courses.png](screenshots/student-courses.png)
- Chi tiết khóa/bài, lớp và quiz: [bộ ảnh màn học viên](07-thiet-ke-giao-dien.md#web--học-viên)
- Giảng viên, đăng nhập demo, không gian giảng dạy: [teacher-dashboard.png](screenshots/teacher-dashboard.png)
- Quản trị viên, đăng nhập demo, dashboard: [admin-dashboard.png](screenshots/admin-dashboard.png)
- Kết quả quiz trên web: [quiz-result.png](screenshots/quiz-result.png)

Ảnh dashboard admin, teacher và danh sách môn học được chụp bằng Playwright Edge
ở 1440×900 trên `http://127.0.0.1:8080`. Ba vai trò đăng nhập thành công;
không phát hiện lỗi JavaScript ở các trang này. Ảnh kết quả quiz là ảnh kiểm thử
giao diện thật đã lưu trong `backend/docs/test-evidence/phase4-quiz-result.png`.

## Phạm vi và nguồn xác minh

- Sơ đồ ERD lấy từ `information_schema` của PostgreSQL local ngày 2026-10-18:
-  18 bảng, 102 cột và 26 khóa ngoại.
- Danh mục học viên có 9 khóa xuất bản (ID 1, 2, 3, 4, 22, 23, 42, 43, 44);
  các khóa 18, 19, 21 đang ẩn và vẫn được giữ trong database.
- Bộ demo giảng viên có 3 khóa, 18 bài, 31 câu hỏi, 2 lớp và 28 học viên/lớp;
  database local có 41 tài khoản (1 ADMIN, 3 TEACHER, 37 STUDENT), trong đó
  32 tên tài khoản thuộc namespace demo.
- Hồ sơ nộp không chứa mật khẩu demo. Bí mật demo chỉ được lưu trong file local
  đã bị `.gitignore`; không phát hành file đó cùng hồ sơ.
- Tài khoản demo và giới hạn môi trường: [DEMO_ACCOUNTS.md](../../backend/docs/DEMO_ACCOUNTS.md).
- Nhật ký kiểm thử gần nhất: [../VERIFICATION.md](../VERIFICATION.md).
