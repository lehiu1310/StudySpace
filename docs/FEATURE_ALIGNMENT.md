# StudySpace: hợp nhất theo yêu cầu đề tài

Cập nhật: 2026-10-18. Trạng thái dưới đây đã được đối chiếu mã nguồn và cơ sở
dữ liệu local; các ghi chú cũ được xem là nhật ký lịch sử, không phải trạng thái hiện tại.

## Nguyên tắc

Một ứng dụng, một luồng học: tài khoản → môn → chương → bài → quiz → tiến độ.
Focus, kế hoạch, nhắc học và AI hỗ trợ luồng học, không phải ứng dụng thứ hai.
Không xóa dữ liệu Room/PostgreSQL của người dùng khi loại bỏ màn hình cũ.

## Đối chiếu yêu cầu

| Yêu cầu | Phần giữ/hợp nhất | Nguồn dữ liệu |
| --- | --- | --- |
| Quản lý nội dung học | Admin quản trị nội dung toàn hệ thống; giảng viên tạo/sửa khóa của mình tại `/teacher.html`, sắp thứ tự chương/bài; upload PDF/PPT/PPTX tối đa 25 MB hoặc dùng link HTTPS | PostgreSQL và `backend/uploads` |
| Quiz trắc nghiệm | Ngân hàng câu hỏi, giới hạn thời gian, chấm ở server; quiz thích ứng dùng tối đa ba lượt gần nhất, ưu tiên câu sai/câu chưa gặp; có lịch sử và ôn câu sai | PostgreSQL |
| Theo dõi tiến độ | Bài đã học, quiz đã nộp, kế hoạch hoàn thành và phút focus được theo dõi riêng | PostgreSQL + Room theo tài khoản |
| Quản lý người dùng | Đăng ký/đăng nhập; vai trò STUDENT/TEACHER/ADMIN; admin gán vai trò và khóa tài khoản | PostgreSQL; phiên Android được bảo vệ bằng Keystore |
| Lớp học và ghi danh | Giảng viên tạo lớp/mã tham gia; học viên ghi danh; giảng viên xem lớp mình phụ trách | PostgreSQL |
| Báo cáo thống kê | Báo cáo học tập/PDF, lịch sử focus/PDF, báo cáo quản trị/CSV, báo cáo lớp theo chương/học viên/câu sai nhiều | Dữ liệu thật; tài khoản mới không tự sinh số liệu |
| Cơ sở dữ liệu quan hệ | PostgreSQL là cơ sở dữ liệu hệ thống; Room là kho cục bộ Android | Không coi Room là thay thế PostgreSQL |
| Trực quan hóa | Chart.js ở cổng web; biểu đồ focus và `MasteryRadar` vẽ bằng Compose Canvas trên Android | Điểm và phiên học thật |
| Tài liệu hệ thống | ERD, use case, hướng dẫn và giới hạn | `docs/submission/` |

## Xử lý ứng dụng cũ

- **Giữ:** FocusScreen, chu kỳ tập trung/nghỉ, âm nền, kế hoạch Room/WorkManager,
  AI Gemini/fallback, BCrypt, Keystore, BiometricPrompt và PDF SDK.
- **Hợp nhất:** ProductAuth, Tổng quan, Kế hoạch và Cá nhân thành các màn chính;
  biểu đồ focus và AI nằm trong StudyInsights.
- **Loại bỏ khỏi luồng chính:** DashboardLegacy, PlannerLegacy, ProfileLegacy,
  GroupsLegacy, LegacyAuthentication, form LearningLogin trùng, đăng ký local
  mới, chat/thành viên giả, đồng bộ cloud chỉ chờ 2 giây, OTP chỉ hiện trong app,
  và cách cộng phút focus khi chỉ đánh dấu xong kế hoạch.
- **Giữ dữ liệu:** Room cũ không bị xóa. Hồ sơ local chỉ được nối khi username và
  mật khẩu khớp; dữ liệu thuộc username khác không tự động chuyển.
- **Thảo luận:** tin nhắn/presence đi qua backend; giao diện polling 5 giây,
  không gọi là WebSocket/realtime.

## Quy tắc tiến độ

1. Hoàn thành bài: ghi `lesson_progress` ở server; sau xác nhận, hoàn thành kế
   hoạch local liên kết với bài và hủy reminder của kế hoạch.
2. Nộp quiz: server chấm và lưu `quiz_attempts`; AI không thay thế điểm chấm.
3. Hoàn thành kế hoạch: ghi `isCompleted` trong Room; giao dịch kiểm tra quyền
   sở hữu và không cộng thưởng trùng.
4. Phút tập trung lấy từ `focus_sessions`, không lấy từ `targetMinutes` của việc
   được đánh dấu hoàn thành.
5. Kế hoạch tạo từ bài giữ liên kết để mở lại bài hoặc bắt đầu focus theo mục tiêu.

## Giới hạn đã xác minh

- Đồng bộ planner/focus nền có WorkManager: Room flow debounce 12 giây, sau đó
  worker một lần có initial delay 12 giây (chưa tính thời gian chờ mạng); worker
  định kỳ 6 giờ yêu cầu có mạng. Periodic work được enqueue khi persist phiên
  sau đăng nhập/bind tài khoản. Khi có xung đột, người dùng chọn bản local hoặc
  server; chưa hợp nhất từng bản ghi và không phải đồng bộ realtime.
- Giảng viên chỉ quản lý nội dung khóa do mình tạo; khóa hệ thống do admin tạo
  vẫn thuộc admin.
- Upload hỗ trợ PDF/PPT/PPTX tối đa 25 MB; video/tài liệu khác dùng link HTTPS.
  Backend phục vụ file từ thư mục upload, cần backup cùng dữ liệu.
- Android có `MasteryRadar` vẽ bằng Compose Canvas trong
  `StudyExperienceScreens.kt`, không dùng thư viện biểu đồ ngoài.
- Gemini trực tiếp chưa hoạt động ở cấu hình local ngày 2026-10-18: health báo
  `access_denied` sau khi thử gọi thật; Google trả HTTP 403
  `PERMISSION_DENIED` với thông điệp project bị từ chối quyền. Chat, giải thích
  câu sai và quiz ôn tập 5 câu đã được kiểm tra ở chế độ `offline`; không coi đó
  là Gemini trực tiếp. Admin health không trả API key.
- Web E2E luồng giảng viên → học viên → bài học → quiz → báo cáo PASS 24 bước
  ngày 2026-10-17. Android/emulator, focus/nghỉ, chat hai thiết bị, PDF, offline
  và quyền thông báo chưa được xác minh trực tiếp trên thiết bị trong đợt này.
