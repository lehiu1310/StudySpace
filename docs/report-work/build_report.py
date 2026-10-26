from __future__ import annotations

import os
import re
import zipfile
from copy import deepcopy
from pathlib import Path
from textwrap import wrap

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor
from docx.table import Table
from docx.text.paragraph import Paragraph
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "BaoCao_DoAn_StudySpace_BanDayDu.docx"
SAMPLE = Path(os.environ.get("STUDYSPACE_REPORT_SAMPLE", Path(os.environ["TEMP"]) / "StudySpace_Report_Sample.docx"))
ASSET = ROOT / "docs" / "report-work" / "assets"
ASSET.mkdir(parents=True, exist_ok=True)

TITLE = "XÂY DỰNG NỀN TẢNG HỌC TẬP TRỰC TUYẾN STUDYSPACE"
SUBTITLE = "Quản lý nội dung, quiz, tiến độ học tập và trợ lý AI"
AS_OF = "Hà Nội, tháng 10 năm 2026"

PAGES = [
    ("1.1. Bối cảnh học tập số", [
        "Học tập ở bậc đại học thường diễn ra đồng thời trên nhiều học phần, mỗi học phần lại có cấu trúc nội dung, bài tập và nhịp đánh giá riêng. Khi tài liệu nằm ở nhiều nơi, người học phải tự tổng hợp tiến độ bằng ghi chú hoặc bảng tính. Việc biết mình đã xem bài nào chưa đồng nghĩa với việc hiểu được phần kiến thức nào đang yếu; vì vậy, hệ thống cần lưu cả hoạt động lẫn kết quả đánh giá theo một cấu trúc có thể truy vấn.",
        "StudySpace được đặt trong bối cảnh đó như một nền tảng hỗ trợ quá trình học, không phải một kho tài liệu độc lập. Một khóa học gồm chương và bài; quiz gắn với chương; kết quả được liên hệ với tài khoản; báo cáo dùng lịch sử này để mô tả sự tiến bộ. Kế hoạch và phiên tập trung bổ sung góc nhìn về nhịp học cá nhân nhưng không thay cho dữ liệu hoàn thành bài hay điểm quiz.",
        "Đề tài kết hợp kiến thức công nghệ thông tin với các nguyên tắc giáo dục thực hành: mục tiêu cần rõ, nội dung được chia nhỏ, phản hồi xuất hiện sau hành động và người học cần nhìn thấy bước tiếp theo. Những nguyên tắc này được chuyển thành yêu cầu giao diện và dữ liệu thay vì chỉ dùng làm khẩu hiệu giới thiệu sản phẩm."
    ]),
    ("1.2. Vấn đề và động cơ lựa chọn", [
        "Nếu chỉ lưu điểm tổng kết, người học khó xác định chương nào cần ôn. Nếu chỉ lưu trạng thái đã mở bài, hệ thống không thể phân biệt việc xem lướt với việc hoàn thành nội dung. Nếu nội dung do giảng viên tạo không liên kết thống nhất với lớp và quiz, học viên sẽ gặp một giao diện có nhiều tác vụ nhưng thiếu một hành trình học xuyên suốt.",
        "StudySpace giải quyết các vấn đề ở phạm vi một đồ án bằng cách tổ chức quan hệ Course–Chapter–Lesson–Question, ghi nhận LessonProgress và QuizAttempt, đồng thời cung cấp vai trò riêng cho học viên, giảng viên và quản trị viên. Luồng được ưu tiên là: giảng viên xây dựng nội dung, xuất bản khóa, tạo lớp; học viên ghi danh, học bài, làm quiz; hệ thống ghi nhận kết quả và trình bày thống kê.",
        "Động cơ thứ hai là biến báo cáo thống kê thành một phần của trải nghiệm học. Điểm thấp không tự động kết luận người học thiếu năng lực; nó là tín hiệu để lựa chọn nội dung ôn tiếp theo. Tương tự, danh sách câu sai của lớp là công cụ để giảng viên xem lại chất lượng câu hỏi hoặc cách giảng, không phải bảng xếp hạng để gây áp lực cho học viên."
    ]),
    ("1.3. Mục tiêu, phạm vi và phương pháp", [
        "Mục tiêu tổng quát là phân tích, thiết kế và xây dựng StudySpace với các chức năng quản lý nội dung học tập, quiz trắc nghiệm, theo dõi tiến độ, quản lý người dùng và báo cáo thống kê. Sản phẩm có cổng web cho ba vai trò, backend REST sử dụng PostgreSQL và ứng dụng Android Kotlin/Compose có lưu trữ cục bộ Room. Chart.js và Canvas Compose phục vụ trực quan hóa phù hợp trên từng nền tảng.",
        "Đây là đồ án liên ngành giữa công nghệ thông tin và giáo dục. Phần công nghệ bao gồm phân tích yêu cầu, thiết kế kiến trúc client–server, lập trình web/Android, cơ sở dữ liệu quan hệ, bảo mật và kiểm thử. Phần giáo dục định hướng cách chia nhỏ nội dung theo môn–chương–bài, thiết kế quiz để phản hồi sau học, theo dõi tiến bộ theo thời gian và đưa ra gợi ý ôn tập có căn cứ. Hai lĩnh vực được kết nối trong cùng luồng sử dụng: giảng viên tổ chức nội dung, học viên học và tự đánh giá, hệ thống ghi nhận dữ liệu để hỗ trợ quyết định dạy và học.",
        "Phạm vi không bao gồm một hệ thống quản lý học tập quy mô thương mại, tích hợp thanh toán, lớp học video trực tiếp hay đánh giá năng lực có giá trị chứng nhận. AI được giới hạn ở hỗ trợ nội dung và phản hồi học tập; Gemini chỉ được gọi từ backend khi cấu hình khả dụng, còn gợi ý offline được phân biệt rõ. Điểm quiz do backend chấm; AI không sửa đáp án hay điểm.",
        "Phương pháp thực hiện gồm phân tích yêu cầu, khảo sát luồng người dùng, thiết kế mô hình dữ liệu quan hệ, triển khai theo mô-đun, kiểm thử tự động và kiểm tra giao diện web bằng Playwright. Các kết quả trong báo cáo được gắn với phiên bản kiểm chứng ngày 22/10/2026. Những điểm chưa có bằng chứng thiết bị được nêu như giới hạn, không suy diễn từ ảnh web."
    ]),
    ("2.1. Cơ sở sư phạm của lộ trình", [
        "Một lộ trình học có ý nghĩa khi người học biết nội dung thuộc môn nào, chương nào và thứ tự nào. Cấu trúc phân cấp của StudySpace cho phép giảng viên chia mục tiêu môn thành các chương, rồi cụ thể hóa thành bài học và câu hỏi đánh giá. Cấu trúc này giúp tránh một danh sách tài liệu phẳng, đồng thời hỗ trợ báo cáo theo đơn vị kiến thức thay vì chỉ tổng hợp điểm toàn khóa.",
        "Bài học được xem như một đơn vị có mục tiêu, nội dung và trạng thái hoàn thành. Việc đánh dấu đã học cung cấp thông tin về tiến trình, còn quiz cung cấp bằng chứng tự đánh giá. Hai loại tín hiệu bổ sung cho nhau nhưng không đồng nhất: hoàn thành bài không tự động chứng minh đã nắm vững, và điểm quiz thấp không làm mất trạng thái bài đã học.",
        "Trên giao diện, bài kế tiếp bị khóa khi bài trước chưa hoàn thành theo quy tắc hiện tại. Cách tổ chức tuần tự tạo giàn giáo cơ bản: người học được dẫn từ nội dung nền đến nội dung tiếp theo. Trong thực tế, giảng viên vẫn cần thiết kế chương/bài hợp lý; phần mềm chỉ thực thi thứ tự đã cấu hình, không tự xác định quan hệ tiên quyết đúng về mặt học thuật."
    ]),
    ("2.2. Đánh giá quá trình và phản hồi", [
        "Quiz trong StudySpace phục vụ đánh giá thường xuyên: người học trả lời câu hỏi, nhận điểm, xem đáp án và giải thích. Giá trị sư phạm không chỉ nằm ở con số mà ở khả năng quay lại đúng câu sai và hiểu vì sao lựa chọn chưa phù hợp. Bởi vậy, attempt lưu ảnh chụp câu hỏi, lựa chọn, đáp án đúng và giải thích tại thời điểm làm bài; lịch sử không bị biến đổi khi giảng viên sửa ngân hàng câu hỏi về sau.",
        "Phản hồi được tổ chức theo ba bước thực dụng: nêu kết quả, chỉ ra chỗ sai, gợi ý hành động tiếp theo. AI có thể diễn đạt lại lời giải hoặc giải thích câu sai trong bối cảnh câu hỏi, nhưng phản hồi đó cần được xem như tài liệu tham khảo. Đáp án và điểm chính thức vẫn xuất phát từ dữ liệu và quy tắc chấm của backend.",
        "Đối với giảng viên, thống kê câu sai nhiều giúp phát hiện câu hỏi mơ hồ hoặc một chương cần giảng lại. Cần diễn giải thận trọng: tỷ lệ sai chịu ảnh hưởng bởi số người trả lời, độ khó, thứ tự học và chất lượng câu hỏi. Vì thế báo cáo nên hiển thị số lượt và tỷ lệ bên cạnh nhau; người dùng không nên kết luận chỉ từ một tỷ lệ đơn lẻ."
    ]),
    ("2.3. Cá nhân hóa và giới hạn heuristic", [
        "Gợi ý học hôm nay của StudySpace hiện dựa trên kết quả học đã lưu: hệ thống tìm chương có dấu hiệu yếu, xem xét câu trả lời sai và đề xuất ôn tập. Quiz thích ứng phía backend dùng heuristic trên tối đa ba lượt gần nhất, ưu tiên câu từng sai hoặc chưa gặp và cân bằng theo mức độ khó sẵn có. Đây là quy tắc chọn nội dung minh bạch hơn một mô hình dự báo không được kiểm chứng.",
        "Heuristic có ưu điểm là dễ giải thích, chạy được với lượng dữ liệu nhỏ và không phụ thuộc dịch vụ bên ngoài. Nhược điểm là chưa mô hình hóa độ quên theo thời gian, mức độ chắc chắn của câu trả lời hay quan hệ tiên quyết giữa khái niệm. Vì thế, thuật ngữ “thích ứng” trong báo cáo mô tả chính sách chọn câu hiện có, không hàm ý hệ thống đã chứng minh tăng hiệu quả học tập bằng một thử nghiệm đối chứng.",
        "Một hướng phát triển hợp lý là lưu thêm lịch sử lượt gặp câu, thời gian trả lời và mức tự tin; sau đó đánh giá chính sách bằng các chỉ số như tỷ lệ hoàn thành, mức cải thiện điểm và số lần lặp lại câu. Mọi thay đổi thuật toán cần được kiểm soát để không tạo cảm giác đề thi tùy tiện hoặc khiến người học không thể so sánh kết quả qua các lần làm."
    ]),
    ("3.1. Tác nhân và ranh giới hệ thống", [
        "Học viên là người tiêu thụ nội dung, ghi danh lớp, hoàn thành bài và làm quiz. Giảng viên chịu trách nhiệm tạo khóa học thuộc quyền quản lý, soạn chương/bài/câu hỏi, xuất bản nội dung đủ điều kiện, tạo lớp và theo dõi hoạt động học. Quản trị viên quản lý tài khoản, vai trò và nội dung trên phạm vi toàn hệ thống. Ba vai trò có năng lực khác nhau, được backend kiểm tra ở mỗi yêu cầu có trạng thái.",
        "Ranh giới hệ thống gồm trình duyệt web, ứng dụng Android, backend Spring Boot và PostgreSQL; Room là vùng lưu trữ cục bộ của Android. Web truy cập nội dung qua API máy chủ. Android đồng bộ snapshot kế hoạch và phiên focus theo tài khoản; các dữ liệu khác được tải hoặc gửi qua API tương ứng. Gemini là dịch vụ bên ngoài tùy chọn và chỉ backend biết khóa cấu hình.",
        "Trong sơ đồ use case, các tác nhân không đại diện cho từng màn hình mà cho quyền và mục tiêu. Ví dụ, “quản lý khóa học” bao gồm tạo, sửa và ẩn/hiện; không bao gồm xóa khóa khỏi cơ sở dữ liệu vì giao diện hiện giữ lịch sử học tập. Quy tắc này được trình bày như giới hạn của phiên bản, tránh để người đọc hiểu rằng mọi thao tác CRUD đều có đủ."
    ]),
    ("3.2. Yêu cầu chức năng", [
        "Nhóm quản lý nội dung bao gồm khóa học, chương, bài học, tài liệu và ngân hàng câu hỏi. Nội dung mới có thể ở trạng thái nháp; chỉ khóa đủ nội dung mới xuất bản được. Giảng viên chỉ sửa nội dung mình sở hữu, còn admin có quyền toàn hệ thống. Học viên chỉ thấy các khóa đã xuất bản và được phép ghi danh hoặc truy cập theo chính sách hiện hành.",
        "Nhóm học tập gồm xem lộ trình, mở bài theo thứ tự, đánh dấu hoàn thành, làm quiz có thời hạn, xem kết quả và ôn câu sai. Nhóm tiến độ gồm lịch sử bài, lượt quiz, kế hoạch và phiên tập trung. Nhóm báo cáo tổng hợp thành biểu đồ, bảng và CSV cho các vai trò phù hợp. Nhóm người dùng gồm xác thực, phân quyền và thao tác quản trị trạng thái tài khoản.",
        "Các yêu cầu được kiểm chứng qua unit test, integration test và E2E. Kiểm thử E2E 24 bước tập trung vào hành trình giảng viên tạo nội dung đến học viên hoàn thành bài, quiz và xem kết quả. Nó không thay thế kiểm thử tải, kiểm thử bảo mật chuyên sâu hay nghiệm thu người dùng; phạm vi của mỗi loại cần được phân biệt rõ khi đánh giá."
    ]),
    ("3.3. Yêu cầu phi chức năng và tiêu chí chấp nhận", [
        "Tính đúng đắn yêu cầu server là nguồn xác thực cho điểm quiz, quyền sở hữu và trạng thái xuất bản. Tính nhất quán yêu cầu cùng một khóa/chương/bài có định danh ổn định thay vì ghép theo vị trí danh sách. Tính khả dụng yêu cầu giao diện phản hồi khi tải, lỗi mạng và trạng thái dữ liệu rỗng; thao tác quan trọng cần có thông báo thành công hoặc lỗi dễ hiểu.",
        "Khả năng bảo trì được hỗ trợ bằng tách frontend tĩnh, controller/service/repository, schema PostgreSQL và module Android data/domain/UI. Khả năng mở rộng trong phạm vi đồ án chủ yếu là phân tách chức năng và dùng chỉ mục/quan hệ dữ liệu hợp lý; chưa có benchmark tải quy mô lớn. Bảo mật gồm hash mật khẩu, session, kiểm tra vai trò và giữ Gemini key phía máy chủ.",
        "Tiêu chí nghiệm thu không chỉ là giao diện render được. Ví dụ, thao tác xuất bản phải được server xác nhận; quiz phải lưu attempt; tiến độ phải truy vấn lại được; báo cáo phải dựa trên bản ghi thật. Trên mobile, chiều rộng 390 px được kiểm tra tràn ngang ở các màn web đã nêu trong hồ sơ; kết quả này không tự động chứng minh mọi thiết bị hoặc mọi trình duyệt đều không có lỗi."
    ]),
    ("4.1. Kiến trúc tổng thể", [
        "StudySpace dùng kiến trúc client–server. Hai client là web và Android; backend cung cấp xác thực, nội dung, quiz, tiến độ, lớp, báo cáo và AI. PostgreSQL lưu dữ liệu chia sẻ và dữ liệu học tập chính thức. Room cung cấp khả năng lưu kế hoạch/focus cục bộ cho Android. Thiết kế này tránh việc giao diện tự tính điểm hoặc tự cấp quyền dựa trên dữ liệu không tin cậy từ client.",
        "Các trang web được phục vụ từ tài nguyên tĩnh của Spring Boot và gọi API cùng origin khi chạy local. Bộ định tuyến phía client chuyển giữa các khu vực học viên, giảng viên và admin. Android dùng ViewModel để quản lý trạng thái màn hình, repository cho thao tác dữ liệu và Room DAO cho truy vấn cục bộ. Backend giữ quy tắc nghiệp vụ dùng chung để web và Android không tự chấm điểm độc lập.",
        "Một nguyên tắc quan trọng là giữ tương thích API cũ trong quá trình nâng cấp. Các tính năng mới được thêm theo endpoint hoặc trường dữ liệu có kiểm soát; giao diện chỉ đổi khi API hiện có đủ dữ liệu. Nếu cần đổi hợp đồng, phiên bản server và client phải được kiểm tra cùng nhau trước khi phát hành APK."
    ]),
    ("4.2. Phân lớp backend và kiểm soát quyền", [
        "Backend Java/Spring Boot tổ chức controller nhận HTTP, xác thực session, kiểm tra role/owner, gọi lớp nghiệp vụ và thao tác PostgreSQL. Dữ liệu trả về được chuẩn hóa thành JSON; lỗi đầu vào hoặc quyền hạn được chuyển thành status/message để client hiển thị. Các endpoint AI cũng đi qua backend để không đóng gói secret vào JavaScript hoặc APK.",
        "Kiểm soát quyền cần áp dụng tại server cho mọi thao tác ghi. Ẩn nút trên giao diện chỉ cải thiện trải nghiệm, không phải cơ chế bảo vệ. Ví dụ, giảng viên không được phép sửa khóa của giảng viên khác chỉ bằng cách tự thay courseId trong request. Admin có phạm vi cao hơn, nhưng thao tác quản trị vẫn cần xác định đối tượng và ghi nhật ký phù hợp.",
        "Khi session không hợp lệ, hệ thống phải yêu cầu đăng nhập lại hoặc trả lỗi xác thực; khi role không phù hợp, server từ chối thay vì im lặng trả dữ liệu. Các bài kiểm tra quyền nên bao gồm người dùng không đăng nhập, sai vai trò, đúng vai trò nhưng sai chủ sở hữu và đúng quyền. Đây là ma trận kiểm thử cần lặp lại khi thêm endpoint quản lý nội dung."
    ]),
    ("4.3. Ứng dụng Android và ranh giới local", [
        "Android được xây bằng Kotlin, Jetpack Compose và Material 3. UI nhận state từ ViewModel thay vì tự giữ toàn bộ nghiệp vụ trong composable. Repository là đầu mối nối DAO, API và bộ xuất báo cáo. Room lưu năm entity: users, study_tasks, focus_sessions, reward_logs và group_chat_messages. Cấu trúc này phục vụ offline cho dữ liệu cá nhân đã thiết kế, không có nghĩa mọi chức năng web hoạt động ngoại tuyến.",
        "Các thao tác học tập cốt lõi như nộp quiz, lấy khóa đã xuất bản và ghi nhận tiến độ cần backend. Kế hoạch và focus có luồng đồng bộ riêng; dữ liệu được đóng gói thành snapshot gắn với userId, gửi qua endpoint cá nhân và lưu trong bảng personal_study_snapshots. Chat lớp được lấy từ backend; entity group chat local cũ không đồng nghĩa với đồng bộ chat nhiều thiết bị.",
        "Composable nên tập trung vào hiển thị, accessibility và xử lý sự kiện. Timer focus, báo cáo, onboarding, form và trạng thái loading cần được nối với state rõ ràng. Để tránh mất dữ liệu, Room migrations phải mô tả chuyển đổi schema; fallback destructive không được dùng làm đường phục hồi mặc định trên dữ liệu người học."
    ]),
    ("5.1. Mô hình quan hệ PostgreSQL", [
        "Cơ sở dữ liệu PostgreSQL local được đối chiếu qua information_schema tại thời điểm báo cáo: 18 bảng, 102 cột và 26 khóa ngoại. Các nhóm bảng chính gồm tài khoản/session, khóa–chương–bài, câu hỏi và attempt, tiến độ, lớp/ghi danh/thông báo/thảo luận, audit log và snapshot học tập cá nhân. Con số là snapshot môi trường kiểm thử ngày 22/10/2026; khi triển khai ở máy khác cần chạy lại truy vấn.",
        "Quan hệ cha–con giữa courses, chapters và lessons bảo đảm cấu trúc nội dung. questions gắn với chapter; quiz_attempts liên kết học viên và chương; attempt_answers lưu ảnh chụp câu hỏi và đáp án. lesson_progress ghi trạng thái từng bài, course_learning ghi dấu quá trình bắt đầu/truy cập khóa. study_classes liên kết khóa với giảng viên; class_enrollments liên kết lớp với học viên.",
        "Ràng buộc khóa ngoại làm rõ vòng đời bản ghi và ngăn một số dữ liệu mồ côi. Khóa ghép phù hợp cho bảng quan hệ nhiều-nhiều như ghi danh và tiến độ. Một số trường nullable phản ánh dữ liệu chưa có hoặc do hệ thống tạo. Các cột created_at mới cho khóa/chương/bài dùng epoch milliseconds; bản ghi cũ được gán thời điểm migration, không phải ngày tạo lịch sử thực tế."
    ]),
    ("5.2. Lược đồ Room và dữ liệu cá nhân", [
        "Room có năm entity và schema version 5. users lưu hồ sơ cục bộ; study_tasks lưu mục tiêu và trạng thái; focus_sessions lưu thời lượng phiên; reward_logs ghi điểm thưởng; group_chat_messages lưu nội dung chat local cũ. Schema này khác PostgreSQL vì hai kho phục vụ mục đích khác nhau. Room không khai báo foreign key cho các tham chiếu userId/taskId được mô tả ở tài liệu ERD.",
        "Quan hệ logic vẫn cần được quản lý nhất quán trong repository: chỉ tải dữ liệu của owner hiện tại, ánh xạ taskId khi restore và xử lý phiên focus tham chiếu task đã thay đổi. Khi đăng xuất hoặc đổi tài khoản, dữ liệu local không được gắn nhầm sang chủ thể khác. Các bài test DAO nên xác nhận truy vấn theo owner và không trả dữ liệu chéo giữa tài khoản.",
        "Migration là một phần của bảo toàn dữ liệu, không chỉ là thao tác kỹ thuật khi nâng phiên bản. Migration 3→4 và 4→5 cần có test với schema cũ; schema export giúp lưu dấu cấu trúc. Nếu phiên bản cũ chỉ tồn tại trên máy phát triển thì phải ghi rõ căn cứ, còn nếu đã phát hành cần viết đường nâng cấp cho mọi version được hỗ trợ."
    ]),
    ("5.3. Đồng bộ snapshot và xử lý xung đột", [
        "WorkManager đồng bộ kế hoạch và focus khi có mạng. Sau thao tác thay đổi, ứng dụng debounce rồi xếp worker một lần; periodic worker được lập lịch sáu giờ và có điều kiện kết nối mạng. Snapshot phía server lưu payload, version, requestId và updatedAt theo tài khoản. Đây là cơ chế đồng bộ theo ảnh chụp, không phải replication từng bản ghi.",
        "Khi hai phía có version khác nhau, ứng dụng yêu cầu người dùng chọn toàn bộ bản local hoặc server. Cách này đơn giản và tránh tự ghi đè âm thầm, nhưng có thể làm mất thay đổi nằm ở hai phía nếu chúng không giống nhau. Hệ thống chưa merge conflict từng task/phiên. Worker cũng không bảo đảm chạy đúng chính xác thời điểm do Android quản lý lịch nền theo pin, mạng và chính sách hệ điều hành.",
        "Kiểm thử đồng bộ cần bao gồm mạng mất trước khi gửi, server lỗi giữa chừng, request lặp, version cũ, đổi tài khoản và restore task có id mới. Idempotency key giúp hạn chế tạo bản snapshot trùng. Nhật ký trạng thái đồng bộ cần tránh ghi nội dung nhạy cảm; giao diện nên cho biết lần đồng bộ thành công gần nhất và hành động thử lại khi lỗi."
    ]),
    ("6.1. Quản lý khóa, chương và bài", [
        "Cổng giảng viên là nơi hình thành nội dung. Quy trình bắt đầu từ khóa nháp, tiếp theo là chương theo thứ tự, bài và tài liệu, rồi câu hỏi gắn với chương. Biểu mẫu cần chọn đúng quan hệ cha trước khi lưu; danh sách phải hiển thị rõ khóa/chương của bản ghi đang sửa. Lỗi từng xảy ra khi khóa mới không được chọn ở phần tạo chương/bài cho thấy giao diện phụ thuộc chặt vào việc tải lại danh mục sau khi lưu.",
        "Khóa chỉ được xuất bản khi có cấu trúc nội dung hợp lệ. Kiểm tra phía server từ chối chương rỗng, bài rỗng hoặc tiêu đề giữ chỗ; client hiển thị thông báo tiếng Việt để giảng viên biết cần bổ sung gì. AI có thể đề xuất bản nháp nhưng không tự xuất bản. Người soạn cần rà soát kiến thức, thứ tự, mục tiêu và lời giải trước khi nội dung đến học viên.",
        "Khóa có thao tác xuất bản/ẩn thay vì xóa khỏi cơ sở dữ liệu. Đây là lựa chọn bảo toàn attempt, tiến độ và báo cáo tham chiếu khóa. Khi ẩn, khóa không còn xuất hiện trong danh mục học viên mới nhưng dữ liệu lịch sử vẫn cần được truy vấn cho báo cáo. Quy tắc hiển thị của khóa ẩn trong lịch sử phải nhất quán để học viên không mất ngữ cảnh kết quả đã có."
    ]),
    ("6.2. Lớp học, ghi danh và trao đổi", [
        "Giảng viên tạo lớp gắn với một khóa đã xuất bản và chia sẻ mã tham gia. Học viên dùng mã để ghi danh; backend kiểm tra lớp đang hoạt động, khóa tương ứng và trạng thái tài khoản. Báo cáo lớp tổng hợp học viên đã ghi danh, tiến độ theo chương, điểm quiz và câu hỏi có nhiều lượt sai. Mã lớp cần có nút sao chép rõ ràng, còn thao tác gỡ học viên cần xác nhận.",
        "Thảo luận được tải khi mở lớp và cập nhật bằng polling 5 giây. Request cũ cần được hủy khi người dùng đổi lớp; request có timeout và trạng thái lỗi/thử lại để giao diện không kẹt “đang tải”. Mỗi tin nhắn hiển thị tên hiển thị và thời gian. Polling tạo độ trễ nhỏ và gửi request định kỳ, khác với WebSocket hai chiều liên tục.",
        "Thông báo của giảng viên là nội dung lớp có mục đích cụ thể, ví dụ nhắc hạn hoàn thành hoặc thông báo tài liệu mới. Thông báo cần liên kết lớp và tác giả để truy vết. Học viên chỉ nên thấy thông báo của lớp đã tham gia; quản trị viên có thể xem theo quyền. Nội dung thông báo nên được giới hạn độ dài, kiểm tra đầu vào và hiển thị an toàn để tránh chèn mã HTML độc hại."
    ]),
    ("6.3. Trình bày nội dung và tài liệu", [
        "Bài học gồm tiêu đề, nội dung, mục tiêu và tài liệu tham khảo. Tài liệu PDF/PPT/PPTX được tải lên giới hạn dung lượng theo cấu hình; loại khác dùng URL HTTPS. Việc tách metadata khỏi file giúp bài học vẫn có cấu trúc và báo cáo; backend cần kiểm tra loại file, tên lưu, kích thước và quyền truy cập. File phải được backup cùng dữ liệu nếu triển khai tự quản lý.",
        "Học viên đi từ danh mục khóa đến chi tiết chương, rồi bài học. Bài đã hoàn thành có dấu hiệu trạng thái; bài tiếp theo bị khóa khi điều kiện tiên quyết chưa đạt. Màn bài học cần hiển thị rõ nút đánh dấu hoàn thành và quay lại lộ trình. Nếu nội dung dài, mục tiêu và đề mục cần được trình bày để người học có thể quét nhanh trước khi đọc sâu.",
        "Tiêu đề và mô tả giữ chỗ làm giảm độ tin cậy của khóa, vì vậy seed và validation cần phát hiện các tên chung chung như “Bài học tiếp theo” hoặc “Lorem”. Seed idempotent phải dùng khóa tự nhiên/định danh ổn định, không tạo bản ghi trùng khi khởi động lại. Nội dung demo cần sát chuyên ngành và câu hỏi phải thuộc chương phù hợp."
    ]),
    ("7.1. Vòng đời làm quiz", [
        "Quiz bắt đầu khi học viên chọn chương hoặc đề ôn tập. Server chọn câu theo chính sách tương ứng và tạo attempt có thời điểm bắt đầu, deadline, tổng số câu và snapshot đáp án. Giao diện hiển thị đồng hồ đếm ngược, chỉ báo câu chưa làm/đã làm/đánh dấu xem lại và nút nộp. Khi còn một phút, ứng dụng cảnh báo; hết giờ thì tự nộp theo luật server.",
        "Bản nháp câu trả lời và thời gian còn lại cần được khôi phục sau tải lại trang để tránh mất công làm bài. Hộp xác nhận nộp liệt kê câu chưa trả lời và câu đã đánh dấu. Khi người học xác nhận, server là nơi quyết định deadline và kết quả hợp lệ; client không được tự thay đổi điểm. Nộp lặp cần được xử lý an toàn để không sinh nhiều kết quả khác nhau cho cùng attempt.",
        "Sau khi nộp, kết quả cho biết điểm, đáp án đã chọn, đáp án đúng và giải thích. Từ đây học viên có thể mở lại câu sai, yêu cầu AI giải thích hoặc bắt đầu ôn tập nhanh. Bằng chứng E2E của luồng học viên thể hiện hành trình nội dung đến quiz; kiểm tra riêng trên thiết bị Android vẫn cần thiết trước khi khẳng định mọi hành vi native đã được quan sát trực quan."
    ]),
    ("7.2. Chấm điểm và lưu lịch sử", [
        "Chấm điểm được thực hiện ở backend so với correct_option đã lưu trong attempt answer snapshot. Kết quả gồm score, total và chi tiết câu. Việc snapshot giúp lịch sử bền vững nếu giảng viên sửa câu gốc, đồng thời cho phép phân tích câu sai theo phiên bản nội dung mà người học thực sự gặp.",
        "Mỗi lần làm quiz tạo một record quiz_attempts và các bản ghi attempt_answers tương ứng. Lịch sử liên kết userId với chapterId, timestamp và trạng thái nộp. Attempt chưa hoàn tất cần được phân biệt để tránh tính vào thống kê điểm trung bình. Dashboard nên hiển thị số lượt rõ ràng và không đưa attempt đang làm vào trung bình chính thức.",
        "Đề ôn tập cá nhân hóa chọn câu từ chương có điểm thấp và câu đã sai. Phản hồi AI nhận câu hỏi, lựa chọn của học viên và đáp án đúng; nội dung đầu vào cần được giới hạn và kiểm tra trước khi gọi model. Nếu AI không khả dụng, ứng dụng vẫn hiển thị đáp án và giải thích có sẵn trong ngân hàng câu hỏi, với nhãn offline phân biệt."
    ]),
    ("7.3. Chính sách chọn câu thích ứng", [
        "Chính sách hiện tại ưu tiên dữ liệu gần đây: tối đa ba lượt quiz, câu đã sai và câu người học chưa gặp. Mức độ khó được gắn trên câu hỏi; thuật toán dùng nhãn này để tạo tập ôn phù hợp. Không có bằng chứng cho một mô hình học máy được huấn luyện trên dữ liệu cohort; thuật toán nên được mô tả là heuristic có thể giải thích.",
        "Các trường hợp biên gồm chương không đủ câu, mọi câu đã gặp, attempt chưa nộp hoặc câu đã bị vô hiệu hóa. Backend phải giới hạn kích thước đề và trả thông báo khi ngân hàng không đủ câu, thay vì tạo đề trùng hoặc trả JSON không hợp lệ. Với AI-generated quiz, schema bắt buộc gồm câu hỏi, bốn lựa chọn, đáp án và giải thích; dữ liệu được validate trước khi hiển thị.",
        "Một chính sách công bằng cần tránh biến điểm yếu thành đề quá khó liên tục. Có thể trộn câu nền với câu yếu, đặt giới hạn lặp và cho người học biết lý do câu được chọn. Đánh giá cải tiến phải đo được thay đổi sau ôn tập, không chỉ số lượng đề sinh ra. Đây là công việc nghiên cứu tiếp theo chứ không phải tiêu chí đã chứng minh trong phiên bản hiện tại."
    ]),
    ("8.1. Theo dõi tiến độ đa chiều", [
        "Tiến độ được tách thành trạng thái bài, điểm quiz, phút focus và kế hoạch. Mỗi đại lượng trả lời câu hỏi khác nhau: người học đã hoàn thành nội dung nào, kết quả tự đánh giá ra sao, dành bao nhiêu thời gian tập trung và đã thực hiện mục tiêu cá nhân đến đâu. Gộp chúng vào một tỷ lệ duy nhất có thể che khuất khác biệt giữa thời gian học và mức độ nắm kiến thức.",
        "Trạng thái môn học được quy ước thống nhất: 0% là Chưa bắt đầu, 1–99% là Đang học, 100% là Hoàn thành. Quy tắc này cần dùng chung giữa web và Android để người học không thấy một môn vừa “chưa bắt đầu” vừa có tiến độ. Tỷ lệ dựa trên số bài hoàn thành trong tổng số bài hợp lệ của khóa.",
        "Dashboard ưu tiên bài đang học và bước tiếp theo thực tế; nếu chưa có dữ liệu, giao diện cung cấp hành động khởi đầu thay cho biểu đồ rỗng. Màn báo cáo thể hiện diễn tiến theo thời gian và theo chương. Học viên có thể tìm lịch sử gần đây, giảng viên xem tổng hợp lớp, còn admin quan sát thống kê hệ thống theo quyền."
    ]),
    ("8.2. Báo cáo cá nhân và biểu đồ", [
        "Biểu đồ điểm quiz theo thời gian giúp quan sát xu hướng thay vì chỉ nhìn điểm cao nhất. Mỗi điểm cần gắn attempt và thời gian; trung bình theo tuần chỉ nên tính các lượt đã nộp. Biểu đồ mức nắm vững theo chương dùng tổng hợp điểm liên quan đến chương và cần ghi chú cách quy đổi, vì số lượt ít có thể làm giá trị dao động mạnh.",
        "Web dùng Chart.js cho biểu đồ báo cáo quản trị; Android có các biểu đồ Compose và MasteryRadar tự vẽ bằng Canvas. Cùng một khái niệm dữ liệu có thể được trình bày khác nhau theo nền tảng, nhưng ý nghĩa trục, thang điểm và trạng thái dữ liệu cần thống nhất. Màu sắc chỉ hỗ trợ nhận biết; không nên là kênh duy nhất để phân biệt mức tốt/yếu.",
        "Khi tài khoản chưa có phiên focus, hệ thống không nên vẽ biểu đồ trống có trục nhưng không có điểm. Thay vào đó, empty state giải thích dữ liệu nào còn thiếu và đưa CTA bắt đầu phiên tập trung. Khi có dữ liệu, tooltip có thể nêu ngày, thời lượng và số phiên. CSV cho phép giảng viên tiếp tục phân tích ngoài hệ thống, nhưng cần tôn trọng phạm vi lớp và quyền truy cập."
    ]),
    ("8.3. Thống kê lớp và quản trị", [
        "Báo cáo lớp cung cấp tiến độ theo chương, điểm học viên và danh sách câu sai nhiều. Heatmap học viên × chương cho phép nhận diện mẫu hình, chẳng hạn một chương có kết quả thấp trên nhiều người. Danh sách học viên cần chú ý dùng tiêu chí như điểm thấp hoặc lâu không hoạt động, nhưng giảng viên cần đối chiếu bối cảnh trước khi can thiệp.",
        "Dashboard quản trị có biểu đồ Chart.js và xuất CSV; báo cáo lớp hiện dùng bảng/heatmap chứ không phải Chart.js. Phân biệt này quan trọng khi mô tả sản phẩm: không nên ghi mọi báo cáo đều là biểu đồ cùng thư viện. Tệp CSV cần tiêu đề cột rõ, định dạng số ổn định và không đưa thông tin cá nhân vượt quyền người tải.",
        "Số liệu demo cohort tại lần xác minh gần nhất gồm hai lớp, mỗi lớp 28 học viên, 3 khóa, 18 bài và 35 câu hỏi (30 đang hoạt động). Đây là dữ liệu demo tách khỏi tài khoản thật. Báo cáo phải nêu thời điểm và nguồn dữ liệu; các số này có thể thay đổi nếu seed hoặc người dùng thao tác trên database local."
    ]),
    ("9.1. AI cho người học", [
        "AI hỗ trợ người học ở ba ngữ cảnh: hỏi đáp theo nội dung bài, giải thích câu trả lời sai và sinh quiz ôn tập. Khi hỏi trong bài, client gửi nội dung bài đang xem làm ngữ cảnh; backend đưa vào request có giới hạn và yêu cầu trả lời trong phạm vi đó. Với câu sai, request có câu hỏi, lựa chọn của học viên và đáp án chuẩn để tạo lời giải ngắn bằng tiếng Việt.",
        "AI quiz ôn tập được tạo theo chương yếu và câu từng sai, theo JSON schema cố định. Server kiểm tra số câu, bốn lựa chọn, đáp án hợp lệ và giải thích trước khi gửi về client. Quiz sinh ra là nội dung luyện tập, không tự động trở thành câu hỏi chính thức của khóa học. Người học vẫn chịu trách nhiệm đọc phản hồi và đối chiếu học liệu.",
        "Gợi ý offline hoạt động khi Gemini chưa cấu hình, lỗi hoặc hết quota. Nguồn gợi ý dùng dữ liệu có sẵn như chương yếu nhất, câu sai và lời giải đã lưu. UI phải hiển thị “AI trực tiếp · Gemini” hoặc “Gợi ý offline” để không đánh đồng nội dung do model sinh với quy tắc cục bộ. Trạng thái có thể thay đổi theo cấu hình backend và hạn mức project."
    ]),
    ("9.2. AI hỗ trợ giảng viên", [
        "Giảng viên có thể yêu cầu AI đề xuất mô tả khóa, cấu trúc chương/bài và câu hỏi trắc nghiệm. Đầu vào gồm tên chủ đề, mục tiêu hoặc chương đang chọn; đầu ra được trình bày dưới dạng bản nháp để giảng viên xem, sửa và quyết định lưu. AI không nên tự tạo hoặc xuất bản hàng loạt mà không có bước duyệt của con người.",
        "Đầu ra cần có tiêu đề cụ thể thay vì tên chương chung chung; câu hỏi phải gắn đúng chương, có bốn lựa chọn, đáp án, độ khó và giải thích. Backend kiểm tra schema và nội dung giữ chỗ trước khi trả kết quả. Nếu prompt thiếu chủ đề hoặc không có nội dung thật, giao diện cảnh báo thay vì tạo một khóa có vẻ hoàn chỉnh nhưng không dạy được kiến thức nào.",
        "AI hữu ích nhất khi giảm thao tác khởi tạo và đề xuất cách diễn đạt, không thay thế chuyên môn giảng dạy. Giảng viên cần xác minh tính đúng đắn, mức độ phù hợp với chương trình và độ khó. Bản nháp chưa lưu giúp ngăn lỗi sinh tự động ảnh hưởng dữ liệu chính thức. Thao tác lưu vẫn đi qua API hiện có và chịu validation/authorization của backend."
    ]),
    ("9.3. Bảo mật AI và dự phòng", [
        "GEMINI_API_KEY chỉ được cấu hình ở backend qua biến môi trường hoặc file local bị ignore. Web client và APK không nhận key. Endpoint health trả trạng thái đã cấu hình, key sai, hết quota hoặc khả dụng mà không trả giá trị bí mật. Health probe có thể tiêu thụ một lượt gọi dịch vụ nhỏ; do đó admin cần dùng khi chẩn đoán thay vì polling liên tục.",
        "Rate limit theo user hạn chế lạm dụng endpoint. Request cần kiểm tra session, vai trò, kích thước nội dung và tần suất. Timeout bảo vệ server khỏi treo khi upstream phản hồi chậm. Log chỉ ghi loại sự kiện và mã lỗi an toàn; không ghi prompt nhạy cảm, token, key hoặc toàn bộ nội dung cá nhân khi không cần thiết.",
        "Khi AI lỗi, UI cần tắt loading, giữ nội dung người dùng nhập và hiển thị nút thử lại. Fallback offline phải nói rõ giới hạn, dùng dữ liệu sẵn có và không giả vờ đã gọi Gemini. Kiểm thử bao gồm thiếu key, key không hợp lệ, quota, mất mạng và response sai schema. Cần kiểm tra health và nhãn provider ngay trước buổi demo vì trạng thái project có thể thay đổi."
    ]),
    ("10.1. Nguyên tắc giao diện đa vai trò", [
        "Web StudySpace dùng hệ màu tím chàm và xanh ngọc, typography rõ thứ bậc, thẻ thông tin có nhãn, số liệu và chú thích tách dòng. Cổng học viên tập trung vào bước học kế tiếp, nội dung môn và phản hồi; cổng giảng viên ưu tiên tác vụ quản trị nội dung/lớp; cổng admin nhấn mạnh quản lý người dùng và báo cáo toàn hệ thống.",
        "Đối với tác vụ nhiều trường, biểu mẫu mở trong modal hoặc drawer để người dùng hiểu họ đang tạo/sửa một đối tượng cụ thể. Sau lưu, hệ thống thông báo và liên kết dữ liệu mới sang mục liên quan. Các thẻ khóa học hiển thị trạng thái xuất bản, số chương/bài/học viên; chương và bài hiển thị thông tin khóa cha nhằm tránh nhầm lẫn khi chỉnh sửa.",
        "Responsive mobile dùng tab đáy cho tác vụ chính của giảng viên và bố cục một cột. Vùng chạm tối thiểu 44px, nút xóa bị loại khỏi giao diện và thao tác ẩn có xác nhận. Các tiêu chí đã được kiểm tra bằng Playwright ở 360, 390, 768 và 1440 px cho các luồng nêu trong verification; không nên khái quát thành đảm bảo trên tất cả thiết bị."
    ]),
    ("10.2. Trải nghiệm người học", [
        "Tổng quan học viên đưa gợi ý học hôm nay, tiếp tục học, mục tiêu tuần, streak và hoạt động gần đây vào một màn hình. Thẻ gợi ý nêu chương yếu và cơ sở đề xuất, có nhãn nguồn AI/offline. Nhờ đó, CTA không chỉ là lời mời chung mà dẫn đến một chương hoặc bài có thật. Nếu chưa có dữ liệu, hệ thống chuyển sang empty state với hướng dẫn bắt đầu.",
        "Danh mục môn hỗ trợ tìm kiếm và lọc; thẻ môn nêu trạng thái, điểm trung bình hoặc bài gần nhất khi có dữ liệu. Trang khóa dùng accordion chương/bài, dấu hoàn thành và trạng thái khóa. Màn bài học dành chỗ cho nội dung, tài liệu và hỏi AI theo bài. Quiz duy trì trạng thái làm bài, đồng hồ và điều hướng câu để giảm khả năng bỏ sót.",
        "Báo cáo cá nhân kết nối điểm quiz, mức nắm theo chương và phút tập trung. Mỗi biểu đồ cần tên và đơn vị rõ, đồng thời đưa empty state khi không có bản ghi. Các nhãn như “Giỏi”, “Khá” hay “Cần ôn thêm” phải có quy tắc ngưỡng nhất quán; không nên gắn nhãn định giá người học khi mẫu dữ liệu quá nhỏ."
    ]),
    ("10.3. Trải nghiệm giảng viên và admin", [
        "Giảng viên cần quản lý nội dung với ít bước chuyển và biết đối tượng nào đang được chỉnh sửa. Thanh điều hướng nhóm Khóa học, Chương học, Bài học, Quiz và Lớp học; các form được tách khỏi danh sách dài bằng drawer/modal. Màn tổng quan hiển thị phần cần chú ý thay vì chỉ là bốn con số tổng không dẫn đến hành động.",
        "Báo cáo lớp cần lọc theo lớp đang phụ trách, nêu số học viên và phạm vi thời gian. Heatmap và câu sai nhiều hỗ trợ so sánh; xuất CSV là lối đi tiếp cho phân tích. Admin có khu vực tổng hợp riêng, quản lý người dùng và nội dung toàn hệ thống. Không dùng chung một nhãn “quản lý” mơ hồ khi quyền thao tác khác nhau.",
        "Các thao tác nhạy cảm như khóa tài khoản, đổi vai trò hoặc ẩn khóa cần xác nhận và phản hồi sau khi hoàn thành. Trạng thái loading, lỗi và thử lại được thể hiện cục bộ trong từng màn. Nếu API lỗi, không nên tiếp tục hiển thị dữ liệu cũ như thể vừa được cập nhật; cần ghi thời điểm dữ liệu hoặc báo rõ chế độ offline."
    ]),
    ("11.1. Xác thực và phân quyền", [
        "Tài khoản gồm tên đăng nhập, tên hiển thị, mật khẩu đã băm, vai trò và trạng thái hoạt động. Session được lưu dạng hash ở backend; client giữ credential phiên để gửi yêu cầu xác thực. Mật khẩu mới dùng BCrypt; nhánh legacy được hỗ trợ nâng cấp khi xác thực thành công nếu dữ liệu cũ phù hợp. Việc loại bỏ hoàn toàn nhánh cũ cần dựa trên kiểm kê dữ liệu thật, không chỉ một mốc thời gian.",
        "Phân quyền theo role Student, Teacher, Admin kết hợp kiểm tra quyền sở hữu đối tượng. Một giảng viên chỉ quản lý khóa thuộc mình; admin có phạm vi toàn hệ thống. API phải bảo vệ dữ liệu ngay cả khi client sửa request thủ công. Các test nên kiểm tra cả luồng thành công và từ chối, bao gồm session hết hạn và tài khoản bị khóa.",
        "Mật khẩu demo được truyền qua biến runtime trong môi trường local và không đặt trong tài liệu công khai. Gemini key lưu ngoài mã nguồn. Hình chụp dùng trong báo cáo đã được chọn để không lộ thông tin bí mật. Khi nộp repository công khai, cần rà soát lịch sử Git, file ignored, log và ảnh chụp thay vì chỉ tìm chuỗi trong source hiện tại."
    ]),
    ("11.2. Bảo vệ dữ liệu và riêng tư", [
        "Dữ liệu học tập liên kết với tài khoản nên có tính cá nhân. Backend chỉ trả báo cáo theo quyền; giảng viên xem lớp mình phụ trách, học viên xem dữ liệu của bản thân, admin theo phạm vi vận hành. Khi xuất CSV/PDF, tệp cần chứa đúng trường cần thiết, không đưa token session hay cấu hình server.",
        "Kế hoạch/focus lưu local trước khi đồng bộ. Room database hiện chưa được mô tả là mã hóa toàn bộ bằng SQLCipher; mã hóa AES-GCM cho backup không đồng nghĩa với mọi hàng Room đã được mã hóa khi nằm trên thiết bị. Đây là ranh giới cần nói rõ. Android Keystore có thể bảo vệ khóa dùng cho các chức năng mã hóa cụ thể, nhưng không tự mã hóa mọi file ứng dụng.",
        "Logging và telemetry cần giảm thiểu dữ liệu nhạy cảm. Lỗi AI nên ghi trạng thái provider và mã lỗi, không ghi API key; lỗi đăng nhập không ghi password; nội dung câu hỏi và tin nhắn chỉ ghi khi cần chẩn đoán và theo chính sách. Trong đồ án, quyền truy cập, giữ dữ liệu và xóa/ẩn nội dung cần được tiếp tục đánh giá nếu triển khai cho người dùng thật."
    ]),
    ("11.3. Triển khai và vận hành local", [
        "Môi trường chạy gồm JDK 17, PostgreSQL 14 trở lên, biến cấu hình DB và backend Spring Boot. Backend phục vụ web trên cổng 8080; PostgreSQL mặc định cổng 5432. Android debug APK được build bằng Gradle Wrapper. Với emulator, địa chỉ host backend thường qua 10.0.2.2; thiết bị thật cần IP LAN và cùng mạng.",
        "Seed cơ bản và rich demo là tùy chọn. Seed idempotent tạo nội dung mẫu và cohort demo riêng; không bật seed demo trên production. DB local cần backup trước thử nghiệm. Gemini API key có thể để trống; khi thiếu, hệ thống sử dụng fallback offline. Khi cấu hình key, backend phải được khởi động lại để đọc giá trị mới và health check dùng để xác minh mà không tiết lộ secret.",
        "Quy trình cài đặt được ghi trong README: tạo database, truyền thông tin kết nối, cài backend, seed dữ liệu và truy cập các cổng. Máy chấm cần làm theo đúng thứ tự và xác nhận health/API. Khi đóng gói, không đưa .env.local, application-local, DB cục bộ, build outputs hoặc cache. APK debug phục vụ kiểm tra, chưa đồng nghĩa với bản release đã ký và sẵn sàng phân phối."
    ]),
    ("12.1. Chiến lược kiểm thử", [
        "Unit test kiểm tra hàm độc lập như quy tắc tiến độ, chính sách quiz, xác thực và mapper. Backend test bao gồm controller/service/repository hoặc integration theo cấu hình dự án. Android test chạy trên Gradle; build debug kiểm tra biên dịch và đóng gói. Web E2E mô phỏng thao tác qua trình duyệt thật, còn smoke test xác minh đăng nhập và layout ở các kích thước cụ thể.",
        "Kết quả được chốt trong hồ sơ kiểm chứng ngày 22/10/2026: Android 56/56 test, backend 50/50, web E2E 24/24 bước; assembleDebug thành công. Các bài test chạy ở máy local trên cấu hình được ghi trong Verification. Kết quả pass chỉ chứng minh các assertions đó trong môi trường tương ứng, không chứng minh không còn lỗi hoặc đạt chuẩn tải production.",
        "Kiểm thử trực quan Android hiện chưa có thiết bị ADB kết nối ở lượt xác minh, vì vậy báo cáo không dùng ảnh web thay cho Android. PDF Android có unit coverage và đường lưu qua Storage Access Framework; thao tác tạo file/mở file thực tế vẫn cần xác minh trên thiết bị. Sự phân biệt giữa kiểm thử mã, build và test thiết bị là điều kiện quan trọng để báo cáo trung thực."
    ]),
    ("12.2. Kết quả kiểm thử theo luồng", [
        "Luồng giảng viên–học viên bao gồm đăng nhập, tạo/chỉnh sửa nội dung, xuất bản khóa, tạo lớp, ghi danh, học bài, làm quiz và mở báo cáo. Bộ E2E 24 bước PASS trên dữ liệu local và in xác nhận dọn dữ liệu QA. Cleanup cần được kiểm tra sau khi chạy để không trộn dữ liệu test vào cohort demo cố định.",
        "Smoke test Playwright kiểm tra vai trò giảng viên và học viên, desktop 1440×900 và mobile 390×844; không có tràn ngang hoặc lỗi JavaScript chưa bắt ở các trang đã kiểm tra. Cổng giảng viên được bổ sung view 360px, 768px và 1440px cho một số phần. Đây là kiểm chứng có phạm vi rõ, không phải kiểm thử mọi trang trên mọi trình duyệt.",
        "AI Gemini trực tiếp được kiểm tra khi cấu hình khả dụng: health trả available, chat bài học gửi đủ nội dung ngữ cảnh, giải thích câu sai và sinh quiz hợp lệ; giảng viên cũng tạo đề cương và câu hỏi. Do quota và quyền project có thể đổi, kết quả này gắn thời điểm 22/10/2026. Trong demo sau này, cần đọc nhãn provider và health mới nhất thay vì dựa vào ảnh cũ."
    ]),
    ("12.3. Đánh giá theo yêu cầu đề tài", [
        "Quản lý nội dung được chứng minh bằng luồng giảng viên tạo khóa/chương/bài/câu hỏi và phân quyền; quiz được chứng minh bằng màn làm bài, lưu attempt và kết quả; theo dõi tiến độ được chứng minh qua bảng lesson_progress/course_learning và dashboard; quản lý người dùng được chứng minh qua vai trò, tài khoản và admin; báo cáo được minh họa bằng báo cáo lớp/admin và biểu đồ.",
        "Yêu cầu cơ sở dữ liệu quan hệ được đáp ứng bằng PostgreSQL backend với 18 bảng, 102 cột và 26 khóa ngoại trong môi trường đối chiếu. Ứng dụng Android có Room cho dữ liệu local. Trực quan hóa dùng Chart.js trên web và Canvas Compose cho radar Android. Tài liệu hồ sơ gồm ERD, use case, thiết kế giao diện và hướng dẫn sử dụng trong docs/submission.",
        "Mức độ hoàn thành không đồng nghĩa với sản phẩm production. Các giới hạn gồm polling, đồng bộ snapshot, release HTTPS chưa triển khai ở local, heuristic quiz chưa có đánh giá sư phạm thực nghiệm, spaced repetition chưa có và kiểm tra trực quan Android chưa hoàn tất. Những điểm này cần xuất hiện trong phần kết luận và khi bảo vệ để phân biệt năng lực đã chứng minh với kế hoạch phát triển."
    ]),
    ("13.1. Kết quả đạt được", [
        "StudySpace hợp nhất các tác vụ thành một hành trình: nội dung do giảng viên quản lý, học viên học và kiểm tra, backend lưu tiến độ, báo cáo phản ánh dữ liệu. Ba vai trò có giao diện riêng nhưng dùng chung backend và PostgreSQL. Android bổ sung trải nghiệm mobile cùng Room cho kế hoạch/focus, còn WorkManager thực hiện đồng bộ nền theo điều kiện mạng.",
        "Sản phẩm có quiz trắc nghiệm với deadline, đánh dấu câu, khôi phục bản nháp và giải thích; báo cáo cá nhân và lớp; AI có chế độ Gemini và offline; xác thực session/role; seed demo idempotent; tài liệu cài đặt và hướng dẫn sử dụng. Việc tạo nội dung có kiểm tra để giảm khóa rỗng và giữ placeholder. Những cải tiến được kiểm chứng bằng test tự động và luồng trình duyệt.",
        "Giá trị chính của đồ án là minh họa cách dữ liệu, giao diện và phản hồi học tập có thể kết nối. Đây là sản phẩm học thuật cần tiếp tục đánh giá với giảng viên và người học thực tế; không có nghiên cứu người dùng định lượng trong phạm vi bằng chứng hiện có. Vì vậy, báo cáo kết luận về mức độ hoàn thiện kỹ thuật, không tuyên bố đã chứng minh tác động học tập trong dài hạn."
    ]),
    ("13.2. Hạn chế và hướng phát triển", [
        "Thảo luận lớp đang polling 5 giây thay vì WebSocket; kế hoạch/focus đồng bộ theo snapshot và conflict giải quyết ở cấp toàn bộ snapshot. Release cần endpoint HTTPS và cấu hình vận hành thật. Quiz thích ứng là heuristic dựa trên tối đa ba lượt gần nhất; spaced repetition chưa triển khai. Các hạn chế này ảnh hưởng độ realtime, khả năng merge dữ liệu và mức cá nhân hóa dài hạn.",
        "Hướng phát triển gần gồm triển khai HTTPS, backup/restore, giám sát lỗi và đánh giá tải; cải thiện đồng bộ theo thực thể; thêm lịch ôn cách quãng; hiệu chỉnh ngưỡng mastery với giảng viên; bổ sung UI test Android và kiểm tra PDF/reminder trên thiết bị thật. AI có thể được đánh giá bằng bộ câu hỏi chuẩn và rubric chấm đầu ra, với giới hạn chi phí/quota và review của con người.",
        "Để kiểm tra giá trị giáo dục, nghiên cứu tiếp theo nên xác định nhóm người học, thời lượng, tiêu chí trước/sau và phương pháp đồng thuận dữ liệu. So sánh một nhóm dùng phản hồi giải thích với nhóm chỉ nhận đúng/sai cần kiểm soát kiến thức nền và thời lượng luyện tập. Không nên dùng số lượt quiz hoặc thời lượng app làm đại diện trực tiếp cho kết quả học tập."
    ]),
    ("13.3. Kết luận", [
        "Đề tài đã xây dựng StudySpace như một hệ thống hỗ trợ học trực tuyến có quản lý nội dung, quiz, tiến độ, người dùng và báo cáo. Thiết kế dùng PostgreSQL làm nguồn dữ liệu chia sẻ, Room cho một phần dữ liệu Android, backend Spring Boot làm lớp quy tắc và các client web/Android làm giao diện theo vai trò.",
        "Các luồng cốt lõi đã được kiểm tra bằng 56 test Android, 50 test backend và 24 bước E2E web theo trạng thái xác minh ngày 22/10/2026. AI trực tiếp được gọi thành công trong lần kiểm tra cùng ngày, nhưng chỉ hiển thị trực tiếp khi health/provider hiện khả dụng; fallback offline là đường dự phòng. Android chưa có xác nhận trực quan trên thiết bị ở lượt chốt.",
        "Kết quả đáp ứng mục tiêu đồ án trong phạm vi đã xác định và cung cấp nền tảng để tiếp tục hoàn thiện. Việc phát triển tiếp cần ưu tiên bảo vệ dữ liệu, chất lượng nội dung, kiểm chứng trên thiết bị, đánh giá sư phạm và vận hành HTTPS. Báo cáo và sản phẩm cần được cập nhật đồng bộ nếu schema, test hoặc trạng thái triển khai thay đổi sau thời điểm kiểm chứng."
    ]),
]

EXPANSIONS = [
    "Ở góc độ người học, vấn đề còn nằm ở chi phí chuyển đổi giữa công cụ: phải nhớ nơi lưu tài liệu, nơi nộp bài và nơi xem điểm. Một giao diện thống nhất giúp giảm thao tác tìm kiếm, nhưng chỉ hiệu quả khi danh mục và quan hệ dữ liệu được tổ chức đáng tin cậy. Vì vậy, cấu trúc course/chapter/lesson là quyết định chức năng lẫn quyết định sư phạm.",
    "Phân tích tình huống cho thấy mỗi bên cần một loại bằng chứng khác nhau. Học viên cần trạng thái cá nhân; giảng viên cần thông tin lớp; admin cần thống kê hệ thống. Tách ba vai trò giúp tránh một bảng điều khiển quá tải và giảm nguy cơ xem nhầm dữ liệu không thuộc phạm vi. Đây cũng là lý do kiểm tra quyền được đặt ở backend.",
    "Phạm vi được giới hạn theo khả năng kiểm chứng của nhóm. Thay vì tuyên bố “cá nhân hóa hoàn toàn”, báo cáo nêu rõ chính sách chọn câu hiện có; thay vì nói đã triển khai production, báo cáo ghi môi trường local. Cách giới hạn này giúp tiêu chí nghiệm thu có thể quan sát và lặp lại, đồng thời chỉ ra phần cần nghiên cứu sau đồ án.",
    "Các lựa chọn này phù hợp với đánh giá quá trình: người học nhận thông tin đủ gần với hành động để có thể điều chỉnh. StudySpace cung cấp dữ liệu và gợi ý; việc quyết định học lại, hỏi giảng viên hay thay đổi kế hoạch vẫn thuộc người học. Hệ thống không nên biến điểm thấp thành một kết luận cố định về năng lực cá nhân.",
    "Thiết kế phản hồi cần giữ kết nối giữa lỗi và kiến thức nền. Một lời giải chỉ nói đáp án đúng là chưa đủ; giải thích nên chỉ ra quy tắc hoặc khái niệm liên quan. Ngân hàng câu hỏi do giảng viên duyệt giữ vai trò nguồn chuẩn, còn AI có thể hỗ trợ diễn đạt. Sự phân công này hạn chế việc mô hình tự tạo đáp án sai nhưng được trình bày thuyết phục.",
    "Một bước cải tiến cần thiết là lưu mức độ tin cậy và lịch sử lần gặp câu, nhưng không nên thêm thuật toán mới trước khi thu thập dữ liệu phù hợp. Cần xác định chính sách xử lý câu ít lượt, chương chưa có quiz và học viên mới. Báo cáo hiện ưu tiên khả năng giải thích của heuristic và ghi rõ những khía cạnh chưa được đo lường.",
    "Kịch bản lỗi quyền là một phần của use case, không chỉ là trường hợp vận hành hiếm. Một học viên không thể gọi API quản trị; giảng viên không thể sửa khóa ngoài quyền; người chưa đăng nhập không thể đọc tiến độ. Các điều kiện từ chối phải được test để phân quyền không chỉ tồn tại dưới dạng nút ẩn trên giao diện.",
    "Yêu cầu phi chức năng được chuyển thành phép kiểm tra có thể lặp: trạng thái loading phải kết thúc, lỗi có đường thử lại, mobile không tràn ngang ở viewport xác định, dữ liệu sau lưu truy vấn lại đúng. Những thuộc tính như hiệu năng tải ở hàng nghìn người dùng vẫn cần benchmark riêng và không được suy ra từ test chức năng hiện tại.",
    "Kiến trúc client–server giúp nhiều giao diện dùng chung nguồn dữ liệu và quy tắc chấm. Điều này cũng tạo phụ thuộc vào tính sẵn sàng của backend. Các màn đã cache có thể trình bày dữ liệu cũ nhưng phải chỉ rõ thời điểm; thao tác ghi cần trạng thái đồng bộ. Phân biệt này đặc biệt quan trọng trên Android khi thiết bị mất mạng.",
    "Mỗi controller cần giới hạn trách nhiệm: xác thực đầu vào, lấy principal, gọi nghiệp vụ và trả kết quả. Các phép tính báo cáo phức tạp không nên nằm trong JavaScript vì người dùng có thể sửa dữ liệu client. Quy tắc dùng chung, chẳng hạn quyền sở hữu khóa, phải được thực thi một lần phía server để giảm sai lệch giữa web và Android.",
    "ViewModel giúp tách vòng đời UI khỏi tải dữ liệu, nhưng không tự giải quyết mọi race condition. Khi đổi lớp, request cũ cần hủy hoặc bị bỏ qua; khi đăng xuất, state phải được xóa; khi gọi mạng thất bại, loading phải tắt. Các trạng thái này tạo thành hợp đồng UI có thể kiểm thử độc lập với màu sắc và bố cục.",
    "ERD là công cụ kiểm tra tính toàn vẹn chứ không chỉ là hình minh họa. Mỗi quan hệ cần có chủ sở hữu, cardinality và chính sách xóa/ẩn. Khóa học không bị xóa để giữ lịch sử; attempt answers giữ snapshot; lớp có enrollment riêng. Các quyết định đó ảnh hưởng đến cả báo cáo và hướng dẫn sử dụng.",
    "Dữ liệu local không được xem là bản sao đầy đủ của máy chủ. Khi app cập nhật version, migration phải bảo toàn các hàng cũ; khi đồng bộ, payload cần có version và request id; khi đổi owner, repository phải cô lập dữ liệu. Việc mô tả chính xác ranh giới giúp người dùng hiểu phần nào có thể xem khi offline.",
    "Conflict resolution hiện ở mức chọn snapshot là thỏa hiệp giữa độ đơn giản và nguy cơ ghi đè. Trước khi mở rộng, cần định danh từng task ổn định, lưu updatedAt và xây quy tắc merge theo thực thể. Các thay đổi từ hai thiết bị không nên tự chọn bên thắng chỉ theo đồng hồ máy, vì đồng hồ có thể lệch.",
    "Quy trình tạo nội dung cần liên tục kiểm tra ngữ cảnh cha. Sau khi lưu khóa, danh mục chương phải được tải lại; sau khi chọn chương, bài và câu hỏi cần liên kết đúng id. Kiểm thử dùng id thật, không chỉ index của danh sách, để tránh lỗi dữ liệu mới được gắn nhầm khi thứ tự hiển thị đổi.",
    "Lớp học kết nối nội dung với một nhóm người học cụ thể. Mã tham gia làm giảm thao tác nhập nhưng cần kiểm soát trùng, hết hiệu lực hoặc lớp bị khóa. Thông báo lớp và thảo luận cần phân biệt; announcement là thông tin do giảng viên phát, còn message là trao đổi hai chiều. Cả hai đều phải theo quyền enrollment.",
    "Định dạng tài liệu cần đi cùng trải nghiệm truy cập. Link HTTPS tránh tải file từ nguồn không an toàn; tệp tải lên cần giới hạn loại và dung lượng. Nếu file bị thiếu sau khi sao lưu database, bài vẫn còn metadata nhưng tài liệu không mở được. Vì vậy hướng dẫn vận hành cần nhắc backup thư mục uploads cùng PostgreSQL.",
    "Đếm giờ client giúp người học quản lý nhịp làm bài, nhưng server deadline mới là mốc có thẩm quyền. Tải lại trang cần khôi phục thời gian còn lại từ deadline máy chủ thay vì bắt đầu lại từ số phút ban đầu. Nộp bài sát thời hạn cũng cần xử lý sai số mạng và thời điểm nhận request nhất quán.",
    "Lưu kết quả và đáp án giải thích ở snapshot làm lịch sử kiểm toán được. Nếu chỉ lưu questionId, việc chỉnh câu có thể làm kết quả cũ hiển thị nội dung mới, gây nhầm lẫn. Snapshot tăng dung lượng nhưng phù hợp với yêu cầu giải thích lịch sử. Thống kê câu sai cần nhóm theo định danh câu nguồn khi có thể, đồng thời nhận biết version câu.",
    "Chính sách chọn câu cần bảo đảm đa dạng và không vượt nội dung người học đã được tiếp cận. Nếu một chương có quá ít câu hoạt động, backend nên giảm số câu hoặc trả cảnh báo thay vì lặp câu quá mức. Độ khó cần được giảng viên gán có chủ đích; nhãn không tự động phản ánh năng lực tuyệt đối của người học.",
    "Các chỉ số nên hiển thị mẫu số. Ví dụ, 80% đúng trên một câu không có độ tin cậy như 80% trên hai mươi câu. Khi có ít attempt, dashboard có thể hiện số lượt hoặc nhãn “dữ liệu còn ít”. Đây là cách giảm diễn giải quá mức và giúp người học nhìn xu hướng như tín hiệu, không phải phán quyết.",
    "Biểu đồ có giá trị khi hỗ trợ hành động. Nếu người học thấy chương thấp, cần có liên kết đến chương; nếu giảng viên thấy nhóm sai cùng câu, cần mở được câu hỏi đó. Trục, đơn vị, khoảng thời gian và cách tổng hợp phải nhất quán. Bản in hoặc CSV nên giữ tên cột để có thể kiểm tra nguồn dữ liệu.",
    "Báo cáo lớp có thể làm lộ thông tin cá nhân nếu xuất dữ liệu quá rộng. Quyền tải CSV cần được giới hạn cho giảng viên phụ trách hoặc admin; các cột không cần cho mục đích dạy học nên bỏ. Khi chia sẻ ảnh báo cáo, nên dùng cohort demo và tránh hiển thị thông tin thật.",
    "Lời nhắc AI phải có phạm vi rõ: nội dung bài đang xem, câu hỏi sai cụ thể hoặc chương cần ôn. Contextual grounding không bảo đảm câu trả lời luôn đúng; nó chỉ cung cấp tài liệu liên quan. UI nên giữ liên kết với bài/câu gốc để người học tự đối chiếu thay vì tin câu trả lời độc lập.",
    "Đầu ra có cấu trúc làm giảm lỗi giao diện nhưng không tự bảo đảm nội dung học thuật. Validation kiểm tra JSON và trường bắt buộc; người giảng dạy kiểm tra độ đúng. Bản nháp được tách khỏi dữ liệu chính thức để tránh một response ngẫu nhiên tạo khóa đã xuất bản. Hành động lưu cần thể hiện rõ những gì sẽ được ghi.",
    "Tính sẵn sàng AI phụ thuộc key, quyền project, quota, mạng và upstream. Key mới trong cùng project có thể vẫn chịu giới hạn chung. Health endpoint giúp chẩn đoán, nhưng status chỉ đúng tại thời điểm gọi. Vì vậy UI không nên lưu nhãn provider cũ sau khi request mới dùng offline.",
    "Màu sắc được gắn ý nghĩa thay vì chỉ trang trí: primary cho hành động chính, accent cho tiến độ/hightlight, trạng thái cho kết quả. Kích thước chữ và nhịp khoảng cách tạo phân cấp. Responsive cần duy trì chức năng quan trọng ở màn nhỏ, không chỉ thu nhỏ desktop hoặc tạo cuộn lồng khó dùng.",
    "Điểm vào bài học cần giảm khoảng cách từ gợi ý đến hành động. Thẻ tiếp tục học cho biết tên bài và môn; “hôm nay nên học gì” nêu cơ sở; lớp học hiện thông báo mới. Khi user chưa có hoạt động, CTA phải hướng dẫn bước đầu thay vì hiển thị số 0 không giải thích.",
    "Cổng giảng viên có tần suất sử dụng lặp lại cao, nên cần tối ưu thao tác quản lý và scan dữ liệu. Drawer/modal giữ ngữ cảnh danh sách; breadcrumb hoặc dòng thuộc khóa giảm nhầm lẫn; xác nhận thao tác giúp tránh mất công. Bảng và heatmap ưu tiên so sánh nhiều người hơn các thẻ trang trí.",
    "Hành động ghi dữ liệu phải có phản hồi: lưu thành công, lỗi validation, lỗi mạng và trạng thái chờ. Toast phù hợp cho xác nhận ngắn; lỗi cần nằm gần biểu mẫu để người dùng sửa. Dialog chỉ nên dành cho quyết định có hậu quả. Điều này giảm phụ thuộc vào thông báo trình duyệt mặc định và làm trải nghiệm nhất quán.",
    "BCrypt làm chậm việc thử mật khẩu hàng loạt và salt riêng tránh cùng mật khẩu cho cùng hash. Tuy nhiên, hash không thay thế chính sách mật khẩu, rate limit đăng nhập, bảo vệ session và backup an toàn. Mọi nhánh legacy cần có kế hoạch loại bỏ, dựa trên dữ liệu được kiểm kê và test nâng cấp.",
    "Dữ liệu demo được tách khỏi tài khoản thật để tránh test làm bẩn hồ sơ người dùng. Seed idempotent phải có thể chạy lại mà không nhân đôi khóa/lớp/câu hỏi. Mật khẩu demo truyền lúc chạy và lưu ngoài Git. Khi công khai repo, cần kiểm tra cả lịch sử commit lẫn artefact được đóng gói.",
    "Chạy local hỗ trợ tái hiện lỗi và demo trên cùng máy, nhưng không phản ánh cấu hình TLS, proxy, giám sát và backup của production. Release cần URL HTTPS mặc định, secrets ở secret manager và quy trình phát hành có ký số. APK debug không dùng làm gói phân phối công khai.",
    "Một bộ test tốt nên bao phủ cả kết quả đúng và sai, trường hợp rỗng và quyền hạn, không chỉ một luồng thành công. E2E cần dọn dữ liệu sau khi chạy; unit test cần ít phụ thuộc thời gian; build cần chạy từ wrapper. Kết quả được ghi kèm môi trường để người đọc biết điều gì đã thực sự được xác nhận.",
    "Số test pass không thể đo chất lượng nội dung khóa hoặc trải nghiệm của người dùng thật. Cần kiểm tra thủ công bằng tài khoản demo, đối chiếu báo cáo với truy vấn database và đọc ảnh chụp ở desktop/mobile. Kiểm thử Android native phải chạy trên emulator/thiết bị; build thành công không chứng minh thao tác UI thực tế.",
    "Bảng đối chiếu yêu cầu giúp giảng viên truy từ đề bài đến tính năng và bằng chứng. Mỗi yêu cầu cần ít nhất một artefact có thể kiểm tra: route/API, bảng dữ liệu, test hoặc ảnh. Nếu một yêu cầu chỉ có tài liệu mô tả mà không có mã nguồn, phải ghi mức chưa hoàn tất thay vì xem là đã đáp ứng.",
    "Các kết quả kỹ thuật cho thấy hệ thống đã tích hợp nhiều lớp, nhưng không thay thế đánh giá mức dễ dùng. Cần thử với sinh viên và giảng viên ngoài nhóm, quan sát thao tác, ghi nhận lỗi và cải tiến. Không có dữ liệu khảo sát trong bộ bằng chứng hiện tại, do đó báo cáo không đưa ra tỷ lệ hài lòng hay kết luận tác động.",
    "Các hướng phát triển cần xếp thứ tự theo rủi ro: trước hết bảo vệ dữ liệu và triển khai HTTPS; tiếp theo cải thiện đồng bộ và thông báo; sau đó đánh giá chính sách quiz/ôn cách quãng; cuối cùng mở rộng phân tích học tập. Việc thêm tính năng mà chưa có dữ liệu và tiêu chí đánh giá có thể làm tăng độ phức tạp mà không tăng giá trị.",
    "Kết luận của đồ án dựa trên phạm vi, mã nguồn và kết quả kiểm thử được ghi rõ. Khi có thay đổi sau ngày chốt, nhóm cần cập nhật báo cáo, số lượng bảng/test và ảnh minh chứng trước khi nộp. Bản báo cáo là bản kỹ thuật có thể tái kiểm chứng, không phải cam kết rằng mọi môi trường vận hành đều có trạng thái giống local."
]

FIGURES = [
    ("teacher-dashboard.png", "Hình 3.1. Tổng quan giảng viên", "Ảnh Playwright thật. Khối cần chú ý giúp giảng viên đi từ số liệu đến học viên, lớp và câu hỏi cụ thể."),
    ("teacher-class-report-desktop.png", "Hình 3.2. Báo cáo lớp", "Ảnh báo cáo lớp thật; bảng và heatmap là cách thể hiện hiện tại, không gọi nhầm là biểu đồ Chart.js."),
    ("student-overview-ai.png", "Hình 3.3. Tổng quan học viên với gợi ý học hôm nay", "Ảnh Playwright thật; gợi ý nêu chương yếu và nguồn dữ liệu. Nhãn provider cần được kiểm tra lại khi demo."),
    ("student-overview-focus.png", "Hình 3.4. Hoạt động và thời lượng tập trung", "Khi chưa có dữ liệu, giao diện dùng màn trống thay cho biểu đồ rỗng."),
    ("student-courses.png", "Hình 3.5. Danh mục môn học", "Danh sách khóa xuất bản; trạng thái tiến độ dựa trên bài đã hoàn thành."),
    ("student-course-detail.png", "Hình 3.6. Lộ trình chương và bài", "Trang chi tiết khóa thể hiện cấu trúc nội dung và trạng thái bài học."),
    ("student-lesson.png", "Hình 3.7. Màn nội dung bài học", "Bài học nối mục tiêu, tài liệu và hành động hoàn thành."),
    ("student-quiz-active.png", "Hình 3.8. Màn hình làm quiz", "Giao diện có đồng hồ, điều hướng câu và trạng thái đánh dấu xem lại."),
    ("student-quiz-result.png", "Hình 3.9. Kết quả quiz và giải thích", "Điểm do server chấm; AI hỗ trợ giải thích nhưng không thay đổi đáp án chuẩn."),
    ("student-progress-report-desktop.png", "Hình 3.10. Báo cáo tiến độ học viên", "Ảnh desktop gồm hoạt động học, biểu đồ và lịch sử."),
    ("admin-dashboard.png", "Hình 3.11. Cổng quản trị", "Admin quản lý tài khoản và nội dung trong phạm vi toàn hệ thống."),
    ("admin-chart-report-desktop.png", "Hình 3.12. Báo cáo thống kê quản trị", "Ảnh biểu đồ Chart.js và chức năng xuất dữ liệu theo quyền."),
]

APPENDIX = [
    ("Phụ lục A. Ma trận yêu cầu và minh chứng", [
        "Quản lý nội dung: `TeacherContentController.java`, trang `teacher.html`, tài liệu use case và ảnh dashboard giảng viên. Quiz: `LearningController.java`, `student-quiz-active.png`, `student-quiz-result.png` và bảng `quiz_attempts`/`attempt_answers`. Tiến độ: `lesson_progress`, `course_learning`, báo cáo học viên. Người dùng: `AdminController.java`, bảng `accounts`, ảnh admin dashboard.",
        "Báo cáo thống kê: API báo cáo lớp/admin, ảnh `teacher-class-report-desktop.png` và `admin-chart-report-desktop.png`. CSDL quan hệ: `schema.sql` và ERD 18 bảng. Trực quan hóa: Chart.js ở admin.js, MasteryRadar ở Android Compose. Use case, thiết kế giao diện và hướng dẫn sử dụng nằm trong `docs/submission/03-use-cases.md`, `07-thiet-ke-giao-dien.md`, `04-huong-dan-su-dung.md`.",
        "Bảng minh chứng là chỉ mục đến mã nguồn và ảnh, không thay thế việc chạy ứng dụng. Mỗi ảnh phải được đọc cùng chú thích về vai trò, kích thước và thời điểm. Các ảnh trong báo cáo là ảnh web; không dùng chúng làm bằng chứng Android native."
    ]),
    ("Phụ lục B. Danh mục thực thể và quan hệ", [
        "Nhóm định danh gồm accounts, sessions, portal_tickets, admin_audit_log. Nhóm nội dung gồm courses, chapters, lessons, questions. Nhóm đánh giá gồm quiz_attempts và attempt_answers. Nhóm tiến độ gồm lesson_progress, course_learning, personal_study_snapshots. Nhóm lớp gồm study_classes, class_enrollments, class_announcements, course_messages và course_presence.",
        "Các cạnh quan hệ gồm tài khoản tạo khóa; khóa chứa chương; chương chứa bài và câu hỏi; tài khoản hoàn thành bài và bắt đầu khóa; tài khoản làm attempt theo chương; attempt chứa snapshot answers; khóa cung cấp lớp; lớp có giảng viên, enrollment và announcement; môn có message/presence; tài khoản có snapshot cá nhân.",
        "PostgreSQL local ngày 22/10/2026 đối chiếu 18 bảng, 102 cột và 26 khóa ngoại. Room có 5 entity, không khai báo foreign key SQLite; các quan hệ userId/taskId là logic ứng dụng. Mọi thay đổi schema cần cập nhật ERD, migration, test và tài liệu cùng một lượt."
    ]),
    ("Phụ lục C. Ma trận kiểm thử tóm tắt", [
        "Android: 56/56 unit/integration test PASS; backend: 50/50 PASS; web journey: 24/24 PASS; assembleDebug PASS. Smoke test responsive web kiểm tra các viewport đã nêu trong Verification. Kết quả là snapshot ngày 22/10/2026 và cần chạy lại khi mã thay đổi.",
        "Các kiểm tra AI xác nhận health available, chat có ngữ cảnh bài học, lời giải câu sai bằng tiếng Việt và quiz đúng schema ở lượt kiểm tra 22/10/2026. Khi key thiếu/sai/quota, phải kiểm tra health và fallback. Trạng thái dựa trên project thực tế, không được cố định trong tài liệu như bảo đảm lâu dài.",
        "Chưa có xác minh trực quan APK hiện tại trên thiết bị và chưa kiểm tra thao tác xuất PDF native trên thiết bị trong lượt chốt. Các điểm này được giữ trong danh sách giới hạn, không suy ra từ compile hoặc unit test."
    ]),
    ("Phụ lục D. Hướng dẫn chạy lại và demo", [
        "Cài JDK 17 và PostgreSQL, tạo database, đặt DB_URL/DB_USER/DB_PASSWORD cùng mật khẩu runtime cho seed. Chạy Gradle Wrapper để build backend installDist rồi khởi động server với seed phù hợp. Gemini tùy chọn được nạp từ GEMINI_API_KEY hoặc `.env.local`; không ghi key vào file công khai. Truy cập cổng web qua localhost:8080.",
        "Demo 8–10 phút: đăng nhập admin xem người dùng/báo cáo; đăng nhập giảng viên xem khóa, hỗ trợ AI bản nháp, lớp và heatmap; đăng xuất rồi đăng nhập học viên, mở khóa, học bài, làm quiz, xem kết quả, hỏi AI và báo cáo. Nếu AI unavailable, đọc health, dùng fallback offline và nói rõ nguồn; nếu mạng lỗi, trình bày ảnh Playwright đã lưu như minh chứng tĩnh.",
        "Không chụp cửa sổ terminal có secret hoặc lưu thông tin xác thực trong tài liệu nộp. Tài khoản demo local nằm ngoài hồ sơ công khai. Trước khi chạy E2E trên DB, xác nhận đó không phải production và kiểm tra dòng cleanup sau test.",
        "Chuẩn bị máy chạy: cài JDK 17 và PostgreSQL tương thích; tạo database riêng cho demo, không dùng production; xác nhận cổng PostgreSQL và thông tin kết nối. Mở PowerShell tại thư mục dự án, truyền `DB_URL`, `DB_USER`, `DB_PASSWORD`, `STUDYSPACE_ADMIN_PASSWORD`, `STUDYSPACE_DEMO_PASSWORD` vào process đang chạy backend. Không đặt secret trong README, script đã commit hoặc tham số dòng lệnh có thể lưu history.",
        "Khởi động backend bằng Gradle Wrapper theo README; đợi health của ứng dụng và trang gốc cổng 8080 phản hồi. Schema được khởi tạo bởi backend trên DB mới; seed rich demo cần được bật có chủ đích. Seed chỉ dành cho namespace demo và có thể đặt lại credential demo theo biến runtime; không chạy trên dữ liệu thật. Nếu backend báo lỗi kết nối, kiểm tra cổng, tên DB, quyền user và log trước khi thao tác tiếp.",
        "Tạo mật khẩu demo an toàn bằng terminal đọc dạng secure string như hướng dẫn trong `docs/DEMO_SCRIPT.md`. Nếu cần Gemini, sao chép `.env.local.example` thành `.env.local`, đặt key tại máy local rồi khởi động lại backend; launcher chỉ in đã/chưa cấu hình, không được in key. Admin kiểm tra `/api/ai/health`; chỉ giới thiệu AI trực tiếp khi status là available và giao diện hiển thị provider Gemini.",
        "Học viên: vào `/login.html`, đăng nhập tài khoản demo, đọc khối gợi ý học hôm nay và nhãn nguồn; vào Môn học, tìm khóa đã xuất bản, mở chi tiết để xem chương/bài. Bài sau có thể bị khóa theo thứ tự; học viên cần đọc bài hiện tại và chọn đánh dấu hoàn thành. Nếu tài liệu là URL ngoài, mở ở tab phù hợp; không nhầm việc mở khóa với việc hệ thống xác nhận hoàn thành.",
        "Trong quiz, học viên theo dõi đồng hồ server cấp cho attempt, chọn đáp án, dùng đánh dấu xem lại ở câu chưa chắc và chuyển bằng thanh số câu. Trước khi nộp, đọc danh sách câu bỏ trống/đã đánh dấu trong hộp xác nhận. Sau nộp, kết quả gồm điểm server chấm, lựa chọn, đáp án đúng và giải thích; quay lại lịch sử để mở attempt cũ. Nếu trình duyệt tải lại, hệ thống chỉ khôi phục khi attempt còn hạn và API trả lại trạng thái đó.",
        "Báo cáo học viên hiển thị lịch sử quiz, tiến độ và thời lượng tập trung từ nguồn có dữ liệu. Biểu đồ rỗng cần được hiểu là chưa có hoạt động trong khoảng thời gian tương ứng, không phải lỗi tính điểm. Kế hoạch tuần trên web hiện được lưu theo cơ chế client hiện hành; không suy ra rằng kế hoạch ấy đã đồng bộ sang Android. Android có Room cho entity local và snapshot sync riêng.",
        "Giảng viên: đăng nhập `/login.html?staff=1`, mở Khóa học, tạo nháp và ghi mục tiêu/mô tả cụ thể. Mở Chương học chọn đúng khóa vừa tạo, thêm chương và thứ tự; tiếp tục ở Bài học & tài liệu để gắn bài vào chương bằng id, nhập nội dung, mục tiêu, tài liệu. Mở Ngân hàng quiz chọn chương, mức khó, câu hỏi, bốn phương án, đáp án và lời giải; xem trước rồi lưu. AI có thể tạo bản nháp, nhưng giảng viên cần duyệt và chỉnh trước khi dùng.",
        "Trước xuất bản, kiểm tra khóa có chương và bài với nội dung thật; server từ chối chương/bài rỗng hoặc tiêu đề placeholder. Xuất bản khiến khóa hiển thị với học viên; ẩn khóa giữ lại lịch sử tiến độ. Tạo lớp gắn với khóa đã xuất bản, sao chép mã tham gia và đăng thông báo. Báo cáo lớp cho biết hoàn thành, điểm/chương, câu sai và học viên cần chú ý; CSV chỉ nên chia sẻ trong phạm vi lớp.",
        "Admin dùng cổng nhân viên, mở Người dùng để tra tài khoản, đổi vai trò hoặc khóa/mở theo đúng chính sách. Hành động quản trị được ghi vào audit log; đổi role có thể thu hồi phiên hiện tại, nên người bị đổi role cần đăng nhập lại. Mục Báo cáo tổng hợp phạm vi hệ thống; số liệu phụ thuộc seed/database hiện tại, vì vậy trước bảo vệ cần xác nhận đang dùng DB demo và nói rõ các số liệu là dữ liệu mẫu.",
        "Khi UI báo lỗi, phân biệt bốn lớp: trình duyệt/API, backend, PostgreSQL và AI upstream. Thử lại chỉ sau khi xác định thao tác trước chưa được ghi để tránh tạo dữ liệu trùng; với quiz, luôn tải lại attempt hiện tại trước khi gửi lần nữa. Khi AI lỗi, dùng gợi ý offline nhưng nêu rõ nguồn. Khi mất mạng, không giả định thao tác học/quiz/thảo luận đã lưu; chờ kết nối rồi kiểm tra server trước khi tiếp tục.",
        "Android: APK debug được tạo bằng `gradlew :app:assembleDebug`; kiểm thử tự động chạy bằng `:app:testDebugUnitTest`. Hướng dẫn này không khẳng định APK đã được kiểm tra trực quan trên thiết bị trong lượt chốt. Vì Room có dữ liệu local, không xóa dữ liệu ứng dụng hoặc cài đặt lại khi chưa sao lưu nếu cần giữ kế hoạch/focus; migration không hỗ trợ schema v1/v2 chưa xác định và không có destructive fallback."
    ]),
    ("Phụ lục E. Thuật ngữ", [
        "Attempt: một lượt làm quiz có thời điểm, deadline và kết quả. Chapter: chương học thuộc một khóa. Course: khóa học có thể xuất bản hoặc ẩn. LessonProgress: trạng thái hoàn thành bài của một học viên. Snapshot: bản chụp dữ liệu kế hoạch/focus để đồng bộ giữa Room và server.",
        "Heuristic: chính sách xác định bằng quy tắc; trong đề tài dùng để ưu tiên câu sai/câu chưa gặp, không phải mô hình máy học đã huấn luyện. Provider: dịch vụ hoặc nguồn tạo phản hồi AI; Gemini là nguồn trực tiếp khi health khả dụng, offline là gợi ý cục bộ. Polling: client lặp request theo chu kỳ để lấy dữ liệu mới.",
        "RBAC: phân quyền theo vai trò. Room: thư viện lưu trữ quan hệ cục bộ Android. PostgreSQL: hệ quản trị cơ sở dữ liệu quan hệ ở backend. WorkManager: API Android lập lịch tác vụ nền có điều kiện. ERD: sơ đồ thực thể–liên kết. E2E: kiểm thử xuyên suốt luồng người dùng qua giao diện và API."
    ]),
    ("Phụ lục F. Đặc tả ca sử dụng", [
        "Các ca sử dụng dưới đây mô tả điều kiện, luồng chính, nhánh thay thế và kết quả cần đạt. Đây là đặc tả hành vi của phiên bản đã kiểm chứng, không phải danh sách tính năng dự kiến. Mọi thao tác ghi cần được backend xác nhận; giao diện không tự xem một yêu cầu gửi đi là thành công.",
        "UC-S01 — Đăng nhập học viên. Tác nhân: học viên đã có tài khoản. Tiền điều kiện: backend hoạt động, tài khoản được bật. Luồng chính: mở cổng học viên, nhập tên đăng nhập/mật khẩu, gửi yêu cầu, nhận session và tải hồ sơ; điều hướng đến khu vực học viên. Nhánh thay thế: sai thông tin hoặc tài khoản bị khóa thì giữ nguyên form và hiện lỗi tiếng Việt; mạng lỗi thì cho thử lại. Hậu điều kiện: session gắn với tài khoản, không đưa mật khẩu vào URL hoặc log.",
        "UC-S02 — Chọn khóa và ghi danh. Tác nhân: học viên. Tiền điều kiện: đã đăng nhập, khóa được xuất bản hoặc lớp có mã hợp lệ. Luồng chính: tìm khóa theo danh sách; mở chi tiết để xem chương/bài; nếu cần, nhập mã lớp và xác nhận tham gia; hệ thống liên kết tài khoản với lớp và khóa. Nhánh thay thế: mã không tồn tại, lớp ngừng hoạt động hoặc học viên đã tham gia thì phản hồi phù hợp và không tạo ghi danh trùng. Hậu điều kiện: khóa/lớp xuất hiện trong phạm vi tài khoản.",
        "UC-S03 — Học bài và mở bài kế tiếp. Tác nhân: học viên. Tiền điều kiện: khóa đang truy cập được và bài nằm trong lộ trình. Luồng chính: mở bài được phép, đọc nội dung/tài liệu, chọn đánh dấu hoàn thành; backend ghi lesson_progress; giao diện làm mới tiến độ và mở bài kế tiếp theo thứ tự. Nhánh thay thế: gửi lỗi/mất mạng thì không báo hoàn tất giả; bài sau vẫn khóa cho đến khi trạng thái đầu tiên được xác nhận. Hậu điều kiện: tiến độ được truy vấn lại theo lesson_id và user_id.",
        "UC-S04 — Bắt đầu, làm và nộp quiz. Tác nhân: học viên. Tiền điều kiện: chương có câu hỏi đang hoạt động. Luồng chính: tạo attempt; server trả deadline cùng bản chụp câu hỏi; học viên chọn đáp án, đánh dấu câu cần xem lại; client lưu nháp và đồng hồ; xác nhận nộp; backend chấm, ghi submitted_at/score và trả kết quả. Nhánh thay thế: hết giờ thì tự nộp; thiếu câu thì hộp thoại nêu rõ; tải lại thì khôi phục trạng thái nếu attempt còn hiệu lực; gửi lặp được xử lý theo idempotency/quy tắc server. Hậu điều kiện: attempt và câu trả lời lịch sử không đổi khi câu hỏi nguồn được sửa.",
        "UC-S05 — Xem lại kết quả và ôn câu sai. Tác nhân: học viên. Tiền điều kiện: attempt đã nộp. Luồng chính: mở lịch sử, chọn attempt, so sánh lựa chọn với đáp án chuẩn, đọc giải thích; chọn ôn tập nhanh để tạo đề từ chương yếu và câu từng sai. Nhánh thay thế: không có lịch sử thì hiện empty state; endpoint AI lỗi thì hiển thị gợi ý offline và nhãn nguồn. Hậu điều kiện: việc xem lại không sửa điểm hay đáp án đã lưu.",
        "UC-S06 — Theo dõi kế hoạch và tập trung. Tác nhân: học viên trên Android/web theo phạm vi tính năng. Tiền điều kiện: tài khoản đăng nhập. Luồng chính: tạo mục tiêu hoặc việc học, chọn hạn và thời lượng, hoàn thành việc; trên Android bắt đầu focus, kết thúc phiên, dữ liệu được lưu local; WorkManager đồng bộ snapshot khi đủ điều kiện mạng. Nhánh thay thế: không có mạng thì giữ dữ liệu local; xung đột version cần theo chính sách chọn/khôi phục hiện tại. Hậu điều kiện: báo cáo chỉ tổng hợp nguồn dữ liệu đã ghi nhận, không coi thời gian kế hoạch là thời gian học thực tế.",
        "UC-S07 — Thảo luận lớp/môn. Tác nhân: học viên và giảng viên có quyền trong lớp/khóa. Tiền điều kiện: session hợp lệ và quan hệ thành viên còn hiệu lực. Luồng chính: mở lớp, tải danh sách trao đổi, gửi tin nhắn, nhận tin mới qua polling; tin hiển thị tên và thời điểm. Nhánh thay thế: đổi lớp hủy yêu cầu cũ; timeout hoặc lỗi hiện nút thử lại; danh sách rỗng dùng màn trống. Hậu điều kiện: chỉ thành viên được đọc/gửi theo quyền server.",
        "UC-S08 — Hỏi gia sư trong bài học. Tác nhân: học viên. Tiền điều kiện: bài học đang mở và backend AI có thể nhận yêu cầu. Luồng chính: nhập câu hỏi, client gửi lesson id/câu hỏi; backend nạp nội dung tương ứng, gọi Gemini nếu khả dụng; UI hiển thị provider và câu trả lời tiếng Việt. Nhánh thay thế: thiếu key, hết quota, timeout hoặc mất mạng thì hiện gợi ý offline khi có, không gọi nhầm là Gemini. Hậu điều kiện: key và system prompt chỉ ở server; điểm quiz không bị tác động.",
        "UC-T01 — Tạo và tổ chức khóa học. Tác nhân: giảng viên. Tiền điều kiện: role TEACHER. Luồng chính: tạo khóa nháp; cập nhật tiêu đề/mô tả; chọn khóa làm ngữ cảnh để tạo chương, bài, tài liệu và câu hỏi; thứ tự lưu theo position; quay lại danh sách để kiểm tra. Nhánh thay thế: dữ liệu thiếu/không hợp lệ được báo tại form; chỉ chủ sở hữu sửa khóa của mình. Hậu điều kiện: mỗi chương/bài/câu hỏi giữ khóa ngoại theo id, không phụ thuộc vị trí của danh sách giao diện.",
        "UC-T02 — Xuất bản khóa. Tác nhân: giảng viên hoặc admin theo quyền. Tiền điều kiện: khóa có nội dung học thật. Luồng chính: yêu cầu xuất bản; backend kiểm tra chương, bài, tiêu đề placeholder và điều kiện liên quan; nếu hợp lệ, published chuyển trạng thái; học viên có thể thấy khóa. Nhánh thay thế: dữ liệu rỗng/placeholder trả thông báo tiếng Việt, không xuất bản một phần. Hậu điều kiện: khóa đã xuất bản có thể được ẩn để giữ lịch sử; thao tác xóa khóa khỏi giao diện bị loại bỏ có chủ đích.",
        "UC-T03 — Soạn câu hỏi và quiz. Tác nhân: giảng viên. Tiền điều kiện: có khóa/chương thuộc quyền quản lý. Luồng chính: chọn chương và độ khó; nhập câu hỏi, bốn lựa chọn, đáp án đúng và giải thích; xem trước; lưu vào ngân hàng. Có thể yêu cầu AI tạo bản nháp, kiểm tra JSON và nội dung rồi chỉnh trước khi lưu. Nhánh thay thế: AI offline vẫn có gợi ý bản nháp được gắn nhãn; câu thiếu đáp án/giải thích không được lưu. Hậu điều kiện: câu hỏi thuộc chapter_id, mức difficulty được lưu rõ.",
        "UC-T04 — Tạo lớp và giao nội dung. Tác nhân: giảng viên. Tiền điều kiện: khóa đã xuất bản. Luồng chính: tạo lớp gắn khóa, nhận mã tham gia, sao chép mã, đăng thông báo; học viên nhập mã để ghi danh. Nhánh thay thế: mã duy nhất được kiểm tra ở server; lớp không hoạt động không nhận ghi danh mới. Hậu điều kiện: báo cáo lớp chỉ tổng hợp thành viên trong lớp và các attempt/tiến độ liên quan.",
        "UC-T05 — Theo dõi và phản hồi lớp. Tác nhân: giảng viên. Tiền điều kiện: lớp có học viên và hoạt động. Luồng chính: mở báo cáo; xem tiến độ chương, điểm quiz, câu sai nhiều, học viên lâu không hoạt động; xuất CSV khi cần; dùng thông báo để giao việc tiếp. Nhánh thay thế: lớp chưa có dữ liệu thì trình bày empty state thay cho biểu đồ giả. Hậu điều kiện: dữ liệu báo cáo chỉ đọc và không làm thay đổi lịch sử học viên.",
        "UC-A01 — Quản lý người dùng. Tác nhân: admin. Tiền điều kiện: phiên ADMIN còn hiệu lực. Luồng chính: tìm tài khoản, xem role/enabled, đổi vai trò hoặc khóa/mở; backend kiểm tra quyền và ghi nhật ký admin_audit_log; thay đổi role thu hồi session phù hợp. Nhánh thay thế: không tìm thấy user hoặc yêu cầu role không hợp lệ thì trả lỗi; không cho client tự đặt quyền. Hậu điều kiện: trạng thái mới được đọc lại từ server và có dấu vết thao tác.",
        "UC-A02 — Xem báo cáo quản trị. Tác nhân: admin. Tiền điều kiện: đăng nhập ADMIN. Luồng chính: mở báo cáo, tải số liệu tổng quan, xem biểu đồ Chart.js/bảng và xuất CSV. Nhánh thay thế: không có dữ liệu thì hiện thông báo; lỗi mạng cho phép thử lại. Hậu điều kiện: báo cáo phản ánh dữ liệu tại thời điểm truy vấn, không thay cho phân tích thống kê suy luận.",
        "Các đặc tả trên bao phủ phần lõi mà đề tài yêu cầu. Luồng đăng ký, khôi phục mật khẩu qua OTP, thông báo đẩy toàn diện, video trực tiếp, thanh toán và quản trị nhiều tổ chức không được tuyên bố là đã hoàn thiện nếu chưa có bằng chứng riêng."
    ]),
    ("Phụ lục G. Từ điển dữ liệu", [
        "Từ điển sau được sinh từ câu lệnh CREATE TABLE trong `backend/src/main/resources/schema.sql`, cùng cấu trúc Room trong `app/src/main/java/com/example/data/Entities.kt`. Khi chạy trình tạo, bộ kiểm tra xác nhận đúng 18 bảng PostgreSQL và 102 cột trước khi đưa danh mục vào báo cáo; nếu schema đổi, cần chạy đối chiếu information_schema và cập nhật tài liệu.",
        "Cột PostgreSQL được trình bày cùng kiểu SQL và ràng buộc/default khai báo trong schema. PK/FK ghép được ghi theo thứ tự thành phần. `created_at` là epoch milliseconds; giá trị migration cho bản ghi cũ chỉ thể hiện thời điểm backfill, không phục dựng ngày lịch sử. Các index không phải khóa và không được tính vào 26 FK.",
        "Danh mục Room chỉ gồm năm entity thật. Các property `userId`, `taskId` và `roomId` là liên kết logic khi không có annotation ForeignKey; `GroupRoom` là model trong bộ nhớ chứ không phải bảng. Snapshot đồng bộ cá nhân chỉ chứa kế hoạch và phiên focus theo hợp đồng hiện tại."
    ]),
    ("Phụ lục H. Danh mục API", [
        "Danh mục 66 route được trích trực tiếp từ các `@RequestMapping` và `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` trong controller backend tại thời điểm chốt. Tên controller cho biết nơi triển khai; quyền thực tế vẫn phải đọc cùng kiểm tra session/role tại method và service. Route không đồng nghĩa endpoint public.",
        "API học tập chịu trách nhiệm trả nội dung và ghi tiến độ; attempt quiz được tạo, lưu đáp án và nộp ở server. API giáo viên quản lý nội dung theo ownership; API admin có phạm vi hệ thống và ghi audit. API AI dùng backend để giữ secret, có health trạng thái an toàn và phản hồi provider/fallback. API personal-study đồng bộ snapshot, không biến mọi entity Room thành bảng server.",
        "Danh mục chỉ bao gồm mapping có trong mã nguồn lúc chốt. Request/response chi tiết, validation và status code cần được kiểm tra tại DTO/controller/test tương ứng; không xem bảng route là thay thế OpenAPI hoặc hợp đồng phiên bản hóa."
    ]),
    ("Phụ lục I. Danh mục kiểm thử", [
        "Danh mục test suite được lấy từ file test hiện có tại thời điểm chốt. Số test chạy/pass/fail là kết quả thực tế ghi trong `docs/VERIFICATION.md`; tên suite chỉ chỉ ra nhóm phạm vi và không được diễn giải thành từng assertion cụ thể nếu chưa mở nội dung test.",
        "Backend test gồm các nhóm xác thực/quản trị, lớp học, migration created_at, seed cohort, Gemini, học tập/quiz, hành trình học, đồng bộ cá nhân, seed audit, mở bài tuần tự và nội dung giảng viên. Android test gồm scope tài khoản, trạng thái tiến độ, biểu đồ, phiên học, fallback câu trả lời, lưu/đồng bộ cá nhân, đồng hồ quiz, thành tựu, focus, bảo mật và nhắc học.",
        "Kiểm thử giao diện E2E 24 bước xác nhận luồng giảng viên tạo khóa/lớp đến học viên ghi danh, học, quiz, thảo luận và báo cáo. Nó chạy trên dữ liệu local có cleanup; không phải kiểm thử production, kiểm thử tải hay chứng nhận bảo mật. APK debug đã build nhưng chưa có xác minh trực quan trên thiết bị ở lượt chốt."
    ]),
    ("Tài liệu tham khảo", [
        "Black, P. and Wiliam, D. (1998). Assessment and Classroom Learning. Assessment in Education: Principles, Policy & Practice, 5(1), 7–74. Hattie, J. and Timperley, H. (2007). The Power of Feedback. Review of Educational Research, 77(1), 81–112. Shute, V. J. (2008). Focus on Formative Feedback. Review of Educational Research, 78(1), 153–189.",
        "Spring Framework Documentation. https://docs.spring.io/spring-framework/reference/ ; PostgreSQL Documentation. https://www.postgresql.org/docs/ ; Android Developers, Room. https://developer.android.com/training/data-storage/room ; Android Developers, WorkManager. https://developer.android.com/topic/libraries/architecture/workmanager ; Jetpack Compose. https://developer.android.com/develop/ui/compose . Truy cập để xây dựng và kiểm tra ứng dụng trong phạm vi đồ án.",
        "Google AI for Developers, Gemini API documentation. https://ai.google.dev/gemini-api/docs ; Chart.js Documentation. https://www.chartjs.org/docs/ ; OWASP Application Security Verification Standard. https://owasp.org/www-project-application-security-verification-standard/ . Tài liệu được dùng để đối chiếu tích hợp API, trực quan hóa và một số nguyên tắc bảo mật; các chức năng cụ thể vẫn được xác minh từ mã nguồn StudySpace."
    ]),
]


def set_cell_shading(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)


def set_repeat_table_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_font(run, name="Times New Roman", size=12, bold=None, italic=None, color="000000"):
    run.font.name = name
    run._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), name)
    run._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), name)
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size)
    run.font.color.rgb = RGBColor.from_string(color)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic


def style_document(doc):
    sec = doc.sections[0]
    sec.page_width, sec.page_height = Inches(8.5), Inches(11)
    sec.top_margin = sec.bottom_margin = sec.left_margin = sec.right_margin = Inches(1)
    normal = doc.styles["Normal"]
    normal.font.name = "Times New Roman"
    normal._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
    normal._element.rPr.rFonts.set(qn("w:hAnsi"), "Times New Roman")
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")
    normal.font.size = Pt(12)
    normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    normal.paragraph_format.line_spacing = 1.35
    normal.paragraph_format.space_after = Pt(6)
    for name, size in (("Heading 1", 16), ("Heading 2", 13), ("Heading 3", 12)):
        s = doc.styles[name]
        s.font.name = "Times New Roman"
        s._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
        s._element.rPr.rFonts.set(qn("w:hAnsi"), "Times New Roman")
        s._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")
        s.font.size = Pt(size)
        s.font.bold = True
        s.font.color.rgb = RGBColor(0, 0, 0)
        s.paragraph_format.keep_with_next = True
        s.paragraph_format.space_before = Pt(10)
        s.paragraph_format.space_after = Pt(5)
    doc.styles["Heading 1"].paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.styles["Heading 2"].paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    doc.styles["Heading 3"].paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    table_style = doc.styles["Table Grid"]
    table_style.font.name = "Times New Roman"
    table_style._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
    table_style._element.rPr.rFonts.set(qn("w:hAnsi"), "Times New Roman")
    table_style._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")


def normalize_report_fonts(doc):
    for name in ("Normal", "Heading 1", "Heading 2", "Heading 3", "Table Grid", "Bảng", "Hình"):
        try:
            style = doc.styles[name]
        except KeyError:
            continue
        style.font.name = "Times New Roman"
        rpr = style._element.get_or_add_rPr()
        fonts = rpr.rFonts
        if fonts is None:
            fonts = OxmlElement("w:rFonts")
            rpr.insert(0, fonts)
        for font_kind in ("ascii", "hAnsi", "eastAsia", "cs"):
            fonts.set(qn(f"w:{font_kind}"), "Times New Roman")


def add_page_number(section):
    p = section.footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run()
    begin = OxmlElement("w:fldChar"); begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText"); instr.set(qn("xml:space"), "preserve"); instr.text = " PAGE "
    separate = OxmlElement("w:fldChar"); separate.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t"); text.text = "1"
    end = OxmlElement("w:fldChar"); end.set(qn("w:fldCharType"), "end")
    for el in (begin, instr, separate, text, end): r._r.append(el)
    set_font(r, size=10)


def add_body(doc, text, first=True):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p.add_run(text)
    return p


def add_table(doc, headers, rows, widths=None, font_size=10.2):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = "Table Grid"
    table.autofit = False
    if widths:
        for col, width in zip(table.columns, widths): col.width = Inches(width)
    hdr = table.rows[0]
    header_props = hdr._tr.get_or_add_trPr()
    repeat_header = OxmlElement("w:tblHeader")
    repeat_header.set(qn("w:val"), "true")
    header_props.append(repeat_header)
    for i, val in enumerate(headers):
        cell = hdr.cells[i]
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        cell.text = ""
        p = cell.paragraphs[0]; p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        r = p.add_run(str(val)); set_font(r, size=font_size)
    for row in rows:
        table_row = table.add_row()
        row_props = table_row._tr.get_or_add_trPr()
        row_props.append(OxmlElement("w:cantSplit"))
        cells = table_row.cells
        for i, val in enumerate(row):
            cells[i].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            cells[i].text = ""
            p = cells[i].paragraphs[0]; p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            r = p.add_run(str(val)); set_font(r, size=font_size)
    doc.add_paragraph()
    return table


def add_caption(doc, text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = True
    set_font(p.add_run(text), size=12, italic=True)


def postgres_dictionary_rows():
    source = (ROOT / "backend" / "src" / "main" / "resources" / "schema.sql").read_text(encoding="utf-8")
    tables = []
    rows = []
    pattern = re.compile(r"CREATE TABLE IF NOT EXISTS (\w+)\s*\((.*?)\);", re.I | re.S)
    for table, body in pattern.findall(source):
        tables.append(table)
        table_rows = []
        for line in body.splitlines():
            definition = line.strip().rstrip(",")
            match = re.match(r"^(\w+)\s+((?:VARCHAR|BIGINT|INT|INTEGER|BOOLEAN|TEXT)(?:\s*\([^)]*\))?)\s*(.*)$", definition, re.I)
            if not match:
                continue
            column, data_type, rules = match.groups()
            rule_parts = []
            if "PRIMARY KEY" in rules.upper(): rule_parts.append("PK")
            if "REFERENCES" in rules.upper():
                target = re.search(r"REFERENCES\s+(\w+)\((\w+)\)", rules, re.I)
                rule_parts.append("FK → " + (f"{target.group(1)}.{target.group(2)}" if target else "tham chiếu"))
            if "UNIQUE" in rules.upper(): rule_parts.append("UNIQUE")
            is_identity = "GENERATED BY DEFAULT AS IDENTITY" in rules.upper()
            if is_identity: rule_parts.append("IDENTITY")
            rule_parts.append("NOT NULL" if ("NOT NULL" in rules.upper() or "PRIMARY KEY" in rules.upper()) else "NULL được phép")
            default = None if is_identity else re.search(r"\bDEFAULT\s+(.+?)(?:\s+CHECK\(|$)", rules, re.I)
            if default: rule_parts.append("DEFAULT " + default.group(1).strip())
            check = re.search(r"CHECK\s*\((.+)\)", rules, re.I)
            if check: rule_parts.append("CHECK " + check.group(1))
            item = [table, f"{column} · {data_type.upper()}", "; ".join(rule_parts)]
            rows.append(item)
            table_rows.append(item)
        composite_pk = re.search(r"PRIMARY KEY\s*\(([^)]*)\)", body, re.I)
        if composite_pk:
            columns = {name.strip().strip('"') for name in composite_pk.group(1).split(",")}
            for item in table_rows:
                column = item[1].split(" · ", 1)[0]
                if column in columns:
                    extra = re.sub(r";?\s*NOT NULL", "", item[2], flags=re.I).strip("; ")
                    item[2] = "PK; NOT NULL" + ("; " + extra if extra else "")
        for unique in re.finditer(r"UNIQUE\s*\(([^)]*)\)", body, re.I):
            columns = {name.strip().strip('"') for name in unique.group(1).split(",")}
            for item in table_rows:
                column = item[1].split(" · ", 1)[0]
                if column in columns and "UNIQUE" not in item[2]:
                    item[2] = (item[2] + "; UNIQUE composite").strip("; ")
    if len(tables) != 18 or len(rows) != 102:
        raise ValueError(f"Schema source changed: expected 18 tables/102 columns, found {len(tables)}/{len(rows)}")
    return rows


def room_dictionary_rows():
    source = (ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "data" / "Entities.kt").read_text(encoding="utf-8")
    rows = []
    pattern = re.compile(r'@Entity\(tableName\s*=\s*"([^"]+)"\)\s*data class (\w+)\((.*?)\n\)', re.S)
    for table, model, body in pattern.findall(source):
        primary = {"users": "username", "study_tasks": "id", "focus_sessions": "id", "reward_logs": "id", "group_chat_messages": "id"}[table]
        for line in body.splitlines():
            clean = line.split("//", 1)[0].strip().rstrip(",")
            match = re.search(r"val\s+(\w+)\s*:\s*([^=]+?)(?:\s*=\s*(.*))?$", clean)
            if match:
                field, data_type, default = match.groups()
                rules = ("PK" + ("; autoGenerate" if table != "users" else "")) if field == primary else ""
                if "?" in data_type: rules = (rules + "; nullable").strip("; ")
                if default: rules = (rules + "; default " + default.strip()).strip("; ")
                rows.append([table, f"{field} · {data_type.strip()}", rules or "Không khai báo ràng buộc DB"])
    if len({row[0] for row in rows}) != 5 or len(rows) != 39:
        raise ValueError(f"Room entity source changed: unexpected entity/property inventory ({len(rows)} fields)")
    return rows


def api_catalog_rows():
    rows = []
    role_by_file = {
        "AuthController.java": "Auth endpoints; public/session vary",
        "LearningController.java": "Học viên đã đăng nhập",
        "TeacherContentController.java": "Giảng viên sở hữu nội dung",
        "AdminController.java": "Quản trị viên",
        "DiscussionController.java": "Thành viên khóa học",
        "PersonalStudyController.java": "Chủ sở hữu snapshot",
        "GeminiController.java": "Session hợp lệ; health giới hạn admin",
        "ClassController.java": "Giảng viên phụ trách hoặc học viên",
    }
    for path in sorted((ROOT / "backend" / "src" / "main" / "java" / "com" / "studyspace" / "server").glob("*Controller.java")):
        source = path.read_text(encoding="utf-8")
        base_match = re.search(r'@RequestMapping\("([^"]*)"\)', source)
        base = base_match.group(1) if base_match else ""
        matches = list(re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\(([^\n)]*)\))?', source))
        for index, match in enumerate(matches):
            args = match.group(2) or ""
            route_match = re.search(r'"([^"]*)"', args)
            route = (route_match.group(1) if route_match else "")
            end = matches[index + 1].start() if index + 1 < len(matches) else len(source)
            following = source[match.end():end]
            method_match = re.search(r"\b(\w+)\s*\([^;]*?\)\s*(?:throws\s+[\w, ]+\s*)?\{", following, re.S)
            action = method_match.group(1) if method_match else "controller handler"
            full_route = (base.rstrip("/") + "/" + route.lstrip("/")) if route else base
            if path.name == "ClassController.java" and full_route.startswith("/api/teacher/"):
                role = "Giảng viên"
            else:
                role = role_by_file.get(path.name, "Đã xác thực")
            rows.append([match.group(1).upper(), full_route, role, f"{path.stem}.{action}()"])
    if len(rows) != 66:
        raise ValueError(f"Backend route inventory changed: expected 66 mappings, found {len(rows)}")
    return sorted(rows, key=lambda row: (row[2], row[1], row[0]))


def make_diagram(path: Path, title: str, boxes, arrows):
    width, height = 1800, 880
    img = Image.new("RGB", (width, height), "white")
    d = ImageDraw.Draw(img)
    font_path = r"C:\Windows\Fonts\arial.ttf"
    bold_path = r"C:\Windows\Fonts\arialbd.ttf"
    f_title = ImageFont.truetype(bold_path, 30)
    f_box = ImageFont.truetype(font_path, 27)
    f_small = ImageFont.truetype(font_path, 22)
    for x in range(0, width, 24):
        major = x % 120 == 0
        d.line((x, 0, x, height), fill="#e3e7ef" if major else "#f3f5f8", width=2 if major else 1)
    for y in range(0, height, 24):
        major = y % 120 == 0
        d.line((0, y, width, y), fill="#e3e7ef" if major else "#f3f5f8", width=2 if major else 1)
    d.text((width // 2, 28), title, font=f_title, fill="#222222", anchor="mt")
    rects = []
    for x, y, w, h, label, detail, color in boxes:
        d.rectangle((x, y, x+w, y+h), fill="#eeebff", outline="#8067db", width=3)
        lines = wrap(label, max(12, int(w / 13)))
        yy = y + 24
        for line in lines:
            d.text((x+w/2, yy), line, font=f_box, fill="#252525", anchor="mt")
            yy += 38
        yy += 10
        for line in wrap(detail, max(16, int(w / 11))):
            d.text((x+w/2, yy), line, font=f_small, fill="#333333", anchor="mt")
            yy += 31
        rects.append((x, y, w, h))
    for a, b, label in arrows:
        ax, ay, aw, ah = rects[a]; bx, by, bw, bh = rects[b]
        ac = (ax + aw / 2, ay + ah / 2)
        bc = (bx + bw / 2, by + bh / 2)
        dx, dy = bc[0] - ac[0], bc[1] - ac[1]
        if abs(dx) >= abs(dy):
            start = (ax + aw if dx > 0 else ax, ac[1])
            end = (bx if dx > 0 else bx + bw, bc[1])
            if abs(start[1] - end[1]) < 2:
                points = [start, end]
            else:
                elbow = (start[0] + end[0]) / 2
                points = [start, (elbow, start[1]), (elbow, end[1]), end]
        else:
            start = (ac[0], ay + ah if dy > 0 else ay)
            end = (bc[0], by if dy > 0 else by + bh)
            if abs(start[0] - end[0]) < 2:
                points = [start, end]
            else:
                elbow = (start[1] + end[1]) / 2
                points = [start, (start[0], elbow), (end[0], elbow), end]
        d.line(points, fill="#566174", width=3, joint="curve")
        dx, dy = end[0] - points[-2][0], end[1] - points[-2][1]
        ux, uy = dx / max(1, (dx * dx + dy * dy) ** 0.5), dy / max(1, (dx * dx + dy * dy) ** 0.5)
        px, py = -uy, ux
        tip = end
        base = (tip[0] - 15 * ux, tip[1] - 15 * uy)
        d.polygon([tip, (base[0] + 8 * px, base[1] + 8 * py), (base[0] - 8 * px, base[1] - 8 * py)], fill="#566174")
        if label:
            if len(points) > 2:
                mx = (points[1][0] + points[2][0]) / 2
                my = (points[1][1] + points[2][1]) / 2
            else:
                first = points[1]
                mx, my = (start[0] + first[0]) / 2, (start[1] + first[1]) / 2
                if abs(mx - start[0]) < 2:
                    mx += 38
            bounds = d.textbbox((mx, my - 22), label, font=f_small, anchor="mm")
            d.rounded_rectangle((bounds[0] - 7, bounds[1] - 3, bounds[2] + 7, bounds[3] + 3), radius=5, fill="#ffffff")
            d.text((mx, my - 22), label, font=f_small, fill="#394150", anchor="mm")
    img.save(path)


def diagram_canvas(title, width=1800, height=900):
    img = Image.new("RGB", (width, height), "white")
    d = ImageDraw.Draw(img)
    font_path = r"C:\Windows\Fonts\arial.ttf"
    bold_path = r"C:\Windows\Fonts\arialbd.ttf"
    for x in range(0, width, 24):
        major = x % 120 == 0
        d.line((x, 0, x, height), fill="#dfe3eb" if major else "#f1f3f7", width=2 if major else 1)
    for y in range(0, height, 24):
        major = y % 120 == 0
        d.line((0, y, width, y), fill="#dfe3eb" if major else "#f1f3f7", width=2 if major else 1)
    title_font = ImageFont.truetype(bold_path, 34)
    d.text((width / 2, 28), title, font=title_font, fill="#1d2433", anchor="mt")
    return img, d, ImageFont.truetype(font_path, 25), ImageFont.truetype(bold_path, 27), ImageFont.truetype(font_path, 21)


def make_usecase_diagram(path: Path):
    img, d, body_font, bold_font, small_font = diagram_canvas("Sơ đồ use case StudySpace")
    # The boundary makes the system scope explicit; actor associations use buses to avoid crossing.
    d.rounded_rectangle((350, 135, 1450, 815), radius=6, fill="#fffefc", outline="#8b77d8", width=4)
    d.rectangle((350, 135, 1450, 190), fill="#fff8cf", outline="#8b77d8", width=2)
    d.text((900, 149), "HỆ THỐNG STUDYSPACE", font=bold_font, fill="#25233b", anchor="mt")

    use_cases = [
        (570, 245, "Học bài và xem tài liệu"),
        (570, 385, "Làm quiz, xem kết quả"),
        (570, 525, "Theo dõi tiến độ"),
        (570, 665, "Tham gia lớp, trao đổi"),
        (1030, 245, "Quản lý khóa học, bài học"),
        (1030, 385, "Soạn ngân hàng câu hỏi"),
        (1030, 525, "Quản lý lớp, xem báo cáo"),
        (1030, 665, "Quản lý người dùng, hệ thống"),
    ]
    # Association buses keep the role-to-use-case map legible at report scale.
    d.line((300, 325, 300, 725), fill="#515a6b", width=3)
    d.line((243, 488, 300, 488), fill="#515a6b", width=3)
    for y in (295, 435, 575, 715):
        d.line((300, y, 570, y), fill="#515a6b", width=3)
    d.line((1500, 285, 1500, 555), fill="#515a6b", width=3)
    d.line((1500, 303, 1547, 303), fill="#515a6b", width=3)
    for y in (295, 435, 575):
        d.line((1430, y, 1500, y), fill="#515a6b", width=3)
    d.line((1560, 675, 1560, 715), fill="#515a6b", width=3)
    d.line((1560, 663, 1602, 663), fill="#515a6b", width=3)
    d.line((1430, 715, 1560, 715), fill="#515a6b", width=3)
    for x, y, label in use_cases:
        d.ellipse((x, y, x + 400, y + 100), fill="#eeebff", outline="#8067db", width=3)
        text = "\n".join(wrap(label, 28))
        bounds = d.multiline_textbbox((0, 0), text, font=body_font, align="center", spacing=3)
        tx = x + (400 - (bounds[2] - bounds[0])) / 2
        ty = y + (100 - (bounds[3] - bounds[1])) / 2
        d.multiline_text((tx, ty), text, font=body_font, fill="#252535", align="center", spacing=3)

    def actor(cx, cy, label):
        d.ellipse((cx - 20, cy - 82, cx + 20, cy - 42), fill="white", outline="#485267", width=4)
        d.line((cx, cy - 42, cx, cy + 24), fill="#485267", width=4)
        d.line((cx - 38, cy - 12, cx + 38, cy - 12), fill="#485267", width=4)
        d.line((cx, cy + 24, cx - 32, cy + 66), fill="#485267", width=4)
        d.line((cx, cy + 24, cx + 32, cy + 66), fill="#485267", width=4)
        d.multiline_text((cx, cy + 82), "\n".join(wrap(label, 17)), font=small_font, fill="#20283a", anchor="mt", align="center", spacing=2)

    actor(205, 500, "Học viên")
    actor(1585, 315, "Giảng viên")
    actor(1640, 675, "Quản trị viên")
    img.save(path)


def make_architecture_diagram(path: Path):
    img, d, body_font, bold_font, small_font = diagram_canvas("Kiến trúc triển khai StudySpace")
    nodes = [
        (130, 245, 420, 190, "Web client", "Học viên · Giảng viên · Admin"),
        (130, 565, 420, 190, "Android", "Kotlin · Compose · Room"),
        (800, 385, 420, 210, "Backend REST", "Spring Boot · xác thực · RBAC"),
        (1370, 385, 330, 210, "PostgreSQL", "Dữ liệu quan hệ dùng chung"),
    ]
    # Route the two client links around nodes rather than through the Android box.
    paths = [
        ([(550, 335), (670, 335), (670, 455), (800, 455)], "HTTPS · JSON", (690, 315)),
        ([(550, 655), (700, 655), (700, 525), (800, 525)], "HTTPS · JSON", (710, 675)),
        ([(1220, 490), (1370, 490)], "SQL", (1295, 465)),
    ]
    for points, label, label_pos in paths:
        d.line(points, fill="#515a6b", width=4, joint="curve")
        x0, y0 = points[-2]
        x1, y1 = points[-1]
        dx, dy = x1 - x0, y1 - y0
        norm = max(1, (dx * dx + dy * dy) ** 0.5)
        ux, uy = dx / norm, dy / norm
        px, py = -uy, ux
        base = (x1 - 16 * ux, y1 - 16 * uy)
        d.polygon([(x1, y1), (base[0] + 8 * px, base[1] + 8 * py), (base[0] - 8 * px, base[1] - 8 * py)], fill="#515a6b")
        bbox = d.textbbox(label_pos, label, font=small_font, anchor="mm")
        d.rounded_rectangle((bbox[0] - 7, bbox[1] - 4, bbox[2] + 7, bbox[3] + 4), radius=5, fill="white")
        d.text(label_pos, label, font=small_font, fill="#394150", anchor="mm")
    for x, y, w, h, label, detail in nodes:
        d.rectangle((x, y, x + w, y + h), fill="#eeebff", outline="#8067db", width=3)
        d.text((x + w / 2, y + 42), label, font=bold_font, fill="#20283a", anchor="mt")
        detail_text = "\n".join(wrap(detail, max(20, int(w / 13))))
        bounds = d.multiline_textbbox((0, 0), detail_text, font=small_font, align="center", spacing=5)
        tx = x + (w - (bounds[2] - bounds[0])) / 2
        d.multiline_text((tx, y + 105), detail_text, font=small_font, fill="#343b4b", align="center", spacing=5)
    img.save(path)


def extract_seal():
    with zipfile.ZipFile(SAMPLE) as z:
        raw = z.read("word/media/image1.png")
    p = ASSET / "phenikaa-seal.png"
    p.write_bytes(raw)
    return p


def clear_body(doc):
    body = doc._element.body
    for child in list(body):
        if child.tag != qn("w:sectPr"):
            body.remove(child)


def insert_body_element(doc, element):
    body = doc._element.body
    body.insert(body.index(body.sectPr), element)


def set_paragraph_text(paragraph, text):
    runs = paragraph.runs
    if not runs:
        paragraph.add_run(text)
        return
    runs[0].text = text
    for run in runs[1:]:
        run.text = ""


def add_report_figure(doc, index):
    filename, caption, explanation = FIGURES[index]
    path = ROOT / "docs" / "submission" / "screenshots" / filename
    if not path.exists():
        return
    if index == 11:
        doc.add_page_break()
    with Image.open(path) as source:
        iw, ih = source.size
    scale = min(6.3 / iw, 5.8 / ih)
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(str(path), width=Inches(iw * scale), height=Inches(ih * scale))
    add_caption(doc, caption)
    add_body(doc, explanation)


def main():
    doc = Document(str(SAMPLE))
    normalize_report_fonts(doc)
    roster_node = deepcopy(doc.tables[0]._tbl)
    clear_body(doc)

    def cover_line(text, size, bold=False, before=0, after=8):
        paragraph = doc.add_paragraph()
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        paragraph.paragraph_format.space_before = Pt(before)
        paragraph.paragraph_format.space_after = Pt(after)
        paragraph.paragraph_format.line_spacing = 1.15
        paragraph.paragraph_format.keep_together = True
        set_font(paragraph.add_run(text), size=size, bold=bold)
        return paragraph

    cover_line("ĐẠI HỌC PHENIKAA", 14, bold=True, after=5)
    cover_line("TRƯỜNG CÔNG NGHỆ THÔNG TIN PHENIKAA", 12, bold=True, after=24)

    logo = doc.add_paragraph()
    logo.alignment = WD_ALIGN_PARAGRAPH.CENTER
    logo.paragraph_format.space_after = Pt(24)
    logo.add_run().add_picture(str(extract_seal()), width=Inches(2.15))

    cover_line("BÁO CÁO ĐỒ ÁN LIÊN NGÀNH", 18, bold=True, after=12)
    cover_line(
        "ĐỀ TÀI XÂY DỰNG NỀN TẢNG HỌC TẬP TRỰC TUYẾN STUDYSPACE",
        14,
        bold=True,
        after=20,
    )
    cover_line("Giảng viên hướng dẫn: ThS. Vũ Thị Ngọc Anh", 12, after=18)
    cover_line("THÀNH VIÊN THỰC HIỆN", 12, bold=True, after=6)
    cover_line("Nguyễn Hữu Lê Hiếu  ·  23010895", 12, after=3)
    cover_line("Phạm Văn Huấn  ·  23010331", 12, after=24)
    cover_line("Hà Nội, 10/2026", 12, after=0)
    doc.add_page_break()

    doc.add_heading("DANH SÁCH VÀ NHIỆM VỤ CÁC THÀNH VIÊN TRONG NHÓM", level=1)
    roster = Table(roster_node, doc._body)
    roster_data = [
        ["Họ tên", "Mã sinh viên", "Nhiệm vụ thực hiện", "Đóng góp"],
        ["Nguyễn Hữu Lê Hiếu", "23010895", "Phối hợp phân tích, thiết kế và xây dựng StudySpace.", "Đóng góp chung"],
        ["Phạm Văn Huấn", "23010331", "Phối hợp xây dựng chức năng, kiểm thử và hoàn thiện báo cáo.", "Đóng góp chung"],
    ]
    for row, values in zip(roster.rows, roster_data):
        for cell, value in zip(row.cells, values):
            cell.text = value
    insert_body_element(doc, roster_node)
    doc.add_page_break()

    doc.add_heading("LỜI CẢM ƠN", level=1)
    for t in [
        "Nhóm em xin trân trọng cảm ơn ThS. Vũ Thị Ngọc Anh đã hướng dẫn, định hướng và góp ý trong quá trình thực hiện đồ án. Những trao đổi về cách xác định phạm vi, tổ chức nội dung và trình bày kết quả giúp nhóm nhìn rõ hơn mối liên hệ giữa yêu cầu giáo dục với lựa chọn kỹ thuật.",
        "Nhóm cũng xin cảm ơn các thầy cô và bạn học đã chia sẻ ý kiến trong quá trình thử nghiệm StudySpace. Các góp ý về thao tác học viên, công việc của giảng viên và cách đọc báo cáo đã giúp nhóm điều chỉnh cấu trúc màn hình, trạng thái dữ liệu và luồng sử dụng.",
        "Trong giới hạn thời gian và kinh nghiệm, sản phẩm và báo cáo vẫn còn những thiếu sót. Nhóm mong nhận được nhận xét của giảng viên để tiếp tục hoàn thiện chất lượng nội dung, tính khả dụng và độ tin cậy của hệ thống. Nhóm xin chân thành cảm ơn."
    ]: add_body(doc, t)
    doc.add_page_break()

    doc.add_heading("MỤC LỤC", level=1)
    toc_items = [
        ("LỜI CẢM ƠN", 3), ("MỤC LỤC", 4), ("DANH MỤC BẢNG", 5), ("DANH MỤC HÌNH", 6), ("MỞ ĐẦU", 7),
        ("CHƯƠNG 1. CƠ SỞ LÝ THUYẾT VÀ TỔNG QUAN", 9),
        ("CHƯƠNG 2. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG LIÊN NGÀNH", 11),
        ("CHƯƠNG 3. TRIỂN KHAI VÀ CÀI ĐẶT HỆ THỐNG", 19),
        ("CHƯƠNG 4. THỰC NGHIỆM VÀ ĐÁNH GIÁ KẾT QUẢ", 37),
        ("CHƯƠNG 5. KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN", 39),
        ("TÀI LIỆU THAM KHẢO", 41), ("PHỤ LỤC", 42),
        ("Phụ lục A. Ma trận yêu cầu và minh chứng", 43), ("Phụ lục B. Danh mục thực thể và quan hệ", 44),
        ("Phụ lục C. Ma trận kiểm thử", 47), ("Phụ lục D. Hướng dẫn chạy lại và demo", 49),
        ("Phụ lục E. Thuật ngữ", 51), ("Phụ lục F. Đặc tả ca sử dụng", 52),
        ("Phụ lục G. Từ điển dữ liệu", 55), ("Phụ lục H. Danh mục API", 61),
        ("Phụ lục I. Danh mục kiểm thử", 64),
    ]
    for item, page in toc_items:
        p = doc.add_paragraph(); p.paragraph_format.tab_stops.add_tab_stop(Inches(6.45), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS)
        set_font(p.add_run(f"{item}\t{page}"), size=12)
    doc.add_page_break()

    doc.add_heading("DANH MỤC BẢNG", level=1)
    for item, page in [
        ("Bảng 2.1. Nhóm yêu cầu chức năng", 12), ("Bảng 2.2. Nhóm bảng PostgreSQL đã đối chiếu", 16),
        ("Bảng 2.3. Nhóm API và trách nhiệm chính", 14), ("Bảng 2.4. Ánh xạ nguyên tắc giáo dục sang thiết kế kỹ thuật", 13),
        ("Bảng 2.5. Phạm vi chức năng theo vai trò", 15), ("Bảng 3.2. Trạng thái trong vòng đời quiz", 22),
        ("Bảng 3.4. Ngữ cảnh AI và kiểm soát", 31), ("Bảng 4.1. Kết quả kiểm chứng tại thời điểm chốt", 37),
        ("Bảng A.1. Yêu cầu của đề tài và bằng chứng", 43),
        ("Bảng B.1. So sánh PostgreSQL, Room và đồng bộ", 44),
        ("Bảng B.2. Danh mục 18 bảng và các quan hệ khóa ngoại", 44),
        ("Bảng B.3. Các entity Room Android", 45), ("Bảng B.4. Danh mục 26 khóa ngoại PostgreSQL", 45),
        ("Bảng C.1. Các bước trong luồng kiểm thử E2E 24/24", 47),
        ("Bảng E.1. Danh mục từ viết tắt", 51),
        ("Bảng G.1. Từ điển 102 cột PostgreSQL", 55), ("Bảng G.2. Từ điển entity Room", 58),
        ("Bảng H.1. Danh mục route backend", 61), ("Bảng I.1. Danh sách test suite", 64),
        ("Bảng I.2. Kết quả chạy kiểm thử đã xác minh", 66)
    ]:
        p = doc.add_paragraph(); p.paragraph_format.tab_stops.add_tab_stop(Inches(6.45), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS)
        set_font(p.add_run(f"{item}\t{page}"), size=12)
    doc.add_page_break()
    doc.add_heading("DANH MỤC HÌNH", level=1)
    for item, page in [
        ("Hình 2.1. Tác nhân và ranh giới ca sử dụng StudySpace", 11),
        ("Hình 2.2. Kiến trúc client–server và kho dữ liệu", 14),
        ("Hình 2.3. Ranh giới đồng bộ Room và PostgreSQL", 18),
        ("Hình 3.1. Tổng quan giảng viên", 20), ("Hình 3.2. Báo cáo lớp", 21),
        ("Hình 3.3. Tổng quan học viên với gợi ý học hôm nay", 25),
        ("Hình 3.4. Hoạt động và thời lượng tập trung", 25), ("Hình 3.5. Danh mục môn học", 26),
        ("Hình 3.6. Lộ trình chương và bài", 27), ("Hình 3.7. Màn nội dung bài học", 27),
        ("Hình 3.8. Màn hình làm quiz", 28), ("Hình 3.9. Kết quả quiz và giải thích", 29),
        ("Hình 3.10. Báo cáo tiến độ học viên", 30), ("Hình 3.11. Cổng quản trị", 35),
        ("Hình 3.12. Báo cáo thống kê quản trị", 36),
        ("Hình B.1. Sơ đồ quan hệ lõi StudySpace", 44)
    ]:
        p = doc.add_paragraph(); p.paragraph_format.tab_stops.add_tab_stop(Inches(6.45), WD_TAB_ALIGNMENT.RIGHT, WD_TAB_LEADER.DOTS)
        set_font(p.add_run(f"{item}\t{page}"), size=12)
    doc.add_page_break()

    if len(PAGES) != len(EXPANSIONS):
        raise ValueError(f"Expansion count mismatch: {len(PAGES)} topics, {len(EXPANSIONS)} expansions")
    opening = [
        ("1. Tính cấp thiết của đề tài", 0),
        ("2. Vấn đề và động cơ lựa chọn", 1),
        ("3. Mục tiêu, phạm vi và phương pháp nghiên cứu", 2),
    ]
    doc.add_heading("MỞ ĐẦU", level=1)
    for title, idx in opening:
        doc.add_heading(title, level=2)
        for text in PAGES[idx][1]: add_body(doc, text)
        add_body(doc, EXPANSIONS[idx])
    doc.add_heading("4. Ý nghĩa liên ngành và thực tiễn", level=2)
    add_body(doc, "Đề tài kết nối công nghệ thông tin với giáo dục: kiến thức về lập trình, cơ sở dữ liệu, bảo mật và thiết kế hệ thống được dùng để hiện thực hóa cách tổ chức học liệu, đánh giá thường xuyên, phản hồi và theo dõi tiến bộ. Giá trị của sản phẩm được xem xét ở cả hai phía: hệ thống phải vận hành đúng và luồng học phải có ý nghĩa với học viên, giảng viên.")

    def render_section(idx, title, level):
        doc.add_heading(title, level=level)
        for text in PAGES[idx][1]: add_body(doc, text)
        add_body(doc, EXPANSIONS[idx])
        if idx == 6:
            pic = ASSET / "usecase.png"
            make_usecase_diagram(pic)
            doc.add_picture(str(pic), width=Inches(6.2)); add_caption(doc, "Hình 2.1. Tác nhân và ranh giới ca sử dụng StudySpace")
        if idx == 7:
            add_caption(doc, "Bảng 2.1. Nhóm yêu cầu chức năng")
            add_table(doc, ["Mã", "Yêu cầu", "Kết quả quan sát"], [
                ["FR-01", "Quản lý nội dung", "Tạo/sửa chương, bài, tài liệu, câu hỏi; xuất bản/ẩn khóa"],
                ["FR-02", "Quiz trắc nghiệm", "Làm có thời hạn; nộp, chấm server, xem giải thích"],
                ["FR-03", "Theo dõi tiến độ", "Bài, quiz, kế hoạch và focus theo tài khoản"],
                ["FR-04", "Quản lý người dùng", "Đăng nhập, role, trạng thái tài khoản"],
                ["FR-05", "Báo cáo thống kê", "Biểu đồ, heatmap, CSV và báo cáo cá nhân"],
            ], [0.65, 1.7, 4.3], 10)
        if idx == 8:
            add_caption(doc, "Bảng 2.4. Ánh xạ nguyên tắc giáo dục sang thiết kế kỹ thuật")
            add_table(doc, ["Nhu cầu giáo dục", "Biểu hiện trong StudySpace", "Thành phần kỹ thuật"], [
                ["Chia nhỏ mục tiêu", "Môn → chương → bài theo thứ tự", "courses, chapters, lessons; giao diện lộ trình"],
                ["Học có giàn giáo", "Bài sau mở khi bài trước hoàn thành", "lesson_progress và API hoàn thành bài"],
                ["Đánh giá thường xuyên", "Quiz gắn với chương, nộp và xem kết quả", "questions, quiz_attempts, attempt_answers"],
                ["Phản hồi kịp thời", "Hiện đáp án đúng, giải thích và câu cần ôn", "Kết quả backend; AI chỉ bổ sung giải thích"],
                ["Tự điều chỉnh", "Theo dõi streak, kế hoạch và thời gian focus", "Dữ liệu cá nhân, Room và snapshot sync"],
                ["Dạy dựa trên bằng chứng", "Xem chương/câu có tỷ lệ sai cao", "Báo cáo lớp và truy vấn thống kê"],
                ["Cá nhân hóa", "Gợi ý ôn chương yếu, ưu tiên câu từng sai", "Heuristic backend và lịch sử attempt"],
                ["Hỗ trợ nhiều vai trò", "Học viên, giảng viên, quản trị viên có phạm vi riêng", "Session, RBAC và kiểm tra quyền tại API"],
            ], [1.35, 2.55, 2.65], 9.5)
        if idx == 9:
            pic = ASSET / "architecture.png"
            make_architecture_diagram(pic)
            doc.add_picture(str(pic), width=Inches(6.35)); add_caption(doc, "Hình 2.2. Kiến trúc client–server và kho dữ liệu")
        if idx == 10:
            add_caption(doc, "Bảng 2.3. Nhóm API và trách nhiệm chính")
            add_table(doc, ["Nhóm API", "Ví dụ route", "Trách nhiệm"], [
                ["Xác thực", "/api/auth/login, /me, /logout", "Đăng nhập, hồ sơ và phiên người dùng"],
                ["Học tập", "/api/courses, /api/lessons/{id}/complete", "Danh sách khóa, nội dung và tiến độ bài"],
                ["Quiz", "/api/chapters/{id}/attempts, /api/attempts/{id}/submit", "Tạo attempt, lưu đáp án và chấm phía server"],
                ["Giảng viên", "/api/teacher/content/courses, /chapters, /lessons, /questions", "Quản lý học liệu và ngân hàng câu hỏi"],
                ["Lớp học", "/api/teacher/classes, /api/classes/join", "Tạo lớp, ghi danh, thông báo và báo cáo lớp"],
                ["AI", "/api/ai/chat, /explain, /review-quiz", "Gọi Gemini phía backend hoặc trả gợi ý dự phòng"],
                ["Quản trị", "/api/admin/users, /reports, /audit", "Quản lý tài khoản, thống kê và nhật ký"],
            ], [1.05, 2.8, 2.8], 9.5)
            add_caption(doc, "Bảng 2.5. Phạm vi chức năng theo vai trò")
            add_table(doc, ["Tác vụ", "Học viên", "Giảng viên", "Quản trị viên"], [
                ["Xem khóa đã xuất bản và học bài", "Khóa công khai", "Không áp dụng", "Quản lý nội dung"],
                ["Làm quiz và xem kết quả", "Dữ liệu cá nhân", "Soạn câu hỏi", "Quản lý ngân hàng"],
                ["Tạo/sửa khóa, chương, bài", "Không", "Khóa của mình", "Toàn hệ thống"],
                ["Quản lý lớp và trao đổi", "Tham gia lớp", "Lớp phụ trách", "Phạm vi quản trị"],
                ["Xem tiến độ và báo cáo", "Của mình", "Lớp phụ trách", "Thống kê toàn hệ thống"],
                ["Đổi vai trò/khóa tài khoản", "Không", "Không", "Có"],
            ], [2.1, 1.35, 1.55, 1.55], 9)
        if idx == 12:
            add_caption(doc, "Bảng 2.2. Nhóm bảng PostgreSQL đã đối chiếu")
            add_table(doc, ["Nhóm", "Bảng tiêu biểu", "Vai trò"], [
                ["Tài khoản", "accounts, sessions, portal_tickets", "Định danh, phiên và quyền truy cập"],
                ["Nội dung", "courses, chapters, lessons, questions", "Cấu trúc học liệu và ngân hàng câu hỏi"],
                ["Đánh giá", "quiz_attempts, attempt_answers", "Lượt làm và ảnh chụp đáp án"],
                ["Tiến độ", "lesson_progress, course_learning", "Hoàn thành bài và quá trình học khóa"],
                ["Lớp", "study_classes, class_enrollments, class_announcements", "Ghi danh và trao đổi lớp"],
                ["Khác", "admin_audit_log, personal_study_snapshots", "Truy vết quản trị và đồng bộ cá nhân"],
            ], [1.1, 2.7, 2.85], 10)
        if idx == 14:
            pic = ASSET / "sync-flow.png"
            make_diagram(pic, "Đồng bộ dữ liệu cá nhân", [
                (80, 270, 360, 220, "Room", "Kế hoạch + phiên focus", "#ffffff"),
                (530, 270, 360, 220, "Worker", "Mạng · retry · version", "#ffffff"),
                (980, 270, 360, 220, "REST API", "GET/PUT snapshot", "#ffffff"),
                (1430, 270, 300, 220, "Server DB", "payload + version", "#ffffff"),
            ], [(0,1,"enqueue"),(1,2,"request"),(2,3,"persist")])
            doc.add_picture(str(pic), width=Inches(6.35)); add_caption(doc, "Hình 2.3. Ranh giới đồng bộ Room và PostgreSQL")
        if idx == 18:
            add_caption(doc, "Bảng 3.2. Trạng thái trong vòng đời quiz")
            add_table(doc, ["Trạng thái", "Ý nghĩa", "Hành vi giao diện"], [
                ["Đang làm", "Attempt đang trong deadline", "Đồng hồ, điều hướng, lưu bản nháp"],
                ["Đánh dấu", "Câu cần xem lại", "Hiện chip trên số câu và xác nhận nộp"],
                ["Hết giờ", "Deadline đã qua", "Tự nộp theo quy tắc server"],
                ["Đã nộp", "Server có kết quả", "Điểm, đáp án, giải thích và lịch sử"],
            ], [1.0, 2.3, 3.35], 10)
        if idx == 24:
            add_caption(doc, "Bảng 3.4. Ngữ cảnh AI và kiểm soát")
            add_table(doc, ["Tác vụ", "Ngữ cảnh", "Kiểm soát"], [
                ["Hỏi theo bài", "Nội dung bài đang xem", "Backend giới hạn ngữ cảnh, trả provider"],
                ["Giải thích sai", "Câu hỏi, lựa chọn, đáp án chuẩn", "AI không thay đổi điểm"],
                ["Sinh quiz", "Chương yếu/câu sai", "Validate JSON schema trước khi hiển thị"],
                ["Soạn nội dung", "Chủ đề/chương do giảng viên chọn", "Bản nháp cần người duyệt"],
            ], [1.25, 2.65, 2.75], 10)
        if idx == 33:
            add_caption(doc, "Bảng 4.1. Kết quả kiểm chứng tại thời điểm chốt")
            add_table(doc, ["Phạm vi", "Công cụ / bằng chứng", "Kết quả"], [
                ["Backend", "JUnit/Spring Boot test", "50/50 PASS; 0 fail/error/skip"],
                ["Android", "Gradle unit/integration test", "56/56 PASS; 0 fail/error/skip"],
                ["Web", "Playwright E2E giảng viên → học viên", "24/24 PASS; cleanup thành công"],
                ["Android build", "Gradle assembleDebug", "PASS; chưa kiểm tra trực quan trên thiết bị"],
                ["AI trực tiếp", "Health + chat/giải thích/quiz Gemini", "PASS trong lần kiểm tra 22/10/2026"],
            ], [1.2, 2.8, 2.4], 9.5)

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 1. CƠ SỞ LÝ THUYẾT VÀ TỔNG QUAN", level=1)
    for idx, title in zip(range(3, 6), ["1.1. Cơ sở sư phạm của lộ trình học", "1.2. Đánh giá quá trình và phản hồi", "1.3. Cá nhân hóa và giới hạn heuristic"]):
        render_section(idx, title, 2)

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 2. PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG LIÊN NGÀNH", level=1)
    design_titles = [
        "2.1. Tác nhân và ranh giới hệ thống", "2.2. Yêu cầu chức năng", "2.3. Yêu cầu phi chức năng và tiêu chí chấp nhận",
        "2.4. Kiến trúc tổng thể", "2.5. Phân lớp backend và kiểm soát quyền", "2.6. Ứng dụng Android và ranh giới local",
        "2.7. Mô hình quan hệ PostgreSQL", "2.8. Lược đồ Room và dữ liệu cá nhân", "2.9. Đồng bộ snapshot và xử lý xung đột",
    ]
    for idx, title in zip(range(6, 15), design_titles): render_section(idx, title, 2)

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 3. TRIỂN KHAI VÀ CÀI ĐẶT HỆ THỐNG", level=1)
    implementation_groups = [
        ("3.1. Quản lý học liệu và lớp học", range(15, 18)),
        ("3.2. Quiz và đánh giá", range(18, 21)),
        ("3.3. Theo dõi tiến độ và báo cáo", range(21, 24)),
        ("3.4. Tích hợp trợ lý AI", range(24, 27)),
        ("3.5. Giao diện và trải nghiệm", range(27, 30)),
        ("3.6. Bảo mật và triển khai", range(30, 33)),
    ]
    for group_title, indices in implementation_groups:
        doc.add_heading(group_title, level=2)
        for n, idx in enumerate(indices, 1):
            original = PAGES[idx][0].split(". ", 2)[-1]
            render_section(idx, f"{group_title.split('.')[0]}.{group_title.split('.')[1]}.{n}. {original}", 3)
        if group_title.startswith("3.1."):
            for fi in (0, 1): add_report_figure(doc, fi)
        if group_title.startswith("3.3."):
            for fi in range(2, 10): add_report_figure(doc, fi)
        if group_title.startswith("3.6."):
            for fi in (10, 11): add_report_figure(doc, fi)

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 4. THỰC NGHIỆM VÀ ĐÁNH GIÁ KẾT QUẢ", level=1)
    for n, idx in enumerate(range(33, 36), 1):
        render_section(idx, f"4.{n}. {PAGES[idx][0].split('. ', 2)[-1]}", 2)

    doc.add_page_break()
    doc.add_heading("CHƯƠNG 5. KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN", level=1)
    for n, idx in enumerate(range(36, 39), 1):
        render_section(idx, f"5.{n}. {PAGES[idx][0].split('. ', 2)[-1]}", 2)

    doc.add_page_break()
    doc.add_heading("TÀI LIỆU THAM KHẢO", level=1)
    for text in APPENDIX[-1][1]: add_body(doc, text)
    doc.add_page_break()
    doc.add_heading("PHỤ LỤC", level=1)
    doc.add_page_break()
    for appendix_index, (title, paragraphs) in enumerate(APPENDIX[:-1]):
        doc.add_heading(title, level=1)
        for text in paragraphs:
            if title.startswith("Phụ lục F") and text.startswith("UC-"):
                case_title, body = text.split(". ", 1)
                doc.add_heading(case_title, level=2)
                add_body(doc, body)
            else:
                add_body(doc, text)
        if title.startswith("Phụ lục A"):
            add_caption(doc, "Bảng A.1. Yêu cầu của đề tài và bằng chứng")
            add_table(doc, ["Yêu cầu", "Bằng chứng chính"], [
                ["Quản lý nội dung", "TeacherContentController, teacher.html, use case"],
                ["Quiz", "LearningController, quiz-active/result, attempt schema"],
                ["Tiến độ", "lesson_progress, course_learning, báo cáo học viên"],
                ["Quản lý người dùng", "AdminController, accounts, ảnh admin"],
                ["Báo cáo thống kê", "Chart.js admin, heatmap lớp, ảnh tiến độ"],
                ["CSDL quan hệ", "PostgreSQL schema, ERD và 26 FK"],
                ["Trực quan hóa", "Chart.js web, Compose Canvas radar Android"],
                ["Tài liệu", "ERD, use case, UI, hướng dẫn trong docs/submission"],
            ], [1.7, 4.95], 9.5)
        if title.startswith("Phụ lục B"):
            pic = ASSET / "erd-core.png"
            make_diagram(pic, "ERD lõi StudySpace", [
                (90, 190, 310, 170, "accounts", "user · teacher · admin", "#eef0ff"),
                (520, 190, 310, 170, "courses", "created_by · published", "#e8fbf7"),
                (950, 190, 310, 170, "chapters", "course_id · position", "#f2f5ff"),
                (1380, 190, 310, 170, "lessons", "chapter_id · content", "#fff5e8"),
                (90, 540, 310, 170, "quiz_attempts", "user_id · chapter_id", "#eef0ff"),
                (520, 540, 310, 170, "attempt_answers", "attempt · question snapshot", "#e8fbf7"),
                (950, 540, 310, 170, "study_classes", "course · teacher", "#f2f5ff"),
            ], [(0,1,"creates"),(1,2,"contains"),(2,3,"contains"),(0,4,"takes"),(4,5,"has"),(1,6,"offers")])
            doc.add_picture(str(pic), width=Inches(6.25)); add_caption(doc, "Hình B.1. Sơ đồ quan hệ lõi; danh mục đầy đủ 18 bảng và 26 FK ở ERD nộp kèm")
            add_caption(doc, "Bảng B.1. So sánh PostgreSQL, Room và đồng bộ")
            add_table(doc, ["Nền tảng", "Thực thể", "Ghi chú quan hệ"], [
                ["PostgreSQL", "18 bảng", "102 cột, 26 FK theo information_schema"],
                ["Room", "5 entity", "Không khai báo FK; tham chiếu owner/task do app quản lý"],
                ["Sync", "personal_study_snapshots", "Snapshot kế hoạch/focus theo user và version"],
            ], [1.1, 1.45, 4.1], 9.5)
            doc.add_page_break()
            add_caption(doc, "Bảng B.2. Danh mục 18 bảng và các quan hệ khóa ngoại")
            add_table(doc, ["Bảng", "Khóa chính", "Quan hệ khóa ngoại chính"], [
                ["accounts", "id", "Không có"],
                ["admin_audit_log", "id", "actor_id → accounts.id"],
                ["attempt_answers", "attempt_id + question_id", "quiz_attempts.id; questions.id"],
                ["chapters", "id", "course_id → courses.id"],
                ["class_announcements", "id", "class_id → study_classes.id; author_id → accounts.id"],
                ["class_enrollments", "class_id + student_id", "study_classes.id; accounts.id"],
                ["course_learning", "user_id + course_id", "accounts.id; courses.id"],
                ["course_messages", "id", "course_id → courses.id; user_id → accounts.id"],
                ["course_presence", "course_id + user_id", "courses.id; accounts.id"],
                ["courses", "id", "created_by → accounts.id (nullable)"],
                ["lesson_progress", "user_id + lesson_id", "accounts.id; lessons.id"],
                ["lessons", "id", "chapter_id → chapters.id"],
                ["personal_study_snapshots", "user_id + version", "user_id → accounts.id"],
                ["portal_tickets", "ticket_hash", "user_id → accounts.id"],
                ["questions", "id", "chapter_id → chapters.id"],
                ["quiz_attempts", "id", "user_id → accounts.id; chapter_id → chapters.id"],
                ["sessions", "token_hash", "user_id → accounts.id"],
                ["study_classes", "id", "course_id → courses.id; teacher_id → accounts.id"],
            ], [1.45, 1.55, 3.6], 8.4)
            add_caption(doc, "Bảng B.3. Các entity Room Android")
            add_table(doc, ["Entity", "Khóa chính", "Nội dung và liên kết logic"], [
                ["users", "username", "Hồ sơ local; không có FK"],
                ["study_tasks", "id", "Kế hoạch theo userId"],
                ["focus_sessions", "id", "Phiên focus; taskId tham chiếu logic"],
                ["reward_logs", "id", "Điểm thưởng theo userId"],
                ["group_chat_messages", "id", "Chat local theo roomId; không đồng bộ snapshot cá nhân"],
            ], [1.65, 1.15, 3.8], 8.8)
            add_caption(doc, "Bảng B.4. Danh mục 26 khóa ngoại PostgreSQL")
            add_table(doc, ["Bảng/cột tham chiếu", "Bảng/cột đích", "Vai trò"], [
                ["sessions.user_id", "accounts.id", "Chủ sở hữu phiên"],
                ["admin_audit_log.actor_id", "accounts.id", "Tác nhân thao tác"],
                ["portal_tickets.user_id", "accounts.id", "Chủ vé đăng nhập portal"],
                ["courses.created_by", "accounts.id", "Giảng viên tạo khóa"],
                ["chapters.course_id", "courses.id", "Chương thuộc khóa"],
                ["lessons.chapter_id", "chapters.id", "Bài thuộc chương"],
                ["lesson_progress.user_id", "accounts.id", "Học viên hoàn thành bài"],
                ["lesson_progress.lesson_id", "lessons.id", "Bài được theo dõi"],
                ["course_learning.user_id", "accounts.id", "Học viên bắt đầu khóa"],
                ["course_learning.course_id", "courses.id", "Khóa đang học"],
                ["questions.chapter_id", "chapters.id", "Câu hỏi thuộc chương"],
                ["quiz_attempts.user_id", "accounts.id", "Người làm quiz"],
                ["quiz_attempts.chapter_id", "chapters.id", "Chương được đánh giá"],
                ["attempt_answers.attempt_id", "quiz_attempts.id", "Lượt làm chứa đáp án"],
                ["attempt_answers.question_id", "questions.id", "Câu hỏi nguồn"],
                ["study_classes.course_id", "courses.id", "Lớp theo khóa"],
                ["study_classes.teacher_id", "accounts.id", "Giảng viên phụ trách"],
                ["class_enrollments.class_id", "study_classes.id", "Lớp có ghi danh"],
                ["class_enrollments.student_id", "accounts.id", "Học viên ghi danh"],
                ["class_announcements.class_id", "study_classes.id", "Thông báo của lớp"],
                ["class_announcements.author_id", "accounts.id", "Người đăng thông báo"],
                ["course_messages.course_id", "courses.id", "Trao đổi theo khóa"],
                ["course_messages.user_id", "accounts.id", "Người gửi trao đổi"],
                ["course_presence.course_id", "courses.id", "Lượt hiện diện theo khóa"],
                ["course_presence.user_id", "accounts.id", "Người học hiện diện"],
                ["personal_study_snapshots.user_id", "accounts.id", "Snapshot theo tài khoản"],
            ], [2.55, 1.55, 2.1], 8)
        if title.startswith("Phụ lục C"):
            add_caption(doc, "Bảng C.1. Các bước trong luồng kiểm thử E2E 24/24")
            e2e_steps = [
                "Giảng viên đăng nhập và tải không gian làm việc",
                "Tạo khóa học nháp từ biểu mẫu thực tế",
                "Sửa mô tả khóa học trong cổng giảng viên",
                "Thêm chương cho khóa học",
                "Tạo hai bài học theo thứ tự",
                "Tải PDF và gắn tài liệu vào bài học",
                "Tạo câu hỏi quiz có giải thích",
                "Xuất bản khóa sau khi có bài học thật",
                "Tạo lớp học và nhận mã tham gia",
                "Sao chép mã lớp từ giao diện giảng viên",
                "Học viên nhận phản hồi khi nhập mã không hợp lệ",
                "Học viên tham gia lớp và thấy khóa được giao",
                "Học viên gửi tin nhắn trao đổi lớp",
                "Học viên thấy bài đầu tiên có thể mở",
                "Bài tiếp theo bị khóa trước khi hoàn thành bài trước",
                "Hoàn thành bài đầu và mở khóa bài tiếp theo",
                "Nộp quiz và xem điểm cùng giải thích",
                "Kết quả quiz xuất hiện trong lịch sử học viên",
                "Giảng viên xem báo cáo hoàn thành và câu trả lời sai",
                "Giảng viên và học viên trao đổi tin nhắn hai chiều",
                "Báo cáo lớp phản ánh bài học và quiz vừa thực hiện",
                "Cổng giảng viên trên mobile không tràn ngang",
                "Luồng học viên mobile không tràn ngang hoặc lỗi runtime",
                "Học viên và giảng viên đăng xuất đúng cổng",
            ]
            add_table(doc, ["Bước", "Kịch bản", "Kết quả"], [
                [str(i + 1), step, "PASS"] for i, step in enumerate(e2e_steps)
            ], [0.55, 5.0, 0.9], 9.5)
        if title.startswith("Phụ lục E"):
            add_caption(doc, "Bảng E.1. Danh mục từ viết tắt")
            add_table(doc, ["Từ viết tắt", "Diễn giải"], [
                ["AI", "Artificial Intelligence — trí tuệ nhân tạo"],
                ["API", "Application Programming Interface — giao diện lập trình ứng dụng"],
                ["E2E", "End-to-End — kiểm thử xuyên suốt"],
                ["ERD", "Entity Relationship Diagram — sơ đồ thực thể–liên kết"],
                ["RBAC", "Role-Based Access Control — phân quyền theo vai trò"],
                ["REST", "Representational State Transfer — kiểu kiến trúc API"],
                ["Room", "Thư viện cơ sở dữ liệu quan hệ cục bộ Android"],
                ["UI/UX", "User Interface / User Experience — giao diện / trải nghiệm người dùng"],
            ], [1.4, 5.2], 10)
        if title.startswith("Phụ lục G"):
            add_caption(doc, "Bảng G.1. Từ điển 102 cột PostgreSQL")
            table = add_table(doc, ["Bảng", "Cột và kiểu SQL", "Ràng buộc / giá trị mặc định"], postgres_dictionary_rows(), [1.55, 1.85, 3.2], 8.8)
            set_repeat_table_header(table.rows[0])
            add_caption(doc, "Bảng G.2. Từ điển entity Room")
            table = add_table(doc, ["Entity", "Trường và kiểu Kotlin", "Khóa / nullability / mặc định"], room_dictionary_rows(), [1.65, 1.8, 3.15], 8.8)
            set_repeat_table_header(table.rows[0])
        if title.startswith("Phụ lục H"):
            add_caption(doc, "Bảng H.1. Danh mục route backend tại thời điểm chốt")
            table = add_table(doc, ["HTTP", "Route", "Phạm vi quyền", "Controller / handler"], api_catalog_rows(), [0.55, 2.15, 1.7, 2.2], 8.2)
            set_repeat_table_header(table.rows[0])
        if title.startswith("Phụ lục I"):
            add_caption(doc, "Bảng I.1. Danh sách test suite trong repository")
            tests = []
            for platform, test_root in (("Android", ROOT / "app" / "src" / "test"), ("Backend", ROOT / "backend" / "src" / "test")):
                for test_file in sorted(test_root.rglob("*Test.kt")) + sorted(test_root.rglob("*Test.java")):
                    display_name = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", test_file.stem)
                    tests.append([platform, display_name, test_file.relative_to(ROOT).as_posix()])
            if len(tests) != 31:
                raise ValueError(f"Test suite inventory changed: expected 31 files, found {len(tests)}")
            table = add_table(doc, ["Phạm vi", "Test suite", "Đường dẫn mã kiểm thử"], tests, [0.9, 2.2, 3.5], 8.5)
            set_repeat_table_header(table.rows[0])
            doc.add_page_break()
            add_caption(doc, "Bảng I.2. Kết quả chạy kiểm thử đã xác minh")
            add_table(doc, ["Bộ kiểm thử", "Kết quả", "Giới hạn diễn giải"], [
                ["Android", "56/56 PASS; 0 fail/error/skip", "Unit/integration; APK chưa được kiểm tra trực quan trên thiết bị"],
                ["Backend", "50/50 PASS; 0 fail/error/skip", "JUnit/Spring Boot trên cấu hình local"],
                ["Web E2E", "24/24 PASS", "Luồng demo local, có cleanup dữ liệu QA"],
                ["AI Gemini", "PASS ngày 22/10/2026", "Quota và cấu hình có thể đổi; cần health check trước demo"],
            ], [1.2, 2.0, 3.4], 9)
        if appendix_index < len(APPENDIX) - 2:
            doc.add_page_break()

    # Bibliography is the final page; remove final empty page break if Word adds one naturally.
    props = doc.core_properties
    props.title = "Báo cáo đồ án liên ngành StudySpace"
    props.subject = "Ứng dụng hỗ trợ học tập trực tuyến"
    props.author = "Nguyễn Hữu Lê Hiếu; Phạm Văn Huấn"
    props.keywords = "StudySpace, học tập trực tuyến, quiz, tiến độ, PostgreSQL"
    doc.save(OUT)
    print(f"Created {OUT}")
    print(f"Screenshot figures included: {sum(1 for f,_,_ in FIGURES if (ROOT/'docs'/'submission'/'screenshots'/f).exists())}")
    print("Final pagination must be verified from the rendered PDF.")


if __name__ == "__main__":
    main()
