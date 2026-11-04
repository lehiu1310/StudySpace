from pathlib import Path
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.enum.shapes import MSO_SHAPE
from pptx.util import Inches, Pt

ROOT=Path(__file__).resolve().parent
SHOTS=ROOT/'docs/submission/screenshots'
OUT=ROOT/'StudySpace_BaoCao_DoAn.pptx'
r=Presentation(); r.slide_width=Inches(13.333); r.slide_height=Inches(7.5)
navy=RGBColor(25,35,63); indigo=RGBColor(79,70,229); teal=RGBColor(20,184,166)
paper=RGBColor(245,247,255); ink=RGBColor(35,45,68); muted=RGBColor(103,117,145); white=RGBColor(255,255,255)
def box(s,x,y,w,h,c,round=False):
 sh=s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if round else MSO_SHAPE.RECTANGLE, Inches(x), Inches(y), Inches(w), Inches(h)); sh.fill.solid(); sh.fill.fore_color.rgb=c; sh.line.fill.background(); return sh
def txt(s,t,x,y,w,h,size=18,c=ink,b=False,align=None):
 sh=s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h)); f=sh.text_frame; f.clear(); f.word_wrap=True; f.margin_left=Inches(.03); f.margin_right=Inches(.03); f.margin_top=Inches(.02); p=f.paragraphs[0]; p.text=t; p.font.name='Aptos'; p.font.size=Pt(size); p.font.bold=b; p.font.color.rgb=c
 if align is not None:p.alignment=align
 return sh
def slide(title,kicker='STUDYSPACE'):
 s=r.slides.add_slide(r.slide_layouts[6]); s.background.fill.solid(); s.background.fill.fore_color.rgb=paper; box(s,0,0,13.333,.1,indigo); txt(s,kicker,.65,.42,6,.22,10,teal,True); txt(s,title,.65,.78,12,.62,28,navy,True); box(s,.65,1.48,.65,.05,teal); txt(s,'BÁO CÁO ĐỒ ÁN  •  STUDYSPACE',.65,7.12,7,.2,9,muted); txt(s,f'{len(r.slides):02d}',12.25,7.1,.4,.2,9,muted,False,PP_ALIGN.RIGHT); return s
def listtext(s,items,x=.9,y=1.95,w=5.6,size=18,step=.88):
 for i,t in enumerate(items):
  yy=y+i*step; box(s,x,yy+.1,.13,.13,teal,True); txt(s,t,x+.3,yy,w-.3,step-.05,size,ink)
def pic(s,name,x,y,w,h,cap):
 p=SHOTS/name
 if p.exists(): s.shapes.add_picture(str(p), Inches(x), Inches(y), width=Inches(w), height=Inches(h)); txt(s,cap,x,y+h+.06,w,.28,10,muted)
# Cover
s=r.slides.add_slide(r.slide_layouts[6]); s.background.fill.solid(); s.background.fill.fore_color.rgb=navy; box(s,0,0,.2,7.5,teal); txt(s,'STUDYSPACE',.9,1.05,6,.4,16,teal,True); txt(s,'Ứng dụng hỗ trợ học tập\ntrực tuyến',.9,1.75,10,1.45,34,white,True); txt(s,'Quản lý nội dung  •  Quiz  •  Theo dõi tiến độ  •  Báo cáo',.95,3.55,10,.45,17,RGBColor(215,222,241)); box(s,.95,4.55,4.5,.05,indigo); txt(s,'BÁO CÁO ĐỒ ÁN',.95,4.9,4,.3,12,white,True); txt(s,'Nguyễn Hữu Lê Hiếu  |  Phạm Văn Huấn\nGVHD: ThS. Vũ Thị Ngọc Anh',.95,5.4,7,.7,14,RGBColor(215,222,241))
# Problem
s=slide('Bài toán và mục tiêu','01 • ĐẶT VẤN ĐỀ'); listtext(s,['Học liệu, bài tập và tiến độ thường bị phân tán.','Người học khó nhận diện phần kiến thức cần ôn.','Giảng viên cần công cụ quản lý nội dung, lớp và kết quả.','StudySpace nối các hoạt động này thành một quy trình học.'],step=.9)
box(s,7.2,2,5.2,3.7,white,True); txt(s,'MỤC TIÊU',7.6,2.4,4,.3,12,indigo,True); txt(s,'Học đúng nội dung.\nBiết rõ tiến độ.',7.6,3,4.3,1.1,27,navy,True); txt(s,'Dữ liệu học tập giúp học viên và giảng viên chọn bước tiếp theo.',7.6,4.55,4.2,.8,16,muted)
# Roles
s=slide('Một nền tảng, ba vai trò','02 • SẢN PHẨM')
for i,(a,b,c) in enumerate([('HỌC VIÊN','Học bài • Quiz\nKế hoạch • Tiến độ',indigo),('GIẢNG VIÊN','Khóa học • Lớp\nQuiz • Báo cáo',teal),('QUẢN TRỊ','Tài khoản • Vai trò\nNội dung • Thống kê',RGBColor(225,157,51))]):
 x=.8+i*4.2; box(s,x,2.1,3.7,2.6,white,True); box(s,x,2.1,3.7,.12,c); txt(s,a,x+.3,2.55,3,.3,13,c,True); txt(s,b,x+.3,3.15,3.1,1,21,navy,True)
txt(s,'Web app là giao diện chính; Android Kotlin/Compose là ứng dụng bổ sung.',.9,5.4,11,.45,16,muted)
# Architecture
s=slide('Kiến trúc hệ thống','03 • THIẾT KẾ')
for i,(a,b) in enumerate([('Web + Android','Giao diện theo vai trò'),('Spring Boot REST API','Xác thực • nghiệp vụ • báo cáo'),('PostgreSQL + Room','Dữ liệu máy chủ + cục bộ')]):
 y=1.95+i*1.35; box(s,.9,y,7.4,.95,white,True); txt(s,a,1.2,y+.18,3.4,.35,18,indigo,True); txt(s,b,4.6,y+.22,3.3,.3,14,muted)
pic(s,'admin-chart-report-desktop.png',9,1.9,3.5,3,'Báo cáo quản trị')
# Teacher
s=slide('Giảng viên tạo nội dung và giao cho lớp','04 • LUỒNG GIẢNG VIÊN'); listtext(s,['Tạo khóa học, chương, bài và tài liệu.','Soạn câu hỏi có đáp án, mức độ, giải thích.','Xuất bản khóa đủ nội dung; tạo lớp và mã tham gia.','Theo dõi hoạt động, câu sai nhiều và tiến độ lớp.'],w=5.7,size=17,step=.85); pic(s,'teacher-class-report-desktop.png',7,1.85,5.5,3.5,'Báo cáo lớp')
# Student
s=slide('Học viên học, tự đánh giá và theo dõi','05 • LUỒNG HỌC VIÊN'); listtext(s,['Chọn khóa đã xuất bản và học theo chương.','Đánh dấu bài đã học; tiếp tục lộ trình.','Làm quiz, xem điểm, câu sai và giải thích.','Xem báo cáo cá nhân và gợi ý ôn tập.'],w=5.5,size=17,step=.9); pic(s,'student-overview-ai.png',7,1.85,5.25,3.6,'Tổng quan học viên')
# Quiz
s=slide('Quiz đóng vòng phản hồi học tập','06 • ĐÁNH GIÁ'); pic(s,'student-quiz-active.png',.8,1.85,4.4,3.65,'Đang làm quiz'); pic(s,'student-quiz-result.png',5.15,1.85,4.4,3.65,'Kết quả và giải thích'); box(s,9.85,2,2.5,3.2,navy,True); txt(s,'LÀM QUIZ\n↓\nXEM KẾT QUẢ\n↓\nÔN CÂU SAI',10.15,2.45,1.9,2.2,17,white,True,PP_ALIGN.CENTER)
# Data reports
s=slide('Báo cáo dựa trên dữ liệu quan hệ','07 • DỮ LIỆU'); listtext(s,['PostgreSQL quản lý tài khoản, nội dung, quiz và tiến độ.','Dashboard tổng hợp theo vai trò; có trực quan hóa dữ liệu.','Đối chiếu hồ sơ: 18 bảng, 102 cột, 26 khóa ngoại.'],w=5.7,size=18,step=1.0); pic(s,'student-progress-report-desktop.png',7,1.9,5.2,3.6,'Báo cáo tiến độ học viên')
# AI
s=slide('AI hỗ trợ theo ngữ cảnh và có phương án offline','08 • AI'); box(s,.9,2,5.5,3.1,white,True); txt(s,'AI TRỰC TIẾP',1.25,2.4,4.7,.3,13,indigo,True); txt(s,'Chat theo bài học\nGiải thích câu sai\nHỗ trợ soạn quiz',1.25,2.95,4.7,1.5,20,navy,True); box(s,6.85,2,5.5,3.1,white,True); txt(s,'GỢI Ý OFFLINE',7.2,2.4,4.7,.3,13,teal,True); txt(s,'Khi AI/mạng không khả dụng, gợi ý ôn tập vẫn dựa trên dữ liệu học đã lưu.',7.2,2.95,4.6,1.25,19,navy,True); txt(s,'Trước demo cần kiểm tra trạng thái Gemini trên môi trường đang chạy.',.95,5.65,11,.35,13,muted)
# Conclusion
s=slide('Kết quả, giới hạn và hướng phát triển','09 • KẾT LUẬN'); listtext(s,['Đã kết nối: quản lý nội dung → học → quiz → báo cáo.','Hồ sơ ghi nhận: Android 56/56 unit test; backend 50/50.','Giới hạn: thảo luận polling; chưa xác nhận trực quan Android trên thiết bị.','Tiếp theo: kiểm thử thiết bị, HTTPS, đồng bộ và spaced repetition.'],w=11.5,size=17,step=.82); txt(s,'Em xin cảm ơn cô và các bạn!',1,6.15,11,.4,22,indigo,True,PP_ALIGN.CENTER)
r.save(OUT); print(OUT)
