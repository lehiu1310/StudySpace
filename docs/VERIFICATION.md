# Kiểm chứng StudySpace

Cập nhật: 2026-10-19. Chỉ trạng thái và số liệu hiện hành được giữ ở đây; nhật ký cũ có số liệu đã lỗi thời đã được gỡ để không bị nhầm với lần kiểm tra hiện tại.

## Trạng thái

| Hạng mục | Kết quả đã kiểm tra |
|---|---|
| PostgreSQL | 18 bảng, 102 cột, 26 khóa ngoại; đối chiếu ERD trong `docs/submission/02-erd.md`. |
| Android unit test | 56/56 PASS, 0 fail, 0 error, 0 skip; 18 suite. |
| Backend test | 49/49 PASS, 0 fail, 0 error, 0 skip; 13 suite. |
| Web E2E | 24/24 bước PASS trên luồng giảng viên → học viên; cleanup dữ liệu QA thành công. |
| Cổng giảng viên responsive | PASS tại 1440×900, 768×900, 390×844, 360×844; không tràn ngang; mục Xóa khóa học không render. |
| Cohort demo | PASS: giảng viên/học viên đăng nhập; 3 khóa, 18 bài, 31 câu hỏi, 2 lớp × 28 học viên và hoạt động nhiều tuần. |
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
