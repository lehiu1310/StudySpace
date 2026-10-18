'use strict';
let token='',content={},classes=[];
const $=id=>document.getElementById(id);
let statusTimer;
const status=(message,kind='success')=>{$('status').replaceChildren(document.createTextNode(message||''));$('status').classList.toggle('is-error',Boolean(message)&&kind==='error');clearTimeout(statusTimer);if(message&&kind!=='error')statusTimer=setTimeout(()=>{if($('status').textContent===message)status('');},3600);};
function escapeHtml(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
async function api(path,method='GET',body){
 let response;try{response=await fetch('/api/'+path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(12000)});}catch(error){if(error.name==='TimeoutError'||error.name==='AbortError')throw Error('Máy chủ phản hồi chậm. Hãy kiểm tra kết nối và thử lại.');if(error.name==='TypeError')throw Error('Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.');throw error;}
 const text=await response.text();let data;try{data=text?JSON.parse(text):{};}catch{throw Error('Máy chủ trả dữ liệu không hợp lệ.');}
 if(!response.ok){if(response.status===401){token='';sessionStorage.removeItem('studyspace_teacher_session');$('workspace').hidden=true;$('login').hidden=false;}throw Error(data.error||'Không thể thực hiện thao tác.');}return data;
}
async function run(action){status('');try{await action();}catch(e){status(e.message,'error');}}
async function uploadFile(file,chapterId){
 const form=new FormData();form.append('file',file);form.append('chapterId',String(chapterId));
 const response=await fetch('/api/teacher/content/materials',{method:'POST',headers:{Authorization:'Bearer '+token},body:form});
 const text=await response.text();let data;try{data=text?JSON.parse(text):{};}catch{throw Error('Máy chủ trả dữ liệu tải lên không hợp lệ.');}
 if(!response.ok)throw Error(data.error||'Không thể tải tài liệu lên.');return data;
}
function row(label,actions=[]){const el=document.createElement('div');el.className='row';const text=document.createElement('span');text.textContent=label;el.append(text);for(const [name,fn]of actions){const button=document.createElement('button');button.textContent=name;button.onclick=()=>run(fn);el.append(button);}return el;}
const editorTitles={'course-form':['course-editor-title','Thêm khóa học','Sửa thông tin khóa học'],'chapter-form':['chapter-editor-title','Thêm chương học','Sửa thông tin chương'],'lesson-form':['lesson-editor-title','Thêm bài học','Sửa nội dung bài học'],'question-form':['question-editor-title','Thêm câu hỏi','Sửa câu hỏi']};
function openEditorDrawer(editorId){const editor=$(editorId);if(!editor)return;for(const other of document.querySelectorAll('.editor-panel:not(.bulk-quiz-panel)'))if(other!==editor)other.open=false;const form=editor.querySelector('form');if(form){form.reset();form.elements.id.value='';const pair=Object.entries(editorTitles).find(([,values])=>values[0]===form.id);if(pair)$(pair[1][0]).textContent=pair[1][1];}editor.open=true;}
function fill(formId,values){const form=$(formId);for(const [key,value]of Object.entries(values)){const input=form.elements.namedItem(key);if(input){if(input.type==='checkbox')input.checked=!!value;else if(input.type!=='file')input.value=value??'';}}const editor=form.closest('details');if(editor){for(const other of document.querySelectorAll('.editor-panel:not(.bulk-quiz-panel)'))if(other!==editor)other.open=false;editor.open=true;const [titleId,newTitle,editTitle]=editorTitles[formId];$(titleId).textContent=values.id?editTitle:newTitle;}}
function renderList(target,items,emptyTitle,emptyDescription,action){const list=$(target);if(items.length){list.replaceChildren(...items);return;}const state=document.createElement('div');state.className='empty-state';const icon=document.createElement('span');icon.className='empty-symbol';icon.textContent='✦';const copy=document.createElement('div');copy.className='empty-copy';const title=document.createElement('b');title.textContent=emptyTitle;const description=document.createElement('p');description.textContent=emptyDescription;copy.append(title,description);state.append(icon,copy);if(action){const button=document.createElement('button');button.type='button';button.className='empty-action';button.textContent=action.label;button.onclick=()=>openEditorDrawer(action.editor);state.append(button);}list.replaceChildren(state);}
function options(select,data,label,old){select.replaceChildren(...data.map(item=>{const option=document.createElement('option');option.value=item.id;option.textContent=label(item);return option;}));if(data.some(item=>String(item.id)===old))select.value=old;}
function nextPosition(items,parentId,parentKey){return items.filter(item=>Number(item[parentKey])===Number(parentId)).reduce((max,item)=>Math.max(max,Number(item.position)||0),0)+1;}
function courseCard(course){
 const card=document.createElement('article');card.className='teacher-course-card';
 const chapters=content.chapters.filter(ch=>Number(ch.course_id)===Number(course.id)),chapterIds=new Set(chapters.map(ch=>Number(ch.id)));
 const lessons=content.lessons.filter(lesson=>chapterIds.has(Number(lesson.chapter_id)));
 const students=classes.filter(item=>Number(item.course_id)===Number(course.id)).reduce((sum,item)=>sum+(Number(item.student_count)||0),0);
 card.innerHTML=`<div class="teacher-course-main"><b class="teacher-course-title">${escapeHtml(course.title)}</b><div class="teacher-course-subline"><span class="teacher-status-chip ${course.published?'':'is-draft'}">${course.published?'Đã xuất bản':'Nháp'}</span><span>${chapters.length} chương · ${lessons.length} bài · ${students} học viên</span></div></div><details class="teacher-course-menu"><summary aria-label="Thao tác với ${escapeHtml(course.title)}" title="Thao tác">⋯</summary><div><button type="button" data-course-edit="${course.id}">Sửa</button><button type="button" data-course-hide="${course.id}">${course.published?'Ẩn khóa':'Xuất bản'}</button></div></details>`;
 card.querySelector('[data-course-edit]').onclick=()=>fill('course-form',course);
 card.querySelector('[data-course-hide]').onclick=()=>run(async()=>{const next=!course.published;if(!confirm(`${next?'Xuất bản':'Ẩn'} khóa học “${course.title}”?`))return;await api(`teacher/content/courses/${course.id}`,'PUT',{title:course.title,description:course.description,published:next});await reload();status(next?'Đã xuất bản khóa học.':'Đã ẩn khóa học khỏi học viên.');});
 card.querySelector('summary').onclick=event=>{event.preventDefault();const menu=card.querySelector('.teacher-course-menu');menu.open=!menu.open;document.querySelectorAll('.teacher-course-menu[open]').forEach(other=>{if(other!==menu)other.open=false;});};
 card.querySelectorAll('.teacher-course-menu button').forEach(button=>button.addEventListener('click',()=>{card.querySelector('.teacher-course-menu').open=false;}));
 return card;
}
function entityCard(title,meta,chip,edit){const card=document.createElement('article');card.className='teacher-entity-card';card.innerHTML=`<div class="teacher-entity-main"><b class="teacher-entity-title">${escapeHtml(title)}</b><span class="teacher-entity-meta">${escapeHtml(meta)}</span></div><span class="teacher-entity-count">${escapeHtml(chip)}</span><button type="button" class="secondary-action entity-edit">Sửa</button>`;card.querySelector('button').onclick=edit;return card;}
function renderAttention(reports){
 const studentHost=$('attention-students'),questionHost=$('attention-questions'),classHost=$('attention-classes'),flagged=[],questions=new Map();
 for(const report of reports){if(!report)continue;for(const student of report.students||[]){const stale=!student.last_activity||Date.now()-Number(student.last_activity)>14*86400000,low=student.average_score!=null&&Number(student.average_score)<60,noQuiz=Number(student.quiz_attempts)===0;if(low||stale||noQuiz)flagged.push({name:student.display_name||student.username,reason:stale?'Lâu không hoạt động':noQuiz?'Chưa làm quiz':`Điểm TB ${Math.round(Number(student.average_score))}%`,score:student.average_score==null?null:Math.round(Number(student.average_score))});}for(const question of report.questions||[]){const key=String(question.question_id??question.prompt),current=questions.get(key)||{prompt:question.prompt,attempts:0,wrong:0};current.attempts+=Number(question.attempts)||0;current.wrong+=Number(question.wrong)||0;questions.set(key,current);}}
 const unique=[...new Map(flagged.map(item=>[item.name,item])).values()].sort((a,b)=>(a.score??-1)-(b.score??-1)).slice(0,5);
 studentHost.innerHTML=unique.length?unique.map(item=>`<div class="attention-row"><b>${escapeHtml(item.name)}</b><small>${escapeHtml(item.reason)}</small>${item.score===null?'':`<span class="attention-pill">${item.score}%</span>`}</div>`).join(''):'<p>Chưa có học viên cần hỗ trợ.</p>';
 const top=[...questions.values()].map(item=>({...item,percent:item.attempts?Math.round(100*item.wrong/item.attempts):0})).sort((a,b)=>b.wrong-a.wrong||b.percent-a.percent).slice(0,5);
 questionHost.innerHTML=top.length?top.map(item=>`<div class="attention-row"><b>${escapeHtml(item.prompt)}</b><small>${item.wrong}/${item.attempts} lượt sai</small><span class="attention-pill">${item.percent}%</span></div>`).join(''):'<p>Chưa có dữ liệu quiz của lớp.</p>';
 classHost.innerHTML=classes.length?classes.map(item=>`<div class="attention-row"><b>${escapeHtml(item.name)}</b><small>${escapeHtml(item.course_title)} · ${Number(item.student_count)||0} học viên</small><button type="button" data-copy-class="${escapeHtml(item.join_code)}" aria-label="Sao chép mã lớp ${escapeHtml(item.join_code)}">${escapeHtml(item.join_code)}</button></div>`).join(''):'<p>Chưa có lớp đang phụ trách.</p>';
 classHost.querySelectorAll('[data-copy-class]').forEach(button=>button.onclick=()=>run(async()=>{await navigator.clipboard.writeText(button.dataset.copyClass);status('Đã sao chép mã lớp.');}));
}
function continueIn(sectionId,editorId){location.hash=sectionId;syncTeacherSection();openEditorDrawer(editorId);}
async function reload(){
 const loadingHosts=['course-list','chapter-list','lesson-list','question-list','class-list'];
 loadingHosts.forEach(id=>{const host=$(id);if(host)host.innerHTML=`<div class="teacher-skeleton" aria-label="Đang tải dữ liệu"><i></i><i></i><i></i></div>`;});
 try{[content,classes]=await Promise.all([api('teacher/content'),api('teacher/classes')]);}
 catch(error){loadingHosts.forEach(id=>{const host=$(id);if(host){host.innerHTML=`<div class="teacher-load-error" role="alert"><b>Chưa tải được dữ liệu</b><span>${escapeHtml(error.message)}</span><button type="button" data-teacher-retry>Thử lại</button></div>`;host.querySelector('[data-teacher-retry]').onclick=()=>run(reload);}});throw error;}
 $('stat-courses').textContent=content.courses.length;
 $('stat-published').textContent=content.courses.filter(c=>c.published).length;
 $('stat-lessons').textContent=content.lessons.length;
 $('stat-questions').textContent=content.questions.filter(q=>q.active).length;
 const drafts=content.courses.filter(course=>!course.published).length;
 $('overview-readiness-title').textContent=drafts?`${drafts} khóa học đang ở bản nháp`:'Nội dung giảng dạy đã được cập nhật';
 $('overview-readiness-copy').textContent=`${classes.length} lớp đang quản lý · ${content.chapters.length} chương · ${content.lessons.length} bài học · ${content.questions.filter(q=>q.active).length} câu quiz.`;
 $('course-count').textContent=content.courses.length+' môn';$('chapter-count').textContent=content.chapters.length+' chương';$('lesson-count').textContent=content.lessons.length+' bài';$('question-count').textContent=content.questions.filter(q=>q.active).length+' câu';
 const courseSelect=document.querySelector('#chapter-form select[name=courseId]');
 const classCourseSelect=document.querySelector('#class-form select[name=courseId]');
 const chapterSelects=document.querySelectorAll('select[name=chapterId]');
 const oldCourse=courseSelect.value,oldChapters=[...chapterSelects].map(s=>s.value);
 options(courseSelect,content.courses,c=>c.title,oldCourse);
 options(classCourseSelect,content.courses.filter(c=>c.published),c=>c.title,classCourseSelect.value);
 options($('announcement-form').elements.classId,classes,c=>`${c.name} · ${c.course_title}`, $('announcement-form').elements.classId.value);
 const aiChapter=$('ai-chapter-select'),oldAiChapter=aiChapter.value;
 options(aiChapter,content.chapters,c=>(content.courses.find(x=>x.id===c.course_id)?.title||'Môn học')+' · '+c.title,oldAiChapter);
 options(chapterSelects[0],content.chapters,c=>content.courses.find(x=>x.id===c.course_id)?.title+' · '+c.title,oldChapters[0]);
 options(chapterSelects[1],content.chapters,c=>content.courses.find(x=>x.id===c.course_id)?.title+' · '+c.title,oldChapters[1]);
 renderList('course-list',content.courses.map(courseCard),'Chưa có khóa học','Tạo môn học đầu tiên để bắt đầu xây dựng lộ trình.',{editor:'course-editor',label:'Tạo khóa học'});
 renderList('chapter-list',content.chapters.map(c=>{const course=content.courses.find(x=>Number(x.id)===Number(c.course_id)),count=content.lessons.filter(l=>Number(l.chapter_id)===Number(c.id)).length;return entityCard(c.title,`Thuộc khóa ${course?.title||'chưa rõ'}`,`${count} bài`,()=>fill('chapter-form',{...c,courseId:c.course_id}));}),'Chưa có chương học','Thêm chương vào một khóa học để sắp xếp nội dung.',{editor:content.courses.length?'chapter-editor':'course-editor',label:content.courses.length?'Thêm chương học':'Tạo khóa học trước'});
 renderList('lesson-list',content.lessons.map(l=>{const chapter=content.chapters.find(ch=>Number(ch.id)===Number(l.chapter_id)),course=content.courses.find(c=>Number(c.id)===Number(chapter?.course_id)),url=String(l.material_url||''),type=/\.pdf(?:$|[?#])/i.test(url)?'PDF':url?'Link':'Bài đọc';return entityCard(l.title,`${course?.title||'Khóa học'} · ${chapter?.title||'Chương học'}`,type,()=>fill('lesson-form',{...l,chapterId:l.chapter_id,materialUrl:l.material_url}));}),'Chưa có bài học','Bài học có thể bao gồm nội dung, tài liệu hoặc đường dẫn tham khảo.',{editor:content.chapters.length?'lesson-editor':'chapter-editor',label:content.chapters.length?'Thêm bài học':'Tạo chương trước'});
 const activeQuestions=content.questions.filter(q=>q.active);renderList('question-list',activeQuestions.map(q=>row(q.prompt+' · '+['Dễ','Trung bình','Khó'][q.difficulty-1],[['Sửa',()=>fill('question-form',{...q,chapterId:q.chapter_id,a:q.option_a,b:q.option_b,c:q.option_c,d:q.option_d,correctOption:q.correct_option})]])),'Ngân hàng câu hỏi còn trống','Thêm câu trắc nghiệm để học viên tự kiểm tra kiến thức.',{editor:content.chapters.length?'question-editor':'chapter-editor',label:content.chapters.length?'Tạo câu hỏi':'Tạo chương trước'});
 const reports=await Promise.all(classes.map(item=>api(`teacher/classes/${item.id}/report`).catch(()=>null)));
 renderAttention(reports);
 renderClasses();
}
function renderClasses(){
 const list=$('class-list');
 if(!classes.length){list.innerHTML='<div class="empty-state"><span class="empty-symbol">✦</span><div class="empty-copy"><b>Chưa có lớp học</b><p>Chọn khóa học đã xuất bản để tạo lớp và gửi mã tham gia cho học viên.</p></div></div>';return;}
 list.innerHTML=classes.map(c=>`<article class="teacher-class-row"><div class="teacher-class-copy"><b>${escapeHtml(c.name)}</b><span>${escapeHtml(c.course_title)} · ${Number(c.student_count)||0} học viên</span></div><label class="join-code">Mã lớp <input readonly value="${escapeHtml(c.join_code)}" aria-label="Mã tham gia ${escapeHtml(c.name)}"></label><div class="teacher-class-actions"><button type="button" data-copy-code="${escapeHtml(c.join_code)}">Sao chép mã</button><button type="button" data-class-report="${c.id}">Báo cáo</button><button type="button" data-class-discussion="${c.course_id}">Thảo luận</button><button type="button" data-announce-class="${c.id}">Thông báo</button></div></article>`).join('');
 list.querySelectorAll('[data-copy-code]').forEach(button=>button.onclick=()=>run(async()=>{await navigator.clipboard.writeText(button.dataset.copyCode);status('Đã sao chép mã lớp.');}));
 list.querySelectorAll('[data-class-report]').forEach(button=>button.onclick=()=>run(()=>showClassReport(button.dataset.classReport)));
 list.querySelectorAll('[data-class-discussion]').forEach(button=>button.onclick=()=>run(()=>showClassDiscussion(Number(button.dataset.classDiscussion))));
 list.querySelectorAll('[data-announce-class]').forEach(button=>button.onclick=()=>{$('announcement-form').elements.classId.value=button.dataset.announceClass;$('announcement-form').scrollIntoView({behavior:'smooth',block:'center'});$('announcement-form').elements.body.focus();});
}
function reportCsv(data){
 const escape=value=>'"'+String(value??'').replaceAll('"','""')+'"';
 const rows=[['Học viên','Chương','Điểm trung bình (%)'],...data.heatmap.map(row=>[row.display_name||row.username,row.chapter_title,row.average_score==null?'':Math.round(Number(row.average_score))])];
 const blob=new Blob(['\ufeff'+rows.map(row=>row.map(escape).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}),url=URL.createObjectURL(blob),link=document.createElement('a');
 link.href=url;link.download=`bao-cao-${String(data.class.name).replace(/[^\p{L}\p{N}-]+/gu,'-')}.csv`;link.click();URL.revokeObjectURL(url);
}
async function showClassReport(id){
 const data=await api(`teacher/classes/${id}/report`),summary=data.summary,report=$('class-report');
 report.hidden=false;$('class-discussion').hidden=true;
 report.innerHTML=`<div class="class-panel-head"><div><span class="section-kicker">BÁO CÁO LỚP</span><h3>${escapeHtml(data.class.name)} · ${escapeHtml(data.class.course_title)}</h3></div><div><button type="button" data-export-report>Xuất CSV</button><button type="button" data-close-panel>Đóng</button></div></div><div class="class-report-stats"><article><small>HỌC VIÊN</small><b>${summary.student_count}</b></article><article><small>HOÀN THÀNH BÀI</small><b>${summary.lesson_completed}/${summary.lesson_total}</b></article><article><small>LƯỢT QUIZ</small><b>${summary.quiz_attempts}</b></article><article><small>ĐIỂM TRUNG BÌNH</small><b>${summary.average_score==null?'—':Math.round(summary.average_score)+'%'}</b></article></div><h4>Tiến độ theo chương</h4><div class="class-report-table">${data.chapters.length?data.chapters.map(ch=>`<div><b>${escapeHtml(ch.title)}</b><span>${ch.lesson_completed}/${ch.lesson_total} bài · ${ch.quiz_attempts} quiz</span><strong>${ch.average_score==null?'—':Math.round(ch.average_score)+'%'}</strong></div>`).join(''):'<p>Chưa có chương học.</p>'}</div><h4>10 câu sai nhiều nhất</h4><div class="class-report-table">${data.questions.length?data.questions.map(q=>`<div><b>${escapeHtml(q.prompt)}</b><span>${q.wrong}/${q.attempts} lượt sai</span><strong>${Math.round(q.wrong_percent)}%</strong></div>`).join(''):'<p>Chưa có dữ liệu quiz.</p>'}</div><h4>Heatmap · học viên × chương</h4><div class="class-report-table class-heatmap">${data.heatmap?.length?data.heatmap.map(cell=>`<div><b>${escapeHtml(cell.display_name||cell.username)}</b><span>${escapeHtml(cell.chapter_title)}</span><strong class="heat-cell" style="--heat:${cell.average_score==null?0:Math.max(8,Math.min(100,Number(cell.average_score)))}%">${cell.average_score==null?'—':Math.round(cell.average_score)+'%'}</strong></div>`).join(''):'<p>Chưa có dữ liệu để lập heatmap.</p>'}</div><h4>Học viên cần chú ý</h4><div class="class-report-table">${data.students.filter(s=>(s.average_score!=null&&Number(s.average_score)<60)||Number(s.quiz_attempts)===0||!s.last_activity||Date.now()-Number(s.last_activity)>14*86400000).slice(0,10).map(s=>`<div><b>${escapeHtml(s.display_name||s.username)}</b><span>${!s.last_activity||Date.now()-Number(s.last_activity)>14*86400000?'Lâu không hoạt động':s.quiz_attempts?'Điểm thấp':'Chưa làm quiz'} · ${s.lesson_completed}/${s.lesson_total} bài</span><strong>${s.average_score==null?'—':Math.round(s.average_score)+'%'}</strong></div>`).join('')||'<p>Hiện chưa có học viên cần chú ý.</p>'}</div><h4>Hoạt động gần đây</h4><div class="class-report-table">${data.students.filter(s=>Number(s.last_activity)>0).slice().sort((a,b)=>Number(b.last_activity)-Number(a.last_activity)).slice(0,5).map(s=>`<div><b>${escapeHtml(s.display_name||s.username)}</b><span>${s.lesson_completed}/${s.lesson_total} bài · ${s.quiz_attempts} quiz</span><strong>${new Date(Number(s.last_activity)).toLocaleString('vi-VN',{dateStyle:'short',timeStyle:'short'})}</strong></div>`).join('')||'<p>Chưa có hoạt động học.</p>'}</div>`;
 report.querySelector('[data-close-panel]').onclick=()=>{report.hidden=true;};
 report.querySelector('[data-export-report]').onclick=()=>reportCsv(data);
}
async function showClassDiscussion(courseId){
 const data=await api(`courses/${courseId}/discussion`),discussion=$('class-discussion'),course=content.courses.find(item=>Number(item.id)===courseId);
 $('class-report').hidden=true;discussion.hidden=false;
 discussion.innerHTML=`<div class="class-panel-head"><div><span class="section-kicker">THẢO LUẬN MÔN HỌC</span><h3>${escapeHtml(course?.title||'Môn học')}</h3></div><button type="button" data-refresh-discussion>↻ Làm mới</button><button type="button" data-close-panel>Đóng</button></div><p class="discussion-online">Đang trực tuyến: ${data.online.map(escapeHtml).join(', ')||'Chưa có thành viên khác'}</p><div class="discussion-messages">${data.messages.length?data.messages.map(m=>`<article><b>${escapeHtml(m.username)}</b><p>${escapeHtml(m.body)}</p><time>${new Date(Number(m.sent_at)).toLocaleString('vi-VN')}</time></article>`).join(''):'<p>Chưa có trao đổi. Gửi lời chào đầu tiên cho lớp.</p>'}</div><form class="discussion-compose"><input name="body" maxlength="2000" required placeholder="Nhắn tin cho lớp…"><button class="primary-action">Gửi</button></form>`;
 discussion.querySelector('[data-close-panel]').onclick=()=>{discussion.hidden=true;};
 discussion.querySelector('[data-refresh-discussion]').onclick=()=>run(()=>showClassDiscussion(courseId));
 discussion.querySelector('.discussion-compose').onsubmit=e=>{e.preventDefault();run(async()=>{await api(`courses/${courseId}/discussion`,'POST',{body:new FormData(e.target).get('body')});await showClassDiscussion(courseId);});};
}
async function generateTeacherQuestions(){const button=$('ai-generate-questions'),chapterId=Number($('ai-chapter-select').value);if(!chapterId){status('Hãy chọn chương cần soạn câu hỏi.');return;}button.disabled=true;button.textContent='AI đang đọc nội dung chương…';$('teacher-ai-results').innerHTML='<div class="teacher-ai-loading">Đang phân tích nội dung bài học và tạo câu hỏi…</div>';try{const data=await api('ai/teacher/question-drafts','POST',{chapterId,questionCount:Number($('ai-question-count').value),difficulty:Number($('ai-question-difficulty').value)});$('teacher-ai-results').innerHTML=`<div class="teacher-ai-result-head"><b>${escapeHtml(data.chapterTitle)} · ${data.questions.length} câu nháp</b><span>${data.provider==='gemini'?'AI trực tiếp · Gemini':'Gợi ý offline · cần rà soát'} · Chưa lưu vào ngân hàng</span></div>${data.warning?`<div class="teacher-ai-warning">${escapeHtml(data.warning)}</div>`:''}${data.questions.map((q,index)=>`<article class="teacher-ai-draft"><div><small>CÂU ${index+1} · ${escapeHtml(data.difficulty)}</small><b>${escapeHtml(q.question)}</b><ol>${q.options.map(option=>`<li>${escapeHtml(option)}</li>`).join('')}</ol><p><strong>Đáp án ${String.fromCharCode(65+q.correctOption)}:</strong> ${escapeHtml(q.explanation)}</p></div><button type="button" data-use-ai-draft="${index}">Dùng câu này</button></article>`).join('')}`;document.querySelectorAll('[data-use-ai-draft]').forEach(applyButton=>applyButton.onclick=()=>{const draft=data.questions[Number(applyButton.dataset.useAiDraft)];fill('question-form',{chapterId:data.chapterId,difficulty:$('ai-question-difficulty').value,prompt:draft.question,a:draft.options[0],b:draft.options[1],c:draft.options[2],d:draft.options[3],correctOption:draft.correctOption,explanation:draft.explanation});status('Đã đưa bản nháp vào biểu mẫu. Kiểm tra rồi bấm Lưu câu hỏi.');});}catch(error){$('teacher-ai-results').innerHTML=`<div class="teacher-ai-error">${escapeHtml(error.message)}</div>`;}finally{button.disabled=false;button.textContent='✦ Tạo bản nháp';}}
async function generateSingleQuestion(){
 const form=$('question-form'),button=$('ai-single-question'),result=$('ai-single-question-result');
 const chapterId=Number(form.elements.chapterId.value),topic=form.elements.prompt.value.trim();
 if(!chapterId){result.innerHTML='<div class="teacher-ai-error">Hãy chọn chương trước khi nhờ AI soạn câu hỏi.</div>';return;}
 const original=button.innerHTML;button.disabled=true;button.innerHTML='<span class="teacher-ai-spinner" aria-hidden="true"></span> Đang soạn câu hỏi…';
 result.innerHTML='<div class="teacher-ai-loading">Đang đọc nội dung chương và tạo bản nháp. Câu hỏi chưa được lưu.</div>';
 try{
  const data=await api('ai/teacher/question-drafts','POST',{chapterId,questionCount:1,difficulty:Number(form.elements.difficulty.value),topic});
  const draft=data.questions[0];
  if(!draft||!Array.isArray(draft.options)||draft.options.length!==4)throw Error('AI chưa tạo được đủ 4 đáp án. Hãy thử lại.');
  form.elements.id.value='';
  fill('question-form',{chapterId:data.chapterId,difficulty:form.elements.difficulty.value,prompt:draft.question,a:draft.options[0],b:draft.options[1],c:draft.options[2],d:draft.options[3],correctOption:draft.correctOption,explanation:draft.explanation});
  result.innerHTML=`<div class="teacher-ai-loading">Đã điền bản nháp từ ${data.provider==='gemini'?'AI trực tiếp · Gemini':'Gợi ý offline'} · hãy kiểm tra nội dung trước khi lưu.</div>${data.warning?`<div class="teacher-ai-warning">${escapeHtml(data.warning)}</div>`:''}`;
  status('AI đã điền câu hỏi, đáp án và giải thích. Hãy rà soát rồi bấm Lưu câu hỏi.');
 }catch(error){result.innerHTML=`<div class="teacher-ai-error">${escapeHtml(error.message)}</div>`;}
 finally{button.disabled=false;button.innerHTML=original;}
}
async function requestTeacherContentDraft(buttonId,resultId,mode,payload){
 const button=$(buttonId),results=$(resultId),original=button.innerHTML;
 button.disabled=true;button.innerHTML='<span class="teacher-ai-spinner" aria-hidden="true"></span> Gemini đang soạn…';
 results.innerHTML='<div class="teacher-ai-loading">Đang tạo nội dung nháp bằng Gemini. Nội dung chưa được lưu.</div>';
 try{
  const draft=await api('ai/teacher/content-drafts','POST',{mode,...payload});
  if(mode==='COURSE'){
   results.innerHTML=`<article class="teacher-content-draft"><div class="teacher-ai-result-head"><b>Đề cương môn học</b><span>${draft.provider==='gemini'?'AI trực tiếp · Gemini':'Gợi ý offline · cần rà soát'} · Chưa lưu</span></div>${draft.warning?`<div class="teacher-ai-warning">${escapeHtml(draft.warning)}</div>`:''}<p>${escapeHtml(draft.description)}</p><b class="teacher-outline-label">4 chương đề xuất</b><ol>${draft.chapters.map(ch=>`<li><b>${escapeHtml(ch.title)}</b><span>${escapeHtml(ch.objective)}</span></li>`).join('')}</ol><button type="button" data-apply-course-draft>Dùng tên môn &amp; mô tả</button></article>`;
   results.querySelector('[data-apply-course-draft]').onclick=()=>{fill('course-form',{title:draft.title,description:draft.description});status('Đã điền bản nháp Gemini. Lưu môn học rồi tạo các chương theo đề cương.');};
  }else if(mode==='CHAPTER'){
   results.innerHTML=`<article class="teacher-content-draft"><div class="teacher-ai-result-head"><b>Chương đề xuất</b><span>${draft.provider==='gemini'?'AI trực tiếp · Gemini':'Gợi ý offline · cần rà soát'} · Chưa lưu</span></div>${draft.warning?`<div class="teacher-ai-warning">${escapeHtml(draft.warning)}</div>`:''}<p>${escapeHtml(draft.objective)}</p><b class="teacher-outline-label">Các bài gợi ý</b><ol>${draft.lessons.map(item=>`<li><b>${escapeHtml(item.title)}</b><span>${escapeHtml(item.outline)}</span></li>`).join('')}</ol><button type="button" data-apply-chapter-draft>Dùng tên chương</button></article>`;
   results.querySelector('[data-apply-chapter-draft]').onclick=()=>{fill('chapter-form',{courseId:payload.courseId,title:draft.title});status('Đã điền tên chương. Lưu chương rồi dùng Gemini soạn nội dung từng bài.');};
  }else{
   const content=draft.content+'\n\nÝ chính cần nhớ:\n'+draft.keyPoints.map(point=>'• '+point).join('\n');
   results.innerHTML=`<article class="teacher-content-draft"><div class="teacher-ai-result-head"><b>${escapeHtml(draft.title)}</b><span>${draft.provider==='gemini'?'AI trực tiếp · Gemini':'Gợi ý offline · cần rà soát'} · Chưa lưu</span></div>${draft.warning?`<div class="teacher-ai-warning">${escapeHtml(draft.warning)}</div>`:''}<p>${escapeHtml(draft.content.slice(0,460))}${draft.content.length>460?'…':''}</p><b class="teacher-outline-label">Ý chính</b><ul>${draft.keyPoints.map(point=>`<li>${escapeHtml(point)}</li>`).join('')}</ul><button type="button" data-apply-lesson-draft>Dùng nội dung này</button></article>`;
   results.querySelector('[data-apply-lesson-draft]').onclick=()=>{fill('lesson-form',{chapterId:payload.chapterId,title:draft.title,content});status('Đã điền nội dung Gemini vào biểu mẫu. Hãy đọc lại và chỉnh sửa trước khi lưu.');};
  }
  if(draft.warning){const warning=document.createElement('p');warning.className='teacher-ai-warning';warning.textContent=draft.warning;results.querySelector('.teacher-content-draft')?.prepend(warning);}
 }catch(error){results.innerHTML=`<div class="teacher-ai-error">${escapeHtml(error.message)}</div>`;}
 finally{button.disabled=false;button.innerHTML=original;}
}
$('login').onsubmit=e=>{e.preventDefault();run(async()=>{const result=await api('auth/login','POST',Object.fromEntries(new FormData(e.target)));if(result.account.role!=='TEACHER')throw Error('Tài khoản này chưa được gán vai trò giảng viên.');await openWorkspace(result);e.target.reset();});};
$('class-form').onsubmit=e=>{e.preventDefault();run(async()=>{const values=Object.fromEntries(new FormData(e.target));await api('teacher/classes','POST',{courseId:Number(values.courseId),name:values.name});e.target.reset();await reload();status('Đã tạo lớp học. Chia sẻ mã tham gia với học viên.');});};
$('logout').onclick=()=>run(async()=>{await api('auth/logout','POST');token='';sessionStorage.removeItem('studyspace_teacher_session');location.reload();});
for(const [formId,endpoint,convert]of [
 ['course-form','courses',f=>({title:f.title,description:f.description,published:f.published==='on'})],
 ['chapter-form','chapters',f=>({title:f.title,courseId:+f.courseId,position:+f.position})],
 ['lesson-form','lessons',f=>({title:f.title,chapterId:+f.chapterId,content:f.content,materialUrl:f.materialUrl,position:+f.position})],
 ['question-form','questions',f=>({chapterId:+f.chapterId,prompt:f.prompt,options:[f.a,f.b,f.c,f.d],correctOption:+f.correctOption,explanation:f.explanation,difficulty:+f.difficulty})]
])$(formId).onsubmit=e=>{e.preventDefault();run(async()=>{
 const values=Object.fromEntries(new FormData(e.target)),isNew=!values.id;
 if(formId==='lesson-form'&&values.materialFile?.size){const uploaded=await uploadFile(values.materialFile,values.chapterId);values.materialUrl=uploaded.url;}
 const saved=await api('teacher/content/'+endpoint+(values.id?'/'+values.id:''),values.id?'PUT':'POST',convert(values));
 e.target.reset();e.target.elements.id.value='';
 const editor=e.target.closest('details');if(editor){editor.open=false;$(editorTitles[formId][0]).textContent=editorTitles[formId][1];}
 await reload();
 if(formId==='course-form'&&isNew){
  continueIn('teacher-chapters','chapter-editor');
  const courseSelect=$('chapter-form').elements.courseId;courseSelect.value=String(saved.id);
  $('chapter-form').elements.position.value=nextPosition(content.chapters,saved.id,'course_id');
  status('Đã lưu khóa học. Khóa học mới đã được chọn; hãy thêm chương đầu tiên.');
 }else if(formId==='chapter-form'&&isNew){
  continueIn('teacher-lessons','lesson-editor');
  const chapterSelect=$('lesson-form').elements.chapterId;chapterSelect.value=String(saved.id);
  $('lesson-form').elements.position.value=nextPosition(content.lessons,saved.id,'chapter_id');
  status('Đã lưu chương. Chương mới đã được chọn; hãy thêm bài học đầu tiên.');
 }else{
  status(formId==='course-form'?(values.published==='on'?'Đã lưu và xuất bản khóa học. Học viên sẽ thấy khóa học trong danh sách.':'Đã lưu khóa học dưới dạng bản nháp; học viên chưa nhìn thấy.'):'Đã lưu nội dung.');
 }
});};
for(const [formId,[titleId,newTitle]]of Object.entries(editorTitles))$(formId).addEventListener('reset',()=>setTimeout(()=>{if($(titleId))$(titleId).textContent=newTitle;}));
for(const link of document.querySelectorAll('.teacher-sidebar a'))link.onclick=()=>{document.querySelectorAll('.teacher-sidebar a').forEach(item=>item.removeAttribute('aria-current'));link.setAttribute('aria-current','true');};

function syncTeacherSection(){
 const workspace=$('workspace');
 if(!workspace||workspace.hidden)return;
 const sections=[...document.querySelectorAll('.teacher-main>.teacher-section')];
 if(!sections.length)return;
 const requested=location.hash.slice(1),active=sections.find(section=>section.id===requested)||sections[0];
 sections.forEach(section=>section.hidden=section!==active);
 document.querySelector('.teacher-main')?.classList.add('has-active-section');
 document.querySelectorAll('.teacher-sidebar a[href^="#teacher-"]').forEach(link=>{
  if(link.hash==='#'+active.id){link.setAttribute('aria-current','page');}else{link.removeAttribute('aria-current');}
 });
 document.querySelectorAll('.teacher-creation-path a[href^="#teacher-"]').forEach(link=>link.classList.toggle('is-current',link.hash==='#'+active.id));
 document.querySelectorAll('.teacher-mobile-nav a[href^="#teacher-"]').forEach(link=>{if(link.hash==='#'+active.id)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');});
 if($('teacher-more-drawer')?.open&&['teacher-chapters','teacher-lessons'].includes(active.id))$('teacher-more-trigger')?.setAttribute('aria-current','page');else $('teacher-more-trigger')?.removeAttribute('aria-current');
}
window.addEventListener('hashchange',syncTeacherSection);
document.querySelectorAll('[data-open-editor]').forEach(button=>button.addEventListener('click',()=>openEditorDrawer(button.dataset.openEditor)));
document.querySelectorAll('[data-close-editor]').forEach(button=>button.addEventListener('click',()=>{const details=button.closest('details');if(details)details.open=false;}));
document.querySelectorAll('.editor-panel:not(.bulk-quiz-panel)').forEach(editor=>editor.addEventListener('toggle',()=>{if(editor.open)for(const other of document.querySelectorAll('.editor-panel:not(.bulk-quiz-panel)'))if(other!==editor)other.open=false;}));
const userTrigger=$('teacher-user-trigger'),userPanel=$('teacher-user-panel');
userTrigger.onclick=()=>{const open=userPanel.hidden;userPanel.hidden=!open;userTrigger.setAttribute('aria-expanded',String(open));};
document.addEventListener('click',event=>{if(!event.target.closest('.teacher-user-menu')){userPanel.hidden=true;userTrigger.setAttribute('aria-expanded','false');}});
const moreDrawer=$('teacher-more-drawer');
$('teacher-more-trigger').onclick=()=>moreDrawer.showModal();
moreDrawer.querySelector('[data-close-more]').onclick=()=>moreDrawer.close();
moreDrawer.querySelectorAll('a').forEach(link=>link.addEventListener('click',()=>moreDrawer.close()));
moreDrawer.addEventListener('click',event=>{if(event.target===moreDrawer)moreDrawer.close();});

async function openWorkspace(result){
 if(result.account.role!=='TEACHER')throw Error('Tài khoản này không có quyền giảng viên.');
 const displayName=result.account.displayName||result.account.display_name||result.account.username||'Giảng viên';
 $('teacher-user-trigger').textContent=[...displayName.trim()][0]?.toLocaleUpperCase('vi-VN')||'G';
 $('teacher-user-trigger').setAttribute('aria-label',`Tài khoản ${displayName}`);
 $('teacher-user-panel').querySelector('span').textContent=displayName;
 token=result.token;sessionStorage.setItem('studyspace_teacher_session',token);sessionStorage.setItem('studyspace_active_role','TEACHER');
 $('login').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;
 if(!location.hash||!document.getElementById(location.hash.slice(1)))history.replaceState(null,'','#teacher-overview');
 syncTeacherSection();await reload();
}
async function restoreWorkspace(){
 const saved=sessionStorage.getItem('studyspace_teacher_session');
 if(saved){token=saved;let account=null;try{account=await api('auth/me');}catch(error){if(error.name==='TypeError'||error.name==='AbortError'||error.name==='TimeoutError'){status('Chưa kết nối được máy chủ. Phiên đăng nhập được giữ lại; thử tải lại khi mạng hoạt động.');return;}sessionStorage.removeItem('studyspace_teacher_session');}
  if(account?.role==='TEACHER'){const displayName=account.displayName||account.display_name||account.username||'Giảng viên';$('teacher-user-trigger').textContent=[...displayName.trim()][0]?.toLocaleUpperCase('vi-VN')||'G';$('teacher-user-trigger').setAttribute('aria-label',`Tài khoản ${displayName}`);$('teacher-user-panel').querySelector('span').textContent=displayName;sessionStorage.setItem('studyspace_active_role','TEACHER');$('login').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;if(!location.hash||!document.getElementById(location.hash.slice(1)))history.replaceState(null,'','#teacher-overview');syncTeacherSection();try{await reload();}catch(error){status(error.message);}return;}
 }
 const fragment=new URLSearchParams(location.hash.slice(1));
 const ticket=fragment.get('ticket');
 if(!ticket){location.replace('/login.html?staff=1');return;}
 history.replaceState(null,'',location.pathname+location.search);
 $('login').hidden=true;
 try{
  const response=await fetch('/api/auth/portal-ticket/exchange',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({ticket})});
  const text=await response.text();let result;try{result=text?JSON.parse(text):{};}catch{throw Error('Máy chủ trả dữ liệu không hợp lệ.');}
  if(!response.ok)throw Error(result.error||'Liên kết đăng nhập không hợp lệ.');
  await openWorkspace(result);
 }catch(error){$('login').hidden=false;status(error.message||'Không thể đăng nhập cổng giảng viên.');}
}
restoreWorkspace();
$('ai-generate-questions').onclick=()=>run(generateTeacherQuestions);
$('ai-single-question').onclick=()=>run(generateSingleQuestion);
$('ai-course-draft').onclick=()=>run(()=>requestTeacherContentDraft('ai-course-draft','ai-course-results','COURSE',{topic:$('course-form').elements.title.value,learningGoals:$('course-form').elements.description.value}));
$('ai-chapter-draft').onclick=()=>run(()=>requestTeacherContentDraft('ai-chapter-draft','ai-chapter-results','CHAPTER',{courseId:Number($('chapter-form').elements.courseId.value),topic:$('chapter-form').elements.title.value,learningGoals:''}));
$('ai-lesson-draft').onclick=()=>run(()=>requestTeacherContentDraft('ai-lesson-draft','ai-lesson-results','LESSON',{chapterId:Number($('lesson-form').elements.chapterId.value),topic:$('lesson-form').elements.title.value,learningGoals:''}));

$('announcement-form').onsubmit=e=>{e.preventDefault();run(async()=>{const values=Object.fromEntries(new FormData(e.target));await api(`teacher/classes/${values.classId}/announcements`,'POST',{body:values.body});e.target.elements.body.value='';status('Đã gửi thông báo đến học viên trong lớp.');});};

function parseCsvLine(line){const fields=[];let value='',quoted=false;for(let i=0;i<line.length;i++){const ch=line[i];if(ch==='"'&&quoted&&line[i+1]==='"'){value+='"';i++;}else if(ch==='"')quoted=!quoted;else if(ch===','&&!quoted){fields.push(value.trim());value='';}else value+=ch;}fields.push(value.trim());return fields;}
let parsedQuizRows=[];
$('quiz-csv-preview').onclick=()=>{
 const lines=$('quiz-csv-input').value.split(/\r?\n/).filter(line=>line.trim()),errors=[],valid=[];
 lines.forEach((line,index)=>{const cells=parseCsvLine(line);if(index===0&&cells[0]?.toLowerCase()==='chapterid')return;const [chapterId,difficulty,prompt,a,b,c,d,answer,explanation]=cells;const chapter=content.chapters.find(item=>Number(item.id)===Number(chapterId));const correctOption=/^[0-3]$/.test(answer)?Number(answer):'ABCD'.indexOf(String(answer||'').toUpperCase());if(cells.length!==9||!chapter||!prompt||![a,b,c,d].every(Boolean)||!explanation||![1,2,3].includes(Number(difficulty))||correctOption<0){errors.push(`Dòng ${index+1}: cần 9 cột hợp lệ, chapterId phải tồn tại và đáp án là A/B/C/D hoặc 0-3.`);return;}valid.push({line:index+1,chapterId:Number(chapterId),difficulty:Number(difficulty),prompt,options:[a,b,c,d],correctOption,explanation});});
 parsedQuizRows=valid;$('quiz-csv-import').disabled=!valid.length;
 $('quiz-csv-results').innerHTML=`<div class="csv-preview-summary"><b>${valid.length} câu hợp lệ</b><span>${errors.length} dòng lỗi</span></div>${errors.map(error=>`<p class="csv-row-error">${escapeHtml(error)}</p>`).join('')}${valid.slice(0,8).map(row=>`<article class="csv-preview-row"><b>Dòng ${row.line} · ${escapeHtml(content.chapters.find(ch=>Number(ch.id)===row.chapterId)?.title||'Chương')}</b><span>${escapeHtml(row.prompt)}</span><small>Đáp án ${'ABCD'[row.correctOption]} · độ khó ${row.difficulty}</small></article>`).join('')}${valid.length>8?`<p>Còn ${valid.length-8} câu hợp lệ khác.</p>`:''}`;
};
$('quiz-csv-import').onclick=()=>run(async()=>{let saved=0;for(const row of parsedQuizRows){await api('teacher/content/questions','POST',{chapterId:row.chapterId,prompt:row.prompt,options:row.options,correctOption:row.correctOption,explanation:row.explanation,difficulty:row.difficulty});saved++;}parsedQuizRows=[];$('quiz-csv-import').disabled=true;$('quiz-csv-results').replaceChildren();$('quiz-csv-input').value='';await reload();status(`Đã lưu ${saved} câu hỏi từ CSV.`);});

$('student-preview-button').onclick=()=>{
 const dialog=$('student-preview-dialog'),host=$('student-preview-content'),published=content.courses.filter(course=>course.published);
 host.innerHTML=`<span class="section-kicker">CHẾ ĐỘ XEM THỬ</span><h2>Trải nghiệm như học viên</h2><label>Khóa học<select id="preview-course-select">${published.map(course=>`<option value="${course.id}">${escapeHtml(course.title)}</option>`).join('')}</select></label><button type="button" id="preview-load" class="primary-action" ${published.length?'':'disabled'}>Mở bản xem trước</button><div id="preview-course-content"></div>`;
 if(published.length)host.querySelector('#preview-load').onclick=()=>run(async()=>{const course=await api(`courses/${Number(host.querySelector('#preview-course-select').value)}`);host.querySelector('#preview-course-content').innerHTML=`<article class="preview-course-card"><h3>${escapeHtml(course.title)}</h3><p>${escapeHtml(course.description||'')}</p>${(course.chapters||[]).map((chapter,index)=>`<section><b>Chương ${index+1} · ${escapeHtml(chapter.title)}</b>${(chapter.lessons||[]).map(lesson=>`<p>▤ ${escapeHtml(lesson.title)}${lesson.material_url?' · Có tài liệu':''}</p>`).join('')||'<p>Chương chưa có bài học.</p>'}</section>`).join('')||'<p>Khóa học chưa có chương.</p>'}</article>`;});
 else host.innerHTML+='<p>Chưa có khóa học xuất bản để xem.</p>';
 dialog.showModal();
};
