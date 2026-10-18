# Kịch bản demo StudySpace — 9 phút

Cập nhật: 2026-10-19. Kịch bản dùng giao diện web hiện tại và bộ cohort demo
local. Không dùng tài khoản demo trên production; không ghi mật khẩu vào tài liệu
này hoặc tài liệu nộp.

## Chuẩn bị

- Trên máy mới, cài JDK 17/PostgreSQL và tạo DB theo [README](../README.md).
- Truyền `DB_URL`, `DB_USER`, `DB_PASSWORD`, `STUDYSPACE_ADMIN_PASSWORD` và
  `STUDYSPACE_DEMO_PASSWORD` vào môi trường của terminal chạy backend. Trên
  PowerShell nhập mật khẩu cohort dạng kín:
  `$s=Read-Host 'Demo password' -AsSecureString; $env:STUDYSPACE_DEMO_PASSWORD=[Net.NetworkCredential]::new('', $s).Password`.
  Build bằng `.\gradlew.bat -p backend installDist --no-daemon`, rồi chạy
  `backend/build/install/studyspace-server/bin/studyspace-server.bat
  --studyspace.seed=true --studyspace.seed.rich-demo=true`.
- `GEMINI_API_KEY` là tùy chọn; để trống thì AI dùng gợi ý offline. Không đưa
  key/mật khẩu vào command line, Git, ảnh chụp hoặc tài liệu nộp.
- Chờ trang `http://localhost:8080/` mở được; endpoint trạng thái Gemini
  `/api/ai/health` cần phiên admin.
- Tài khoản: admin `admin`; giảng viên `demo_cohort_teacher`; học viên
  `demo_cohort_10`. Bí mật xác thực chỉ nằm trong file local đã bị
  `.gitignore`; không chụp hoặc chia sẻ file đó.
- Cohort local hiện có 3 khóa, 18 bài, 31 câu hỏi, 2 lớp với 28 học viên/lớp
  và hoạt động học nhiều tuần. Dữ liệu này là demo riêng, không đại diện cho
  tài khoản người dùng thật.
- Nếu chưa có phiên, đăng nhập cùng cổng tại `/login.html?staff=1` cho admin/
  giảng viên; học viên dùng `/login.html`. Đăng xuất trước khi đổi vai trò trong
  cùng profile trình duyệt.

## Trình diễn

| Thời gian | Vai trò và thao tác | Nội dung cần nêu |
|---|---|---|
| 0:00–1:00 | **Admin:** đăng nhập tại `/login.html?staff=1`, mở mục Người dùng rồi Báo cáo; đăng xuất. | Admin quản lý tài khoản/vai trò và số liệu toàn hệ thống. |
| 1:00–2:30 | **Giảng viên:** đăng nhập `demo_cohort_teacher`. Xem Tổng quan: bốn thẻ số liệu và khối **Cần chú ý** gồm học viên cần hỗ trợ, câu hỏi sai nhiều, lớp phụ trách. | Dữ liệu mẫu có học viên cần theo dõi và tỷ lệ sai theo câu; mã lớp có thể sao chép. |
| 2:30–3:30 | Vào **Khóa học** từ sidebar desktop hoặc tab đáy mobile. Mở menu `⋯` của khóa để xem **Sửa** và **Ẩn/Xuất bản**. Thử mở **Chương học** hoặc **Bài học & tài liệu** từ tab **Thêm** trên mobile. | Khóa không bị xóa để giữ lịch sử; chỉ có thể ẩn/hiện. Form thêm/sửa mở trong ngăn kéo. |
| 3:30–4:30 | Vào **Lớp học**, chọn lớp `CSDL · Lớp thực hành 01`, mở **Báo cáo** và xem tiến độ theo chương/học viên. Sao chép mã tham gia nếu cần. | Báo cáo phản ánh dữ liệu lớp mẫu; mã lớp dùng để ghi danh. |
| 4:30–5:00 | Đăng xuất giảng viên; đăng nhập học viên `demo_cohort_10` tại `/login.html`. | Vai trò học viên có khu vực và menu riêng. |
| 5:00–6:00 | Vào **Môn học**, mở **Cơ sở dữ liệu · Lớp thực hành mẫu**, chọn bài đang mở. | Nội dung đi theo Khóa học → Chương → Bài; bài sau khóa cho tới khi hoàn thành bài trước. |
| 6:00–7:00 | Đánh dấu bài hoàn thành, mở quiz chương, chọn đáp án và nộp. Xem điểm, đáp án và giải thích. | Backend lưu tiến độ/lịch sử và chịu trách nhiệm chấm quiz. |
| 7:00–8:00 | Mở **Báo cáo** xem lịch sử, điểm và thời lượng học; quay lại **Lớp học** để đọc thảo luận/thông báo. | Thống kê lấy từ hoạt động học đã lưu. |
| 8:00–9:00 | Mở chức năng AI nếu cần; đọc nhãn **AI trực tiếp · Gemini** hoặc **Gợi ý offline**. Kết thúc bằng đăng xuất. | Không gọi fallback là Gemini. Nếu offline/quota lỗi, nêu rõ chế độ gợi ý offline. |

## Nếu có sự cố

- **AI chưa sẵn sàng/hết quota:** trình bày nhãn trạng thái và gợi ý offline; không
  khẳng định đã gọi Gemini.
- **Mạng/backend lỗi:** không lặp thao tác ghi; kiểm tra `http://localhost:8080`
  rồi thử lại. Có thể dùng ảnh trong `docs/submission/screenshots/`, nói rõ đó
  là ảnh kiểm chứng chứ không phải thao tác live.
- **Không đăng nhập được:** xác nhận đúng username và vai trò, kiểm tra database
  local; chỉ người vận hành máy mới lấy thông tin bí mật từ file local ignored.
- **Khóa không thấy ở học viên:** xác nhận khóa đang xuất bản. Khóa nháp/đã ẩn
  không hiện trong danh mục học viên.
- **Không đủ thời gian:** dùng khóa mẫu đã xuất bản, bỏ qua soạn nội dung mới và
  giữ luồng minh họa đăng nhập → học bài → quiz → báo cáo.

## Kiểm thử tự động

Luồng E2E 24 bước nằm trong `backend/web-e2e-journey.cjs`. Script tạo tài khoản,
khóa, lớp và hoạt động QA tạm thời; sau khi chạy phải xác nhận dòng `CLEANUP`
thành công. Không chạy script trên database production.

Để đăng nhập kiểm chứng cả ba vai trò và chụp ảnh trang báo cáo, đặt
`STUDYSPACE_DEMO_PASSWORD` trong terminal rồi chạy
`node backend/demo-login-evidence.cjs`. Script chỉ in PASS/FAIL, không in
password hoặc token. Ảnh được lưu trong `docs/submission/screenshots/`.
