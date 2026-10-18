# Tài khoản demo StudySpace

Cập nhật: 2026-10-18. Tên tài khoản và trạng thái dữ liệu được đối chiếu với
backend/PostgreSQL local. Không đưa mật khẩu vào tài liệu được chia sẻ.

Các tài khoản dưới đây chỉ dùng trình diễn trên máy local, không phải tài khoản
người dùng thật và không được dùng trên server public.

| Vai trò | Tên đăng nhập | Mật khẩu | Trạng thái đã kiểm tra |
|---|---|---|---|
| Admin | `admin` | Sinh cục bộ, xem bằng `backend/show-admin-login.ps1` | Đăng nhập cổng quản trị local thành công; bí mật lưu DPAPI trong `backend/.local-secrets.xml`. |
| Giảng viên | `demo_gv_261015135744` | Chỉ trong `docs/DEMO_ACCOUNTS.local.md` (bị `.gitignore`) | Đăng nhập cổng giảng viên local thành công. Có 3 khóa do tài khoản sở hữu (18, 19, 21), hiện là bản nháp/chưa xuất bản; có 3 lớp. |
| Học viên | `demo_focus_student` | Chỉ trong `docs/DEMO_ACCOUNTS.local.md` (bị `.gitignore`) | Có snapshot 4 tuần phiên tập trung; hiện không có lịch sử quiz seed. |
| Giảng viên lớp mẫu | `demo_cohort_teacher` | Chỉ trong `docs/DEMO_ACCOUNTS.md` (bị `.gitignore`) | Sở hữu 3 khóa, 18 bài, 31 câu hỏi và 2 lớp, mỗi lớp có 28 học viên. |
| Học viên lớp mẫu | `demo_cohort_01` … `demo_cohort_28` | Chỉ trong `docs/DEMO_ACCOUNTS.md` (bị `.gitignore`) | 4 tuần quiz/focus; nhóm điểm cao, trung bình, cần hỗ trợ và 5 tài khoản nghỉ học gần đây. |

Seed nội dung cơ bản chạy khi `STUDYSPACE_SEED=true` (`.\\start-portable.ps1 -Seed`).
Snapshot focus chỉ được thêm nếu tài khoản chưa có snapshot; chạy lại không tạo
trùng. Không bật seed demo trên server public.

Rich cohort seed chỉ chạy khi truyền `-RichDemo` cho `backend/start-local.ps1`.
Mật khẩu cohort local được giữ trong file ignored `docs/DEMO_ACCOUNTS.md`; dữ
liệu mẫu có namespace `demo_cohort_*` và không sửa các tài khoản người dùng.

## Dùng dữ liệu local

- Mật khẩu giảng viên/học viên, nếu tồn tại trên máy này, nằm trong
  `docs/DEMO_ACCOUNTS.local.md`; file này được loại khỏi Git bằng `.gitignore`.
- Tài khoản admin được tạo riêng cho máy local; script hiển thị mật khẩu từ
  Windows DPAPI. Không ghi bí mật admin vào tài liệu hoặc ảnh chụp.
- Mật khẩu giảng viên là trạng thái DB local, không được đặt lại bởi seed trên
  database mới. Cần đặt lại riêng trước demo nếu DB mới chưa được chuẩn bị.
- Tài khoản demo không đại diện cho người dùng thật. E2E dùng tài khoản QA tạm
  thời và dọn đúng các tài khoản đó sau khi chạy.
