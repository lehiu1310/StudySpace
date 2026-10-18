# -*- coding: utf-8 -*-
from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "BaoCao_StudySpace_HoanThien.docx"

BLUE = "0B84B3"
BLUE_DARK = "075985"
PURPLE = "7B1FA2"
GRAY = "666666"
LIGHT_BLUE = "E8F6FB"
LIGHT_PURPLE = "F4EAF8"
LIGHT_GRAY = "F5F7FA"
BORDER = "D8E2EA"


def set_cell_shading(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_border(cell, color: str = BORDER, size: str = "6") -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = tc_pr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tc_pr.append(borders)
    for edge in ("top", "left", "bottom", "right"):
        node = borders.find(qn(f"w:{edge}"))
        if node is None:
            node = OxmlElement(f"w:{edge}")
            borders.append(node)
        node.set(qn("w:val"), "single")
        node.set(qn("w:sz"), size)
        node.set(qn("w:space"), "0")
        node.set(qn("w:color"), color)


def set_cell_margins(cell, top=100, start=120, bottom=100, end=120) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    margins = tc_pr.first_child_found_in("w:tcMar")
    if margins is None:
        margins = OxmlElement("w:tcMar")
        tc_pr.append(margins)
    for key, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = margins.find(qn(f"w:{key}"))
        if node is None:
            node = OxmlElement(f"w:{key}")
            margins.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_table_width(table, widths_cm: list[float]) -> None:
    table.autofit = False
    for row in table.rows:
        for idx, width in enumerate(widths_cm):
            if idx < len(row.cells):
                row.cells[idx].width = Cm(width)
                row.cells[idx].vertical_alignment = WD_ALIGN_VERTICAL.CENTER
                set_cell_margins(row.cells[idx])


def add_page_field(paragraph) -> None:
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = "PAGE"
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(begin)
    run._r.append(instr)
    run._r.append(end)


def configure_document(doc: Document) -> None:
    section = doc.sections[0]
    section.page_width = Cm(21)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.0)
    section.bottom_margin = Cm(2.0)
    section.left_margin = Cm(3.0)
    section.right_margin = Cm(1.8)
    section.header_distance = Cm(1.25)
    section.footer_distance = Cm(1.25)

    for style_name in ("Normal", "Body Text"):
        style = doc.styles[style_name]
        style.font.name = "Times New Roman"
        style._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")
        style.font.size = Pt(13)
    body = doc.styles["Body Text"]
    body.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    body.paragraph_format.line_spacing = 1.25
    body.paragraph_format.space_after = Pt(6)

    heading_specs = [
        ("Heading 1", 17, BLUE_DARK, 14, 8),
        ("Heading 2", 14, BLUE, 10, 6),
        ("Heading 3", 13, "1F4D78", 8, 4),
    ]
    for name, size, color, before, after in heading_specs:
        style = doc.styles[name]
        style.font.name = "Times New Roman"
        style._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor.from_string(color)
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True

    caption = doc.styles["Caption"]
    caption.font.name = "Times New Roman"
    caption._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")
    caption.font.size = Pt(10)
    caption.font.italic = True
    caption.font.color.rgb = RGBColor.from_string(GRAY)
    caption.paragraph_format.space_after = Pt(8)

    footer = section.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = footer.add_run("StudySpace Secure Learning System | Trang ")
    run.font.size = Pt(9)
    run.font.color.rgb = RGBColor.from_string(GRAY)
    add_page_field(footer)


def heading(doc: Document, text: str, level: int = 1) -> None:
    doc.add_paragraph(text, style=f"Heading {level}")


def body(doc: Document, text: str) -> None:
    doc.add_paragraph(text, style="Body Text")


def bullet(doc: Document, text: str) -> None:
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.left_indent = Cm(0.8)
    p.paragraph_format.first_line_indent = Cm(-0.3)
    p.add_run(text)


def number(doc: Document, text: str) -> None:
    p = doc.add_paragraph(style="List Number")
    p.add_run(text)


def callout(doc: Document, title: str, content: str, fill: str = LIGHT_BLUE, color: str = BLUE_DARK) -> None:
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.columns[0].width = Cm(16)
    cell = table.cell(0, 0)
    set_cell_shading(cell, fill)
    set_cell_border(cell)
    set_cell_margins(cell, top=160, bottom=160, start=180, end=180)
    p = cell.paragraphs[0]
    p.style = doc.styles["Body Text"]
    r = p.add_run(title)
    r.bold = True
    r.font.color.rgb = RGBColor.from_string(color)
    p.add_run("\n" + content)


def table(doc: Document, headers: list[str], rows: list[list[str]], widths: list[float]) -> None:
    t = doc.add_table(rows=1, cols=len(headers))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    for idx, header in enumerate(headers):
        cell = t.rows[0].cells[idx]
        cell.text = header
        set_cell_shading(cell, "E8EEF5")
        set_cell_border(cell, BLUE_DARK, "8")
        set_cell_margins(cell)
        for p in cell.paragraphs:
            for r in p.runs:
                r.bold = True
                r.font.color.rgb = RGBColor.from_string(BLUE_DARK)
    for row in rows:
        cells = t.add_row().cells
        for idx, value in enumerate(row):
            cells[idx].text = value
            set_cell_border(cells[idx])
            set_cell_margins(cells[idx])
    set_table_width(t, widths)


def code_block(doc: Document, content: str) -> None:
    t = doc.add_table(rows=1, cols=1)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = t.cell(0, 0)
    set_cell_shading(cell, "F3F4F6")
    set_cell_border(cell)
    set_cell_margins(cell, top=140, bottom=140, start=160, end=160)
    p = cell.paragraphs[0]
    r = p.add_run(content)
    r.font.name = "Courier New"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "Courier New")
    r.font.size = Pt(9)


def image(doc: Document, path: Path, caption: str, width_cm: float = 7.5) -> None:
    if not path.exists():
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(str(path), width=Cm(width_cm))
    cap = doc.add_paragraph(caption, style="Caption")
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER


def cover(doc: Document) -> None:
    outer = doc.add_table(rows=1, cols=1)
    outer.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = outer.cell(0, 0)
    set_cell_border(cell, BLUE_DARK, "18")
    set_cell_margins(cell, top=220, bottom=220, start=240, end=240)

    p = cell.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cover_lines = [
        ("TRƯỜNG ĐẠI HỌC PHENIKAA\n", 14, True, BLUE_DARK),
        ("KHOA CÔNG NGHỆ THÔNG TIN\n\n", 13, True, BLUE_DARK),
        ("BÁO CÁO BÀI TẬP LỚN\n", 18, True, BLUE_DARK),
        ("MÔN PHÂN TÍCH VÀ THIẾT KẾ PHẦN MỀM\n\n", 14, True, "111111"),
        ("PHÁT TRIỂN HỆ THỐNG HỌC TẬP TẬP TRUNG STUDYSPACE (EDUPOMODORO)\n", 17, True, BLUE_DARK),
        ("ỨNG DỤNG POMODORO, AI CÁ NHÂN HÓA, BẢO MẬT DI ĐỘNG VÀ NHẮC LỊCH THÔNG MINH\n\n", 14, True, PURPLE),
    ]
    for text, size, bold, color in cover_lines:
        run = p.add_run(text)
        run.font.size = Pt(size)
        run.bold = bold
        run.font.color.rgb = RGBColor.from_string(color)

    info = cell.add_table(rows=0, cols=2)
    rows = [
        ("Học phần", "Phân Tích Và Thiết Kế Phần Mềm"),
        ("Giảng viên hướng dẫn", "TS. Nguyễn Hữu Đạt"),
        ("Nhóm thực hiện", "Nhóm StudySpace Phenikaa"),
        ("Sinh viên", "Nguyễn Hữu Lê Hiếu - MSV: 23010895\nLê Khả Hoàng - MSV: 23010314"),
        ("Phiên bản báo cáo", "Cập nhật sau hoàn thiện app, build và chạy thử trên Medium Phone"),
        ("Thời gian", "Hà Nội, Tháng 6 Năm 2026"),
    ]
    for key, value in rows:
        cells = info.add_row().cells
        cells[0].text = key
        cells[1].text = value
        for c in cells:
            set_cell_border(c, "FFFFFF", "0")
            set_cell_margins(c, top=80, bottom=80)
    set_table_width(info, [5.0, 9.0])
    doc.add_page_break()


def toc(doc: Document) -> None:
    heading(doc, "Mục Lục")
    entries = [
        "Lời mở đầu",
        "Chương 1. Giới thiệu chung về hệ thống",
        "Chương 2. Phân tích yêu cầu và đặc tả SRS",
        "Chương 3. Thiết kế chức năng và kiến trúc phần mềm",
        "Chương 4. Mô hình dữ liệu và xử lý nghiệp vụ",
        "Chương 5. Các chức năng đã hoàn thiện trong ứng dụng",
        "Chương 6. Thiết kế bảo mật",
        "Chương 7. Thiết kế giao diện và trải nghiệm người dùng",
        "Chương 8. Kiểm thử, CI/CD và nghiệm thu",
        "Chương 9. Đánh giá, hạn chế và hướng phát triển",
        "Phụ lục. Minh chứng kỹ thuật và tài liệu tham khảo",
    ]
    for entry in entries:
        body(doc, entry)
    doc.add_page_break()


def write_intro(doc: Document) -> None:
    heading(doc, "Lời Mở Đầu")
    body(
        doc,
        "StudySpace (EduPomodoro) là ứng dụng Android hỗ trợ sinh viên quản lý mục tiêu học tập, "
        "tập trung theo Pomodoro, nhận nhắc lịch thông minh, theo dõi tiến độ và học cùng nhóm. "
        "Báo cáo này là bản cập nhật sau khi ứng dụng đã được sửa lỗi UI, bổ sung bảo mật thật, "
        "khôi phục Gradle Wrapper, thêm test/CI và nâng cấp các tính năng vốn còn mô phỏng trong bản cũ."
    )
    body(
        doc,
        "Phiên bản hiện tại được xây dựng bằng Kotlin, Jetpack Compose, Room Database và kiến trúc MVVM. "
        "Các điểm nâng cấp nổi bật gồm BCrypt random salt, Android Keystore với AES-GCM, BiometricPrompt thật, "
        "WorkManager reminder, xuất PDF bằng PdfDocument, Study Groups có chat lưu Room và AI Skill Assistant "
        "đọc bối cảnh học tập trước khi đề xuất bài luyện."
    )
    callout(
        doc,
        "Lưu ý trung thực về phạm vi",
        "Báo cáo cũ có một số mô tả chưa đúng với trạng thái app như SHA-256 salt cố định, AES-CBC và mô phỏng AI. "
        "Bản mới chỉ nêu phần đã triển khai thật. Firebase realtime, OTP email/SMS, SQLCipher và certificate pinning "
        "được đưa vào hướng phát triển, không trình bày như chức năng đã hoàn tất.",
        fill=LIGHT_PURPLE,
        color=PURPLE,
    )


def chapter_1(doc: Document) -> None:
    heading(doc, "Chương 1. Giới Thiệu Chung về Hệ Thống")
    heading(doc, "1.1 Mô tả tổng quan", 2)
    body(
        doc,
        "StudySpace là hệ thống học tập cá nhân trên Android, hướng tới sinh viên cần một môi trường tập trung, "
        "có lịch học rõ ràng, có thống kê tiến độ và có lớp bảo vệ dữ liệu cơ bản. Ứng dụng tổ chức trải nghiệm "
        "theo bốn vùng chính: Tổng quan, Phòng tập trung, Lịch học và Nhóm học."
    )
    table(
        doc,
        ["Nhóm", "Mô tả sau khi hoàn thiện"],
        [
            ["Nền tảng", "Android native, Kotlin, Jetpack Compose, Material 3."],
            ["Dữ liệu local", "Room Database với users, study_tasks, focus_sessions, reward_logs và group_chat_messages."],
            ["Tập trung", "Pomodoro, nghỉ ngắn, thiền định, âm thanh nền, start/pause/reset và ghi FocusSession."],
            ["Nhắc lịch", "WorkManager schedule/cancel notification theo dữ liệu reminder của StudyTask."],
            ["Bảo mật", "BCrypt random salt, Keystore AES-GCM, BiometricPrompt, khóa phiên khi inactive."],
            ["AI", "Gemini API nếu có key; fallback offline theo StudySkillRequest khi không có key."],
            ["Kiểm thử", "JUnit/Robolectric, Gradle Wrapper, GitHub Actions build/test APK."],
        ],
        [4.0, 12.0],
    )
    heading(doc, "1.2 Mục tiêu", 2)
    goals = [
        "Giúp người học chia mục tiêu lớn thành các phiên tập trung đo được.",
        "Không hiển thị số liệu giả cho tài khoản mới; dashboard phải phản ánh dữ liệu FocusSession thật.",
        "Nâng cấp các nút mô phỏng thành tính năng có tác động thật khi demo: biometric, reminder, PDF export.",
        "Tăng độ tin cậy bằng test tự động và khả năng build từ terminal/CI.",
        "Trình bày rõ giới hạn hiện tại để báo cáo không bị thổi phồng so với code.",
    ]
    for item in goals:
        bullet(doc, item)


def chapter_2(doc: Document) -> None:
    heading(doc, "Chương 2. Phân Tích Yêu Cầu và Đặc Tả SRS")
    heading(doc, "2.1 Yêu cầu chức năng", 2)
    table(
        doc,
        ["Mã", "Chức năng", "Đặc tả nghiệm thu"],
        [
            ["FR-01", "Đăng ký/đăng nhập", "Tạo user, hash mật khẩu bằng BCrypt, verify hash cũ và rehash khi cần."],
            ["FR-02", "Dashboard", "Hiển thị XP, task, phiên focus và biểu đồ tuần từ dữ liệu thật."],
            ["FR-03", "Focus room", "Chọn task, chọn mode, bấm bắt đầu/tạm dừng, timer chạy và ghi FocusSession."],
            ["FR-04", "Planner", "CRUD task, đặt hạn, bật reminder, giao diện nút thêm không vỡ trên Medium Phone."],
            ["FR-05", "Smart Reminder", "WorkManager tạo/cancel unique work, notification channel riêng, xin quyền Android 13+."],
            ["FR-06", "Study Groups", "Danh sách phòng, vào phòng, presence theo chu kỳ, chat lưu bền vững trong Room."],
            ["FR-07", "AI Skill Assistant", "Đề xuất bài luyện dựa trên task, level, điểm yếu, phong cách học và dữ liệu app."],
            ["FR-08", "Export", "Xuất PDF thật bằng PdfDocument và xuất CSV dữ liệu học tập."],
            ["FR-09", "Bảo mật", "BiometricPrompt, session lock, Keystore AES-GCM cho backup, không hard-code key/IV."],
        ],
        [1.7, 4.0, 10.3],
    )
    heading(doc, "2.2 Yêu cầu phi chức năng", 2)
    table(
        doc,
        ["Nhóm", "Tiêu chí"],
        [
            ["Bảo mật", "Không lưu plaintext password, salt riêng theo user, khóa AES nằm trong Android Keystore."],
            ["Khả dụng", "Các nút chính rõ ràng, text không tràn, layout ổn trên AVD Medium_Phone."],
            ["Tin cậy", "Có Gradle Wrapper, test pass và CI build APK tự động."],
            ["Hiệu năng", "Room Flow/StateFlow cập nhật UI; background task giao cho WorkManager."],
            ["Khả trì", "Tách data/util/ViewModel; chức năng rủi ro có test."],
        ],
        [4.0, 12.0],
    )
    heading(doc, "2.3 Use case chính", 2)
    for step in [
        "User đăng ký tài khoản, hệ thống tạo BCrypt hash và lưu vào Room.",
        "User đăng nhập, hệ thống verify BCrypt hoặc hash legacy rồi migrate sang BCrypt.",
        "User tạo mục tiêu học, bật reminder, WorkManager schedule notification đúng giờ.",
        "User vào phòng tập trung, chọn task, chạy timer và lưu FocusSession.",
        "User vào nhóm học, gửi chat, tin nhắn được lưu trong group_chat_messages theo roomId.",
        "User mở AI Skill Assistant, app tạo StudySkillRequest và nhận đề xuất từ Gemini hoặc fallback offline.",
        "User xuất báo cáo PDF, hệ thống tạo file .pdf thật bằng PdfDocument.",
    ]:
        number(doc, step)


def chapter_3(doc: Document) -> None:
    heading(doc, "Chương 3. Thiết Kế Chức Năng và Kiến Trúc Phần Mềm")
    heading(doc, "3.1 Kiến trúc MVVM", 2)
    body(
        doc,
        "Ứng dụng dùng Compose UI ở tầng giao diện, StudyViewModel ở tầng trạng thái/nghiệp vụ, "
        "StudyRepository ở tầng dữ liệu trung gian và Room DAO ở tầng lưu trữ. Các tiện ích độc lập như "
        "SecurityUtils, GeminiAssistant và StudyReminderWorker được tách khỏi UI để giảm phụ thuộc."
    )
    code_block(
        doc,
        "Compose UI -> StudyViewModel -> StudyRepository -> StudyDao -> Room Database\n"
        "           -> SecurityUtils / GeminiAssistant / StudyReminderWorker"
    )
    heading(doc, "3.2 Component chính", 2)
    table(
        doc,
        ["Component", "Vai trò"],
        [
            ["MainActivity", "Host Compose, xử lý BiometricPrompt, notification permission và điều hướng màn hình."],
            ["StudyViewModel", "Quản lý user state, tasks, focus timer, AI challenge, room state và session timeout."],
            ["StudyRepository", "Đóng gói truy cập Room, export CSV/PDF, map dữ liệu chat sang model UI."],
            ["StudyDao/StudyDatabase", "Truy vấn users, study_tasks, focus_sessions, reward_logs, group_chat_messages."],
            ["SecurityUtils", "BCrypt, verify hash legacy, Android Keystore và AES-GCM cho backup."],
            ["StudyReminderWorker", "Tạo notification đúng giờ bằng WorkManager và NotificationCompat."],
            ["GeminiAssistant", "Gọi Gemini nếu có key, fallback offline contextual nếu không có key."],
        ],
        [4.3, 11.7],
    )
    heading(doc, "3.3 Trạng thái module sau nâng cấp", 2)
    table(
        doc,
        ["Module", "Trạng thái"],
        [
            ["Auth/Profile", "Hoạt động, mật khẩu BCrypt, hỗ trợ biometric và session lock."],
            ["Dashboard", "Đã bỏ dữ liệu thống kê giả, tài khoản mới hiển thị empty state."],
            ["Focus", "Đã có nút bắt đầu/tạm dừng rõ ràng và ghi phiên học."],
            ["Planner", "Nút thêm mục tiêu gọn, task CRUD và reminder hoạt động."],
            ["Groups", "Giao diện phòng học thực tế hơn, chat local persistent."],
            ["AI Skill", "Cá nhân hóa theo dữ liệu học tập, không còn preset quá sơ sài."],
            ["Security/Export", "BCrypt, Keystore AES-GCM, BiometricPrompt và PDF thật."],
        ],
        [4.0, 12.0],
    )


def chapter_4(doc: Document) -> None:
    heading(doc, "Chương 4. Mô Hình Dữ Liệu và Xử Lý Nghiệp Vụ")
    heading(doc, "4.1 Mô hình dữ liệu Room", 2)
    table(
        doc,
        ["Entity", "Khóa chính", "Ý nghĩa"],
        [
            ["User", "username", "Thông tin tài khoản, displayName, email, phone, school, major, passwordHash BCrypt."],
            ["StudyTask", "id", "Mục tiêu học tập, category, targetMinutes, elapsedTimeSeconds, dueDate, reminder fields."],
            ["FocusSession", "id", "Lịch sử phiên focus/break/meditation, taskId, taskTitle, durationMinutes, timestamp."],
            ["RewardLog", "id", "Điểm XP và lý do nhận thưởng."],
            ["GroupChatEntity", "id", "Tin nhắn phòng học theo roomId, sender, message, timestamp."],
        ],
        [3.5, 3.0, 9.5],
    )
    heading(doc, "4.2 Reminder lifecycle", 2)
    for step in [
        "Người dùng tạo hoặc cập nhật StudyTask có bật smart reminder.",
        "ViewModel gọi StudyReminderWorker.schedule(context, task).",
        "Worker tạo OneTimeWorkRequest với initialDelay từ reminderHour/reminderMinute.",
        "WorkManager enqueueUniqueWork theo task id để tránh tạo trùng reminder.",
        "Đến giờ, Worker hiển thị NotificationCompat trên channel StudySpace Reminders.",
        "Khi task bị xóa hoặc tắt reminder, app cancel unique work tương ứng.",
    ]:
        number(doc, step)
    heading(doc, "4.3 Study Groups lifecycle", 2)
    body(
        doc,
        "Khi user join room, ViewModel cập nhật active room, subscribe Flow tin nhắn theo roomId và seed message nếu phòng chưa có dữ liệu. "
        "Tin nhắn user và auto message đều được ghi xuống Room, sau đó UI nhận lại qua StateFlow. Presence của thành viên được mô phỏng theo chu kỳ "
        "focus/review/break có thời lượng còn lại, giúp phòng học logic hơn thay vì chỉ là danh sách hard-code."
    )
    callout(
        doc,
        "Giới hạn hiện tại của Study Groups",
        "Chat nhóm hiện lưu bền vững trên thiết bị bằng Room, chưa phải realtime đa thiết bị. Đây là bước nâng cấp thật so với mô phỏng cũ, "
        "nhưng Firebase Firestore/Realtime Database vẫn là hướng phát triển nếu cần demo nhiều máy.",
        fill=LIGHT_GRAY,
    )


def chapter_5(doc: Document) -> None:
    heading(doc, "Chương 5. Các Chức Năng Đã Hoàn Thiện Trong Ứng Dụng")
    heading(doc, "5.1 Dashboard và thống kê tuần", 2)
    body(
        doc,
        "Biểu đồ hiệu suất tuần đã được sửa để lấy dữ liệu từ FocusSession thật. Với tài khoản mới, app hiển thị 0m và trạng thái chưa có dữ liệu, "
        "không còn các cột thống kê giả như bản cũ."
    )
    heading(doc, "5.2 Phòng tập trung", 2)
    body(
        doc,
        "Focus screen hiện có nút bắt đầu/tạm dừng rõ ràng dưới timer, trạng thái sẵn sàng/đang chạy, lựa chọn task liên kết, mode focus/break/meditation "
        "và panel âm thanh nền. Khi phiên hoàn tất, app ghi FocusSession, cập nhật tiến độ task và cộng XP."
    )
    image(doc, ROOT / "focus_new.png", "Hình 5.1 - Màn Phòng tập trung sau khi sửa nút bắt đầu và layout timer.")
    heading(doc, "5.3 Planner và Smart Reminder", 2)
    body(
        doc,
        "Planner có nút thêm mục tiêu gọn hơn, form tạo task có category, target duration, trạng thái hoàn thành và reminder. Reminder dùng WorkManager "
        "thay vì delay giả trong UI, có notification channel riêng và xử lý quyền POST_NOTIFICATIONS trên Android 13+."
    )
    heading(doc, "5.4 Study Groups thực tế hơn", 2)
    body(
        doc,
        "Study Groups đã bỏ block legacy hard-code, danh sách phòng có sức chứa, lịch học, tag, nhịp Pomodoro. Màn active room hiển thị thành viên online, "
        "phase còn lại, chat và dữ liệu học hôm nay của user. Nút rời phòng đã được sửa để không bị ép dọc trên Medium Phone."
    )
    image(doc, ROOT / "emulator_groups.png", "Hình 5.2 - Danh sách Study Groups sau nâng cấp.")
    image(doc, ROOT / "emulator_room_chat.png", "Hình 5.3 - Phòng học active có online presence và chat.")
    heading(doc, "5.5 Trợ lý Kỹ năng AI", 2)
    body(
        doc,
        "AI Skill Assistant không chỉ chọn môn và mục tiêu nữa. Request hiện gồm category, task đang mở, level, điểm yếu, phong cách học, time budget, deadline, "
        "weekly focus minutes, số task mở và số phiên focus. Output có diagnosis, micro plan, exercise, checklist tự chấm, độ khó và next action."
    )
    image(doc, ROOT / "emulator_ai_card2.png", "Hình 5.4 - AI card đọc dữ liệu học tập và task hiện tại.")
    image(doc, ROOT / "emulator_ai_result3.png", "Hình 5.5 - Kết quả AI fallback theo môn Lập trình, có checklist và bước tiếp theo.")
    heading(doc, "5.6 Export PDF thật", 2)
    body(
        doc,
        "Chức năng export PDF sử dụng android.graphics.pdf.PdfDocument trong Android SDK. File xuất ra là PDF thật, không còn là HTML/TXT đổi đuôi. "
        "Nội dung export gồm tổng quan, danh sách task và lịch sử phiên học."
    )


def chapter_6(doc: Document) -> None:
    heading(doc, "Chương 6. Thiết Kế Bảo Mật")
    heading(doc, "6.1 BCrypt thay SHA-256 salt cố định", 2)
    body(
        doc,
        "Bản cũ dùng SHA-256 với salt cố định nên không phù hợp khi trình bày bảo mật. Bản hiện tại dùng org.mindrot:jbcrypt:0.4 với BCrypt.gensalt(12). "
        "Salt được nhúng riêng trong từng hash, giúp giảm rủi ro rainbow table và làm chậm brute force. Hệ thống vẫn verify được hash legacy rồi rehash sang BCrypt."
    )
    heading(doc, "6.2 Android Keystore và AES-GCM", 2)
    body(
        doc,
        "SecurityUtils tạo khóa AES bằng Android Keystore System, dùng AES/GCM/NoPadding, IV random và prefix phiên bản KS1 cho dữ liệu backup. "
        "Khóa không hard-code trong source và không cần lưu plaintext key/IV."
    )
    heading(doc, "6.3 BiometricPrompt thật", 2)
    body(
        doc,
        "MainActivity dùng androidx.biometric.BiometricPrompt với BIOMETRIC_STRONG hoặc DEVICE_CREDENTIAL. Người dùng có thể xác thực bằng sinh trắc học "
        "hoặc fallback PIN/password hệ thống. Đây là API Android chuẩn, không phải nút mô phỏng."
    )
    heading(doc, "6.4 Session management", 2)
    body(
        doc,
        "ViewModel ghi nhận thời điểm app vào background và khóa lại nếu inactive quá 5 phút trong trường hợp bật 2FA hoặc biometric. Luồng này giảm rủi ro "
        "người khác mở lại app khi user rời thiết bị."
    )
    table(
        doc,
        ["Rủi ro", "Giải pháp đã làm", "Trạng thái"],
        [
            ["Lộ mật khẩu", "BCrypt random salt, verify và rehash legacy hash", "Hoàn tất"],
            ["Hard-code key", "Android Keystore quản lý AES key", "Hoàn tất cho backup"],
            ["Sửa file backup", "AES-GCM có authentication tag", "Hoạt động"],
            ["Mở app sau khi user rời máy", "Session lock + biometric/2FA", "Hoạt động"],
            ["Trích xuất Room DB", "Chưa tích hợp SQLCipher", "Hướng phát triển"],
        ],
        [3.4, 8.0, 4.6],
    )


def chapter_7(doc: Document) -> None:
    heading(doc, "Chương 7. Thiết Kế Giao Diện và Trải Nghiệm Người Dùng")
    body(
        doc,
        "Giao diện sử dụng Compose và Material 3 với tông xanh/cyan, điểm nhấn tím. Sau khi sửa, các lỗi nhìn thấy ngay đã được xử lý: "
        "button lịch học không còn quá to, dashboard tài khoản mới không hiện thống kê giả, focus room có nút bắt đầu rõ, Study Groups không còn nút rời phòng bị ép dọc, "
        "AI card có nhiều ngữ cảnh hơn và output hữu ích hơn."
    )
    table(
        doc,
        ["Màn hình", "Cải tiến chính", "Kết quả UX"],
        [
            ["Dashboard", "Empty state cho chart tuần", "Dữ liệu mới phản ánh đúng 0 phút học."],
            ["Focus", "Primary action rõ, timer card, soundscape gọn", "User biết bấm đâu để bắt đầu."],
            ["Planner", "Button thêm gọn, chips ổn định", "Không vỡ layout trên Medium Phone."],
            ["Groups", "Room cards, active room, chat, member status", "Cảm giác phòng học thật hơn."],
            ["AI", "Thêm level, weakness, style, time budget, deadline", "Đề xuất bớt chung chung."],
        ],
        [3.0, 7.0, 6.0],
    )


def chapter_8(doc: Document) -> None:
    heading(doc, "Chương 8. Kiểm Thử, CI/CD và Nghiệm Thu")
    heading(doc, "8.1 Test tự động", 2)
    table(
        doc,
        ["Test", "Mục tiêu", "Kết quả"],
        [
            ["ExampleRobolectricTest", "Kiểm app name StudySpace", "Pass"],
            ["SecurityUtilsTest", "BCrypt random salt, verify password, rehash", "Pass"],
            ["StudyDatabaseRobolectricTest", "Insert/read User, Task, Session, Reward", "Pass"],
            ["StudyDatabaseRobolectricTest - group chat", "Insert/query group_chat_messages theo roomId", "Pass"],
        ],
        [4.7, 8.2, 3.1],
    )
    heading(doc, "8.2 Build và CI/CD", 2)
    body(
        doc,
        "Repo đã khôi phục Gradle Wrapper gồm gradlew, gradlew.bat và gradle/wrapper. GitHub Actions android.yml chạy testDebugUnitTest, assembleDebug "
        "và upload APK artifact. Nhờ đó người chấm có thể build từ terminal hoặc CI thay vì phụ thuộc Android Studio GUI."
    )
    code_block(
        doc,
        ".\\gradlew.bat testDebugUnitTest assembleDebug\n"
        "adb install -r app/build/outputs/apk/debug/app-debug.apk\n"
        "adb shell am start -n com.aistudio.studyspace.vuxkwa/com.example.MainActivity"
    )
    heading(doc, "8.3 Nghiệm thu thủ công", 2)
    table(
        doc,
        ["Mã", "Luồng kiểm thử", "Kỳ vọng", "Kết quả"],
        [
            ["TC-01", "Đăng ký tài khoản mới", "Tạo user, lưu BCrypt hash, vào dashboard", "Pass"],
            ["TC-02", "Dashboard account mới", "Biểu đồ tuần hiển thị 0m và chưa có dữ liệu", "Pass"],
            ["TC-03", "Planner tạo mục tiêu", "Task mới xuất hiện và có thể đặt reminder", "Pass"],
            ["TC-04", "Focus start/pause", "Có nút bắt đầu rõ, timer chạy và cập nhật state", "Pass"],
            ["TC-05", "Study Groups join room", "Active room đúng, nút rời phòng không vỡ layout", "Pass"],
            ["TC-06", "AI fallback", "Sinh bài luyện đúng môn Lập trình, có checklist", "Pass"],
            ["TC-07", "Build terminal", "Gradle wrapper build/test thành công", "Pass"],
        ],
        [1.5, 4.0, 8.3, 2.2],
    )
    callout(
        doc,
        "Kết quả chạy app",
        "Ứng dụng đã được cài và mở trên AVD Medium_Phone. Logcat sau các luồng kiểm thử chính không ghi nhận FATAL EXCEPTION hoặc AndroidRuntime crash.",
        fill=LIGHT_BLUE,
    )


def chapter_9(doc: Document) -> None:
    heading(doc, "Chương 9. Đánh Giá, Hạn Chế và Hướng Phát Triển")
    heading(doc, "9.1 Kết quả đạt được", 2)
    for item in [
        "App build được bằng Gradle Wrapper và có test tự động cho bảo mật, Room DB, app name.",
        "Các lỗi UI dễ thấy đã sửa: focus button, planner button, chart giả, nút rời phòng.",
        "Các tính năng demo trọng tâm thực tế hơn: WorkManager reminder, PDF thật, Study Groups lưu chat Room, AI contextual fallback.",
        "Bảo mật mật khẩu và backup đã nâng lên BCrypt + Keystore AES-GCM.",
    ]:
        bullet(doc, item)
    heading(doc, "9.2 Hạn chế trung thực", 2)
    for item in [
        "Study Groups chưa realtime đa thiết bị; hiện là local persistent chat.",
        "Cloud sync vẫn cần Firebase/Firestore nếu muốn đồng bộ production.",
        "OTP 2FA hiện là in-app notification; chưa gửi email/SMS thật.",
        "Room database chưa mã hóa toàn bộ bằng SQLCipher.",
        "Certificate pinning cho Gemini/Firebase chưa triển khai.",
    ]:
        bullet(doc, item)
    heading(doc, "9.3 Hướng phát triển", 2)
    table(
        doc,
        ["Ưu tiên", "Hạng mục", "Lý do"],
        [
            ["1", "Firebase Firestore/Realtime Database cho Study Groups", "Tạo chat realtime đa thiết bị, nhìn thấy ngay khi demo."],
            ["2", "SQLCipher cho Room", "Hoàn thiện lớp bảo vệ dữ liệu local ở mức database."],
            ["3", "Firebase Auth/SMTP OTP", "Biến 2FA từ notification nội bộ thành OTP gửi thật."],
            ["4", "Certificate pinning", "Tăng khả năng chống MITM cho API call."],
            ["5", "Compose UI test", "Kiểm thử hồi quy luồng đăng ký -> focus -> planner -> export."],
        ],
        [2.0, 6.3, 7.7],
    )


def appendix(doc: Document) -> None:
    heading(doc, "Phụ Lục. Minh Chứng Kỹ Thuật và Tài Liệu Tham Khảo")
    heading(doc, "A. Cấu trúc dữ liệu Room", 2)
    code_block(
        doc,
        "users(username PK, displayName, passwordHash, email, phoneNumber, school, major, createdAt)\n"
        "study_tasks(id PK, userId, title, category, targetMinutes, elapsedTimeSeconds, dueDate, reminderHour, reminderMinute)\n"
        "focus_sessions(id PK, userId, taskId, taskTitle, durationMinutes, sessionType, timestamp)\n"
        "reward_logs(id PK, userId, pointsEarned, reason, timestamp)\n"
        "group_chat_messages(id PK, roomId, sender, message, timestamp)"
    )
    heading(doc, "B. Dữ liệu đầu vào AI", 2)
    body(
        doc,
        "StudySkillRequest truyền cho Gemini hoặc fallback gồm category, goal, level, weakness, learningStyle, "
        "timeBudgetMinutes, deadline, weeklyFocusMinutes, openTaskCount, completedTaskCount, focusSessionCount và recentTaskTitles."
    )
    code_block(
        doc,
        "{\n"
        '  "title": "Nhiệm vụ cá nhân hóa",\n'
        '  "diagnosis": "Chẩn đoán theo dữ liệu học tập",\n'
        '  "microPlan": ["0-5p...", "5-20p...", "20-25p..."],\n'
        '  "exercise": "Bài tập thực hành cụ thể",\n'
        '  "answerChecklist": ["Có sản phẩm", "Ghi lỗi", "Nêu bước tiếp theo"],\n'
        '  "estimatedMinutes": 25,\n'
        '  "difficulty": "Vừa sức",\n'
        '  "nextAction": "Chạy một phiên Focus"\n'
        "}"
    )
    heading(doc, "C. Tài liệu tham khảo", 2)
    refs = [
        "Android Developers. WorkManager documentation.",
        "Android Developers. Biometric authentication and BiometricPrompt.",
        "Android Developers. Android Keystore system.",
        "Android Developers. PdfDocument API.",
        "Jetpack Compose and Material Design 3 documentation.",
        "Provos, N. and Mazieres, D. A Future-Adaptable Password Scheme: BCrypt.",
        "OWASP Mobile Application Security Verification Standard (MASVS).",
        "Google AI Gemini API documentation.",
    ]
    for ref in refs:
        bullet(doc, ref)


def write_report(doc: Document) -> None:
    cover(doc)
    toc(doc)
    write_intro(doc)
    doc.add_page_break()
    chapter_1(doc)
    doc.add_page_break()
    chapter_2(doc)
    doc.add_page_break()
    chapter_3(doc)
    doc.add_page_break()
    chapter_4(doc)
    doc.add_page_break()
    chapter_5(doc)
    doc.add_page_break()
    chapter_6(doc)
    doc.add_page_break()
    chapter_7(doc)
    doc.add_page_break()
    chapter_8(doc)
    doc.add_page_break()
    chapter_9(doc)
    doc.add_page_break()
    appendix(doc)


def main() -> None:
    doc = Document()
    configure_document(doc)
    write_report(doc)
    doc.core_properties.title = "Báo cáo StudySpace hoàn thiện"
    doc.core_properties.subject = "Phân tích và thiết kế phần mềm"
    doc.core_properties.author = "Nhóm StudySpace Phenikaa"
    doc.core_properties.comments = "Generated after StudySpace app upgrade and verification."
    doc.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()
