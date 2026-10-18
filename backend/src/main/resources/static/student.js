'use strict';
const $=id=>document.getElementById(id);let token=sessionStorage.getItem('studyspace_student_session')||'',courses=[],progress={},studentClasses=[],activeCourse=null,activeAttempt=null,studentDataLoading=false,progressLoadError=null,quizTimer=null,quizSaveTimer=null,quizMarked=new Set(),quizWarned=false,currentAccount=null,selectedClassId=null,nextLesson=null;const discussionRequests=new Map();
function toast(message){const el=$('student-toast');el.textContent=message;el.classList.add('visible');setTimeout(()=>el.classList.remove('visible'),3200);}
async function api(path,method='GET',body,options={}){const timeout=AbortSignal.timeout(12000),signal=options.signal?AbortSignal.any([options.signal,timeout]):timeout;const response=await fetch('/api/'+path,{method,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal});const raw=await response.text();let data={};try{data=raw?JSON.parse(raw):{};}catch{}if(!response.ok){if(response.status===401){sessionStorage.removeItem('studyspace_student_session');location.replace('/login.html');}throw Error(data.error||'Không thể tải dữ liệu. Vui lòng thử lại.');}return data;}
function escapeHtml(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function empty(title,description,actionLabel='',action=''){return `<div class="empty-box"><span class="student-empty-icon" aria-hidden="true">✦</span><b>${escapeHtml(title)}</b><span class="student-empty-copy">${escapeHtml(description)}</span>${action?`<button class="student-empty-action" type="button" data-empty-action="${action}">${escapeHtml(actionLabel)}</button>`:''}</div>`;}
function aiProviderLabel(provider){return provider==='gemini'?'AI trực tiếp · Gemini':'Gợi ý offline';}
function courseProgressState(course){const total=Number(course?.lesson_count||0),done=Number(course?.completed_count||0);return total>0&&done>=total?'Hoàn thành':total>0&&done>0?'Đang học':'Chưa bắt đầu';}
function displayPersonName(name,role){const value=String(name||'').trim();return /@(admin|teacher|student)$/i.test(value)?({ADMIN:'Quản trị viên',TEACHER:'Giảng viên',STUDENT:'Học viên'}[String(role||'').toUpperCase()]||'Thành viên'):value||'Thành viên';}
async function load(){if(!token){location.replace('/login.html');return;}try{const account=await api('auth/me');if(account.role!=='STUDENT'){location.replace(account.role==='TEACHER'?'/teacher.html':'/admin.html');return;}sessionStorage.setItem('studyspace_active_role','STUDENT');const greeting=account.displayName||account.username;$('username-label').textContent=greeting;$('greeting-name').textContent=greeting;$('avatar').textContent=(greeting.trim()[0]||'H').toLocaleUpperCase('vi');[courses,progress,studentClasses]=await Promise.all([api('courses'),api('progress'),api('classes/mine')]);render();renderClasses();const welcome=sessionStorage.getItem('studyspace_welcome');if(welcome){toast(welcome);sessionStorage.removeItem('studyspace_welcome');}}catch(error){toast(error.message);}}
function render(){const total=courses.reduce((n,c)=>n+Number(c.lesson_count||0),0),done=courses.reduce((n,c)=>n+Number(c.completed_count||0),0),attempts=(progress.history||[]).filter(x=>x.submitted_at!=null),average=attempts.length?Math.round(attempts.reduce((n,x)=>n+100*Number(x.score)/Math.max(1,Number(x.total)),0)/attempts.length):null;$('completed-count').textContent=`${done}/${total}`;$('quiz-count').textContent=attempts.length;$('average-score').textContent=average==null?'—':`${average}%`;$('course-total').textContent=`${courses.length} môn học`;$('course-list').innerHTML=courses.length?courses.map((c,i)=>{const pct=c.lesson_count?Math.round(100*c.completed_count/c.lesson_count):0;return `<button class="course-card" data-course="${c.id}" type="button"><span class="course-card-top"><span class="course-icon">${i%2?'⌘':'▤'}</span><span class="course-progress">${pct}% hoàn thành</span></span><h3>${escapeHtml(c.title)}</h3><p>${escapeHtml(c.description||'Bắt đầu học theo lộ trình của môn này.')}</p><span class="progress-track"><span style="width:${pct}%"></span></span><span class="course-open">${c.completed_count?'Tiếp tục học':'Bắt đầu học'} <span>→</span></span></button>`;}).join(''):empty('Chưa có môn học được xuất bản','Giảng viên sẽ đăng nội dung tại đây khi khóa học sẵn sàng.','Làm mới danh sách','refresh');const history=attempts.slice(0,6);$('history-list').innerHTML=history.length?history.map(row=>`<div class="history-row"><b>${escapeHtml(row.chapter_title||'Quiz ôn tập')}</b><span>${new Date(Number(row.submitted_at)).toLocaleDateString('vi-VN')}</span><span class="score-chip">${Math.round(100*Number(row.score)/Math.max(1,Number(row.total)))}%</span></div>`).join(''):empty('Chưa có quiz đã nộp','Làm quiz trong một chương để theo dõi kết quả tại đây.','Xem môn học','courses');const mastery=(progress.mastery||[]).filter(row=>Number(row.attempts)>0).sort((a,b)=>Number(a.percentage)-Number(b.percentage));if(mastery.length){$('ai-recommendation-title').textContent=`Ưu tiên ôn: ${mastery[0].title}`;$('ai-recommendation-copy').textContent=`Điểm trung bình chương này ${Math.round(Number(mastery[0].percentage))}% · dựa trên ${mastery[0].attempts} lượt quiz đã nộp. AI sẽ tạo 5 câu luyện tập bám vào bài học và lỗi trước đây.`;}else{$('ai-recommendation-title').textContent='Bắt đầu với một bài quiz';$('ai-recommendation-copy').textContent='Khi có kết quả quiz, AI sẽ nhận diện chương cần củng cố và tạo bộ câu hỏi ôn tập phù hợp. Bạn cũng có thể hỏi gia sư bất cứ lúc nào.';}$('ai-review-button').disabled=!attempts.length;document.querySelectorAll('[data-course]').forEach(button=>button.onclick=()=>openCourse(Number(button.dataset.course)));document.querySelectorAll('[data-empty-action]').forEach(button=>button.onclick=()=>button.dataset.emptyAction==='refresh'?load():document.querySelector('.student-section').scrollIntoView({behavior:'smooth',block:'start'}));}
async function openCourse(id){try{const course=await api('courses/'+id);activeCourse=course;$('student-metrics').hidden=true;$('student-ai').hidden=true;document.querySelector('.student-heading').hidden=true;document.querySelector('.student-section').hidden=true;document.querySelector('.report-section').hidden=true;$('course-view').hidden=false;renderCourse();window.scrollTo({top:0,behavior:'smooth'});}catch(error){toast(error.message);}}
function renderClasses(){
 cancelDiscussionRequests();
 $('class-total').textContent=`${studentClasses.length} lớp`;
 const list=$('student-class-list');
 if(!studentClasses.length){list.innerHTML=empty('Chưa tham gia lớp nào','Nhập mã tham gia do giảng viên chia sẻ để xem lớp và trao đổi cùng mọi người.');return;}
 if(!studentClasses.some(item=>Number(item.id)===Number(selectedClassId)))selectedClassId=studentClasses[0].id;
 const selected=studentClasses.find(item=>Number(item.id)===Number(selectedClassId));
 list.innerHTML=`<article class="student-class-card"><header><div><h3>${escapeHtml(selected.name)}</h3><p>${escapeHtml(selected.course_title)}</p><p class="class-teacher-name">Giảng viên: ${escapeHtml(displayPersonName(selected.teacher_name,'TEACHER'))}</p></div><button class="button button-primary" type="button" data-open-class-course="${selected.course_id}">Mở môn học</button></header><div class="discussion-class-picker"><label for="discussion-class-select">Chọn lớp thảo luận</label><select id="discussion-class-select">${studentClasses.map(item=>`<option value="${item.id}" ${Number(item.id)===Number(selectedClassId)?'selected':''}>${escapeHtml(item.name)} · ${escapeHtml(item.course_title)}</option>`).join('')}</select></div><div class="student-discussion"><div class="discussion-head"><b>Thảo luận lớp</b><button type="button" data-refresh-discussion="${selected.course_id}" aria-label="Làm mới thảo luận">↻</button></div><div class="student-discussion-body" id="discussion-${selected.course_id}"><span class="loading-line"><i></i> Đang tải trao đổi</span></div><form class="student-discussion-form" data-send-course="${selected.course_id}"><input name="body" maxlength="2000" required placeholder="Chia sẻ với lớp…"><button type="submit" aria-label="Gửi tin nhắn">Gửi</button></form></div></article>`;
 list.querySelector('#discussion-class-select').onchange=event=>{selectedClassId=Number(event.target.value);renderClasses();};
 list.querySelector('[data-open-class-course]').onclick=()=>openCourse(Number(selected.course_id));
 list.querySelector('[data-refresh-discussion]').onclick=()=>refreshClassDiscussion(Number(selected.course_id));
 list.querySelector('[data-send-course]').onsubmit=event=>{event.preventDefault();sendClassMessage(Number(selected.course_id),event.currentTarget);};
 if(studentScreen==='classes')loadClassDiscussions();
}
function cancelDiscussionRequests(){for(const controller of discussionRequests.values())controller.abort();discussionRequests.clear();}
function loadClassDiscussions(){const selected=studentClasses.find(item=>Number(item.id)===Number(selectedClassId));if(studentScreen==='classes'&&selected)refreshClassDiscussion(Number(selected.course_id));}
async function refreshClassDiscussion(courseId){
 const host=$(`discussion-${courseId}`);if(!host)return;
 if(discussionRequests.has(courseId))return;
 const controller=new AbortController();discussionRequests.set(courseId,controller);
 try{
  const data=await api(`courses/${courseId}/discussion`,'GET',undefined,{signal:controller.signal});
  if(controller.signal.aborted||studentScreen!=='classes'||discussionRequests.get(courseId)!==controller)return;
  const messages=data.messages.length?data.messages.map(message=>`<article><b>${escapeHtml(displayPersonName(message.display_name||message.username,message.role))}</b><p>${escapeHtml(message.body)}</p><time>${new Date(Number(message.sent_at)).toLocaleTimeString('vi-VN',{hour:'2-digit',minute:'2-digit'})}</time></article>`).join(''):empty('Chưa có tin nhắn','Hãy bắt đầu cuộc trò chuyện cùng lớp.');
  const others=data.online.filter(person=>person.username!==currentAccount?.username);
  host.innerHTML=`${others.length?`<div class="discussion-online">${others.length} người đang trực tuyến</div>`:''}<div class="discussion-messages">${messages}</div>`;
 }catch(error){
  if(controller.signal.aborted)return;
  host.innerHTML=`<div class="discussion-error" role="status"><span>Chưa tải được trao đổi. Kiểm tra kết nối rồi thử lại.</span><button type="button" data-retry-discussion="${courseId}">Thử lại</button></div>`;
  host.querySelector('[data-retry-discussion]').onclick=()=>refreshClassDiscussion(courseId);
 }finally{if(discussionRequests.get(courseId)===controller)discussionRequests.delete(courseId);}
}
async function sendClassMessage(courseId,form){
 const input=form.elements.namedItem('body'),button=form.querySelector('button');button.disabled=true;
 try{await api(`courses/${courseId}/discussion`,'POST',{body:input.value.trim()});input.value='';await refreshClassDiscussion(courseId);}
 catch(error){toast(error.message);}
 finally{button.disabled=false;}
}
function renderCourse(){if(!activeCourse)return;const chapters=activeCourse.chapters||[];$('course-content').innerHTML=`<header class="course-detail-head"><span class="student-kicker">MÔN HỌC</span><h2>${escapeHtml(activeCourse.title)}</h2><p>${escapeHtml(activeCourse.description||'')}</p></header>${chapters.map(ch=>`<section class="chapter-block"><h3 class="chapter-title">${escapeHtml(ch.title)}</h3>${(ch.lessons||[]).map(lesson=>`<article class="lesson-row"><div><h4>${escapeHtml(lesson.title)}</h4><p>${escapeHtml(lesson.content||'')}</p>${lesson.material_url?`<p><a href="${escapeHtml(lesson.material_url)}" target="_blank" rel="noopener">Mở tài liệu ↗</a></p>`:''}</div><div>${lesson.completed?'<span class="lesson-state">✓ Đã học</span>':`<button data-complete="${lesson.id}" ${lesson.locked?'disabled':''}>${lesson.locked?'Đang khóa':'Đánh dấu học xong'}</button>`}</div></article>`).join('')||empty('Chưa có bài học','Chương này chưa có nội dung.')}${Number(ch.question_count)>0?`<button class="chapter-quiz" data-quiz="${ch.id}">Làm quiz · ${ch.question_count} câu</button>`:''}</section>`).join('')||empty('Chưa có nội dung','Môn học chưa được thêm chương.')}`;document.querySelectorAll('[data-complete]').forEach(button=>button.onclick=()=>completeLesson(button));document.querySelectorAll('[data-quiz]').forEach(button=>button.onclick=()=>startQuiz(button.dataset.quiz));}
async function completeLesson(button){button.disabled=true;try{await api(`lessons/${button.dataset.complete}/complete`,'PUT',{});toast('Đã lưu tiến độ bài học.');const courseId=activeCourse.id;[courses,progress]=await Promise.all([api('courses'),api('progress')]);render();await openCourse(courseId);}catch(error){button.disabled=false;toast(error.message);}}
async function startQuiz(chapterId){try{activeAttempt=await api(`chapters/${chapterId}/attempts`,'POST');renderQuiz(false);}catch(error){toast(error.message);}}
function renderQuiz(submitted){const qs=activeAttempt.questions||[];$('quiz-modal').hidden=false;$('quiz-content').innerHTML=`<span class="student-kicker">QUIZ CHƯƠNG</span><h2>${submitted?'Kết quả của bạn':'Cùng kiểm tra kiến thức'}</h2><p class="quiz-subtitle">${submitted?'Bài làm đã được lưu vào tiến độ.':`${qs.length} câu hỏi · Chọn đáp án phù hợp nhất.`}${activeAttempt.provider==='offline'?' · Bộ câu hỏi dựa trên bài học và câu sai đã lưu':''}</p>${qs.map((q,index)=>`<section class="quiz-question"><b>${index+1}. ${escapeHtml(q.prompt)}</b>${[q.option_a,q.option_b,q.option_c,q.option_d].map((option,i)=>`<label class="quiz-option"><input type="radio" name="q-${q.question_id}" value="${i}" ${q.selected_option===i?'checked':''} ${submitted?'disabled':''}><span>${escapeHtml(option)}</span></label>`).join('')}${submitted&&q.explanation?`<small>${escapeHtml(q.selected_option===q.correct_option?'Đúng. ':'Chưa chính xác. ')}${escapeHtml(q.explanation)}</small>`:''}${submitted&&q.selected_option!==null&&q.selected_option!==q.correct_option?`<button class="ai-explain-button" data-explain="${index}" type="button">Hỏi AI giải thích</button><p class="ai-explain-result" id="ai-explain-${index}" aria-live="polite"></p>`:''}</section>`).join('')}${submitted?`<div class="quiz-result">Điểm: ${activeAttempt.score}/${activeAttempt.total} câu (${Math.round(100*activeAttempt.score/Math.max(1,activeAttempt.total))}%)</div><button class="quiz-submit" id="done-quiz">Đóng kết quả</button>`:'<button class="quiz-submit" id="submit-quiz">Nộp bài và xem kết quả</button>'}`;if(!submitted)$('submit-quiz').onclick=submitQuiz;else{$('done-quiz').onclick=()=>{$('quiz-modal').hidden=true;load();};document.querySelectorAll('[data-explain]').forEach(button=>button.onclick=()=>explainWrongAnswer(Number(button.dataset.explain),button));}}
async function explainWrongAnswer(index,button){const q=activeAttempt.questions[index],options=[q.option_a,q.option_b,q.option_c,q.option_d],result=$(`ai-explain-${index}`);button.disabled=true;button.textContent='Đang chuẩn bị giải thích…';result.textContent='Đang đối chiếu với lời giải đã lưu.';try{const answer=await api('ai/explain','POST',{question:q.prompt,selectedAnswer:options[q.selected_option],correctAnswer:options[q.correct_option],selectedOption:q.selected_option,correctOption:q.correct_option,sourceExplanation:q.explanation});result.textContent=`${aiProviderLabel(answer.provider)} · ${answer.explanation}`;button.textContent=answer.provider==='offline'?'Thử lại với AI':'Hỏi lại AI';button.disabled=false;}catch{result.textContent='Chưa thể tải lời giải lúc này. Hãy thử lại.';button.disabled=false;button.textContent='Thử lại';}}
async function saveQuizDraft(){if(!activeAttempt||Date.now()>=Number(activeAttempt.clientDeadline??activeAttempt.deadline))return;const answers={};for(const q of activeAttempt.questions||[])if(q.selected_option!==null)answers[q.question_id]=Number(q.selected_option);try{activeAttempt=await api(`attempts/${activeAttempt.id}/answers`,'PUT',{answers});syncQuizDeadline();}catch(error){toast(error.message);}}
async function submitQuiz(auto=false){if(!activeAttempt||activeAttempt.submitted_at)return;const unanswered=(activeAttempt.questions||[]).filter(q=>q.selected_option===null).map(q=>Number(q.position)+1),marked=(activeAttempt.questions||[]).filter(q=>quizMarked.has(Number(q.question_id))).map(q=>Number(q.position)+1);if(!auto&&!window.confirm(`Xác nhận nộp bài?\nCâu chưa trả lời: ${unanswered.length?unanswered.join(', '):'không có'}\nCâu đã đánh dấu: ${marked.length?marked.join(', '):'không có'}`))return;clearTimeout(quizSaveTimer);try{await saveQuizDraft();activeAttempt=await api(`attempts/${activeAttempt.id}/submit`,'POST');sessionStorage.removeItem(quizStorageKey());quizMarked.clear();renderQuiz(true);}catch(error){toast(error.message);}}
function showHome(){activeCourse=null;$('course-view').hidden=true;$('student-metrics').hidden=false;$('student-ai').hidden=false;document.querySelector('.student-heading').hidden=false;document.querySelector('.student-section').hidden=false;document.querySelector('.report-section').hidden=false;}
$('class-join-form').onsubmit=async event=>{event.preventDefault();const input=$('class-code'),button=event.submitter;button.disabled=true;try{const result=await api('classes/join','POST',{code:input.value.trim()});studentClasses=await api('classes/mine');input.value='';renderClasses();toast(`Đã tham gia lớp ${result.name}.`);}catch(error){toast(error.message);}finally{button.disabled=false;}};
$('back-courses').onclick=showHome;$('home-button').onclick=showHome;$('refresh-button').onclick=load;$('logout').onclick=async()=>{try{await api('auth/logout','POST',{});}catch{}sessionStorage.removeItem('studyspace_student_session');sessionStorage.removeItem('studyspace_welcome');if(sessionStorage.getItem('studyspace_active_role')==='STUDENT')sessionStorage.removeItem('studyspace_active_role');location.assign('/login.html');};
setInterval(()=>{if(document.visibilityState==='visible'&&studentScreen==='classes')loadClassDiscussions();},5000);
async function startAiReview(){const button=$('ai-review-button');button.disabled=true;button.textContent='Đang chuẩn bị bài ôn…';try{activeAttempt=await api('ai/review-quiz','POST');renderQuiz(false);}catch(error){toast(error.message==='Hãy hoàn thành ít nhất một quiz để hệ thống xác định chương cần ôn.'?error.message:'Chưa thể tạo bài ôn lúc này. Hãy thử lại.');}finally{button.disabled=false;button.textContent='✦ Ôn tập cá nhân hóa';}}
async function askAi(){const input=$('ai-question'),button=$('ai-ask-button'),answer=$('ai-answer'),question=input.value.trim();if(!question){input.focus();return;}let retry=$('ai-retry-button');if(!retry){retry=document.createElement('button');retry.id='ai-retry-button';retry.className='ai-explain-button ai-retry-button';retry.type='button';retry.textContent='Thử lại';answer.after(retry);retry.onclick=askAi;}retry.hidden=true;button.disabled=true;answer.textContent='Gia sư đang xem tiến độ học của bạn…';try{const result=await api('ai/chat','POST',{question,lessonContext:activeLesson?.content||''});answer.innerHTML=`<span class="ai-provider-label">${aiProviderLabel(result.provider)}</span><br>${escapeHtml(result.answer)}`;retry.hidden=result.provider!=='offline';retry.textContent='Thử lại với AI';if(result.provider!=='offline')input.value='';}catch{answer.textContent='AI đang tạm thời không khả dụng. Bạn vẫn có thể xem gợi ý dựa trên tiến độ học đã lưu.';retry.hidden=false;retry.textContent='Thử lại';}finally{button.disabled=false;}}
$('ai-review-button').onclick=startAiReview;$('ai-ask-button').onclick=askAi;$('ai-question').addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();askAi();}});
const renderStudentHome=render;
render=function(){
 renderStudentHome();
 document.querySelectorAll('[data-course]').forEach(card=>{
  const course=courses.find(item=>Number(item.id)===Number(card.dataset.course));
  if(!course)return;
  const lessonCount=Number(course.lesson_count||0),completed=Number(course.completed_count||0);
  const isComplete=lessonCount>0&&completed>=lessonCount;
  const isStarted=courseProgressState(course)==='Đang học';
  const label=isComplete?'Hoàn thành':isStarted?'Đang học':'Chưa bắt đầu';
  const state=document.createElement('span');
  state.className='course-card-state '+(isComplete?'is-complete':isStarted?'is-active':'is-new');
  state.textContent=label;
  card.querySelector('.course-card-top').append(state);
  card.querySelector('.course-open').innerHTML=`${isComplete?'Xem lại':isStarted?'Tiếp tục học':'Bắt đầu học'} <span>→</span>`;
 });
};
const openStudentCourse=openCourse;
openCourse=async function(id){
 try{await api(`courses/${id}/start`,'PUT',{});courses=await api('courses');render();await openStudentCourse(id);}
 catch(error){toast(error.message);}
};
let studentScreen='home',studentScreenStack=[],activeLesson=null;
function quizStorageKey(){return `studyspace_active_quiz_${token.slice(-12)}`;}
function syncQuizDeadline(){if(activeAttempt?.deadline!=null&&activeAttempt?.server_now!=null)activeAttempt.clientDeadline=Date.now()+Number(activeAttempt.deadline)-Number(activeAttempt.server_now);}
function rememberAttempt(){if(activeAttempt?.id)sessionStorage.setItem(quizStorageKey(),JSON.stringify({id:activeAttempt.id,marked:[...quizMarked]}));}
async function restoreActiveAttempt(){try{const saved=JSON.parse(sessionStorage.getItem(quizStorageKey())||'null');if(!saved?.id)return;activeAttempt=await api(`attempts/${saved.id}`);syncQuizDeadline();quizMarked=new Set((saved.marked||[]).map(Number));if(activeAttempt.submitted_at){sessionStorage.removeItem(quizStorageKey());return;}renderQuiz(false);enterStudentScreen('quiz');}catch{sessionStorage.removeItem(quizStorageKey());}}
let studyData={data:{sessions:[]}};
function syncStudentScreen(){
 $('home-view').hidden=studentScreen!=='home';
 $('courses-view').hidden=studentScreen!=='courses';
 $('classes-view').hidden=studentScreen!=='classes';
 $('course-view').hidden=studentScreen!=='course';
 $('lesson-view').hidden=studentScreen!=='lesson';
 $('quiz-view').hidden=studentScreen!=='quiz';
 $('report-view').hidden=studentScreen!=='report';
 $('home-button').classList.toggle('nav-current',studentScreen==='home');
 $('courses-nav').classList.toggle('nav-current',studentScreen==='courses');
 $('classes-nav').classList.toggle('nav-current',studentScreen==='classes');
 $('report-nav').classList.toggle('nav-current',studentScreen==='report');
 window.scrollTo({top:0,behavior:'instant'});
}
function enterStudentScreen(next){
 if(next===studentScreen)return;
 if(studentScreen==='classes'&&next!=='classes')cancelDiscussionRequests();
 if(next==='quiz')$('back-quiz').textContent=studentScreen==='lesson'?'← Quay lại bài học':studentScreen==='course'?'← Quay lại môn học':'← Quay lại tổng quan';
 studentScreenStack.push(studentScreen);
 studentScreen=next;
 history.pushState({studySpaceView:next},'',`#${next}`);
 syncStudentScreen();
 if(next==='classes')loadClassDiscussions();
}
function backStudentScreen(){
 if(history.state?.studySpaceView){history.back();return;}
 studentScreen=studentScreenStack.pop()||'home';
 syncStudentScreen();
 renderCurrentStudentScreen();
}
function renderCurrentStudentScreen(){
 if(studentScreen==='course')renderCourse();
 else if(studentScreen==='lesson'&&activeLesson){const found=findLesson(activeLesson.id);if(found)renderLesson(found.chapter);}
 else if(studentScreen==='classes')renderClasses();
 else if(studentScreen==='report')renderStudyReport();
}
window.addEventListener('popstate',event=>{
 const previous=studentScreen;
 studentScreen=event.state?.studySpaceView||studentScreenStack.pop()||'home';
 if(previous==='classes'&&studentScreen!=='classes')cancelDiscussionRequests();
 syncStudentScreen();
 renderCurrentStudentScreen();
});
showHome=function(){
 cancelDiscussionRequests();
 studentScreen='home';studentScreenStack=[];activeCourse=null;activeLesson=null;
 syncStudentScreen();
 history.replaceState({studySpaceHome:true},'',location.pathname+location.search);
};
$('home-button').onclick=showHome;
$('back-courses').onclick=backStudentScreen;
$('back-lesson').onclick=backStudentScreen;
$('back-quiz').onclick=backStudentScreen;
$('back-report').onclick=backStudentScreen;
openCourse=async function(id){
 try{
  await api(`courses/${id}/start`,'PUT',{});
  [courses,progress]=await Promise.all([api('courses'),api('progress')]);
  const course=await api('courses/'+id);activeCourse=course;render();renderCourse();enterStudentScreen('course');
 }catch(error){toast(error.message);}
};
renderCourse=function(){
 if(!activeCourse)return;
 const chapters=activeCourse.chapters||[];
 $('course-content').innerHTML=`<header class="course-detail-head"><span class="student-kicker">MÔN HỌC</span><h1>${escapeHtml(activeCourse.title)}</h1><p>${escapeHtml(activeCourse.description||'')}</p></header>${chapters.map(ch=>`<section class="chapter-block"><h2 class="chapter-title">${escapeHtml(ch.title)}</h2><div class="chapter-lessons">${(ch.lessons||[]).map((lesson,index)=>`<article class="lesson-row"><div><span class="lesson-order">BÀI ${index+1}</span><h3>${escapeHtml(lesson.title)}</h3><p>${lesson.completed?'Đã hoàn thành':'Bài học theo lộ trình của chương.'}</p></div><div class="lesson-actions">${lesson.completed?'<span class="lesson-state">✓ Đã học</span>':''}<button data-open-lesson="${lesson.id}" ${lesson.locked?'disabled':''}>${lesson.locked?'Hoàn thành bài trước':lesson.completed?'Xem lại bài':'Mở bài học'}</button></div></article>`).join('')||empty('Chưa có bài học','Chương này chưa có nội dung.')}</div>${Number(ch.question_count)>0?`<button class="chapter-quiz" data-quiz="${ch.id}">Làm quiz · ${ch.question_count} câu</button>`:''}</section>`).join('')||empty('Chưa có nội dung','Môn học chưa được thêm chương.')}`;
 document.querySelectorAll('[data-open-lesson]').forEach(button=>button.onclick=()=>openLesson(Number(button.dataset.openLesson)));
 document.querySelectorAll('[data-quiz]').forEach(button=>button.onclick=()=>startQuiz(button.dataset.quiz));
};
function findLesson(id){for(const chapter of activeCourse?.chapters||[])for(const lesson of chapter.lessons||[])if(Number(lesson.id)===Number(id))return{chapter,lesson};return null;}
function openLesson(id){
 const found=findLesson(id);if(!found)return;
 activeLesson=found.lesson;renderLesson(found.chapter);enterStudentScreen('lesson');
}
function renderLesson(chapter){
 if(!activeLesson)return;
 const lessons=(chapter.lessons||[]),index=lessons.findIndex(item=>Number(item.id)===Number(activeLesson.id)),next=lessons.slice(index+1).find(item=>!item.locked);
 $('lesson-content').innerHTML=`<article class="lesson-page"><header class="course-detail-head"><span class="student-kicker">${escapeHtml(chapter.title)}</span><h1>${escapeHtml(activeLesson.title)}</h1><p>${activeLesson.completed?'Bài học đã được lưu vào tiến độ của bạn.':'Học xong nội dung rồi đánh dấu để lưu tiến độ.'}</p></header><div class="lesson-body">${escapeHtml(activeLesson.content||'Nội dung bài học đang được cập nhật.').replace(/\n/g,'<br>')}${activeLesson.material_url?`<p class="lesson-material"><a href="${escapeHtml(activeLesson.material_url)}" target="_blank" rel="noopener">Mở hoặc tải tài liệu ↗</a></p>`:''}</div><div class="lesson-footer">${activeLesson.completed?'<span class="lesson-state">✓ Đã hoàn thành</span>':`<button class="button button-primary" id="complete-current-lesson" type="button">Đánh dấu đã học</button>`}${next?`<button class="button button-secondary" id="next-lesson" type="button">Bài tiếp theo →</button>`:''}</div></article>`;
 const complete=$('complete-current-lesson');if(complete)complete.onclick=()=>completeCurrentLesson(complete,chapter);
 const nextButton=$('next-lesson');if(nextButton)nextButton.onclick=()=>openLesson(Number(next.id));
}
async function completeCurrentLesson(button,chapter){
 button.disabled=true;
 try{
  await api(`lessons/${activeLesson.id}/complete`,'PUT',{});
  [courses,progress,activeCourse]=await Promise.all([api('courses'),api('progress'),api('courses/'+activeCourse.id)]);
  loadNextLesson().then(()=>renderContinueLearning());
  const found=findLesson(activeLesson.id);if(found)activeLesson=found.lesson;
  const updatedChapter=activeCourse.chapters.find(item=>Number(item.id)===Number(chapter.id));
  render();renderLesson(updatedChapter||chapter);toast('Đã lưu tiến độ bài học.');
 }catch(error){button.disabled=false;toast(error.message);}
}
startQuiz=async function(chapterId){
 try{activeAttempt=await api(`chapters/${chapterId}/attempts`,'POST');syncQuizDeadline();quizWarned=false;quizMarked=new Set();rememberAttempt();renderQuiz(false);enterStudentScreen('quiz');}
 catch(error){toast(error.message);}
};
renderQuiz=function(submitted){
 const qs=activeAttempt.questions||[];
 if(quizTimer)clearInterval(quizTimer);
 $('quiz-content').innerHTML=`<h1>${submitted?'Kết quả của bạn':'Cùng kiểm tra kiến thức'}</h1><p class="quiz-subtitle">${submitted?'Bài làm đã được lưu vào tiến độ.':`${qs.length} câu hỏi`} <strong id="quiz-countdown"></strong>${activeAttempt.provider==='offline'?' · Bộ ôn tập tạo từ bài học và câu sai đã lưu':''}</p>${!submitted?`<nav class="quiz-question-nav" aria-label="Điều hướng câu hỏi">${qs.map((q,index)=>`<button type="button" data-question-nav="${index}" class="${q.selected_option!==null?'is-answered':''} ${quizMarked.has(Number(q.question_id))?'is-marked':''}">${index+1}</button>`).join('')}</nav>`:''}${qs.map((q,index)=>`<section class="quiz-question" id="quiz-question-${index}"><b>${index+1}. ${escapeHtml(q.prompt)}</b>${[q.option_a,q.option_b,q.option_c,q.option_d].map((option,i)=>`<label class="quiz-option"><input type="radio" name="q-${q.question_id}" value="${i}" ${q.selected_option===i?'checked':''} ${submitted?'disabled':''}><span>${escapeHtml(option)}</span></label>`).join('')}${!submitted?`<button type="button" class="quiz-mark ${quizMarked.has(Number(q.question_id))?'is-marked':''}" data-mark-question="${q.question_id}">${quizMarked.has(Number(q.question_id))?'★ Đã đánh dấu':'☆ Đánh dấu xem lại'}</button>`:''}${submitted&&q.explanation?`<small>${escapeHtml(q.selected_option===q.correct_option?'Đúng. ':'Chưa chính xác. ')}${escapeHtml(q.explanation)}</small>`:''}${submitted&&q.selected_option!==null&&q.selected_option!==q.correct_option?`<button class="ai-explain-button" data-explain="${index}" type="button">Hỏi AI giải thích</button><p class="ai-explain-result" id="ai-explain-${index}" aria-live="polite"></p>`:''}</section>`).join('')}${submitted?`<div class="quiz-result">Điểm: ${activeAttempt.score}/${activeAttempt.total} câu (${Math.round(100*activeAttempt.score/Math.max(1,activeAttempt.total))}%)</div><button class="quiz-submit" id="done-quiz">Quay lại nội dung học</button>`:'<button class="quiz-submit" id="submit-quiz">Nộp bài và xem kết quả</button>'}`;
 if(!submitted){const tick=()=>{const seconds=Math.max(0,Math.ceil((Number(activeAttempt.clientDeadline??activeAttempt.deadline)-Date.now())/1000)),clock=$('quiz-countdown');if(clock){clock.textContent=`Còn ${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`;clock.classList.toggle('is-warning',seconds<=60);}if(seconds<=60&&!quizWarned){quizWarned=true;toast('Còn 1 phút. Hãy kiểm tra và nộp bài.');}if(seconds===0){clearInterval(quizTimer);submitQuiz(true);}};tick();quizTimer=setInterval(tick,1000);$('submit-quiz').onclick=()=>submitQuiz(false);document.querySelectorAll('[data-mark-question]').forEach(button=>button.onclick=()=>{const id=Number(button.dataset.markQuestion),marked=quizMarked.has(id);marked?quizMarked.delete(id):quizMarked.add(id);button.classList.toggle('is-marked',!marked);button.textContent=marked?'☆ Đánh dấu xem lại':'★ Đã đánh dấu';document.querySelectorAll('[data-question-nav]').forEach(nav=>{if(Number(nav.textContent)===Number(button.closest('.quiz-question').id.split('-').at(-1))+1)nav.classList.toggle('is-marked',!marked);});rememberAttempt();});document.querySelectorAll('[data-question-nav]').forEach(button=>button.onclick=()=>document.getElementById(`quiz-question-${button.dataset.questionNav}`)?.scrollIntoView({behavior:'smooth',block:'center'}));document.querySelectorAll('.quiz-option input').forEach(input=>input.onchange=()=>{const q=qs.find(item=>String(item.question_id)===input.name.slice(2));if(q)q.selected_option=Number(input.value);rememberAttempt();document.querySelectorAll('[data-question-nav]').forEach((button,index)=>button.classList.toggle('is-answered',qs[index].selected_option!==null));clearTimeout(quizSaveTimer);quizSaveTimer=setTimeout(saveQuizDraft,350);});}
 else{$('done-quiz').onclick=async()=>{backStudentScreen();await load();};document.querySelectorAll('[data-explain]').forEach(button=>button.onclick=()=>explainWrongAnswer(Number(button.dataset.explain),button));}
};
const renderQuizWithProvider=renderQuiz;
renderQuiz=function(submitted){
 renderQuizWithProvider(submitted);
 if(activeAttempt?.provider){const subtitle=document.querySelector('.quiz-subtitle'),badge=document.createElement('span');badge.className='ai-provider-label';badge.textContent=aiProviderLabel(activeAttempt.provider);subtitle?.append(' ',badge);}
};
$('ai-review-button').onclick=async()=>{
 const button=$('ai-review-button');button.disabled=true;
 button.textContent='Đang chuẩn bị bài ôn…';
 try{activeAttempt=await api('ai/review-quiz','POST');syncQuizDeadline();quizWarned=false;quizMarked=new Set();rememberAttempt();renderQuiz(false);enterStudentScreen('quiz');}
 catch(error){toast(error.message==='Hãy hoàn thành ít nhất một quiz để hệ thống xác định chương cần ôn.'?error.message:'Chưa thể chuẩn bị bài ôn lúc này. Hãy thử lại.');}
 finally{button.disabled=false;button.textContent='✦ Ôn tập cá nhân hóa';}
};
function focusSessions(){return (studyData?.data?.sessions||[]).filter(session=>session.sessionType==='focus'&&Number(session.durationMinutes)>0&&Number(session.timestamp)>0);}
function weeklyFocus(){
 const now=new Date(),monday=new Date(now);monday.setHours(0,0,0,0);monday.setDate(now.getDate()-((now.getDay()+6)%7));
 const days=Array.from({length:7},(_,index)=>{const date=new Date(monday);date.setDate(monday.getDate()+index);return{date,label:['T2','T3','T4','T5','T6','T7','CN'][index],minutes:0};});
 for(const session of focusSessions()){const date=new Date(Number(session.timestamp));date.setHours(0,0,0,0);const index=Math.round((date-monday)/86400000);if(index>=0&&index<7)days[index].minutes+=Number(session.durationMinutes);}
 return days;
}
function focusChartMarkup(compact=false){
 const days=weeklyFocus(),max=Math.max(30,...days.map(day=>day.minutes)),total=days.reduce((sum,day)=>sum+day.minutes,0),today=(new Date().getDay()+6)%7;
 if(!total)return `<div class="focus-empty"><span class="focus-empty-icon" aria-hidden="true">◷</span><b>Chưa có phiên tập trung nào tuần này</b><p>Bắt đầu một phiên tập trung để theo dõi nhịp học của bạn.</p><button type="button" class="button button-primary" data-start-focus>Bắt đầu tập trung</button></div>`;
 return `<div class="focus-chart ${compact?'focus-chart-compact':''}" role="img" aria-label="Thời lượng tập trung từng ngày trong tuần: ${days.map(day=>`${day.label} ${day.minutes} phút`).join(', ')}">${days.map((day,index)=>`<div class="focus-chart-day"><span class="focus-chart-value">${day.minutes}<small>p</small></span><div class="focus-chart-track"><i class="${index===today?'is-today':''}" style="height:${day.minutes?Math.max(7,Math.round(day.minutes/max*100)):3}%"></i></div><b>${day.label}</b></div>`).join('')}</div><p class="focus-chart-total">${total?`Tuần này bạn đã tập trung <strong>${total} phút</strong>.`:'Chưa có phiên tập trung trong tuần này.'}</p>`;
}
function ensureStudyReportUI(){
 if(!$('study-time-preview'))$('student-metrics').insertAdjacentHTML('afterend',`<section class="study-time-preview" id="study-time-preview"><header><div><span class="student-kicker">NHỊP HỌC 7 NGÀY</span><h2>Thời lượng tập trung</h2><p id="study-time-summary">Đang đồng bộ phiên học…</p></div><button class="report-open-button" id="report-open-button" type="button">Báo cáo chi tiết <span>→</span></button></header><div id="study-time-chart"></div></section>`);
 $('report-open-button').onclick=openStudyReport;
}
function ensureStudentSectionPages(){
 const main=document.querySelector('.student-main');
 if(!$('courses-view')){
  const section=[...document.querySelectorAll('#home-view>.student-section')].find(item=>!item.classList.contains('class-section')&&!item.classList.contains('report-section'));
  const page=document.createElement('section');page.id='courses-view';page.className='task-view student-list-page';page.hidden=true;
  page.innerHTML='<header class="task-page-heading"><h1>Môn học</h1></header>';
  if(section){page.append(section);main.append(page);}
 }
 if(!$('classes-view')){
  const section=$('student-classes'),page=document.createElement('section');page.id='classes-view';page.className='task-view student-list-page';page.hidden=true;
  page.innerHTML='<header class="task-page-heading"><h1>Lớp học</h1></header>';
  if(section){page.append(section);main.append(page);}
 }
}
function ensureStudentNavigation(){
 const nav=document.querySelector('.student-topbar nav');let anchor=$('home-button');
 for(const [id,label,screen]of [['courses-nav','Môn học','courses'],['classes-nav','Lớp học','classes'],['report-nav','Báo cáo','report']]){
  let button=$(id);if(!button){button=document.createElement('button');button.id=id;button.type='button';button.textContent=label;anchor.insertAdjacentElement('afterend',button);}
  button.onclick=()=>screen==='report'?openStudyReport():enterStudentScreen(screen);anchor=button;
 }
}
function ensureContinueLearningUI(){
 const heading=document.querySelector('.student-heading');
 if(!$('continue-learning')&&heading)heading.insertAdjacentHTML('afterend',`<section class="continue-learning" id="continue-learning"><div class="continue-copy"><span class="continue-state" id="continue-state">ĐANG TẢI</span><h2 id="continue-title">Tiếp tục hành trình học tập</h2><p id="continue-description">Một bước nhỏ hôm nay sẽ giúp bạn tiến gần mục tiêu hơn.</p><div class="continue-progress"><span><i id="continue-progress-bar"></i></span><b id="continue-progress-label">0%</b></div><button class="continue-action" id="continue-course-button" type="button">Mở lộ trình <span>→</span></button></div><button class="continue-visual" id="continue-next-lesson" type="button"><span class="continue-visual-book" aria-hidden="true">▤</span><span class="continue-visual-step"><b id="continue-lesson-title">Bài tiếp theo</b><span id="continue-lesson-course"></span><i aria-hidden="true"></i></span></button></section>`);
}
async function loadNextLesson(){
 nextLesson=null;
 const candidates=[...courses].sort((a,b)=>Number(b.started_at!=null)-Number(a.started_at!=null));
 const details=await Promise.allSettled(candidates.map(course=>api(`courses/${course.id}`)));
 for(let index=0;index<details.length;index++){
  const result=details[index];if(result.status!=='fulfilled')continue;
  for(const chapter of result.value.chapters||[]){const lesson=(chapter.lessons||[]).find(item=>!item.completed&&!item.locked);if(lesson){nextLesson={courseId:Number(candidates[index].id),lessonId:Number(lesson.id),lessonTitle:lesson.title,courseTitle:candidates[index].title};return;}}
 }
}
function renderContinueLearning(){
 ensureContinueLearningUI();
 const unfinished=courses.find(course=>(course.started_at!=null||Number(course.completed_count)>0)&&Number(course.lesson_count)>Number(course.completed_count||0))||courses.find(course=>Number(course.lesson_count)>Number(course.completed_count||0));
 const course=unfinished||courses[0],done=Number(course?.completed_count||0),total=Number(course?.lesson_count||0),pct=total?Math.round(done*100/total):0,started=courseProgressState(course)==='Đang học',complete=courseProgressState(course)==='Hoàn thành';
 $('continue-state').textContent=courseProgressState(course).toLocaleUpperCase('vi');
 $('continue-title').textContent=course?(complete?'Bạn đã hoàn thành':started?'Tiếp tục học': 'Bắt đầu với môn học'):'Chưa có khóa học được xuất bản';
 $('continue-description').textContent=course?`${course.title} · ${done}/${total} bài học đã hoàn thành${started&&!complete?' · Tiến độ của bạn đã được lưu.':''}`:'Các môn học được giảng viên xuất bản sẽ xuất hiện tại đây.';
 $('continue-progress-bar').style.width=`${pct}%`;$('continue-progress-label').textContent=`${pct}%`;
 $('continue-course-button').innerHTML=`${complete?'Ôn lại lộ trình':started?'Tiếp tục học':course?'Bắt đầu học':'Xem môn học'} <span>→</span>`;
 $('continue-learning').classList.toggle('is-complete',Boolean(complete));
 $('continue-course-button').onclick=()=>course?openCourse(Number(course.id)):enterStudentScreen('courses');
 $('continue-lesson-title').textContent=nextLesson?.lessonTitle||(complete?'Đã hoàn thành tất cả bài học':'Chưa có bài học tiếp theo');
 $('continue-lesson-course').textContent=nextLesson?.courseTitle||'BÀI HỌC TIẾP THEO';
 $('continue-next-lesson').disabled=!nextLesson;
 $('continue-next-lesson').onclick=async()=>{if(!nextLesson)return;await openCourse(nextLesson.courseId);if(findLesson(nextLesson.lessonId))openLesson(nextLesson.lessonId);};
}
function renderStudyTime(){
 ensureStudyReportUI();const total=weeklyFocus().reduce((sum,day)=>sum+day.minutes,0);
 $('study-time-summary').textContent=total?`${total} phút học tập trung trong tuần này`:'Thời gian được tính từ các phiên tập trung đã đồng bộ.';
 $('study-time-chart').innerHTML=focusChartMarkup(true);
 document.querySelectorAll('[data-start-focus]').forEach(button=>button.onclick=startWebFocus);
}
function startWebFocus(){
 const dialog=document.createElement('dialog');dialog.className='web-focus-dialog';dialog.innerHTML='<form method="dialog"><button class="modal-close" aria-label="Đóng">×</button></form><span class="student-kicker">PHIÊN TẬP TRUNG</span><h2>25 phút học sâu</h2><p id="web-focus-clock">25:00</p><button id="web-focus-stop" class="button button-primary" type="button">Kết thúc và lưu phiên</button>';document.body.append(dialog);dialog.showModal();let remaining=1500;const clock=dialog.querySelector('#web-focus-clock');const timer=setInterval(()=>{remaining--;clock.textContent=`${Math.floor(remaining/60)}:${String(remaining%60).padStart(2,'0')}`;if(remaining<=0){clearInterval(timer);saveWebFocus(dialog,25);}},1000);dialog.addEventListener('close',()=>clearInterval(timer));dialog.querySelector('#web-focus-stop').onclick=()=>{const minutes=Math.floor((1500-remaining)/60);if(minutes<1){toast('Hãy tập trung ít nhất 1 phút để lưu phiên.');return;}clearInterval(timer);saveWebFocus(dialog,minutes);};
}
async function saveWebFocus(dialog,minutes){if(dialog.dataset.saved)return;dialog.dataset.saved='true';try{const sessions=[...(studyData?.data?.sessions||[]),{id:Math.floor(Date.now()%2000000000)+1,taskId:null,taskTitle:'Phiên tập trung trên web',durationMinutes:minutes,sessionType:'focus',timestamp:Date.now()}];const result=await api('personal-study','PUT',{expectedVersion:Number(studyData.version||0),requestId:crypto.randomUUID(),data:{plans:studyData?.data?.plans||[],sessions}});studyData=result;dialog.close();dialog.remove();renderStudyTime();if(studentScreen==='report')renderStudyReport();toast(`Đã lưu phiên tập trung ${minutes} phút.`);}catch(error){dialog.dataset.saved='';toast(error.message);}}
function renderStudyReport(){
 const sessions=focusSessions().sort((a,b)=>Number(b.timestamp)-Number(a.timestamp)),days=weeklyFocus(),weekMinutes=days.reduce((sum,day)=>sum+day.minutes,0),weekSessions=sessions.filter(item=>{const date=new Date(Number(item.timestamp));const start=new Date();start.setHours(0,0,0,0);start.setDate(start.getDate()-((start.getDay()+6)%7));return date>=start;});
 const quizzes=(progress.history||[]).filter(item=>item.submitted_at!=null).slice(0,8);
 $('report-content').innerHTML=`<header class="report-hero"><div><span class="student-kicker">TIẾN ĐỘ CỦA BẠN</span><h1>Báo cáo học tập</h1><p>Theo dõi thời gian tập trung và kết quả quiz đã lưu.</p></div><span class="report-hero-icon" aria-hidden="true">↗</span></header><section class="report-metrics"><article><span>THỜI GIAN TUẦN NÀY</span><strong>${weekMinutes}<small> phút</small></strong><p>Thời lượng phiên tập trung</p></article><article><span>PHIÊN TẬP TRUNG</span><strong>${weekSessions.length}</strong><p>Phiên đã đồng bộ trong tuần</p></article><article><span>QUIZ ĐÃ NỘP</span><strong>${quizzes.length}</strong><p>Lượt làm được ghi nhận</p></article></section><section class="report-panel"><header><div><span class="student-kicker">THỨ HAI – CHỦ NHẬT</span><h2>Thời lượng học theo ngày</h2></div><span class="report-period">7 ngày gần nhất</span></header>${focusChartMarkup()}${weekMinutes===0?'<p class="report-empty-note">Biểu đồ sẽ cập nhật sau khi StudySpace đồng bộ phiên tập trung đầu tiên.</p>':''}</section><div class="report-columns"><section class="report-panel"><header><div><span class="student-kicker">HOẠT ĐỘNG GẦN ĐÂY</span><h2>Phiên tập trung</h2></div></header><div class="report-activity-list">${sessions.slice(0,8).map(session=>`<article><span class="activity-dot"></span><div><b>${escapeHtml(session.taskTitle||'Phiên tập trung')}</b><small>${new Date(Number(session.timestamp)).toLocaleString('vi-VN',{dateStyle:'medium',timeStyle:'short'})}</small></div><strong>${Number(session.durationMinutes)} phút</strong></article>`).join('')||empty('Chưa có phiên tập trung','Phiên học sẽ xuất hiện tại đây sau khi đồng bộ từ ứng dụng.')}</div></section><section class="report-panel"><header><div><span class="student-kicker">KẾT QUẢ LUYỆN TẬP</span><h2>Lịch sử quiz</h2></div></header><div class="report-activity-list">${quizzes.map(item=>`<article><span class="activity-dot quiz-dot"></span><div><b>${escapeHtml(item.chapter_title||'Quiz ôn tập')}</b><small>${new Date(Number(item.submitted_at)).toLocaleDateString('vi-VN')}</small></div><strong>${Math.round(100*Number(item.score)/Math.max(1,Number(item.total)))}%</strong></article>`).join('')||empty('Chưa có quiz đã nộp','Kết quả sẽ được lưu tại đây sau khi bạn hoàn thành quiz.')}</div></section></div>`;
}
function openStudyReport(){renderStudyReport();enterStudentScreen('report');}
function syncAiRecommendationSource(){
 const badge=$('ai-recommendation-source');if(!badge)return;
 const hasResults=(progress.history||[]).some(item=>item.submitted_at!=null);
 badge.textContent=hasResults?'Gợi ý offline · từ kết quả quiz':'Gợi ý offline · bắt đầu từ lộ trình';
}
const renderWithCourseState=render;
render=function(){ensureStudentSectionPages();ensureStudentNavigation();ensureContinueLearningUI();renderWithCourseState();syncAiRecommendationSource();renderContinueLearning();renderStudyTime();document.querySelectorAll('[data-empty-action="courses"]').forEach(button=>button.onclick=()=>enterStudentScreen('courses'));};
function studentLoadError(message){
 const error=message?.name==='TimeoutError'?'Máy chủ phản hồi quá lâu. Kiểm tra kết nối rồi thử lại.':message?.name==='TypeError'||message?.name==='AbortError'?'Không kết nối được máy chủ. Kiểm tra localhost rồi thử lại.':message?.message||'Không thể tải dữ liệu. Kiểm tra kết nối rồi thử lại.';
 return `<div class="student-data-error" role="alert"><span aria-hidden="true">!</span><div><b>Chưa tải được phần này</b><p>${escapeHtml(error)}</p></div><button type="button" data-retry-student>Tải lại</button></div>`;
}
function showStudentDataError(target,message){
 const host=$(target);if(!host)return;
 host.innerHTML=studentLoadError(message);
 host.querySelector('[data-retry-student]').onclick=()=>load();
}
load=async function(){
 if(!token){location.replace('/login.html');return;}
 if(studentDataLoading)return;
 studentDataLoading=true;
 try{
  let account;
  try{account=await api('auth/me');}
  catch(error){showStudentDataError('course-list',error);showStudentDataError('history-list',error);showStudentDataError('student-class-list',error);return;}
  if(account.role!=='STUDENT'){location.replace(account.role==='TEACHER'?'/teacher.html':'/admin.html');return;}
  currentAccount=account;
  sessionStorage.setItem('studyspace_active_role','STUDENT');
  const greeting=account.displayName||account.username;$('username-label').textContent=greeting;$('greeting-name').textContent=greeting;$('avatar').textContent=(greeting.trim()[0]||'H').toLocaleUpperCase('vi');
  const results=await Promise.allSettled([api('courses'),api('progress'),api('classes/mine'),api('personal-study')]);
  const [courseResult,progressResult,classResult,studyResult]=results;
  courses=courseResult.status==='fulfilled'?courseResult.value:[];
  progress=progressResult.status==='fulfilled'?progressResult.value:{};
  studentClasses=classResult.status==='fulfilled'?classResult.value:[];
  studyData=studyResult.status==='fulfilled'?studyResult.value:{data:{sessions:[]}};
  loadNextLesson().then(()=>{if(studentScreen==='home')renderContinueLearning();});
  if(location.hash==='#classes'){
   studentScreen='classes';studentScreenStack=[];
   history.replaceState({studySpaceView:'classes'},'',location.pathname+location.search+'#classes');
   syncStudentScreen();
  }
  progressLoadError=progressResult.status==='rejected'?progressResult.reason:null;
  render();renderClasses();await restoreActiveAttempt();
  if(courseResult.status==='rejected')showStudentDataError('course-list',courseResult.reason);
  if(progressResult.status==='rejected')showStudentDataError('history-list',progressResult.reason);
  if(classResult.status==='rejected')showStudentDataError('student-class-list',classResult.reason);
  if(progressLoadError){$('completed-count').textContent='—';$('quiz-count').textContent='—';$('average-score').textContent='—';}
  const welcome=sessionStorage.getItem('studyspace_welcome');if(welcome){toast(welcome);sessionStorage.removeItem('studyspace_welcome');}
 }catch(error){
  console.error('StudySpace student screen failed to load:',error);
  $('course-total').textContent='Chưa tải được';
  showStudentDataError('course-list',error);
  showStudentDataError('history-list',error);
  showStudentDataError('student-class-list',error);
  toast(error.message||'Không thể tải dữ liệu học viên.');
 }
 finally{studentDataLoading=false;}
};
ensureStudentSectionPages();ensureStudentNavigation();ensureStudyReportUI();
load();
