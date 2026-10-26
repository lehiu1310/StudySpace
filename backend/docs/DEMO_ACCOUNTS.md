# Tài khoản demo StudySpace

Cập nhật: 2026-10-22. Tên tài khoản và trạng thái dữ liệu được đối chiếu với
backend/PostgreSQL local. Không đưa mật khẩu vào tài liệu được chia sẻ.

Các tài khoản dưới đây chỉ dùng trình diễn trên máy local, không phải tài khoản
người dùng thật và không được dùng trên server public.

| Vai trò | Tên đăng nhập | Cách lấy thông tin xác thực | Trạng thái đã kiểm tra |
|---|---|---|---|
| Admin | `admin` | Tạo riêng trên máy local qua DPAPI; xem bằng `backend/show-admin-login.ps1` | Đăng nhập cổng quản trị local thành công; bí mật lưu DPAPI trong `backend/.local-secrets.xml`. |
| Giảng viên | `demo_gv_261015135744` | Chỉ lưu trong file local bị `.gitignore` | Đăng nhập cổng giảng viên local thành công. Có 3 khóa do tài khoản sở hữu (18, 19, 21), hiện là bản nháp/chưa xuất bản; có 3 lớp. |
| Học viên | `demo_focus_student` | Chỉ lưu trong file local bị `.gitignore` | Có snapshot 4 tuần phiên tập trung; hiện không có lịch sử quiz seed. |
| Giảng viên quay demo | `demo_cohort_teacher` | Chỉ lưu trong file local bị `.gitignore` | Đăng nhập và E2E tạo khóa/lớp/báo cáo với học viên cố định ngày 2026-10-22; sở hữu 3 khóa, 18 bài, 35 câu hỏi (30 đang hoạt động) và 2 lớp, mỗi lớp có 28 học viên. |
| Học viên quay demo | `demo_cohort_10` | Chỉ lưu trong file local bị `.gitignore` | Đăng nhập thành công ngày 2026-10-22; có 36 quiz đã nộp (05/08–21/10), 1 lượt chưa nộp bắt đầu 22/10, 27 phiên focus (04/08–21/10), 2 lớp và 3 kế hoạch tháng 11. |
| Học viên lớp mẫu | `demo_cohort_01` … `demo_cohort_28` | Chỉ lưu trong file local bị `.gitignore` | 12 tuần quiz/focus; nhóm điểm cao, trung bình, cần hỗ trợ và 5 tài khoản không hoạt động hơn 30 ngày. |

Seed nội dung cơ bản chạy khi `STUDYSPACE_SEED=true` (`.\\start-portable.ps1 -Seed`).
Snapshot focus chỉ được thêm nếu tài khoản chưa có snapshot; chạy lại không tạo
trùng. Không bật seed demo trên server public.

Rich cohort seed chỉ chạy khi truyền `-RichDemo` cho
`backend/start-portable.ps1`. Mật khẩu cohort local được giữ trong file ignored
`docs/DEMO_ACCOUNTS.md`; mỗi lần seed cập nhật mật khẩu cho namespace
`demo_cohort_*` và tái tạo hoạt động mẫu trong phạm vi các tài khoản đó. Không
sửa các tài khoản người dùng khác.

## Dùng dữ liệu local

- Mật khẩu giảng viên/học viên cố định để quay demo nằm trong
  `docs/DEMO_ACCOUNTS.md` ở máy local; file này được loại khỏi Git bằng
  `.gitignore`.
- Tài khoản admin được tạo riêng cho máy local; script hiển thị mật khẩu từ
  Windows DPAPI. Không ghi bí mật admin vào tài liệu hoặc ảnh chụp.
- Rich demo seed đặt lại mật khẩu của các tài khoản `demo_cohort_*` theo
  `STUDYSPACE_DEMO_PASSWORD`; seed không đổi mật khẩu các tài khoản khác.
- Tài khoản demo không đại diện cho người dùng thật. E2E dùng tài khoản QA tạm
  thời và dọn đúng các tài khoản đó sau khi chạy.
