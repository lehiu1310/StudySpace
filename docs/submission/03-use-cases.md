# 3. Use case theo vai trò

Cập nhật: 2026-10-19. Các ca sử dụng dưới đây được đối chiếu với API, giao diện
web và luồng Android hiện có.

```mermaid
flowchart LR
    Student[Học viên]
    Teacher[Giảng viên]
    Admin[Quản trị viên]

    Student --> S1[Đăng ký / đăng nhập]
    Student --> S2[Xem khóa, chương, bài học]
    Student --> S3[Đánh dấu hoàn thành bài]
    Student --> S4[Làm quiz và xem giải thích]
    Student --> S5[Xem tiến độ, lịch sử, báo cáo]
    Student --> S6[Tham gia lớp bằng mã]
    Student --> S7[Thảo luận theo môn]
    Student --> S8[Lập kế hoạch và phiên tập trung]
    Student --> S9[Nhận gợi ý AI hoặc offline]

    Teacher --> T1[Đăng nhập]
    Teacher --> T2[Tạo và quản lý khóa của mình]
    Teacher --> T3[Soạn chương, bài, tài liệu, câu hỏi]
    Teacher --> T4[Xuất bản khóa đủ nội dung]
    Teacher --> T5[Tạo lớp và chia sẻ mã tham gia]
    Teacher --> T6[Xem tiến độ và báo cáo lớp]
    Teacher --> T7[Tham gia thảo luận môn]
    Teacher --> T8[Yêu cầu AI hỗ trợ soạn nội dung]

    Admin --> A1[Đăng nhập quản trị]
    Admin --> A2[Quản lý nội dung toàn hệ thống]
    Admin --> A3[Gán vai trò / khóa tài khoản]
    Admin --> A4[Xem báo cáo và xuất CSV]
```

## Luồng học viên

Đăng nhập → chọn khóa đã xuất bản → mở bài theo thứ tự → đánh dấu hoàn thành →
làm quiz theo chương → backend chấm và lưu lịch sử → xem điểm/đáp án/giải thích
→ theo dõi báo cáo.

## Luồng giảng viên

Đăng nhập → tạo khóa nháp → thêm chương, bài và câu hỏi → xuất bản sau khi nội
dung đạt điều kiện → tạo lớp gắn với khóa → chia sẻ mã → theo dõi tiến độ và
quiz của học viên.

## Luồng quản trị viên

Đăng nhập → quản lý nội dung dùng chung → quản lý vai trò/trạng thái tài khoản →
xem thống kê toàn hệ thống và tải CSV.

