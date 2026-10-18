package com.studyspace.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DemoCohortSeeder {
    static final String TEACHER_USERNAME = "demo_cohort_teacher";
    static final String CLASS_CODE = "DEMO2026";
    static final int STUDENT_COUNT = 28;
    private static final String COURSE_TITLE = "Cơ sở dữ liệu · Lớp thực hành mẫu";
    private static final String CLASS_NAME = "CSDL · Lớp thực hành 01";
    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final String[] FIRST_NAMES = {"An", "Bình", "Chi", "Dũng", "Giang", "Hà", "Huy", "Khanh", "Linh", "Minh", "Ngọc", "Phúc", "Quân", "Trang"};
    private static final String[][] CHAPTERS = {
        {"Mô hình quan hệ và khóa", "Khóa chính, khóa ngoại và mô hình hóa quan hệ."},
        {"Chuẩn hóa dữ liệu", "Phụ thuộc hàm, khóa ứng viên và phân rã 1NF–3NF. Đây là chương có độ khó cao nhất trong bộ mẫu."},
        {"Truy vấn và giao dịch SQL", "Kết hợp bảng, tổng hợp dữ liệu và bảo đảm tính chất ACID."}
    };
    private static final String[][][] LESSONS = {
        {{"Thực thể, thuộc tính và quan hệ", "Xác định thực thể, thuộc tính và quan hệ trước khi tạo bảng. Mỗi bảng nên biểu diễn một nhóm đối tượng có cùng ý nghĩa; thuộc tính có kiểu dữ liệu và miền giá trị rõ ràng. Quan hệ một-nhiều thường được lưu bằng khóa ngoại ở phía nhiều."},
         {"Khóa chính và khóa ngoại", "Khóa chính định danh duy nhất một hàng và không nhận NULL. Khóa ngoại tham chiếu khóa chính hoặc khóa duy nhất ở bảng khác, giúp cơ sở dữ liệu từ chối dữ liệu tham chiếu không tồn tại. Quan hệ nhiều-nhiều cần bảng nối."}},
        {{"Phụ thuộc hàm và khóa ứng viên", "Phụ thuộc hàm X → Y nghĩa là hai hàng có cùng X thì phải có cùng Y. Bao đóng X+ giúp kiểm tra X có suy ra toàn bộ thuộc tính hay không; nếu có, X là siêu khóa. Loại thuộc tính dư để tìm khóa ứng viên tối thiểu."},
         {"Phân rã đến 3NF", "1NF yêu cầu giá trị nguyên tố; 2NF loại phụ thuộc bộ phận vào một phần khóa ghép; 3NF loại phụ thuộc bắc cầu của thuộc tính không khóa vào khóa. Phân rã cần kiểm tra bảo toàn phụ thuộc và phép nối không mất mát, không chỉ tách bảng theo cảm tính."}},
        {{"JOIN và tổng hợp", "JOIN kết hợp hàng qua điều kiện khóa. LEFT JOIN vẫn giữ hàng phía trái khi không có đối tác và trả NULL ở phía phải. GROUP BY tạo nhóm; WHERE lọc hàng trước khi nhóm, HAVING lọc nhóm sau khi tổng hợp."},
         {"Giao dịch và ACID", "Atomicity gom thao tác thành tất cả-hoặc-không; Consistency giữ ràng buộc; Isolation kiểm soát giao dịch đồng thời; Durability giữ kết quả đã commit. Dùng transaction khi nhiều cập nhật phải cùng thành công, và chọn mức cô lập theo yêu cầu."}}
    };
    private static final String[][][] QUESTIONS = {
        {
            {"Một bảng enrollments nối students và courses. Khóa chính phù hợp nhất là gì?", "student_id", "course_id", "(student_id, course_id)", "enrolled_at", "2", "Bảng nối cần ngăn một học viên đăng ký trùng cùng khóa; cặp hai khóa tạo định danh cho một lượt ghi danh.", "1"},
            {"Khóa ngoại có tác dụng chính nào?", "Tăng tốc mọi truy vấn", "Bảo đảm giá trị tham chiếu tồn tại", "Tự mã hóa dữ liệu", "Buộc cột không NULL", "1", "FOREIGN KEY duy trì toàn vẹn tham chiếu; nó không tự bảo đảm hiệu năng hay tính duy nhất.", "1"},
            {"Quan hệ nhiều-nhiều thường được mô hình hóa bằng cách nào?", "Lưu danh sách ID trong một chuỗi", "Tạo bảng nối chứa hai khóa ngoại", "Sao chép toàn bộ bảng", "Dùng một cột Boolean", "1", "Bảng nối biểu diễn mỗi cặp liên kết thành một hàng và có thể lưu thêm thuộc tính của quan hệ.", "2"},
            {"Một hàng khóa chính có thể nhận NULL không?", "Có, nếu bảng nhỏ", "Có, nếu có khóa ngoại", "Không", "Chỉ khi kiểu là số", "2", "Khóa chính vừa duy nhất vừa NOT NULL.", "1"},
            {"Khi nào nên tạo bảng nối riêng?", "Khi hai thực thể có quan hệ nhiều-nhiều", "Khi một bảng có ít hơn 10 hàng", "Khi cần thay mọi khóa chính", "Khi muốn bỏ ràng buộc", "0", "Bảng nối lưu từng liên kết giữa hai tập thực thể và tránh lặp danh sách trong một ô.", "2"},
            {"Khóa chính ghép (A,B) cho phép trường hợp nào?", "A trùng và B trùng ở hai hàng", "A trùng nếu B khác", "B trùng nếu A khác", "Cả B và C", "3", "Tính duy nhất áp dụng cho tổ hợp; từng thành phần riêng lẻ có thể trùng.", "2"}
        },
        {
            {"Cho R(A,B,C), F={A→B, B→C}. Bao đóng A+ là gì?", "{A}", "{A,B}", "{A,B,C}", "{B,C}", "2", "Từ A suy ra B, rồi từ B suy ra C; do đó A+ chứa cả ba thuộc tính.", "2"},
            {"Với khóa ghép (student_id, course_id), thuộc tính student_name chỉ phụ thuộc student_id. Vi phạm dạng chuẩn nào?", "1NF", "2NF", "3NF בלבד", "Không vi phạm", "1", "Đây là phụ thuộc bộ phận của thuộc tính không khóa vào một phần khóa ghép, vi phạm 2NF.", "3"},
            {"Trong R(A,B,C), F={A→B, B→C}, nếu A là khóa thì điều gì gây vi phạm 3NF?", "A→B", "B→C vì B không phải siêu khóa và C không phải thuộc tính khóa", "A là khóa", "Không có phụ thuộc", "1", "B→C là phụ thuộc bắc cầu qua thuộc tính không khóa; quan hệ cần phân rã hoặc tổng hợp theo 3NF.", "3"},
            {"Phân rã R(A,B,C) thành R1(A,B), R2(B,C) theo B→C. Điều kiện nào quan trọng để phép nối không mất mát?", "Giao R1∩R2 phải xác định R1 hoặc R2", "Hai bảng phải cùng số hàng", "Mỗi bảng có khóa tự tăng", "Không cần điều kiện", "0", "Giao thuộc tính B phải là siêu khóa của ít nhất một quan hệ con để phép nối nhị phân không mất mát.", "3"},
            {"Một quan hệ có khóa ghép (A,B), phụ thuộc A→C. Cách xử lý 2NF phù hợp là gì?", "Tách R1(A,C) và giữ bảng liên kết chứa (A,B)", "Xóa khóa B", "Đưa mọi cột thành TEXT", "Chỉ tạo index trên C", "0", "Đưa thuộc tính phụ thuộc một phần khóa sang quan hệ riêng giúp loại phụ thuộc bộ phận mà vẫn giữ liên kết.", "3"},
            {"Điều kiện 3NF cho mọi phụ thuộc không tầm thường X→A là gì?", "X luôn chỉ có một thuộc tính", "X là siêu khóa hoặc A là thuộc tính khóa", "A phải là số nguyên", "X và A phải khác bảng", "1", "Định nghĩa 3NF cho phép X là siêu khóa hoặc thuộc tính vế phải là thuộc tính khóa (prime attribute).", "3"}
        },
        {
            {"Mệnh đề nào lọc nhóm sau GROUP BY?", "WHERE", "HAVING", "FROM", "LIMIT", "1", "HAVING lọc kết quả nhóm sau khi tính toán tổng hợp.", "1"},
            {"LEFT JOIN không tìm thấy hàng khớp thì cột phía phải có giá trị gì?", "0", "Chuỗi rỗng", "NULL", "Hàng bị bỏ", "2", "LEFT JOIN giữ hàng bên trái và điền NULL cho các cột phía phải không có đối tác.", "2"},
            {"Đặc tính ACID nào bảo đảm mọi thao tác trong giao dịch cùng thành công hoặc cùng rollback?", "Atomicity", "Consistency", "Isolation", "Durability", "0", "Atomicity biểu diễn tính tất cả-hoặc-không của giao dịch.", "1"},
            {"Mệnh đề nào lọc hàng trước khi nhóm?", "HAVING", "WHERE", "ORDER BY", "COMMIT", "1", "WHERE loại hàng đầu vào trước khi GROUP BY thực hiện tổng hợp.", "1"},
            {"Vì sao cần transaction khi chuyển tiền giữa hai tài khoản?", "Để hai cập nhật cùng commit hoặc cùng rollback", "Để bỏ khóa ngoại", "Để truy vấn luôn nhanh hơn", "Để ẩn số dư khỏi chủ tài khoản", "0", "Nếu chỉ một vế cập nhật, tổng tiền bị sai; transaction gom hai thay đổi thành đơn vị nguyên tử.", "2"},
            {"Isolation giải quyết vấn đề nào?", "Định dạng ngày tháng", "Tương tác giữa các giao dịch chạy đồng thời", "Mã hóa mật khẩu", "Sao lưu ngoài máy", "1", "Isolation kiểm soát mức độ các giao dịch đồng thời nhìn thấy ảnh hưởng của nhau.", "2"}
        }
    };
    private static final String[][] KOTLIN_CHAPTERS = {
        {"Kiểu dữ liệu và null safety", "Khai báo kiểu, xử lý giá trị nullable an toàn và tránh lỗi runtime."},
        {"Collection và hàm", "Chọn collection phù hợp, biến đổi dữ liệu bằng hàm bậc cao và viết hàm dễ kiểm thử."},
        {"Lập trình hướng đối tượng", "Tổ chức lớp, data class, interface và extension theo hướng tái sử dụng."}
    };
    private static final String[][][] KOTLIN_LESSONS = {
        {{"val, var và kiểu cơ bản", "val không cho gán lại tham chiếu; var cho phép gán lại. Kotlin suy luận kiểu khi biểu thức khởi tạo rõ ràng. Chọn kiểu dữ liệu theo miền giá trị và tránh chuyển kiểu ngầm không an toàn."}, {"Nullable và safe call", "String? biểu diễn giá trị có thể null. Safe call ?. chỉ truy cập khi khác null; Elvis ?: cung cấp giá trị thay thế. Tránh !! vì có thể ném NullPointerException khi dữ liệu thực tế khác giả định."}},
        {{"List, Set và Map", "List giữ thứ tự và có thể chứa phần tử lặp; Set biểu diễn tập giá trị duy nhất; Map ánh xạ khóa sang giá trị. Dùng giao diện chỉ đọc khi không cần sửa collection để hạn chế tác dụng phụ."}, {"map, filter và lambda", "filter chọn phần tử theo điều kiện, map biến đổi từng phần tử và associateBy tạo ánh xạ theo khóa. Lambda nên ngắn, tên biến rõ và tránh lồng nhiều phép biến đổi khiến luồng dữ liệu khó đọc."}},
        {{"Class, interface và kế thừa", "Class đóng gói trạng thái cùng hành vi. Interface mô tả hợp đồng có thể được nhiều kiểu triển khai. Ưu tiên composition khi quan hệ không thực sự là “is-a”, và giữ constructor nêu rõ phụ thuộc."}, {"Data class và extension", "Data class sinh equals, hashCode, toString và copy dựa trên constructor chính. Extension bổ sung hàm tiện ích nhưng được phân giải tĩnh; đặt tên rõ và tránh tạo cảm giác rằng nó thay đổi lớp gốc."}}
    };
    private static final String[][][] KOTLIN_QUESTIONS = {
        {{"Kiểu nào có thể nhận giá trị null?", "String", "String?", "Int", "Boolean", "1", "Dấu hỏi trong String? đánh dấu kiểu nullable.", "1"}, {"Elvis operator ?: thường dùng để làm gì?", "Ép kiểu sang số", "So sánh hai chuỗi", "Chọn giá trị thay thế khi vế trái null", "Khai báo interface", "2", "Elvis trả về vế phải khi biểu thức bên trái là null.", "1"}},
        {{"Cấu trúc nào đảm bảo các phần tử không trùng?", "List", "Array", "Set", "Pair", "2", "Set mô hình hóa tập hợp phần tử duy nhất.", "1"}, {"Hàm map thường thực hiện thao tác nào?", "Biến đổi từng phần tử thành giá trị mới", "Xóa mọi phần tử", "Sắp xếp theo thứ tự", "Chặn giá trị null", "0", "map áp dụng phép biến đổi lên mỗi phần tử và trả collection kết quả.", "2"}},
        {{"Data class tạo sẵn hàm nào dựa trên constructor chính?", "onCreate", "equals và copy", "main", "measure", "1", "Data class sinh các hàm dữ liệu như equals, hashCode, toString và copy.", "1"}, {"Extension function có làm thay đổi lớp gốc không?", "Có, luôn thêm method vào bytecode lớp", "Chỉ khi lớp là abstract", "Không; lời gọi được phân giải tĩnh", "Chỉ trên Android", "2", "Extension không sửa lớp; cách phân giải dựa trên kiểu khai báo tại điểm gọi.", "2"}}
    };
    private static final String[][] ALGORITHM_CHAPTERS = {
        {"Độ phức tạp và tìm kiếm", "Ước lượng chi phí thuật toán và chọn tìm kiếm tuyến tính hoặc nhị phân."},
        {"Sắp xếp và cấu trúc dữ liệu", "So sánh thuật toán sắp xếp và cấu trúc dữ liệu theo thao tác cần tối ưu."},
        {"Đệ quy và đồ thị", "Thiết kế điều kiện dừng, duyệt đồ thị và kiểm soát trạng thái đã thăm."}
    };
    private static final String[][][] ALGORITHM_LESSONS = {
        {{"Big-O và tăng trưởng", "Big-O mô tả tốc độ tăng của chi phí theo kích thước đầu vào, bỏ qua hệ số và số hạng bậc thấp. O(n log n) thường tăng chậm hơn O(n²) khi n lớn, nhưng hằng số và bộ nhớ vẫn cần cân nhắc."}, {"Tìm kiếm tuyến tính và nhị phân", "Tìm tuyến tính kiểm tra lần lượt, chạy O(n). Tìm nhị phân giảm nửa khoảng tìm mỗi bước nên O(log n), nhưng yêu cầu dữ liệu đã được sắp xếp."}},
        {{"Insertion sort và merge sort", "Insertion sort đơn giản, chạy O(n²) trung bình và hiệu quả với mảng nhỏ gần có thứ tự. Merge sort chạy O(n log n) ổn định, đổi lại cần bộ nhớ phụ cho bước trộn."}, {"Stack, queue và hash map", "Stack hoạt động theo LIFO, queue theo FIFO. Hash map tra cứu trung bình gần O(1) khi hàm băm phân bố tốt; va chạm cần được xử lý và hiệu năng xấu nhất có thể giảm."}},
        {{"Đệ quy và điều kiện dừng", "Mỗi nhánh đệ quy phải tiến gần điều kiện dừng. Độ sâu lớn tiêu tốn call stack; với đầu vào lớn hãy cân nhắc vòng lặp hoặc memoization để tránh tính lặp."}, {"BFS, DFS và tập đã thăm", "BFS dùng queue để duyệt theo lớp; DFS dùng stack hoặc đệ quy để đi sâu. Tập visited ngăn duyệt lặp và tránh vòng vô hạn trong đồ thị có chu trình."}}
    };
    private static final String[][][] ALGORITHM_QUESTIONS = {
        {{"Tìm nhị phân cần điều kiện nào?", "Mảng đã sắp xếp", "Mảng chỉ có số nguyên", "Không có phần tử trùng", "Kích thước là lũy thừa hai", "0", "Mỗi bước loại một nửa khoảng tìm nên thứ tự đã sắp xếp là điều kiện cần.", "1"}, {"Độ phức tạp tìm nhị phân là gì?", "O(1)", "O(log n)", "O(n)", "O(n²)", "1", "Khoảng tìm giảm một nửa sau mỗi lần so sánh, tạo số bước logarit.", "2"}},
        {{"Cấu trúc nào hoạt động theo LIFO?", "Queue", "Set", "Stack", "Graph", "2", "Stack lấy phần tử vào sau ra trước: Last In, First Out.", "1"}, {"Merge sort thường có độ phức tạp thời gian nào?", "O(log n)", "O(n)", "O(n log n)", "O(n²)", "2", "Chia log n tầng và tổng công việc ở mỗi tầng là tuyến tính.", "2"}},
        {{"BFS thường dùng cấu trúc nào để duyệt theo lớp?", "Queue", "Stack", "Heap", "Hash set", "0", "Queue xử lý các đỉnh theo thứ tự được phát hiện, tạo duyệt theo chiều rộng.", "1"}, {"Vì sao DFS/BFS cần tập visited trên đồ thị có chu trình?", "Để sắp xếp đỉnh", "Để ngăn duyệt lặp và vòng vô hạn", "Để giảm số cạnh", "Để tìm mọi đường đi ngắn nhất", "1", "Đánh dấu đã thăm giúp thuật toán không quay lặp qua chu trình.", "2"}}
    };

    private final JdbcTemplate db;
    private final AuthService auth;
    private final ObjectMapper json = new ObjectMapper();
    @Value("${studyspace.demo.password:}") private String demoPassword;

    DemoCohortSeeder(JdbcTemplate db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    @Transactional
    void seed() {
        if (demoPassword == null || demoPassword.length() < 12)
            throw new IllegalStateException("Rich demo seed requires STUDYSPACE_DEMO_PASSWORD with at least 12 characters.");

        long teacherId = ensureAccount(TEACHER_USERNAME, "Cô Nguyễn Minh Anh", "TEACHER");
        long courseId = ensureCourse(teacherId, COURSE_TITLE, "Lớp dữ liệu minh họa riêng: mô hình quan hệ, chuẩn hóa và truy vấn SQL; gồm hoạt động bốn tuần với nhiều mức tiến độ.");
        long[] chapterIds = new long[CHAPTERS.length];
        List<List<Long>> lessonIds = new ArrayList<>();
        List<List<Long>> questionIds = new ArrayList<>();
        for (int c = 0; c < CHAPTERS.length; c++) {
            chapterIds[c] = ensureChapter(courseId, c);
            lessonIds.add(new ArrayList<>());
            questionIds.add(new ArrayList<>());
            for (int l = 0; l < LESSONS[c].length; l++) lessonIds.get(c).add(ensureLesson(chapterIds[c], c, l));
            for (int q = 0; q < QUESTIONS[c].length; q++) questionIds.get(c).add(ensureQuestion(chapterIds[c], c, q));
        }
        long classId = ensureClass(courseId, teacherId, CLASS_CODE, CLASS_NAME);
        long kotlinCourseId = seedSupplementalCourse(teacherId, "Lập trình Kotlin · Lớp thực hành mẫu", "Từ kiểu dữ liệu và null safety đến collection, hàm và hướng đối tượng.", "DEMOKOT26", "Kotlin · Lớp thực hành 01", KOTLIN_CHAPTERS, KOTLIN_LESSONS, KOTLIN_QUESTIONS);
        long algorithmCourseId = seedSupplementalCourse(teacherId, "Thuật toán · Lớp thực hành mẫu", "Phân tích độ phức tạp, cấu trúc dữ liệu, tìm kiếm, sắp xếp và đồ thị.", null, null, ALGORITHM_CHAPTERS, ALGORITHM_LESSONS, ALGORITHM_QUESTIONS);
        long kotlinClassId = ensureClass(kotlinCourseId, teacherId, "DEMOKOT26", "Kotlin · Lớp thực hành 01");
        long anchor = db.queryForObject("SELECT created_at FROM study_classes WHERE id=?", Long.class, classId);
        List<Long> studentIds = new ArrayList<>();
        for (int i = 0; i < STUDENT_COUNT; i++) {
            String username = studentUsername(i);
            long studentId = ensureAccount(username, studentName(i), "STUDENT");
            studentIds.add(studentId);
            if (db.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE class_id=? AND student_id=?", Integer.class, classId, studentId) == 0)
                db.update("INSERT INTO class_enrollments(class_id,student_id,enrolled_at) VALUES(?,?,?)",
                    classId, studentId, anchor - Duration.ofDays(35).toMillis());
            if (db.queryForObject("SELECT COUNT(*) FROM class_enrollments WHERE class_id=? AND student_id=?", Integer.class, kotlinClassId, studentId) == 0)
                db.update("INSERT INTO class_enrollments(class_id,student_id,enrolled_at) VALUES(?,?,?)",
                    kotlinClassId, studentId, anchor - Duration.ofDays(35).toMillis());
            seedStudentActivity(studentId, chapterIds, lessonIds, questionIds, i, anchor);
        }
        seedMessages(courseId, teacherId, studentIds, anchor);
    }

    private long ensureAccount(String username, String displayName, String role) {
        var rows = db.queryForList("SELECT id,role FROM accounts WHERE username=?", username);
        if (!rows.isEmpty()) {
            long id = ((Number) rows.get(0).get("id")).longValue();
            String existingRole = Objects.toString(rows.get(0).get("role"));
            if (!role.equals(existingRole)) throw new IllegalStateException("Demo account has unexpected role: " + username);
            return id;
        }
        return insert("INSERT INTO accounts(username,display_name,password_hash,role) VALUES(?,?,?,?)",
            username, displayName, auth.passwords.encode(demoPassword), role);
    }

    private long ensureCourse(long teacherId, String title, String description) {
        var rows = db.queryForList("SELECT id FROM courses WHERE created_by=? AND title=? ORDER BY id LIMIT 1", teacherId, title);
        if (!rows.isEmpty()) return ((Number) rows.get(0).get("id")).longValue();
        return insert("INSERT INTO courses(title,description,published,created_by) VALUES(?,?,TRUE,?)",
            title, description, teacherId);
    }

    private long seedSupplementalCourse(long teacherId, String title, String description, String classCode, String className,
                                        String[][] chapters, String[][][] lessons, String[][][] questions) {
        long courseId = ensureCourse(teacherId, title, description);
        for (int chapter = 0; chapter < chapters.length; chapter++) {
            long chapterId = ensureChapter(courseId, chapters[chapter][0], chapter + 1);
            for (int lesson = 0; lesson < lessons[chapter].length; lesson++)
                ensureLesson(chapterId, lessons[chapter][lesson][0], lessons[chapter][lesson][1], lesson + 1);
            for (String[] question : questions[chapter]) ensureQuestion(chapterId, question);
        }
        if (classCode != null) ensureClass(courseId, teacherId, classCode, className);
        return courseId;
    }

    private long ensureChapter(long courseId, int index) {
        return ensureChapter(courseId, CHAPTERS[index][0], index + 1);
    }

    private long ensureChapter(long courseId, String title, int position) {
        var rows = db.queryForList("SELECT id FROM chapters WHERE course_id=? AND title=?", courseId, title);
        if (!rows.isEmpty()) return ((Number) rows.get(0).get("id")).longValue();
        return insert("INSERT INTO chapters(course_id,title,position) VALUES(?,?,?)", courseId, title, position);
    }

    private long ensureLesson(long chapterId, int chapterIndex, int lessonIndex) {
        return ensureLesson(chapterId, LESSONS[chapterIndex][lessonIndex][0], LESSONS[chapterIndex][lessonIndex][1], lessonIndex + 1);
    }

    private long ensureLesson(long chapterId, String title, String body, int position) {
        var rows = db.queryForList("SELECT id FROM lessons WHERE chapter_id=? AND title=?", chapterId, title);
        if (!rows.isEmpty()) return ((Number) rows.get(0).get("id")).longValue();
        return insert("INSERT INTO lessons(chapter_id,title,content,material_url,position) VALUES(?,?,?,'',?)",
            chapterId, title, body, position);
    }

    private long ensureQuestion(long chapterId, int chapterIndex, int questionIndex) {
        return ensureQuestion(chapterId, QUESTIONS[chapterIndex][questionIndex]);
    }

    private long ensureQuestion(long chapterId, String[] q) {
        var rows = db.queryForList("SELECT id FROM questions WHERE chapter_id=? AND prompt=?", chapterId, q[0]);
        if (!rows.isEmpty()) return ((Number) rows.get(0).get("id")).longValue();
        return insert("INSERT INTO questions(chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty) VALUES(?,?,?,?,?,?,?,?,?)",
            chapterId, q[0], q[1], q[2], q[3], q[4], Integer.parseInt(q[5]), q[6], Integer.parseInt(q[7]));
    }

    private long ensureClass(long courseId, long teacherId) {
        return ensureClass(courseId, teacherId, CLASS_CODE, CLASS_NAME);
    }

    private long ensureClass(long courseId, long teacherId, String code, String name) {
        var rows = db.queryForList("SELECT id FROM study_classes WHERE join_code=?", code);
        if (!rows.isEmpty()) {
            long id = ((Number) rows.get(0).get("id")).longValue();
            var owner = db.queryForList("SELECT teacher_id,course_id FROM study_classes WHERE id=?", id);
            if (((Number) owner.get(0).get("teacher_id")).longValue() != teacherId || ((Number) owner.get(0).get("course_id")).longValue() != courseId)
            throw new IllegalStateException("Demo class code is already owned by a different class.");
            return id;
        }
        return insert("INSERT INTO study_classes(course_id,teacher_id,name,join_code,active,created_at) VALUES(?,?,?, ?,TRUE,?)",
            courseId, teacherId, name, code, System.currentTimeMillis());
    }

    private void seedStudentActivity(long userId, long[] chapters, List<List<Long>> lessons,
                                     List<List<Long>> questions, int studentIndex, long anchor) {
        int completed = studentIndex < 7 ? 6 : studentIndex < 18 ? 3 : studentIndex < 23 ? 1 : 0;
        List<Long> orderedLessons = lessons.stream().flatMap(Collection::stream).toList();
        long courseId = db.queryForObject("SELECT course_id FROM study_classes WHERE join_code=?", Long.class, CLASS_CODE);
        if (db.queryForObject("SELECT COUNT(*) FROM course_learning WHERE user_id=? AND course_id=?", Integer.class, userId, courseId) == 0)
            db.update("INSERT INTO course_learning(user_id,course_id,started_at,last_accessed_at) VALUES(?,?,?,?)",
                userId, courseId, anchor - Duration.ofDays(28).toMillis(), anchor - Duration.ofDays(studentIndex >= 23 ? 17 : studentIndex % 5).toMillis());
        for (int l = 0; l < completed; l++) {
            long completedAt = anchor - Duration.ofDays(25 - Math.min(20, l * 3L)).toMillis();
            if (db.queryForObject("SELECT COUNT(*) FROM lesson_progress WHERE user_id=? AND lesson_id=?", Integer.class, userId, orderedLessons.get(l)) == 0)
                db.update("INSERT INTO lesson_progress(user_id,lesson_id,completed_at) VALUES(?,?,?)",
                    userId, orderedLessons.get(l), completedAt);
        }
        for (int week = 0; week < (studentIndex >= 23 ? 2 : 4); week++) {
            for (int c = 0; c < chapters.length; c++) seedAttempt(userId, chapters[c], questions.get(c), studentIndex, week, c, anchor);
        }
        seedFocusSnapshot(userId, studentIndex, anchor);
    }

    private void seedAttempt(long userId, long chapterId, List<Long> questionIds, int studentIndex, int week, int chapterIndex, long anchor) {
        int base = studentIndex < 7 ? 90 : studentIndex < 18 ? 74 : 57;
        int percent = Math.max(20, Math.min(100, base + week * 2 - (chapterIndex == 1 ? 19 : 0)));
        int score = Math.round(percent * questionIds.size() / 100f);
        long startedAt = anchor - Duration.ofDays((4L - week) * 7L - 2L).toMillis() + (studentIndex * 31L + chapterIndex * 7L) * 60_000L;
        var existing = db.queryForList("SELECT id FROM quiz_attempts WHERE user_id=? AND chapter_id=? AND started_at=?", userId, chapterId, startedAt);
        if (!existing.isEmpty()) return;
        long attemptId = insert("INSERT INTO quiz_attempts(user_id,chapter_id,started_at,deadline,submitted_at,score,total) VALUES(?,?,?,?,?,?,?)",
            userId, chapterId, startedAt, startedAt + Duration.ofMinutes(12).toMillis(), startedAt + Duration.ofMinutes(7).toMillis(), score, questionIds.size());
        int wrongCount = questionIds.size() - score;
        for (int i = 0; i < questionIds.size(); i++) {
            long questionId = questionIds.get(i);
            Map<String, Object> q = db.queryForMap("SELECT * FROM questions WHERE id=?", questionId);
            boolean wrong = Math.floorMod(i + studentIndex + week, questionIds.size()) < wrongCount;
            int correct = ((Number) q.get("correct_option")).intValue();
            Integer selected = wrong ? (correct + 1 + Math.floorMod(studentIndex + i, 3)) % 4 : correct;
            db.update("INSERT INTO attempt_answers(attempt_id,question_id,position,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,selected_option) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                attemptId, questionId, i, q.get("prompt"), q.get("option_a"), q.get("option_b"), q.get("option_c"), q.get("option_d"), correct, q.get("explanation"), selected);
        }
    }

    private void seedFocusSnapshot(long userId, int studentIndex, long anchor) {
        if (db.queryForObject("SELECT COUNT(*) FROM personal_study_snapshots WHERE user_id=?", Integer.class, userId) > 0) return;
        List<Map<String, Object>> sessions = new ArrayList<>();
        int dayStride = studentIndex >= 23 ? 5 : 3;
        for (int day = studentIndex >= 23 ? 14 : 0; day < 28; day += dayStride) {
            sessions.add(Map.of("id", sessions.size() + 1, "taskTitle", "Ôn tập Cơ sở dữ liệu", "durationMinutes", 20 + Math.floorMod(studentIndex * 3 + day, 36),
                "sessionType", "focus", "timestamp", anchor - day * DAY));
        }
        try {
            String payload = "{\"plans\":[],\"sessions\":" + json.writeValueAsString(sessions) + "}";
            db.update("INSERT INTO personal_study_snapshots(user_id,version,request_id,payload,updated_at) VALUES(?,1,?,?,?)",
                userId, "demo-cohort-seed-v1", payload, anchor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize demo focus sessions.", e);
        }
    }

    private void seedMessages(long courseId, long teacherId, List<Long> students, long anchor) {
        String[][] messages = {
            {"Cô đã đăng tài liệu ôn tập phần chuẩn hóa. Các em thử làm câu 4 trước buổi tới nhé.", "-1", "3"},
            {"Em đang phân vân cách kiểm tra phép phân rã có mất mát, cô có thể gợi ý thêm không ạ?", "8", "9"},
            {"Mình kiểm tra giao của hai lược đồ trước, rồi xem giao đó có xác định một vế không nhé.", "12", "14"},
            {"Đã hiểu hơn rồi, cảm ơn cô và mọi người!", "5", "20"}
        };
        for (String[] message : messages) {
            long userId = message[1].equals("-1") ? teacherId : students.get(Integer.parseInt(message[1]));
            String body = message[0];
            int exists = db.queryForObject("SELECT COUNT(*) FROM course_messages WHERE course_id=? AND user_id=? AND body=?", Integer.class, courseId, userId, body);
            if (exists == 0) db.update("INSERT INTO course_messages(course_id,user_id,body,sent_at) VALUES(?,?,?,?)",
                courseId, userId, body, anchor - Duration.ofDays(Long.parseLong(message[2])).toMillis());
        }
    }

    static String studentUsername(int index) { return "demo_cohort_%02d".formatted(index + 1); }
    static String studentName(int index) { return FIRST_NAMES[index % FIRST_NAMES.length] + " " + List.of("Nguyễn", "Trần", "Lê", "Phạm", "Võ", "Đặng").get(index % 6) + " · " + "%02d".formatted(index + 1); }

    private long insert(String sql, Object... values) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
