# StudySpace

StudySpace là hệ thống hỗ trợ học trực tuyến gồm cổng web học viên/giảng viên/quản trị viên, backend Spring Boot và ứng dụng Android Kotlin/Compose. PostgreSQL là CSDL máy chủ; Room lưu dữ liệu cục bộ của ứng dụng Android.

## Yêu cầu

- Windows 10/11 hoặc Linux/macOS; JDK 17 trở lên (mã Java backend và Android target Java 17).
- PostgreSQL 14 trở lên, đang chạy và cho phép kết nối TCP.
- Android Studio/Android SDK chỉ cần nếu muốn build hoặc cài ứng dụng Android.
- Gemini API key là tùy chọn. Không đặt key trong mã nguồn hoặc APK.

## Tạo database PostgreSQL

Tạo role và database bằng tài khoản quản trị PostgreSQL (đổi mật khẩu mẫu bằng mật khẩu tự chọn):

```sql
CREATE USER studyspace WITH PASSWORD 'CHANGE_THIS_DATABASE_PASSWORD';
CREATE DATABASE studyspace OWNER studyspace;
```

Không commit mật khẩu đã thay vào Git. PostgreSQL mặc định lắng nghe cổng `5432`; nếu máy dùng host/cổng khác, sửa `DB_URL` tương ứng.

## Chạy backend và web

Từ thư mục gốc repo, PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/studyspace'
$env:DB_USER = 'studyspace'
$secureDb = Read-Host 'PostgreSQL password' -AsSecureString
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $secureDb).Password
$secureAdmin = Read-Host 'New admin password (minimum 12 characters)' -AsSecureString
$env:STUDYSPACE_ADMIN_PASSWORD = [Net.NetworkCredential]::new('', $secureAdmin).Password
$secureDemo = Read-Host 'Demo cohort password (minimum 12 characters)' -AsSecureString
$env:STUDYSPACE_DEMO_PASSWORD = [Net.NetworkCredential]::new('', $secureDemo).Password
. .\backend\load-gemini-config.ps1
if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) { Write-Output 'Gemini: chưa cấu hình' }
else { Write-Output "Gemini: đã cấu hình (nguồn: $env:STUDYSPACE_GEMINI_KEY_SOURCE)" }
.\gradlew.bat -p backend installDist --no-daemon
& '.\backend\build\install\studyspace-server\bin\studyspace-server.bat' '--studyspace.seed=true' '--studyspace.seed.rich-demo=true'
```

Gemini key là tùy chọn. Cách ổn định cho các lần chạy sau là sao chép
`.env.local.example` thành `.env.local`, rồi nhập key vào dòng `GEMINI_API_KEY=`.
`.env.local` đã bị `.gitignore` loại khỏi Git. Không gửi file này, không chụp
màn hình có key và không đặt key trong APK. Hoặc nhập kín trong PowerShell:

```powershell
$secureGemini = Read-Host 'Gemini API key (Enter để bỏ qua)' -AsSecureString
if ($secureGemini.Length -gt 0) { $env:GEMINI_API_KEY = [Net.NetworkCredential]::new('', $secureGemini).Password }
```

Launcher local/portable ưu tiên biến môi trường `GEMINI_API_KEY`, sau đó file
gốc `.env.local`, rồi mới dùng biến tương thích cũ `STUDYSPACE_GEMINI_API_KEY`;
nếu không có cấu hình nào thì backend dùng gợi ý offline. Launcher
in `Gemini: đã cấu hình (nguồn: ...)` hoặc `Gemini: chưa cấu hình`, không in key.
Backend cũng ghi đúng một dòng nguồn lúc khởi động. Nếu đặt key qua biến môi
trường, chạy launcher trong chính cửa sổ PowerShell đó. Sau khi đổi key, dừng
và khởi động lại backend.

Sau khi đăng nhập admin, gọi `GET /api/ai/health`. Endpoint probe Gemini và chỉ
trả `configured`, `status`, `message`, không trả key. Trạng thái gồm
`not_configured`, `invalid_key`, `quota_limited`, `available`, `project_denied`,
`access_denied` và `unavailable`. Health probe có gọi Gemini nên tiêu thụ một lượt nhỏ.

`--studyspace.seed=true` nạp nội dung học mẫu và tài khoản admin cục bộ nếu chưa có; `--studyspace.seed.rich-demo=true` tạo cohort dùng cho demo. Chỉ chạy một backend instance trên cùng DB. Seed có tính lặp an toàn; không bật seed demo trên production. Với DB cá nhân không muốn có cohort mẫu, bỏ hai tham số seed. Sau khi dừng backend, xóa secret khỏi terminal bằng `Remove-Item Env:STUDYSPACE_DEMO_PASSWORD, Env:STUDYSPACE_ADMIN_PASSWORD, Env:GEMINI_API_KEY`.

| Thành phần | Địa chỉ |
|---|---|
| Cổng học viên | `http://localhost:8080/student.html` |
| Cổng giảng viên | `http://localhost:8080/teacher.html` |
| Cổng quản trị | `http://localhost:8080/admin.html` |
| Đăng nhập | `http://localhost:8080/login.html` |
| Gemini health (admin session required) | `http://localhost:8080/api/ai/health` |
| PostgreSQL | `localhost:5432` |

## Chạy bộ dữ liệu demo local

Không dùng dữ liệu demo trên máy chủ public. Trên máy phát triển đã cấu hình PostgreSQL portable, lệnh `pwsh -File backend/start-portable.ps1 -Seed -RichDemo` khởi tạo schema, nội dung nền và cohort. Trước lệnh, truyền password runtime mà seed sẽ dùng:

```powershell
$secure = Read-Host 'Demo password (minimum 12 characters)' -AsSecureString
$env:STUDYSPACE_DEMO_PASSWORD = [Net.NetworkCredential]::new('', $secure).Password
pwsh -File backend/start-portable.ps1 -Seed -RichDemo
Remove-Item Env:STUDYSPACE_DEMO_PASSWORD
```

Lệnh portable chỉ dành cho môi trường đã có `backend/portable/` và file bí mật DPAPI local. Trên máy mới, dùng PostgreSQL cài đặt và lệnh `studyspace-server.bat` ở mục trên. Tên demo và trạng thái dữ liệu được liệt kê trong [DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md); bí mật không nằm trong repo. Launcher portable cũng đọc `.env.local` từ thư mục gốc repo.

## Kiểm thử

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:assembleDebug
.\gradlew.bat -p backend test --rerun-tasks
```

Kết quả kiểm chứng gần nhất: Android 56/56 test; backend 49/49 test. Nhật ký, phiên bản công cụ và bằng chứng ở [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Build và cài APK Android

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK debug đầu ra: `app/build/outputs/apk/debug/app-debug.apk`. Trên Android Emulator, backend host thường được truy cập qua `http://10.0.2.2:8080`; trên điện thoại thật dùng địa chỉ LAN của máy chạy backend và cùng mạng Wi-Fi. Ứng dụng phải được cấu hình đúng base URL trong Cài đặt nếu không dùng emulator.

## Tài liệu

- [Hồ sơ nộp](docs/submission/README.md) — ERD, use case, hướng dẫn sử dụng, thiết kế giao diện và bảng minh chứng.
- [Kịch bản demo](docs/DEMO_SCRIPT.md) — luồng trình diễn ba vai trò.
- [Kiểm chứng](docs/VERIFICATION.md) — kết quả test/build/E2E đã chạy.
- [Đối chiếu chức năng](docs/FEATURE_ALIGNMENT.md) và [lưu trữ phát triển](docs/DEV_STORAGE.md).

Không đưa `.env`, `application-local*`, file DPAPI, database local, `build/`, APK build và cache Gradle vào Git. Gemini key chỉ cấu hình ở môi trường backend; để trống thì các tính năng AI dùng fallback offline.
