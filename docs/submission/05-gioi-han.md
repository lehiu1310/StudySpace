# 5. Giới hạn và hướng phát triển

Cập nhật: 2026-10-19. Mỗi giới hạn được đối chiếu mã nguồn, cấu hình hoặc trạng
thái môi trường; đây không phải cam kết đã triển khai.

| Giới hạn hiện tại | Hướng phát triển |
|---|---|
| Thảo luận lớp cập nhật bằng polling 5 giây, chưa dùng WebSocket. | Chuyển sang WebSocket/SSE khi cần realtime và mở rộng số người dùng. |
| Kế hoạch/focus có WorkManager định kỳ 6 giờ và đồng bộ một lần sau thay đổi khi có mạng; xung đột yêu cầu chọn toàn bộ bản local/server, chưa merge từng bản ghi. | Bổ sung đồng bộ nền có quan sát trạng thái, xử lý conflict theo từng thực thể và kiểm tra mất mạng lâu. |
| Bản release cần URL HTTPS và hạ tầng triển khai thật; môi trường hiện kiểm tra là localhost. | Triển khai backend sau HTTPS reverse proxy, cấu hình secrets, backup/restore và giám sát. |
| Quiz thích ứng dùng heuristic: độ khó từ tối đa ba lượt gần nhất, ưu tiên câu sai/câu chưa gặp. | Đánh giá thuật toán trên dữ liệu học thật; bổ sung spaced repetition có lịch ôn và đo hiệu quả. |
| Spaced repetition chưa được triển khai. | Thiết kế lịch lặp lại cách quãng và cho học viên xem lịch ôn. |
| Khóa học chỉ có thao tác ẩn/hiện (unpublish/publish), không có chức năng xóa khỏi database. Giữ khóa giúp bảo toàn lịch sử học, quiz và báo cáo liên quan. | Nếu cần vòng đời xóa, thiết kế lưu trữ mềm có kiểm tra tham chiếu và chính sách lưu lịch sử trước; hiện không xóa khóa. |
| Gemini health ở local trả `not_configured` ngày 2026-10-17; chưa xác nhận live Gemini. | Cấu hình key chỉ ở server, kiểm tra quota/lỗi/mất mạng, xác thực output và giữ nhãn provider chính xác. |
| Chưa kiểm tra trọn luồng Android trực tiếp trên thiết bị trong lượt này. | Chạy checklist trên Medium Phone hoặc thiết bị vật lý, gồm PDF, reminder, focus, offline và quyền Android 13+. |
| Timestamp cũ của khóa/chương/bài là thời điểm migration, không tái tạo được ngày tạo lịch sử. | Dùng `created_at` thật cho dữ liệu mới; ghi rõ nguồn dữ liệu cũ khi xuất báo cáo. |

