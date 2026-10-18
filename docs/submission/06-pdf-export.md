# 6. Trạng thái kiểm chứng xuất PDF

Cập nhật: 2026-10-19. Chỉ ghi điều có bằng chứng trong mã nguồn và các log hiện
có trong workspace.

## Kết luận

- Không tìm thấy stack trace `FATAL EXCEPTION` đã lưu gắn với thao tác xuất PDF.
  Vì thiếu log crash gốc nên **chưa xác định được nguyên nhân lịch sử**; không
  thể khẳng định lỗi cũ là quyền bộ nhớ, `FileProvider`, null dữ liệu hay lỗi
  thư viện.
- Đường xuất báo cáo tiến độ hiện dùng Android Storage Access Framework:
  `ProductReportsScreen` gọi `ActivityResultContracts.CreateDocument("application/pdf")`
  (`app/src/main/java/com/example/learning/StudyExperienceScreens.kt:194-196`).
  Luồng này không cần `WRITE_EXTERNAL_STORAGE` và không dùng `FileProvider`.
- `LearningViewModel.prepareProgressReportExport()` kiểm tra nội dung trước khi
  mở hộp chọn file; nếu chưa có dữ liệu hợp lệ, trả lỗi thân thiện
  (`app/src/main/java/com/example/learning/LearningViewModel.kt:477-489`).
- Khi lưu, `exportReport()` mở URI bằng `ContentResolver`, ghi PDF trên
  `Dispatchers.IO`, bắt exception và ghi tag `StudySpacePdfExport`
  (`LearningViewModel.kt:491-514`). `task()` nhận lỗi để đưa ra UI thay vì để
  exception thoát khỏi ViewModel (`LearningViewModel.kt:183-200`).
- Tệp PDF được dựng bằng `android.graphics.pdf.PdfDocument` trong
  `ProgressPdfExporter.kt`; `StudyRepository.exportToPDF()` là đường xuất lịch
  sử focus/plan riêng và lưu trong app Documents (`StudyRepository.kt:112-177`).
- Android unit test có kiểm tra dữ liệu báo cáo rỗng/hợp lệ; chưa có bằng chứng
  kiểm thử thao tác ghi file PDF trên thiết bị trong lượt này. Không có emulator
  ADB kết nối để tái hiện crash hoặc xác nhận file mở được.

## Cách tự kiểm tra trên điện thoại

1. Cài APK debug mới và đăng nhập tài khoản có tiến độ bài/quiz.
2. Mở **Cá nhân → Báo cáo học tập → Xuất tiến độ và điểm quiz (PDF)**.
3. Chọn thư mục/tên file trong bộ chọn lưu của Android; chờ thông báo thành
   công rồi mở PDF từ Files để xác nhận có nội dung.
4. Lặp lại bằng tài khoản chưa có tiến độ/quiz: app phải báo chưa có dữ liệu,
   không mở app crash dialog.
5. Lặp lại với **Xuất lịch sử tập trung (PDF)** khi có phiên focus và khi chưa
   có phiên. Đây là đường xuất riêng; xác minh file được tạo và mở được.
6. Nếu tái hiện lỗi, lấy log ngay sau thao tác:

   ```sh
   adb logcat -c
   # Thực hiện xuất PDF trên điện thoại
   adb logcat -d -v threadtime | grep -A 40 -E "FATAL EXCEPTION|StudySpacePdfExport|StudySpaceFocusPdf"
   ```

Chỉ log tái hiện mới xác định được nguyên nhân gốc. Không yêu cầu quyền ghi bộ
nhớ dùng chung cho đường `CreateDocument`; người dùng chọn vị trí lưu trong
Storage Access Framework.

