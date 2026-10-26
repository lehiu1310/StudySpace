# Kiểm chứng StudySpace

Cập nhật: 2026-10-22. Chỉ trạng thái và số liệu đã xác minh trên máy local được giữ ở đây.

## Trạng thái

| Hạng mục | Kết quả đã kiểm tra |
|---|---|
| PostgreSQL | 18 bảng, 102 cột, 26 khóa ngoại; đối chiếu ERD trong `docs/submission/02-erd.md`. |
| Android unit test | 56/56 PASS, 0 fail, 0 error, 0 skip; 18 suite. APK debug build thành công; chưa có thiết bị để kiểm tra trực quan. |
| Backend test | 50/50 PASS, 0 fail, 0 error, 0 skip; 13 suite. |
| Web E2E | 24/24 bước PASS trên luồng giảng viên → học viên; cleanup dữ liệu QA thành công. |
| Fixed-account UI smoke | PASS: giảng viên và học viên đăng nhập thật; hai màn hình sẵn sàng tại 1440×900 và 390×844, không tràn ngang hoặc lỗi JavaScript chưa bắt. |
| Fixed-account screenshots | Playwright desktop/mobile thật tại `backend/test-evidence/current-demo-smoke-{teacher,student}-{desktop,mobile}.png`. |
| Next-lesson loading regression | PASS: `node backend/student-next-lesson-e2e.cjs`, 11 assertions; response trì hoãn 1,5 giây, loading state đúng, nút mở đúng bài tiếp theo ở desktop/mobile. |
| Cổng giảng viên responsive | PASS tại 1440×900, 768×900, 390×844, 360×844; không tràn ngang; mục Xóa khóa học không render. |
| Cohort demo | PASS: hai tài khoản cố định đăng nhập; giảng viên sở hữu 3 khóa, 18 bài, 35 câu hỏi (30 đang hoạt động), 2 lớp × 28 học viên; học viên có 36 quiz đã nộp, 1 lượt chưa nộp, 27 phiên focus, 2 lớp và 3 kế hoạch tháng 11. |
| Kế hoạch học viên | PASS: 14 assertions, thêm/hoàn thành/xóa QA rồi đối chiếu nguyên bản kế hoạch và focus; 1440×900, 390×844, không tràn ngang. |
| AI quiz giảng viên | PASS: offline trả 5 câu khác nhau, bốn lựa chọn và giải thích; Playwright desktop/mobile, không tràn ngang ở 390px; bản nháp chưa lưu. |
| AI Gemini trực tiếp | PASS: health admin HTTP 200 `configured=true`, `available`; học viên hỏi bài thật với 235/235 ký tự ngữ cảnh, giải thích câu sai lịch sử 357 ký tự; quiz ôn 5 câu giấu đáp án, chấm đúng 4/5 khi cố ý chọn một câu sai và Gemini giải thích đúng câu đó. Giảng viên tạo đề cương 4 chương và sinh 5 câu/20 lựa chọn/5 giải thích. UI desktop/mobile đều nhãn Gemini, không tràn ngang. AI timeout UI 35 giây (upstream tối đa 25 giây); API thường 12 giây. Ảnh: `backend/test-evidence/demo-ai-student-chat-live-context-desktop.png`, `backend/test-evidence/demo-ai-review-quiz-live-{desktop,mobile}.png`, `backend/test-evidence/demo-ai-review-quiz-live-result-mobile.png`, `backend/test-evidence/demo-ai-teacher-quiz-live-{desktop,mobile}.png`. |
| Đăng nhập demo | PASS trên DB hiện có và DB PostgreSQL cô lập mới: admin, giảng viên cohort, học viên cohort. Mật khẩu không được ghi vào log/ảnh. |
| Ảnh báo cáo | Playwright desktop: tiến độ học viên, báo cáo lớp và biểu đồ Chart.js trong báo cáo quản trị; lưu tại `docs/submission/screenshots/`. Báo cáo lớp hiện dùng bảng/heatmap, không phải Chart.js. |
| Android APK | Debug APK được build tại `app/build/outputs/apk/debug/app-debug.apk`; **chưa kiểm tra trực quan APK hiện tại trên thiết bị** trong lượt chốt này. |

## Cách chạy lại

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-daemon --max-workers=1 --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 --console=plain
.\gradlew.bat -p backend test --rerun-tasks --no-daemon --max-workers=1 --console=plain
node backend/web-e2e-journey.cjs
```

E2E dùng database local, tạo tài khoản/khóa/lớp tạm và phải in trạng thái cleanup thành công. Không chạy trên production. `backend/demo-login-evidence.cjs` xác thực đăng nhập và chụp ảnh; cần truyền runtime `STUDYSPACE_ADMIN_PASSWORD` và `STUDYSPACE_DEMO_PASSWORD` cùng địa chỉ backend tùy chọn qua `STUDYSPACE_BASE_URL`.

## DB sạch và hướng dẫn cài đặt

README đã được thử trên database PostgreSQL mới, riêng biệt với DB phát triển. Backend tự tạo schema (18 bảng), seed dữ liệu mẫu và cohort; sau khi khởi động lại backend trên DB đó, đăng nhập ba vai trò đều PASS. DB kiểm tra là dữ liệu tạm do lượt xác minh tạo, không dùng DB phát triển hiện có.

## Bảo mật và dữ liệu demo

- Mật khẩu seed nhận qua `STUDYSPACE_DEMO_PASSWORD`; không còn mật khẩu mặc định trong mã nguồn.
- Admin seed nhận qua `STUDYSPACE_ADMIN_PASSWORD`; file DPAPI và các file chứa tài khoản/mật khẩu demo local bị `.gitignore` loại trừ.
- Gemini key tùy chọn, chỉ cấu hình ở backend bằng `GEMINI_API_KEY`; không đưa vào APK, Git hoặc ảnh.
- Git vừa được khởi tạo trong workspace; vì trước đó chưa có `.git`, không thể truy ngược lịch sử trước lần commit đầu. Chênh lệch các con số cũ không được coi là bằng chứng test bị xóa.
- Không có xác nhận trực quan cho APK Android hiện tại. Không dùng ảnh Android cũ thay bằng chứng thiết bị mới.
