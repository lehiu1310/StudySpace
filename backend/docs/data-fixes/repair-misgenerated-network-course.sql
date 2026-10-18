-- One-time repair for the known malformed course generated in the local teacher portal.
-- It preserves course/chapter/lesson/question IDs, enrollment, progress, and quiz snapshots.
BEGIN;
DO $$
DECLARE
    course_key BIGINT;
    chapter_key BIGINT;
    match_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO match_count
    FROM courses c
    WHERE LOWER(c.title) = 'mạng máy tính'
      AND c.description LIKE 'Lộ trình mạng máy tính dành cho Sinh viên cơ bản.%'
      AND EXISTS (SELECT 1 FROM chapters ch WHERE ch.course_id = c.id AND ch.title = 'Chương tiếp theo trong lộ trình')
      AND EXISTS (SELECT 1 FROM lessons l JOIN chapters ch ON ch.id = l.chapter_id
                  WHERE ch.course_id = c.id AND l.title = 'Bài học: Chương tiếp theo trong lộ trình');

    IF match_count = 0 THEN
        RAISE NOTICE 'No matching malformed course found; no repair needed.';
        RETURN;
    END IF;
    IF match_count <> 1 THEN
        RAISE EXCEPTION 'Expected one malformed network course, found %; refusing to update.', match_count;
    END IF;

    SELECT c.id INTO course_key
    FROM courses c
    WHERE LOWER(c.title) = 'mạng máy tính'
      AND c.description LIKE 'Lộ trình mạng máy tính dành cho Sinh viên cơ bản.%';

    UPDATE courses
    SET description = 'Lộ trình cơ bản về mạng máy tính: tìm hiểu TCP/IP, địa chỉ IP, DNS, cổng mạng và giao tiếp giữa các dịch vụ. Mỗi bài có mục tiêu, nội dung thực hành và câu hỏi tự kiểm tra.'
    WHERE id = course_key;

    UPDATE chapters SET title = 'Mạng máy tính và giao thức cơ bản', position = 1
    WHERE course_id = course_key AND title = 'Chương tiếp theo trong lộ trình';
    SELECT id INTO chapter_key FROM chapters WHERE course_id = course_key AND title = 'Mạng máy tính và giao thức cơ bản';

    UPDATE lessons
    SET title = 'Mô hình TCP/IP, DNS và cổng mạng', position = 1,
        content = E'Mục tiêu: phân biệt vai trò của IP, TCP, UDP, DNS và cổng mạng trong một kết nối ứng dụng.\n\nIP chuyển gói tin giữa các mạng. TCP cung cấp luồng dữ liệu tin cậy và đúng thứ tự; UDP có ít cơ chế hơn và phù hợp khi ứng dụng tự xử lý mất gói hoặc ưu tiên độ trễ. DNS phân giải tên miền thành địa chỉ IP. Cổng giúp hệ điều hành chuyển dữ liệu tới đúng dịch vụ trên máy.\n\nKhi kiểm tra một kết nối web, lần lượt xác minh DNS, địa chỉ IP, cổng dịch vụ, TLS và phản hồi HTTP thay vì chỉ thử lại ngẫu nhiên.\n\nTự kiểm tra: vì sao kết nối được tới địa chỉ IP nhưng vẫn có thể không truy cập được dịch vụ?'
    WHERE chapter_id = chapter_key AND title = 'Bài học: Chương tiếp theo trong lộ trình';

    UPDATE questions
    SET prompt = 'TCP có đặc điểm nào ở tầng giao vận?',
        option_a = 'Cung cấp luồng dữ liệu tin cậy và theo thứ tự',
        option_b = 'Phân giải tên miền thành địa chỉ IP',
        option_c = 'Chuyển tiếp gói tin giữa các mạng',
        option_d = 'Mã hóa nội dung HTTP mặc định',
        correct_option = 0,
        explanation = 'TCP cung cấp cơ chế truyền tin cậy, sắp thứ tự và truyền lại dữ liệu bị mất; DNS và IP có vai trò khác.'
    WHERE chapter_id = chapter_key AND prompt LIKE 'Về cơ sở dữ liệu%';

    UPDATE questions
    SET prompt = 'DNS chủ yếu dùng để làm gì?',
        option_a = 'Mã hóa mật khẩu người dùng',
        option_b = 'Phân giải tên miền thành địa chỉ IP',
        option_c = 'Đảm bảo mọi gói tin được gửi đúng thứ tự',
        option_d = 'Chọn bài học tiếp theo trong khóa học',
        correct_option = 1,
        explanation = 'DNS tra cứu tên miền và trả về dữ liệu phân giải như địa chỉ IP để client tìm máy chủ.'
    WHERE chapter_id = chapter_key AND prompt LIKE 'Theo bài học%';
END $$;
COMMIT;
