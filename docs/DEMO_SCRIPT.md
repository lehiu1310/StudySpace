# Kịch bản demo StudySpace — 9 phút

Cập nhật: 2026-10-22. Kịch bản dùng giao diện web hiện tại và bộ cohort demo
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
- Gemini key là tùy chọn. Tạo file local bằng
  `Copy-Item .env.local.example .env.local`, rồi nhập key vào `GEMINI_API_KEY=`
  (hoặc đặt biến môi trường `GEMINI_API_KEY` trong chính terminal chạy backend).
  `.env.local` đã bị Git ignore. Không đưa key/mật khẩu vào command line, Git,
  ảnh chụp hoặc tài liệu nộp.
- Với cách chạy thủ công, nạp cấu hình bằng `. .\backend\load-gemini-config.ps1`;
  helper ưu tiên `GEMINI_API_KEY`, tiếp đến `.env.local`, rồi biến tương thích
  cũ `STUDYSPACE_GEMINI_API_KEY`; helper không in key.
- Chạy lại backend để nạp key. Launcher in trạng thái và nguồn cấu hình, không
  in giá trị. Backend log một dòng nguồn lúc khởi động. Admin gọi
  `GET /api/ai/health` để xem `not_configured`, `invalid_key`, `quota_limited`,
  `available`, `project_denied`, `access_denied` hoặc `unavailable`. Endpoint chỉ trả trạng thái
  và thông báo an toàn; mỗi lần kiểm tra có thể tiêu thụ một lượt Gemini nhỏ.
- Kiểm chứng local ngày 2026-10-22: lần health check mới nhất trả HTTP 200,
  `configured=true`, `status=available`; kiểm thử trực tiếp cũng nhận
  `provider=gemini` ở chat bài học và sinh quiz giảng viên. Trạng thái quota có
  thể đổi; trước buổi quay hãy kiểm tra lại health và luôn đọc nhãn provider
  ngay trên giao diện. Key từng bị lộ phải được thu hồi; giữ key thay thế trong
  `.env.local`, không dán vào tài liệu hoặc command history.
  Xem [hướng dẫn xử lý Gemini](https://ai.google.dev/gemini-api/docs/troubleshooting)
  nếu trạng thái vẫn không hoạt động.
- Chờ trang `http://localhost:8080/` mở được; endpoint trạng thái Gemini
  `/api/ai/health` cần phiên admin.
- Tài khoản: admin `admin`; giảng viên `demo_cohort_teacher`; học viên
  `demo_cohort_10`. Bí mật xác thực chỉ nằm trong file local đã bị
  `.gitignore`; không chụp hoặc chia sẻ file đó.
- Cặp tài khoản cố định để quay luồng học là giảng viên `demo_cohort_teacher`
  và học viên `demo_cohort_10`; cùng mật khẩu nằm trong file local bị
  `.gitignore`. Seed rich demo đặt lại mật khẩu cho đúng namespace demo theo
  biến runtime `STUDYSPACE_DEMO_PASSWORD`.
- Cohort local có 3 khóa, 18 bài, 35 câu hỏi (30 đang hoạt động), 2 lớp với
  28 học viên/lớp.
  Truy vấn DB ngày 22/10/2026 xác nhận 918 lượt quiz trong cohort từ 05/08
  đến 21/10, 694 phiên tập trung từ 04/08 đến 22/10, và 3 kế hoạch của học
  viên demo có hạn từ 03/11 đến 24/11/2026. Hoạt động tháng 11 chưa diễn ra
  vì ngày hiện tại là 22/10; chỉ các kế hoạch tháng 11 là dữ liệu tương lai.
  Riêng học viên `demo_cohort_10` có 36 quiz đã nộp từ 05/08–21/10, một lượt
  quiz chưa nộp bắt đầu ngày 22/10, 27 phiên focus từ 04/08–21/10 và 2 lớp.
  Lần rà API ngày 22/10 không thấy kế hoạch QA còn sót.
  Dữ liệu này là demo riêng, không đại diện cho tài khoản người dùng thật.
- Ảnh Playwright đối chiếu hai vai trò ở 1440x900 và 390x844 nằm tại
  `backend/test-evidence/demo-cohort-*.png`; đây là ảnh kiểm thử local ngày
  22/10/2026, không phải ảnh từ thiết bị Android.
- Ảnh Playwright UI AI offline ngày 22/10/2026: học viên chat tại
  `backend/test-evidence/demo-ai-student-chat-offline.png`; giảng viên xem bản
  nháp môn tại `backend/test-evidence/demo-ai-teacher-course-offline.png` và
  tạo 5 câu quiz tại `backend/test-evidence/demo-ai-teacher-quiz-offline.png`.
  Đây là fallback offline, không phải ảnh Gemini trực tiếp.
- Ảnh Playwright Gemini trực tiếp ngày 22/10/2026: hỏi gia sư ngay trong bài
  học tại `backend/test-evidence/demo-ai-student-chat-live-context-desktop.png`
  (gửi đủ 235/235 ký tự nội dung bài, nhãn “AI trực tiếp · Gemini”, không lỗi
  JavaScript); giảng viên tạo 5 câu tại
  `backend/test-evidence/demo-ai-teacher-quiz-live-desktop.png`.
- Ảnh quiz ôn tập cá nhân hóa Gemini của học viên: đề 5 câu desktop/mobile tại
  `backend/test-evidence/demo-ai-review-quiz-live-{desktop,mobile}.png`; kết quả
  chấm 4/5 và lời giải thích AI cho câu sai tại
  `backend/test-evidence/demo-ai-review-quiz-live-result-mobile.png`. Đây là
  attempt QA đã được dọn; khi quay tương tác thật, việc nộp quiz sẽ lưu thêm
  một lượt vào lịch sử học viên.
- Ảnh màn Kế hoạch học tập mới kiểm tra bằng tài khoản `demo_cohort_10` tại
  `backend/test-evidence/student-plans-desktop.png` (1440×900) và
  `backend/test-evidence/student-plans-mobile.png` (390×844). Ba việc tháng 11
  được giữ nguyên sau kiểm thử tạo/hoàn thành/xóa một việc QA tạm.
- Ảnh cổng giảng viên sau khi sửa lỗi sinh quiz trùng:
  `backend/test-evidence/demo-ai-teacher-quiz-fixed-desktop.png` và
  `backend/test-evidence/demo-ai-teacher-quiz-fixed-mobile.png`. Năm câu là bản
  nháp offline, mỗi câu có bốn lựa chọn và giải thích, chưa được lưu; mobile
  390px không tràn ngang.
- Nếu chưa có phiên, đăng nhập cùng cổng tại `/login.html?staff=1` cho admin/
  giảng viên; học viên dùng `/login.html`. Đăng xuất trước khi đổi vai trò trong
  cùng profile trình duyệt.

## Trình diễn

| Thời gian | Vai trò và thao tác | Nội dung cần nêu |
|---|---|---|
| 0:00–1:00 | **Admin:** đăng nhập tại `/login.html?staff=1`, mở mục Người dùng rồi Báo cáo; đăng xuất. | Admin quản lý tài khoản/vai trò và số liệu toàn hệ thống. |
| 1:00–2:30 | **Giảng viên:** đăng nhập `demo_cohort_teacher`. Xem Tổng quan: bốn thẻ số liệu và khối **Cần chú ý** gồm học viên cần hỗ trợ, câu hỏi sai nhiều, lớp phụ trách. | Dữ liệu mẫu có học viên cần theo dõi và tỷ lệ sai theo câu; mã lớp có thể sao chép. |
| 2:30–3:30 | Vào **Khóa học**, mở hỗ trợ Gemini để xem đề cương môn 4 chương (bản nháp, chưa lưu); qua **Ngân hàng quiz** sinh 5 câu từ chương thật, kiểm tra nhãn nguồn rồi thoát không lưu. Sau đó mở menu `⋯` của khóa để xem **Sửa** và **Ẩn/Xuất bản**. | Gemini hỗ trợ giảng viên soạn nội dung và câu hỏi; giảng viên duyệt trước khi lưu. Khóa chỉ ẩn/hiện để giữ lịch sử. |
| 3:30–4:30 | Vào **Lớp học**, chọn lớp `CSDL · Lớp thực hành 01`, mở **Báo cáo** và xem biểu đồ điểm theo chương, tỷ lệ sai theo câu, heatmap 28 học viên × 3 chương và danh sách cần chú ý. Sao chép mã tham gia nếu cần. | Báo cáo dùng dữ liệu cohort 12 tuần; chương Chuẩn hóa có điểm thấp nhất, câu hỏi khó nhất có tỷ lệ sai khoảng 51%. |
| 4:30–5:00 | Đăng xuất giảng viên; đăng nhập học viên `demo_cohort_10` tại `/login.html`. | Vai trò học viên có khu vực và menu riêng. |
| 5:00–6:00 | Vào **Môn học**, mở **Cơ sở dữ liệu · Lớp thực hành mẫu**, chọn bài đang mở. | Nội dung đi theo Khóa học → Chương → Bài; bài sau khóa cho tới khi hoàn thành bài trước. |
| 6:00–7:00 | Đánh dấu bài hoàn thành, mở quiz chương, chọn đáp án và nộp. Xem điểm, đáp án và giải thích. | Backend lưu tiến độ/lịch sử và chịu trách nhiệm chấm quiz. |
| 7:00–8:00 | Mở **Báo cáo** xem điểm/lịch sử; quay lại **Lớp học** để xem thảo luận, rồi mở **Kế hoạch**. Chỉ mục tiêu tuần và ba việc học có hạn 03/11, 10/11, 24/11; không đánh dấu hoàn thành việc tương lai. | Báo cáo phản ánh hoạt động đã lưu trong giai đoạn 03/08–21/10; các mốc tháng 11 là kế hoạch, chưa phải hoạt động đã xảy ra. Mục tiêu tuần của bản web lưu trên trình duyệt hiện tại. |
| 8:00–9:00 | Tại **Kế hoạch**, nhắc ba việc tháng 11 là kế hoạch sắp tới. Mở một bài học, hỏi gia sư về nội dung đang xem và đọc nhãn nguồn. Nếu còn thời gian, về Tổng quan bấm **Ôn tập cá nhân hóa**, nộp một câu sai rồi bấm giải thích AI; đăng xuất. | Lượt kiểm tra local 22/10/2026: health `configured=true`, `available`; chat bài học gửi đủ nội dung bài và trả `provider=gemini`. Quiz ôn cá nhân hóa đã được kiểm tra 5 câu, điểm 4/5 và giải thích câu sai trực tiếp. Nếu nhãn đổi sang offline, nói rõ đó là gợi ý dự phòng, không gọi là Gemini. |

## Nếu có sự cố

- **AI chưa sẵn sàng/hết quota:** xem `GET /api/ai/health` bằng phiên admin.
  Kết quả có thể thay đổi theo hạn mức hiện hành; chỉ khẳng định đang dùng
  Gemini khi UI hiện “AI trực tiếp · Gemini”. Nếu hiện “Gợi ý offline”, tiếp
  tục demo bằng gợi ý dự phòng và không gọi đó là câu trả lời Gemini. Key từng
  bị lộ phải được thu hồi; lưu key thay thế trong `.env.local`, khởi động lại
  backend và kiểm tra health trước khi quay.
  Quota áp dụng theo project, không theo từng key, nên key mới trong cùng
  project không tự tăng quota. Kiểm tra project và giới hạn hiện hành trong
  [AI Studio Rate Limits](https://ai.google.dev/gemini-api/docs/rate-limits).
  Nếu vẫn bị giới hạn, kiểm tra quota/project trong AI Studio và tham
  khảo [hướng dẫn xử lý Gemini API](https://ai.google.dev/gemini-api/docs/troubleshooting)
  cùng [quản lý API key](https://ai.google.dev/gemini-api/docs/api-key).
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

Luồng E2E 24 bước nằm trong `backend/web-e2e-journey.cjs`. Chế độ mặc định tạo
tài khoản QA tạm; để chạy đúng cặp tài khoản cố định, đặt
`STUDYSPACE_FIXED_DEMO=1` cùng `STUDYSPACE_DEMO_PASSWORD` và
`STUDYSPACE_DB_PASSWORD`. Chế độ này chỉ tạo rồi dọn khóa/lớp/quiz test; không
xóa tài khoản hay lịch sử học kỳ. Xác nhận dòng `CLEANUP` thành công. Không chạy
script trên database production.

Màn kế hoạch được kiểm thử riêng bằng `node backend/student-plans-e2e.cjs` với
`STUDYSPACE_DEMO_USER=demo_cohort_10` và `STUDYSPACE_DEMO_PASSWORD` đọc từ file
local ignored. Bài test thêm, hoàn thành rồi xóa một việc QA và xác nhận ba việc
tháng 11 cùng toàn bộ phiên tập trung được giữ nguyên; ảnh desktop/mobile được
ghi trong `backend/test-evidence/student-plans-*.png`.

Quiz AI của giảng viên được kiểm tra bằng `node backend/teacher-ai-quiz-e2e.cjs`
với cùng mật khẩu local và `STUDYSPACE_DEMO_TEACHER=demo_cohort_teacher`. Bài
test chỉ tạo bản nháp chưa lưu; xác nhận năm câu riêng biệt, bốn lựa chọn, giải
thích, nhãn provider và không có lỗi JavaScript.

Kết quả chạy local ngày 22/10/2026: luồng trên **24/24 bước PASS**, cleanup
thành công; kiểm tra cohort desktop/mobile có **20/20 assertions PASS**. Backend
**50/50** và Android **56/56** unit/integration tests PASS; `assembleDebug`
thành công. E2E Gemini trực tiếp của học viên xác nhận ngữ cảnh bài học 235/235
  ký tự, nhãn provider chính xác và 0 lỗi trình duyệt; ảnh ở
  `backend/test-evidence/demo-ai-student-chat-live-context-desktop.png`. Giảng
  viên có ảnh Playwright desktop/mobile sinh 5 câu với nhãn Gemini trực tiếp tại
  `backend/test-evidence/demo-ai-teacher-quiz-live-{desktop,mobile}.png`. Trạng thái AI
phải được health/UI kiểm tra lại ngay trước khi quay vì quota có thể đổi.

Để đăng nhập kiểm chứng cả ba vai trò và chụp ảnh trang báo cáo, đặt
`STUDYSPACE_DEMO_PASSWORD` trong terminal rồi chạy
`node backend/demo-login-evidence.cjs`. Script chỉ in PASS/FAIL, không in
password hoặc token. Ảnh được lưu trong `docs/submission/screenshots/`.
