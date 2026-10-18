package com.studyspace.server;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
class Bootstrap implements ApplicationRunner {
    private final AuthService auth;
    private final AdminController admin;
    private final DemoCohortSeeder demoCohortSeeder;
    @Value("${studyspace.admin.password:}") String password;
    @Value("${studyspace.demo.password:}") String demoPassword;
    @Value("${studyspace.seed:false}") boolean seed;
    @Value("${studyspace.seed.rich-demo:false}") boolean richDemo;
    Bootstrap(AuthService auth,AdminController admin,DemoCohortSeeder demoCohortSeeder) {this.auth=auth;this.admin=admin;this.demoCohortSeeder=demoCohortSeeder;}
    @Override @Transactional public void run(ApplicationArguments args) {
        if(!password.isEmpty() && auth.db.queryForObject("SELECT COUNT(*) FROM accounts WHERE role='ADMIN'",Integer.class)==0) {
            if(password.length()<12)throw new IllegalArgumentException("Admin password requires at least 12 characters");
            auth.db.update("INSERT INTO accounts(username,display_name,password_hash,role) VALUES('admin','Quản trị viên',?,'ADMIN')",auth.passwords.encode(password));
        }
        if(!seed) {
            if (richDemo) demoCohortSeeder.seed();
            return;
        }
        boolean empty=auth.db.queryForObject("SELECT COUNT(*) FROM courses",Integer.class)==0;
        if(empty) {
        long course=admin.insert("courses","title,description,published","Cơ sở dữ liệu","Từ mô hình quan hệ đến truy vấn SQL. Học từng bài và kiểm tra kiến thức sau mỗi chương.",true);
        long chapter=admin.insert("chapters","course_id,title,position",course,"Mô hình quan hệ và khóa",1);
        admin.insert("lessons","chapter_id,title,content,material_url,position",chapter,"Khóa chính và khóa ngoại","Mỗi bảng lưu một loại thực thể. Mỗi dòng là một bản ghi, mỗi cột là một thuộc tính.\n\nKhóa chính định danh duy nhất một dòng và không được NULL. Khóa chính có thể gồm nhiều cột.\n\nKhóa ngoại tham chiếu khóa chính hoặc khóa duy nhất của bảng khác, giúp bảo đảm toàn vẹn tham chiếu. Khóa ngoại không bắt buộc duy nhất trong bảng con.\n\nVí dụ: students(id, name), courses(id, title), enrollments(student_id, course_id). Bảng enrollments biểu diễn quan hệ nhiều-nhiều; cặp student_id và course_id có thể là khóa chính ghép.\n\nTự kiểm tra: vì sao không nên lưu danh sách mã môn dưới dạng chuỗi phân cách bằng dấu phẩy trong bảng students?","https://www.postgresql.org/docs/current/ddl-constraints.html",1);
        question(chapter,"Tính chất bắt buộc của khóa chính?","Duy nhất và không NULL","Chỉ chứa số","Luôn có một cột","Được trùng nếu khác tên",0,"Khóa chính định danh từng bản ghi, phải duy nhất và không NULL.");
        question(chapter,"Quan hệ sinh viên đăng ký nhiều môn, mỗi môn có nhiều sinh viên nên biểu diễn thế nào?","Thêm một cột cho mỗi môn","Dùng bảng đăng ký trung gian","Gộp tất cả vào một chuỗi","Nhân đôi bảng sinh viên",1,"Bảng trung gian lưu từng cặp sinh viên-môn và có thể chứa ngày đăng ký.");
        question(chapter,"Khóa ngoại giúp bảo đảm điều gì?","Mọi cột đều duy nhất","Bảng luôn có dữ liệu","Toàn vẹn tham chiếu","Truy vấn luôn nhanh hơn",2,"Khóa ngoại ngăn tham chiếu tới bản ghi không tồn tại, theo quy tắc ràng buộc đã khai báo.");
        long sql=admin.insert("chapters","course_id,title,position",course,"Truy vấn SQL",2);
        admin.insert("lessons","chapter_id,title,content,material_url,position",sql,"SELECT, WHERE và GROUP BY","SELECT chọn các cột cần trả về. FROM xác định nguồn dữ liệu. WHERE lọc từng dòng trước khi nhóm.\n\nVí dụ: SELECT name FROM students WHERE score >= 8;\n\nGROUP BY gom các dòng theo thuộc tính để tính COUNT, SUM, AVG. HAVING lọc kết quả nhóm.\n\nVí dụ: SELECT course_id, COUNT(*) FROM enrollments GROUP BY course_id HAVING COUNT(*) >= 10;\n\nLEFT JOIN giữ mọi dòng bên trái, kể cả khi không có dòng khớp ở bên phải. Cột phía phải khi đó có giá trị NULL. Dùng IS NULL để kiểm tra NULL, không dùng = NULL.","https://www.postgresql.org/docs/current/tutorial-sql.html",1);
        question(sql,"Mệnh đề nào lọc kết quả sau GROUP BY?","WHERE","HAVING","ORDER BY","FROM",1,"HAVING lọc nhóm sau khi tính tổng hợp, WHERE lọc dòng trước khi nhóm.");
        question(sql,"Cách kiểm tra email chưa có giá trị?","email = NULL","email == NULL","email IS NULL","email = 'NULL'",2,"NULL thể hiện giá trị chưa biết; kiểm tra bằng IS NULL.");
        question(sql,"LEFT JOIN giữ những dòng nào?","Mọi dòng bảng bên trái","Chỉ dòng khớp hai bảng","Mọi dòng bảng bên phải","Chỉ dòng không khớp",0,"LEFT JOIN giữ toàn bộ dòng bên trái, điền NULL ở phía phải khi không có dòng khớp.");
        long kotlin=admin.insert("courses","title,description,published","Lập trình Kotlin","Nắm vững biến, kiểu dữ liệu và null safety qua ví dụ ngắn.",true);
        long basics=admin.insert("chapters","course_id,title,position",kotlin,"Biến và null safety",1);
        admin.insert("lessons","chapter_id,title,content,material_url,position",basics,"val, var và kiểu nullable","val khai báo tham chiếu chỉ gán một lần; var cho phép gán lại. Một đối tượng được trỏ bởi val vẫn có thể thay đổi nếu bản thân nó là mutable.\n\nString không nhận null; String? có thể nhận null. Toán tử ?. chỉ gọi thuộc tính hoặc hàm khi đối tượng khác null. Toán tử ?: cung cấp giá trị thay thế.\n\nVí dụ: val length = name?.length ?: 0\n\nTránh dùng !! khi chưa chắc dữ liệu khác null, vì nó có thể gây NullPointerException. Ưu tiên kiểm tra null, safe call hoặc return sớm.","https://kotlinlang.org/docs/null-safety.html",1);
        question(basics,"Khai báo nào cho phép biến nhận null?","val name: String","val name: String?","val name: Int","val name: Boolean",1,"Dấu ? đánh dấu kiểu nullable trong Kotlin.");
        question(basics,"Toán tử ?: dùng để làm gì?","Ép kiểu bắt buộc","So sánh hai số","Chọn giá trị thay thế khi vế trái null","Lặp qua danh sách",2,"Elvis operator trả về vế phải khi vế trái null.");
        question(basics,"Phát biểu nào đúng về val?","Đối tượng luôn bất biến","Không thể gán lại tham chiếu","Không thể chứa danh sách","Chỉ dùng trong hàm",1,"val ngăn gán lại tham chiếu, không tự làm đối tượng trở thành immutable.");
        }
        seedAdditionalContent();
        ensureAlgorithmSecurityContent();
        ensureDemoContentCoverage();
        seedDemoFocusAccount();
        if (richDemo) demoCohortSeeder.seed();
    }

    private void seedDemoFocusAccount() {
        if (demoPassword.isBlank()) return;
        String username = "demo_focus_student";
        var existing = auth.db.queryForList("SELECT id FROM accounts WHERE username=?", username);
        long userId;
        if (existing.isEmpty()) {
            auth.db.update("INSERT INTO accounts(username,display_name,password_hash,role) VALUES(?,?,?,'STUDENT')",
                username, "Học viên demo", auth.passwords.encode(demoPassword));
            userId = auth.db.queryForObject("SELECT id FROM accounts WHERE username=?", Long.class, username);
        } else userId = ((Number) existing.get(0).get("id")).longValue();
        if (auth.db.queryForObject("SELECT COUNT(*) FROM personal_study_snapshots WHERE user_id=?", Integer.class, userId) > 0) return;
        long now = System.currentTimeMillis();
        var sessions = new ArrayList<Map<String,Object>>();
        int id = 1;
        for (int daysAgo = 0; daysAgo < 28; daysAgo++) {
            sessions.add(Map.of("id", id++, "taskTitle", daysAgo % 3 == 0 ? "Ôn tập theo kế hoạch" : "Phiên tập trung",
                "durationMinutes", 20 + (daysAgo * 7 % 36), "sessionType", "focus",
                "timestamp", now - daysAgo * 86_400_000L));
        }
        try {
            String payload = "{\"plans\":[],\"sessions\":" + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sessions) + "}";
            auth.db.update("INSERT INTO personal_study_snapshots(user_id,version,request_id,payload,updated_at) VALUES(?,1,?,?,?)",
                userId, UUID.randomUUID().toString(), payload, now);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not seed demo focus data", e);
        }
    }

    private void ensureDemoContentCoverage() {
        ensureCourseLessons("Cơ sở dữ liệu", "Từ mô hình quan hệ đến truy vấn SQL.", "Mô hình quan hệ và khóa", new String[][]{
            {"Chuẩn hóa dữ liệu và phụ thuộc", "Chuẩn hóa giảm dữ liệu lặp và bất thường cập nhật. Ở 1NF, mỗi ô chứa một giá trị nguyên tố; 2NF loại phụ thuộc bộ phận vào khóa ghép; 3NF loại phụ thuộc bắc cầu giữa thuộc tính không khóa. Luôn xác định khóa và phụ thuộc hàm trước khi phân rã, sau đó kiểm tra phép nối có mất mát hay không."},
            {"JOIN và kết hợp bảng", "INNER JOIN chỉ giữ các dòng có khóa khớp ở cả hai phía. LEFT JOIN giữ mọi dòng bên trái và điền NULL khi không có dòng tương ứng. Nối bảng qua khóa chính/khóa ngoại thay vì ghép chuỗi; kiểm tra số dòng vì quan hệ một-nhiều có thể làm kết quả tăng số bản ghi."},
            {"Transaction và tính chất ACID", "Transaction gom nhiều thao tác thành một đơn vị. Atomicity đảm bảo tất cả cùng thành công hoặc cùng rollback; Consistency giữ ràng buộc dữ liệu; Isolation kiểm soát tương tác đồng thời; Durability giữ kết quả sau commit. Dùng transaction cho chuyển tiền hoặc cập nhật nhiều bảng cần nhất quán."},
            {"Ràng buộc và tính toàn vẹn dữ liệu", "NOT NULL yêu cầu có giá trị, UNIQUE ngăn trùng, CHECK giới hạn miền hợp lệ và FOREIGN KEY kiểm tra tham chiếu. Đặt ràng buộc gần dữ liệu giúp mọi client cùng tuân theo quy tắc. Chọn ON DELETE phù hợp để tránh xóa dây chuyền ngoài ý muốn."},
            {"Index và kế hoạch truy vấn", "Index giúp tìm hàng nhanh hơn nhưng chiếm dung lượng và làm INSERT/UPDATE tốn thêm chi phí. Tạo index trên cột lọc hoặc nối thường dùng, sau đó kiểm tra execution plan và dữ liệu thực tế; không nên thêm index cho mọi cột một cách máy móc."}
        });
        ensureCourseLessons("Lập trình Kotlin", "Nắm vững cú pháp và mô hình dữ liệu Kotlin.", "Biến và null safety", new String[][]{
            {"Collection bất biến và mutable", "List, Set và Map là các giao diện collection phổ biến. Ưu tiên val và collection chỉ đọc khi không cần thay đổi; dùng MutableList hoặc MutableMap khi phải cập nhật. map biến đổi từng phần tử, filter chọn phần tử theo điều kiện, còn groupBy gom nhóm theo khóa."},
            {"Hàm và giá trị trả về", "Khai báo hàm bằng fun, mô tả kiểu tham số và kiểu trả về. Kotlin hỗ trợ tham số mặc định và named arguments giúp lời gọi dễ đọc. Hàm biểu thức có thể dùng dấu =; khi một hàm chỉ làm một việc nhỏ, tách nó ra giúp kiểm thử và tái sử dụng."},
            {"Điều kiện và vòng lặp", "if trong Kotlin là biểu thức có thể trả về giá trị. when phù hợp khi một giá trị cần so với nhiều nhánh. Dùng for để duyệt range hoặc collection; tránh thay đổi chỉ số thủ công nếu có thể dùng forEachIndexed hoặc các toán tử collection."},
            {"Data class và mô hình dữ liệu", "data class tạo sẵn equals, hashCode, toString và copy dựa trên constructor chính. Dùng nó cho dữ liệu như Student(id, name), giữ thuộc tính định danh ổn định và dùng copy để tạo trạng thái mới thay vì sửa ngầm đối tượng dùng chung."},
            {"Extension function và tái sử dụng", "Extension function thêm hàm tiện ích vào một kiểu mà không kế thừa hay sửa lớp gốc. Khai báo fun String.lastWord() và gọi như thành viên, nhưng extension được phân giải tĩnh theo kiểu khai báo. Giữ extension nhỏ, ít tác dụng phụ và tránh tạo API gây nhầm với hàm thành viên."}
        });
        ensureCourseLessons("Thuật toán và cấu trúc dữ liệu", "Luyện phân tích thuật toán và cấu trúc dữ liệu.", "Độ phức tạp và tìm kiếm", new String[][]{
            {"Sắp xếp cơ bản và tính ổn định", "Insertion sort duyệt từng phần tử và chèn vào đoạn đã sắp xếp; trung bình và xấu nhất O(n²), nhưng hiệu quả với mảng nhỏ hoặc gần có thứ tự. Một thuật toán ổn định giữ nguyên thứ tự tương đối của các phần tử có khóa bằng nhau; tính ổn định quan trọng khi sắp xếp nhiều tiêu chí."},
            {"Đệ quy và điều kiện dừng", "Hàm đệ quy giải bài toán bằng cách gọi lại chính nó trên đầu vào nhỏ hơn. Mọi nhánh phải tiến tới điều kiện dừng; nếu không, lời gọi tăng cho đến khi tràn stack. Phân tích cả độ sâu đệ quy và tổng số nhánh để ước lượng thời gian và bộ nhớ."},
            {"Hash table và va chạm", "Hash table ánh xạ khóa tới bucket bằng hàm băm, thường cho tìm kiếm trung bình O(1). Hai khóa có thể cùng bucket; xử lý va chạm bằng chaining hoặc open addressing. Hiệu năng phụ thuộc phân bố hash và hệ số tải, nên cần cập nhật hoặc resize khi bảng quá đầy."},
            {"Đồ thị và duyệt BFS", "Đồ thị gồm đỉnh và cạnh có thể có hướng hoặc trọng số. BFS dùng queue để duyệt theo từng lớp khoảng cách và tìm đường đi ít cạnh nhất trong đồ thị không trọng số. Đánh dấu đã thăm khi đưa đỉnh vào queue để tránh lặp; độ phức tạp danh sách kề là O(V+E)."},
            {"Chọn thuật toán theo ràng buộc", "Xem giới hạn n, bộ nhớ, thứ tự dữ liệu và yêu cầu chính xác trước khi chọn thuật toán. Với n nhỏ, thuật toán đơn giản đôi khi đủ tốt; với n lớn, O(n²) có thể quá chậm. Đo trên dữ liệu đại diện và giữ trường hợp biên trong bộ kiểm thử."}
        });
        ensureCourseLessons("Mạng máy tính và Web căn bản", "Tìm hiểu TCP/IP, HTTP và bảo mật web.", "HTTP, API và bảo mật", new String[][]{
            {"REST API và mã trạng thái", "REST API thường biểu diễn tài nguyên bằng URL và dùng method HTTP theo ý nghĩa: GET đọc, POST tạo hoặc kích hoạt, PUT thay thế/cập nhật, DELETE xóa. Status 2xx là thành công, 4xx là lỗi yêu cầu hoặc xác thực, 5xx là lỗi xử lý phía server. Client nên kiểm tra status và parse lỗi có kiểm soát."},
            {"Cookie, session và CSRF", "Cookie được trình duyệt gửi tự động theo miền và đường dẫn; cờ Secure giới hạn HTTPS, HttpOnly ngăn JavaScript đọc cookie, SameSite giảm một số request cross-site. Với xác thực dựa trên cookie, kiểm tra CSRF token hoặc chính sách SameSite phù hợp cho thao tác thay đổi dữ liệu."},
            {"JSON và kiểm tra đầu vào API", "JSON biểu diễn object, array, string, number, boolean và null. Server cần validate kiểu, độ dài và miền giá trị trước khi ghi cơ sở dữ liệu; không tin dữ liệu từ client dù giao diện đã kiểm tra. Phản hồi lỗi nên có status phù hợp và không làm lộ stack trace."},
            {"TLS và xác minh chứng chỉ", "TLS mã hóa kênh và cho client xác minh chứng chỉ máy chủ theo chuỗi tin cậy, tên miền và thời hạn. Không tắt kiểm tra hostname hoặc chấp nhận mọi chứng chỉ để sửa lỗi kết nối. Certificate pinning chỉ nên triển khai khi có kế hoạch xoay vòng pin và xử lý chứng chỉ dự phòng."},
            {"Phân trang API", "Phân trang giới hạn lượng dữ liệu trả về mỗi lần. Offset đơn giản nhưng có thể bỏ sót hoặc lặp bản ghi khi dữ liệu thay đổi; cursor dựa trên khóa ổn định thường phù hợp với danh sách lớn. API cần giới hạn page size để tránh truy vấn hoặc phản hồi quá lớn."}
        });

        long databaseChapter = chapterId("Cơ sở dữ liệu", "Mô hình quan hệ và khóa");
        long sqlChapter = chapterId("Cơ sở dữ liệu", "Truy vấn SQL");
        long kotlinChapter = chapterId("Lập trình Kotlin", "Biến và null safety");
        long algorithmChapter = chapterId("Thuật toán và cấu trúc dữ liệu", "Độ phức tạp và tìm kiếm");
        long webChapter = chapterId("Mạng máy tính và Web căn bản", "HTTP, API và bảo mật");
        ensureQuestion(databaseChapter, "Khi nào phân rã bảng giúp đạt chuẩn hóa cao hơn?", "Khi giảm phụ thuộc bộ phận hoặc bắc cầu mà vẫn giữ được dữ liệu", "Khi thêm càng nhiều cột nullable càng tốt", "Khi bỏ mọi khóa ngoại", "Khi ghép mọi bảng thành một", 0, 2, "Phân rã dựa trên khóa và phụ thuộc hàm để hạn chế lặp, đồng thời cần kiểm tra phép nối không mất mát.");
        ensureQuestion(sqlChapter, "Nếu một giao dịch cập nhật hai tài khoản nhưng chỉ một cập nhật thành công thì tính chất ACID nào bị vi phạm?", "Atomicity", "Durability", "Indexing", "Normalization", 0, 3, "Atomicity yêu cầu các thao tác của giao dịch được commit cùng nhau hoặc rollback cùng nhau.");
        ensureQuestion(kotlinChapter, "Biểu thức nào gọi length an toàn khi name có thể null?", "name.length", "name!!.length", "name?.length", "length(name)", 2, 2, "Safe-call ?. chỉ truy cập length khi name khác null và trả null nếu name là null.");
        ensureQuestion(algorithmChapter, "Với mảng đã sắp xếp n phần tử, số lần chia đôi cần thiết để tìm kiếm nhị phân kết thúc có bậc nào?", "O(1)", "O(log n)", "O(n)", "O(n²)", 1, 3, "Mỗi vòng lặp giảm khoảng tìm kiếm còn một nửa nên số vòng tăng theo logarit cơ số 2 của n.");
        ensureQuestion(webChapter, "Client đã đăng nhập nhưng không có quyền truy cập tài nguyên thường nhận mã nào?", "200", "301", "403", "503", 2, 2, "403 biểu thị đã xác thực nhưng bị từ chối quyền; 401 thường dùng khi chưa xác thực hoặc thông tin phiên không hợp lệ.");
        ensureQuestion(databaseChapter, "Khóa ngoại trong bảng enrollment thường tham chiếu tới đâu?", "Khóa chính của bảng sinh viên hoặc môn học", "Tên hiển thị của sinh viên", "Một chuỗi JSON bất kỳ", "Vị trí dòng trong bảng", 0, 1, "Khóa ngoại tham chiếu khóa định danh của bảng được liên kết và hỗ trợ toàn vẹn tham chiếu.");
        ensureQuestion(sqlChapter, "Mệnh đề nào lọc nhóm sau khi áp dụng GROUP BY?", "WHERE", "HAVING", "FROM", "LIMIT", 1, 1, "HAVING lọc nhóm sau tổng hợp; WHERE lọc các dòng trước khi nhóm.");
        ensureQuestion(kotlinChapter, "Toán tử Elvis `?:` trả về vế phải trong trường hợp nào?", "Luôn luôn", "Khi vế trái là null", "Khi hai vế bằng nhau", "Khi vòng lặp kết thúc", 1, 1, "Elvis operator dùng giá trị thay thế ở vế phải khi biểu thức bên trái null.");
        ensureQuestion(algorithmChapter, "Khi nào insertion sort thường có lợi thế dù độ phức tạp xấu nhất là O(n²)?", "Dữ liệu nhỏ hoặc gần như đã sắp xếp", "Dữ liệu luôn là đồ thị lớn", "Cần truy vấn hash O(1)", "Mảng chưa được cấp phát", 0, 2, "Insertion sort có chi phí thấp và chạy gần tuyến tính khi số nghịch thế ít.");
        ensureQuestion(webChapter, "Vì sao bearer token không nên đặt trong query string?", "URL có thể bị lưu trong log, lịch sử hoặc referrer", "Query string luôn bị mã hóa bằng TLS", "Server không thể đọc query string", "Token chỉ dùng cho GET", 0, 3, "URL thường xuất hiện ở nhiều nơi ghi log/chia sẻ; token nên gửi qua header Authorization và được bảo vệ.");

        int activeQuestions = auth.db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE", Integer.class);
        if (activeQuestions < 30) throw new IllegalStateException("StudySpace demo seed must provide at least 30 active questions; found " + activeQuestions);
        for (int level = 1; level <= 3; level++) {
            if (auth.db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE AND difficulty=?", Integer.class, level) == 0)
                throw new IllegalStateException("StudySpace demo seed is missing difficulty level " + level);
        }
        if (auth.db.queryForObject("SELECT COUNT(*) FROM questions WHERE active=TRUE AND (explanation IS NULL OR TRIM(explanation)='')", Integer.class) > 0)
            throw new IllegalStateException("StudySpace demo questions must include explanations");
    }

    private void ensureCourseLessons(String title, String description, String chapterTitle, String[][] lessonDrafts) {
        var course = auth.db.queryForList("SELECT id FROM courses WHERE title=?", title);
        long courseId;
        if (course.isEmpty()) courseId = admin.insert("courses", "title,description,published", title, description, true);
        else courseId = ((Number) course.get(0).get("id")).longValue();

        var chapters = auth.db.queryForList("SELECT id FROM chapters WHERE course_id=? ORDER BY position,id", courseId);
        long chapterId;
        if (chapters.isEmpty()) chapterId = admin.insert("chapters", "course_id,title,position", courseId, chapterTitle, 1);
        else {
            var target = auth.db.queryForList("SELECT id FROM chapters WHERE course_id=? AND title=?", courseId, chapterTitle);
            chapterId = target.isEmpty() ? admin.insert("chapters", "course_id,title,position", courseId, chapterTitle, chapters.size() + 1) : ((Number) target.get(0).get("id")).longValue();
        }

        int lessonCount = auth.db.queryForObject("SELECT COUNT(*) FROM lessons WHERE chapter_id=?", Integer.class, chapterId);
        int position = auth.db.queryForObject("SELECT COUNT(*) FROM lessons WHERE chapter_id=?", Integer.class, chapterId) + 1;
        for (var draft : lessonDrafts) {
            if (lessonCount >= 5) break;
            int exists = auth.db.queryForObject("SELECT COUNT(*) FROM lessons WHERE chapter_id=? AND title=?", Integer.class, chapterId, draft[0]);
            if (exists == 0) {
                admin.insert("lessons", "chapter_id,title,content,material_url,position", chapterId, draft[0], draft[1], "", position++);
                lessonCount++;
            }
        }
        if (lessonCount < 5) throw new IllegalStateException("StudySpace seed has fewer than five lessons for " + title);
    }

    private long chapterId(String courseTitle, String chapterTitle) {
        return ((Number) auth.db.queryForList("SELECT ch.id FROM chapters ch JOIN courses c ON c.id=ch.course_id WHERE c.title=? AND ch.title=?", courseTitle, chapterTitle).get(0).get("id")).longValue();
    }

    private void ensureQuestion(long chapter, String prompt, String a, String b, String c, String d, int correct, int difficulty, String explanation) {
        if (auth.db.queryForObject("SELECT COUNT(*) FROM questions WHERE chapter_id=? AND prompt=?", Integer.class, chapter, prompt) == 0)
            admin.insert("questions", "chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty", chapter, prompt, a, b, c, d, correct, explanation, difficulty);
    }

    private void ensureAlgorithmSecurityContent() {
        String courseTitle = "Bảo mật thuật toán";
        String description = "Học cách thiết kế và đánh giá thuật toán trước các rủi ro thực tế: rò rỉ thời gian, dùng sai nguyên thủy mật mã, quản lý nonce và kiểm thử triển khai.";
        var courses = auth.db.queryForList("SELECT id,published FROM courses WHERE lower(title)=lower(?) ORDER BY id LIMIT 1", courseTitle);
        long courseId;
        boolean shouldPublish;
        if (courses.isEmpty()) {
            courseId = admin.insert("courses", "title,description,published", courseTitle, description, false);
            shouldPublish = true;
        } else {
            var course = courses.get(0);
            courseId = ((Number) course.get("id")).longValue();
            shouldPublish = Boolean.TRUE.equals(course.get("published"));
            auth.db.update("UPDATE courses SET title=?,description=? WHERE id=?", courseTitle, description, courseId);
        }

        long foundations = ensureAlgorithmChapter(courseId, "Nền tảng bảo mật thuật toán", 1,
            "Chương tiếp theo trong lộ trình");
        long crypto = ensureAlgorithmChapter(courseId, "Nguyên lý mật mã ứng dụng an toàn", 2, null);
        long verification = ensureAlgorithmChapter(courseId, "Phân tích và kiểm thử an toàn", 3, null);

        String[][] lessons = {
            {"Mô hình đe dọa và mục tiêu an toàn", "Mục tiêu: phân biệt tài sản, tác nhân tấn công, bề mặt tấn công và ranh giới tin cậy.\n\nBảo mật thuật toán bắt đầu bằng câu hỏi dữ liệu nào cần bảo vệ, ai có thể quan sát hoặc sửa dữ liệu, và hệ thống cho phép họ làm gì. Tính bí mật ngăn đọc trái phép; toàn vẹn phát hiện sửa đổi; xác thực xác định nguồn gửi; sẵn sàng bảo đảm dịch vụ có thể sử dụng.\n\nVí dụ: một API nhận mã reset mật khẩu cần giới hạn thời hạn, chỉ dùng một lần và gắn với đúng tài khoản. Nếu chỉ làm mã khó đoán nhưng không giới hạn số lần thử, kẻ tấn công vẫn có thể dò tìm.\n\nThực hành: vẽ luồng đăng nhập, đánh dấu dữ liệu nhạy cảm và viết ít nhất ba giả định cần kiểm chứng."},
            {"Thời gian chạy và kênh kề", "Mục tiêu: nhận biết thời gian, bộ nhớ đệm và nhánh điều kiện có thể làm lộ bí mật.\n\nĐộ phức tạp Big O mô tả tốc độ tăng tổng quát nhưng không nói mọi đầu vào chạy cùng thời gian. Nếu một thuật toán so sánh từng ký tự của MAC rồi dừng tại vị trí sai đầu tiên, thời gian phản hồi có thể tiết lộ số ký tự đầu đã khớp. Đây là kênh kề thời gian.\n\nSo sánh bí mật nên dùng primitive constant-time đã được thư viện mật mã kiểm chứng; không tự viết vòng lặp so sánh hoặc thêm sleep để che thời gian. Đo đạc kiểm thử giúp phát hiện khác biệt rõ ràng nhưng không tự chứng minh an toàn tuyệt đối.\n\nThực hành: xác định nhánh phụ thuộc dữ liệu bí mật và thay bằng API so sánh an toàn của thư viện."},
            {"Chọn hash, MAC và chữ ký số", "Mục tiêu: chọn đúng primitive cho kiểm tra toàn vẹn, xác thực thông điệp và xác minh nguồn gửi.\n\nHash mật mã như SHA-256 tạo dấu kiểm tra nhưng không có khóa, vì vậy kẻ sửa dữ liệu có thể tự tính lại hash. HMAC kết hợp hash với khóa bí mật để xác thực thông điệp giữa các bên cùng giữ khóa. Chữ ký số dùng cặp khóa riêng/công khai, cho phép kiểm tra chữ ký mà không chia sẻ khóa ký.\n\nKhông dùng hash thông thường làm MAC và không tự ghép chuỗi với khóa theo cách tùy ý. Dùng API chuẩn, kiểm tra độ dài/định dạng đầu vào và quản lý khóa ngoài mã nguồn.\n\nVí dụ: HMAC-SHA-256 xác thực webhook; chữ ký số phù hợp để xác minh gói cập nhật phát hành bởi nhà cung cấp."},
            {"Mã hóa đối xứng và nonce", "Mục tiêu: mô tả đúng cách dùng AES-GCM và tránh tái sử dụng nonce với cùng khóa.\n\nMã hóa đối xứng dùng cùng một khóa để mã hóa và giải mã. AES-GCM vừa bảo mật nội dung vừa phát hiện sửa đổi; dữ liệu giải mã chỉ được chấp nhận khi thẻ xác thực hợp lệ. Nonce/IV cần duy nhất cho mỗi lần mã hóa với cùng khóa. Tái sử dụng nonce trong GCM có thể làm lộ quan hệ giữa bản rõ và phá vỡ tính xác thực.\n\nLưu cùng ciphertext các thông số không bí mật như phiên bản định dạng và nonce; không lưu khóa cùng tệp mã hóa. Trên Android, khóa nên được tạo và giữ trong Android Keystore.\n\nThực hành: mô tả cách sinh nonce ngẫu nhiên đủ dài, đóng gói dữ liệu và xử lý lỗi xác thực mà không trả bản rõ một phần."},
            {"Kiểm tra đầu vào và giới hạn tài nguyên", "Mục tiêu: phát hiện đầu vào có thể làm thuật toán tiêu tốn quá nhiều CPU, bộ nhớ hoặc đệ quy.\n\nMột thuật toán đúng về mặt toán học vẫn có thể bị khai thác nếu nhận độ dài không giới hạn. Biểu thức chính quy gây backtracking, phép lũy thừa trên số lớn và đệ quy không giới hạn có thể dẫn tới cạn tài nguyên.\n\nĐặt giới hạn kích thước trước khi parse, chọn thuật toán có độ phức tạp phù hợp và dùng timeout/giới hạn concurrency ở tầng dịch vụ. Kiểm tra cả trường hợp rỗng, cực đại, tràn số và cấu trúc lồng nhau sâu.\n\nThực hành: thêm test biên cho một parser và xác nhận input quá cỡ bị từ chối trước khi xử lý tốn kém."},
            {"Kiểm thử mật mã và triển khai", "Mục tiêu: dùng test vector, kiểm thử âm và rà soát lỗi triển khai thay vì tự chứng minh bằng vài ví dụ.\n\nTest vector chuẩn giúp xác nhận thuật toán và cách serialize dữ liệu. Kiểm thử âm cần thay từng bit ciphertext/tag, dùng sai khóa, nonce trùng và dữ liệu bị cắt; mọi trường hợp phải thất bại an toàn. Fuzzing có thể tìm input gây crash hoặc vượt giới hạn.\n\nKhông ghi plaintext, khóa, token hoặc nội dung nhạy cảm vào log. Khi xác thực thất bại, trả lỗi chung cho client và giữ chi tiết kỹ thuật trong log đã loại dữ liệu bí mật.\n\nChecklist: thư viện được bảo trì, tham số theo khuyến nghị, khóa có vòng đời rõ, test có trường hợp biên và quy trình xoay khóa được ghi lại."}
        };
        long[] chapterIds = {foundations, crypto, verification};
        for (int i = 0; i < chapterIds.length; i++) {
            ensureAlgorithmLesson(chapterIds[i], lessons[i * 2][0], lessons[i * 2][1], i == 0 ? "Bài học: Chương tiếp theo trong lộ trình" : null, 1);
            ensureAlgorithmLesson(chapterIds[i], lessons[i * 2 + 1][0], lessons[i * 2 + 1][1], null, 2);
        }

        String[][] questions = {
            {"Vì sao so sánh MAC bằng cách dừng ở ký tự sai đầu tiên có thể nguy hiểm?", "Thời gian phản hồi có thể tiết lộ số ký tự đầu khớp", "Hash sẽ tự đổi độ dài", "Nonce bị biến thành khóa", "Chữ ký số mất khóa công khai", "0", "2", "Độ dài thời gian phụ thuộc vị trí khác biệt tạo kênh kề giúp suy đoán MAC."},
            {"Điểm khác nhau quan trọng giữa hash thường và HMAC là gì?", "HMAC dùng khóa bí mật để xác thực thông điệp", "Hash luôn mã hóa bản rõ", "HMAC không cần đầu vào", "Hash dùng cặp khóa công khai", "0", "1", "Hash không có khóa nên ai cũng có thể tính lại; HMAC cần khóa bí mật dùng chung."},
            {"Khi dùng AES-GCM, yêu cầu nào áp dụng cho nonce với cùng một khóa?", "Nonce phải duy nhất cho mỗi lần mã hóa", "Nonce phải giữ bí mật như khóa", "Có thể dùng lại nếu bản rõ ngắn", "Nonce phải bằng 0", "0", "2", "Tái sử dụng nonce trong GCM có thể làm lộ dữ liệu và phá xác thực."},
            {"Vì sao hash của tệp không đủ để xác thực nguồn gửi tệp?", "Kẻ sửa tệp có thể tự tính lại hash", "Hash không hoạt động trên tệp", "Hash luôn bị mất khi tải xuống", "Hash chỉ dùng cho số nguyên", "0", "1", "Hash không có khóa bí mật; dùng chữ ký số hoặc HMAC tùy mô hình tin cậy."},
            {"Một endpoint nhận số nguyên dùng làm số vòng lặp không giới hạn có rủi ro nào?", "Cạn CPU hoặc tài nguyên do giá trị cực lớn", "Tự động nâng quyền admin", "Nonce tự trở nên duy nhất", "Mọi test vector đều pass", "0", "3", "Giới hạn đầu vào và tài nguyên ngăn yêu cầu gây xử lý quá lâu hoặc tiêu tốn bộ nhớ."},
            {"Test âm phù hợp cho hàm giải mã AES-GCM là gì?", "Đổi một bit của tag rồi xác nhận giải mã thất bại", "Chỉ kiểm tra ciphertext đúng một lần", "Ghi khóa vào log để so sánh", "Bỏ qua kiểm tra tag khi lỗi", "0", "3", "Thay đổi tag phải khiến xác thực thất bại và ứng dụng không được trả bản rõ."}
        };
        for (int i = 0; i < questions.length; i++) {
            int chapterIndex = i / 2;
            int difficulty = Integer.parseInt(questions[i][6]);
            ensureAlgorithmQuestion(chapterIds[chapterIndex], questions[i], difficulty, i == 0);
        }
        if (shouldPublish) auth.db.update("UPDATE courses SET published=TRUE WHERE id=?", courseId);
    }

    private long ensureAlgorithmChapter(long courseId, String title, int position, String oldTitle) {
        var matches = oldTitle == null
            ? auth.db.queryForList("SELECT id FROM chapters WHERE course_id=? AND title=? ORDER BY id LIMIT 1", courseId, title)
            : auth.db.queryForList("SELECT id FROM chapters WHERE course_id=? AND (title=? OR title=?) ORDER BY CASE WHEN title=? THEN 0 ELSE 1 END,id LIMIT 1", courseId, title, oldTitle, title);
        if (matches.isEmpty()) return admin.insert("chapters", "course_id,title,position", courseId, title, position);
        long chapterId = ((Number) matches.get(0).get("id")).longValue();
        auth.db.update("UPDATE chapters SET title=?,position=? WHERE id=?", title, position, chapterId);
        return chapterId;
    }

    private void ensureAlgorithmLesson(long chapterId, String title, String content, String oldTitle, int position) {
        var existing = oldTitle == null
            ? auth.db.queryForList("SELECT id,content FROM lessons WHERE chapter_id=? AND title=? ORDER BY id LIMIT 1", chapterId, title)
            : auth.db.queryForList("SELECT id,content FROM lessons WHERE chapter_id=? AND (title=? OR title=?) ORDER BY CASE WHEN title=? THEN 0 ELSE 1 END,id LIMIT 1", chapterId, title, oldTitle, title);
        if (existing.isEmpty()) {
            admin.insert("lessons", "chapter_id,title,content,material_url,position", chapterId, title, content, "", position);
            return;
        }
        long lessonId = ((Number) existing.get(0).get("id")).longValue();
        String oldContent = Objects.toString(existing.get(0).get("content"), "");
        boolean isLegacyPlaceholder = oldTitle != null && oldContent.contains("Hiểu khái niệm và biết cách áp dụng vào một ví dụ.");
        if (!title.equals(auth.db.queryForObject("SELECT title FROM lessons WHERE id=?", String.class, lessonId)) || isLegacyPlaceholder)
            auth.db.update("UPDATE lessons SET title=?,content=?,material_url='',position=? WHERE id=?", title, content, position, lessonId);
    }

    private void ensureAlgorithmQuestion(long chapterId, String[] question, int difficulty, boolean replaceLegacy) {
        String prompt = question[0];
        var exists = auth.db.queryForList("SELECT id FROM questions WHERE chapter_id=? AND prompt=? ORDER BY id LIMIT 1", chapterId, prompt);
        if (!exists.isEmpty()) return;
        var legacy = replaceLegacy
            ? auth.db.queryForList("SELECT id FROM questions WHERE chapter_id=? AND active=TRUE AND prompt LIKE 'Theo bài học%' ORDER BY id LIMIT 1", chapterId)
            : List.<java.util.Map<String,Object>>of();
        Object[] values = {question[1], question[2], question[3], question[4]};
        int correct = Integer.parseInt(question[5]);
        String explanation = question[7];
        if (!legacy.isEmpty()) {
            long id = ((Number) legacy.get(0).get("id")).longValue();
            auth.db.update("UPDATE questions SET prompt=?,option_a=?,option_b=?,option_c=?,option_d=?,correct_option=?,explanation=?,difficulty=?,active=TRUE WHERE id=?",
                prompt, values[0], values[1], values[2], values[3], correct, explanation, difficulty, id);
        } else {
            admin.insert("questions", "chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty,active",
                chapterId, prompt, values[0], values[1], values[2], values[3], correct, explanation, difficulty, true);
        }
    }

    private void seedAdditionalContent() {
        if(auth.db.queryForObject("SELECT COUNT(*) FROM courses WHERE title=?",Integer.class,"Thuật toán và cấu trúc dữ liệu")==0) {
            long course=admin.insert("courses","title,description,published","Thuật toán và cấu trúc dữ liệu","Luyện tư duy phân tích độ phức tạp, mảng, ngăn xếp, hàng đợi và tìm kiếm.",true);
            long complexity=admin.insert("chapters","course_id,title,position",course,"Độ phức tạp và tìm kiếm",1);
            admin.insert("lessons","chapter_id,title,content,material_url,position",complexity,"Big O và tăng trưởng đầu vào","Big O mô tả tốc độ tăng của chi phí khi kích thước đầu vào tăng. O(1) không phụ thuộc n; O(log n) thường chia đôi không gian tìm kiếm; O(n) duyệt một lượt; O(n log n) phổ biến ở thuật toán sắp xếp tốt; O(n²) thường xuất hiện ở vòng lặp lồng nhau.\n\nKhi phân tích, xác định phép toán chi phối, bỏ hằng số và xét trường hợp phù hợp. Bộ nhớ phụ cũng cần được tính riêng.","",1);
            admin.insert("lessons","chapter_id,title,content,material_url,position",complexity,"Tìm kiếm nhị phân","Tìm kiếm nhị phân yêu cầu dữ liệu đã sắp xếp. Mỗi bước so sánh phần tử giữa với giá trị cần tìm rồi loại bỏ một nửa khoảng còn lại, vì vậy thời gian là O(log n).\n\nDùng mid = low + (high - low) / 2 để tránh tràn khi cộng low và high. Sau mỗi bước, khoảng tìm kiếm phải giảm và điều kiện dừng phải xử lý được mảng rỗng.","",2);
            question(complexity,"Điều kiện bắt buộc để tìm kiếm nhị phân hoạt động đúng?","Mảng có số phần tử chẵn","Dữ liệu được sắp xếp","Không có giá trị trùng","Dùng danh sách liên kết",1,"Mỗi bước dựa vào thứ tự để loại bỏ một nửa khoảng tìm kiếm.");
            question(complexity,"Độ phức tạp thời gian của tìm kiếm nhị phân là gì?","O(1)","O(log n)","O(n)","O(n²)",1,"Mỗi vòng lặp giảm khoảng tìm kiếm còn một nửa.");
            question(complexity,"Cách tính mid nào hạn chế tràn số nguyên?","(low + high) / 2","low + (high - low) / 2","low * high / 2","high - low / 2",1,"Biểu thức này không cộng trực tiếp hai chỉ số lớn.");
            question(complexity,"Hai vòng lặp đều duyệt n phần tử thường có độ phức tạp nào?","O(log n)","O(n)","O(n²)","O(2n)",2,"Hai vòng lặp lồng nhau thực hiện số phép toán tỷ lệ với n bình phương.");
            long structures=admin.insert("chapters","course_id,title,position",course,"Cấu trúc dữ liệu tuyến tính",2);
            admin.insert("lessons","chapter_id,title,content,material_url,position",structures,"Stack và Queue","Stack theo nguyên tắc vào sau ra trước (LIFO), phù hợp với undo và lời gọi hàm. Queue theo nguyên tắc vào trước ra trước (FIFO), phù hợp với hàng đợi xử lý.\n\nChọn cấu trúc theo thao tác cần tối ưu: thêm/xóa ở đầu, cuối hay truy cập theo chỉ số.","",1);
            admin.insert("lessons","chapter_id,title,content,material_url,position",structures,"Chọn cấu trúc cho bài toán","Mảng truy cập theo chỉ số O(1) nhưng chèn giữa thường cần dịch phần tử O(n). Stack/queue giới hạn cách lấy phần tử để thao tác đầu/cuối đơn giản.\n\nTrước khi chọn, liệt kê thao tác phổ biến, kích thước dữ liệu và thứ tự xử lý cần giữ.","",2);
            question(structures,"Stack lấy phần tử theo thứ tự nào?","FIFO","LIFO","Theo giá trị nhỏ nhất","Ngẫu nhiên",1,"Stack lấy ra phần tử được thêm gần nhất.");
            question(structures,"Queue phù hợp nhất với quy tắc nào?","Vào sau ra trước","Vào trước ra trước","Lấy phần tử lớn nhất","Truy cập ngẫu nhiên",1,"Queue xử lý phần tử theo thứ tự đến.");
            question(structures,"Truy cập phần tử mảng theo chỉ số thường có độ phức tạp?","O(1)","O(log n)","O(n)","O(n²)",0,"Mảng tính trực tiếp địa chỉ từ chỉ số.");
            question(structures,"Chèn đầu mảng động thường tốn O(n) vì sao?","Phải sắp xếp lại toàn bộ","Các phần tử sau vị trí chèn phải dịch","Phải duyệt đồ thị","Phải cấp phát đệ quy",1,"Các phần tử hiện có cần dịch để tạo chỗ ở đầu.");
        }
        if(auth.db.queryForObject("SELECT COUNT(*) FROM courses WHERE title=?",Integer.class,"Mạng máy tính và Web căn bản")==0) {
            long course=admin.insert("courses","title,description,published","Mạng máy tính và Web căn bản","Nắm mô hình TCP/IP, địa chỉ, HTTP, API và an toàn khi trao đổi dữ liệu.",true);
            long network=admin.insert("chapters","course_id,title,position",course,"Mạng và giao thức",1);
            admin.insert("lessons","chapter_id,title,content,material_url,position",network,"Mô hình TCP/IP","Mô hình TCP/IP tổ chức giao tiếp theo các lớp: truy cập mạng, Internet, giao vận và ứng dụng. IP chuyển gói giữa các mạng; TCP cung cấp luồng tin cậy theo thứ tự; UDP giảm chi phí khi ứng dụng chấp nhận mất gói hoặc tự xử lý.\n\nĐịa chỉ IP xác định giao diện mạng, còn cổng giúp hệ điều hành chuyển dữ liệu tới đúng tiến trình.","",1);
            admin.insert("lessons","chapter_id,title,content,material_url,position",network,"DNS và kết nối dịch vụ","DNS ánh xạ tên miền sang địa chỉ IP. Sau khi phân giải tên, client thiết lập kết nối tới máy chủ và cổng dịch vụ.\n\nKhi chẩn đoán lỗi, kiểm tra lần lượt phân giải DNS, khả năng kết nối mạng, cổng, TLS và phản hồi ứng dụng thay vì chỉ thử lại ngẫu nhiên.","",2);
            question(network,"Giao thức nào đảm bảo luồng dữ liệu tin cậy, theo thứ tự?","UDP","TCP","DNS","ARP",1,"TCP hỗ trợ truyền tin tin cậy và sắp thứ tự luồng byte.");
            question(network,"DNS chủ yếu dùng để làm gì?","Mã hóa mật khẩu","Đổi tên miền thành địa chỉ IP","Cấp phát điểm quiz","Nén file PDF",1,"DNS phân giải tên miền thành dữ liệu định tuyến như địa chỉ IP.");
            question(network,"Cổng mạng giúp hệ điều hành xác định điều gì?","Hệ điều hành của máy đối tác","Tiến trình/dịch vụ nhận dữ liệu","Vị trí GPS","Loại cơ sở dữ liệu",1,"Port định tuyến dữ liệu tới dịch vụ đang lắng nghe trên máy.");
            question(network,"UDP khác TCP ở đặc điểm nào?","Luôn đảm bảo giao hàng","Không cần địa chỉ IP","Không đảm bảo giao hàng theo thứ tự ở tầng giao vận","Chỉ dùng cho web",2,"UDP nhẹ hơn nhưng không cung cấp cơ chế tin cậy như TCP.");
            long web=admin.insert("chapters","course_id,title,position",course,"HTTP, API và bảo mật",2);
            admin.insert("lessons","chapter_id,title,content,material_url,position",web,"HTTP request và response","HTTP trao đổi theo request/response. Request có method, URL, headers và đôi khi có body; response có status code, headers và body. GET thường đọc dữ liệu, POST tạo hoặc kích hoạt xử lý, PUT cập nhật, DELETE xóa hoặc lưu trữ.\n\nMã 2xx báo thành công, 4xx thường liên quan yêu cầu/client, 5xx thường là lỗi phía server.","",1);
            admin.insert("lessons","chapter_id,title,content,material_url,position",web,"HTTPS và token phiên","HTTPS bảo vệ dữ liệu trên đường truyền bằng TLS và giúp client xác minh máy chủ. Token phiên là thông tin nhạy cảm: không ghi vào log, URL hoặc lưu nơi công khai.\n\nAPI phải kiểm tra quyền ở server cho từng tài nguyên; ẩn nút trên giao diện không thay thế kiểm soát truy cập.","",2);
            question(web,"HTTP status 401 thường biểu thị điều gì?","Yêu cầu hợp lệ","Chưa xác thực hoặc phiên không hợp lệ","Tài nguyên không đổi","Server đã xong bảo trì",1,"401 biểu thị cần xác thực hoặc thông tin xác thực không hợp lệ.");
            question(web,"HTTPS bổ sung lớp bảo vệ nào cho HTTP?","TLS bảo vệ kênh truyền và xác minh máy chủ","Tự động chống mọi lỗi logic","Tự động phân quyền mọi bản ghi","Thay thế cơ sở dữ liệu",0,"HTTPS dùng TLS để bảo vệ dữ liệu khi truyền và xác thực endpoint.");
            question(web,"Nơi nào không nên đặt bearer token?","Header Authorization","Kho bí mật được bảo vệ","Query string của URL","Bộ nhớ phiên phù hợp",2,"URL dễ lọt vào lịch sử, log và header referrer.");
            question(web,"Ẩn nút quản trị ở giao diện có đủ để bảo vệ API không?","Có, vì người dùng không thấy nút","Không, server phải tự kiểm tra quyền","Chỉ cần đổi màu nút","Có, nếu dùng HTTPS",1,"Kiểm soát quyền phải được thực thi ở server cho từng API.");
        }
    }
    void question(long chapter,String prompt,String a,String b,String c,String d,int correct,String explanation) {
        admin.insert("questions","chapter_id,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty",chapter,prompt,a,b,c,d,correct,explanation,1);
    }
}
