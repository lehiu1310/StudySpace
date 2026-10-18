-- Disable duplicate questions that were copied into the wrong chapter by an old local demo account.
-- Correctly assigned copies remain active; submitted attempts are retained unchanged.
BEGIN;
DO $$
DECLARE
    owner_key BIGINT;
BEGIN
    SELECT id INTO owner_key FROM accounts WHERE username = 'demo_gv_261015135744' AND role = 'TEACHER';
    IF owner_key IS NULL THEN
        RAISE NOTICE 'Legacy demo teacher not present; no question repair needed.';
        RETURN;
    END IF;

    UPDATE questions q SET active = FALSE
    FROM chapters ch JOIN courses c ON c.id = ch.course_id
    WHERE q.chapter_id = ch.id AND c.created_by = owner_key
      AND c.title = 'Cấu trúc dữ liệu & giải thuật'
      AND ch.title = 'Mảng, tìm kiếm và sắp xếp'
      AND q.prompt = 'Cấu trúc nào hoạt động theo nguyên tắc LIFO?';

    UPDATE questions q SET active = FALSE
    FROM chapters ch JOIN courses c ON c.id = ch.course_id
    WHERE q.chapter_id = ch.id AND c.created_by = owner_key
      AND c.title = 'Cấu trúc dữ liệu & giải thuật'
      AND ch.title = 'Ngăn xếp và hàng đợi'
      AND q.prompt IN (
        'Độ phức tạp trung bình của tìm kiếm nhị phân trên mảng đã sắp xếp là gì?',
        'Điều kiện cần để tìm kiếm nhị phân hoạt động đúng trên mảng là gì?'
      );

    UPDATE questions q SET active = FALSE
    FROM chapters ch JOIN courses c ON c.id = ch.course_id
    WHERE q.chapter_id = ch.id AND c.created_by = owner_key
      AND c.title = 'Phát triển Android với Kotlin'
      AND ch.title = 'Kotlin nền tảng'
      AND q.prompt = 'Trong Compose, composable được đánh dấu bằng annotation nào?';

    UPDATE questions q SET active = FALSE
    FROM chapters ch JOIN courses c ON c.id = ch.course_id
    WHERE q.chapter_id = ch.id AND c.created_by = owner_key
      AND c.title = 'Phát triển Android với Kotlin'
      AND ch.title = 'Giao diện với Jetpack Compose'
      AND q.prompt IN (
        'Từ khóa nào khai báo biến chỉ đọc trong Kotlin?',
        'Thao tác nào phù hợp để lọc phần tử trong collection Kotlin?'
      );
END $$;
COMMIT;
